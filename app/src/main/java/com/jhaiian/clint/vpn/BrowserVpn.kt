package com.jhaiian.clint.vpn

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress

/**
 * The browser's built-in VPN: a WireGuard tunnel from the Cruise VPN backend that carries only this
 * app's traffic. Ported from OneApp VPN's `ConnectWireGuardUseCase` + `WireGuardTunnelManager`.
 *
 * Quota and access are enforced by the backend for WireGuard (`/vpn/connect` refuses expired or
 * credit-less accounts and opens the freemium metering session itself); this client only has to
 * notice when the server ends the session, which [startPolling] does.
 */
object BrowserVpn {

    private const val TAG = "AetherNetVpn"
    private const val TUNNEL_NAME = "AetherNet"
    private const val PREF_COUNTRY_ID = "vpn_country_id"
    private const val SESSION_POLL_MS = 30_000L
    private const val HANDSHAKE_TIMEOUT_MS = 20_000L
    /** WireGuard re-handshakes every 2 minutes under keepalive; well past that the peer is gone. */
    private const val STALE_HANDSHAKE_MS = 200_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Mutex()

    private val _state = MutableStateFlow<BrowserVpnState>(BrowserVpnState.Disconnected)
    val state: StateFlow<BrowserVpnState> = _state.asStateFlow()

    private val _countries = MutableStateFlow<List<VpnCountry>>(emptyList())
    val countries: StateFlow<List<VpnCountry>> = _countries.asStateFlow()

    private val _countriesError = MutableStateFlow<String?>(null)
    val countriesError: StateFlow<String?> = _countriesError.asStateFlow()

    private lateinit var appContext: Context
    private lateinit var store: VpnSessionStore
    private lateinit var api: VpnApi
    private lateinit var auth: VpnAuthenticator
    private val backend: GoBackend by lazy { GoBackend(appContext) }

    private var connectJob: Job? = null
    private var pollJob: Job? = null
    private var peerPublicKey: String? = null
    private var tearingDown = false

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            // The system took the tunnel down under us (another VPN, permission revoked).
            if (newState == Tunnel.State.DOWN && !tearingDown && _state.value is BrowserVpnState.Connected) {
                Log.i(TAG, "Tunnel went down externally")
                scope.launch { disconnectInternal(failure = appContext.getString(R.string.vpn_error_revoked)) }
            }
        }
    }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        store = VpnSessionStore(appContext)
        api = VpnApi(store)
        auth = VpnAuthenticator(appContext, api, store)
        // A process that died with a tunnel up never closed its server session; close it now so the
        // backend frees this device's peer slot and stops metering.
        scope.launch {
            if (store.sessionOpen && _state.value is BrowserVpnState.Disconnected) {
                runCatching { authorized { api.disconnect() } }
                store.sessionOpen = false
            }
        }
    }

    val isActive: Boolean
        get() = _state.value.let { it is BrowserVpnState.Connected || it is BrowserVpnState.Connecting }

    fun selectedCountryId(context: Context): Long =
        PreferenceManager.getDefaultSharedPreferences(context).getLong(PREF_COUNTRY_ID, 0L)

    fun refreshCountries(force: Boolean = false) {
        if (!force && _countries.value.isNotEmpty()) return
        scope.launch {
            _countriesError.value = null
            try {
                _countries.value = loadCountries()
            } catch (e: Exception) {
                Log.w(TAG, "Loading servers failed", e)
                _countriesError.value = messageFor(e)
            }
        }
    }

    /** Needs VPN consent already granted (`VpnService.prepare` returned null). */
    fun connect(context: Context, country: VpnCountry?) {
        country?.let {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(PREF_COUNTRY_ID, it.id).apply()
        }
        connectJob?.cancel()
        _state.value = BrowserVpnState.Connecting(country)
        // Started while the user is in the app, so the foreground-service start is allowed.
        ContextCompat.startForegroundService(appContext, VpnForegroundService.startIntent(appContext))
        connectJob = scope.launch {
            lifecycle.withLock {
                if (tunnelIsUp()) tearDown(closeSession = true)
                try {
                    val target = resolveCountry(country)
                    _state.value = BrowserVpnState.Connecting(target)
                    val hosting = target.bestWireGuardHosting
                        ?: throw VpnApiException(404, "VPN_NO_SERVER_AVAILABLE", "No WireGuard server")
                    val params = openSession(target.id, hosting.protocolId)
                    store.sessionOpen = true
                    peerPublicKey = params.peerPublicKey
                    backend.setState(tunnel, Tunnel.State.UP, buildConfig(params))
                    if (!awaitHandshake(params.peerPublicKey)) {
                        throw IOException("Handshake timed out")
                    }
                    _state.value = BrowserVpnState.Connected(target, System.currentTimeMillis())
                    startPolling()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "Connect failed", e)
                    tearDown(closeSession = true)
                    VpnForegroundService.stop(appContext)
                    _state.value = BrowserVpnState.Failed(messageFor(e))
                }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch { disconnectInternal(failure = null) }
    }

    private suspend fun disconnectInternal(failure: String?) {
        lifecycle.withLock {
            if (_state.value is BrowserVpnState.Disconnected) return
            _state.value = BrowserVpnState.Disconnecting
            tearDown(closeSession = true)
            VpnForegroundService.stop(appContext)
            _state.value = failure?.let { BrowserVpnState.Failed(it) } ?: BrowserVpnState.Disconnected
        }
    }

    private suspend fun tearDown(closeSession: Boolean) {
        pollJob?.cancel()
        pollJob = null
        tearingDown = true
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            .onFailure { Log.w(TAG, "Tunnel down failed", it) }
        tearingDown = false
        peerPublicKey = null
        // After the tunnel is down, so this call leaves over the normal network. Only when a session
        // was actually opened: cancelling before that must not trigger a sign-in just to say goodbye.
        if (closeSession && store.sessionOpen) {
            runCatching { authorized { api.disconnect() } }
                .onFailure { e ->
                    if ((e as? VpnApiException)?.errorKey != "VPN_SESSION_NOT_FOUND") Log.w(TAG, "Session close failed", e)
                }
            store.sessionOpen = false
        }
    }

    private fun tunnelIsUp(): Boolean = runCatching { backend.getState(tunnel) == Tunnel.State.UP }.getOrDefault(false)

    private suspend fun loadCountries(): List<VpnCountry> =
        authorized { api.activeServers() }
            .filter { it.bestWireGuardHosting != null }
            .sortedWith(compareBy<VpnCountry> { it.order }.thenBy { it.name })

    private suspend fun resolveCountry(requested: VpnCountry?): VpnCountry {
        if (requested != null) return requested
        val list = _countries.value.ifEmpty { loadCountries().also { _countries.value = it } }
        val savedId = selectedCountryId(appContext)
        return list.firstOrNull { it.id == savedId } ?: list.firstOrNull()
            ?: throw VpnApiException(404, "VPN_NO_SERVER_AVAILABLE", "No WireGuard server")
    }

    /** `POST /vpn/connect`, rotating the device key once if the backend says it is still bound. */
    private suspend fun openSession(countryId: Long, protocolId: Int): WireGuardParams {
        val keys = keyPair(rotate = false)
        return try {
            authorized { api.connect(countryId, protocolId, keys.publicKey.toBase64()) }
        } catch (e: VpnApiException) {
            if (e.errorKey != "VPN_PUBLIC_KEY_IN_USE") throw e
            Log.i(TAG, "WireGuard key in use; rotating once")
            authorized { api.connect(countryId, protocolId, keyPair(rotate = true).publicKey.toBase64()) }
        }
    }

    private fun keyPair(rotate: Boolean): KeyPair {
        val saved = store.wireGuardPrivateKey
        if (!rotate && saved != null) {
            runCatching { return KeyPair(Key.fromBase64(saved)) }
        }
        return KeyPair().also { store.wireGuardPrivateKey = it.privateKey.toBase64() }
    }

    private fun buildConfig(params: WireGuardParams): Config {
        val iface = Interface.Builder()
            .setKeyPair(keyPair(rotate = false))
            .addAddress(InetNetwork.parse(params.address))
            .addDnsServer(InetAddress.getByName(params.dns))
            .setMtu(params.mtu)
            // Browser-only tunnel: the web views live in this process, so this carries every page.
            .includeApplication(appContext.packageName)
            .build()
        val peer = Peer.Builder()
            .parsePublicKey(params.peerPublicKey)
            .setEndpoint(InetEndpoint.parse(params.endpoint))
            .setPersistentKeepalive(params.persistentKeepalive)
        if (params.presharedKey.isNotBlank()) peer.parsePreSharedKey(params.presharedKey)
        val allowed = params.allowedIps.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { InetNetwork.parse(it) }
        allowed.forEach { peer.addAllowedIp(it) }
        // Without an IPv6 default route, IPv6 traffic would leave outside the tunnel.
        if (allowed.none { it.address is Inet6Address && it.mask == 0 }) peer.addAllowedIp(InetNetwork.parse("::/0"))
        return Config.Builder().setInterface(iface).addPeer(peer.build()).build()
    }

    private suspend fun awaitHandshake(peerKey: String): Boolean {
        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (latestHandshakeMillis(peerKey) > 0L) return true
            delay(500)
        }
        return latestHandshakeMillis(peerKey) > 0L
    }

    private fun latestHandshakeMillis(peerKey: String): Long = runCatching {
        backend.getStatistics(tunnel).peer(Key.fromBase64(peerKey))?.latestHandshakeEpochMillis() ?: 0L
    }.getOrDefault(0L)

    /**
     * Ends the tunnel when the backend ends the session (trial over, credits spent, replaced).
     * The polls ride the tunnel itself, so a dead peer shows up as network errors rather than a
     * closed session; a handshake gone stale is what tells those apart from a brief outage.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(SESSION_POLL_MS)
                val ended = try {
                    !authorized { api.hasSession() }
                } catch (e: Exception) {
                    val key = peerPublicKey ?: return@launch
                    val age = System.currentTimeMillis() - latestHandshakeMillis(key)
                    Log.w(TAG, "Session poll failed, handshake age ${age}ms", e)
                    age > STALE_HANDSHAKE_MS
                }
                if (ended) {
                    Log.i(TAG, "VPN session ended by the backend or the peer went away")
                    scope.launch { disconnectInternal(failure = appContext.getString(R.string.vpn_error_session_ended)) }
                    return@launch
                }
            }
        }
    }

    /** Runs [call] with a valid access token, signing in again once if the token is rejected. */
    private suspend fun <T> authorized(call: () -> T): T {
        auth.ensureSignedIn()
        return try {
            call()
        } catch (e: VpnApiException) {
            if (e.httpCode != 401) throw e
            auth.invalidateAccessToken()
            call()
        }
    }

    private fun messageFor(e: Exception): String {
        val c = appContext
        val key = (e as? VpnApiException)?.errorKey
        if (key != null) {
            // Play Integrity / key attestation rejections (x-error-key from play_integrity_service.py).
            val isIntegrity = key in INTEGRITY_ERROR_KEYS || key.startsWith("ATTESTATION_") || key.contains("INTEGRITY")
            return when {
                key == "NO_ADS_CREDITS" -> c.getString(R.string.vpn_error_no_credits)
                key == "VPN_SUBSCRIPTION_EXPIRED" -> c.getString(R.string.vpn_error_expired)
                key == "VPN_USER_DISABLED" -> c.getString(R.string.vpn_error_disabled)
                key == "VPN_NO_SERVER_AVAILABLE" -> c.getString(R.string.vpn_error_no_server)
                key == "VPN_PEER_UNREACHABLE" -> c.getString(R.string.vpn_error_unreachable)
                isIntegrity -> c.getString(R.string.vpn_error_integrity, key)
                else -> c.getString(R.string.vpn_error_generic, key)
            }
        }
        return when (e) {
            is com.google.android.play.core.integrity.StandardIntegrityException ->
                c.getString(R.string.vpn_error_integrity, "Play Integrity ${e.statusCode}")
            is com.wireguard.android.backend.BackendException ->
                if (e.reason == com.wireguard.android.backend.BackendException.Reason.VPN_NOT_AUTHORIZED) {
                    c.getString(R.string.vpn_error_permission)
                } else c.getString(R.string.vpn_error_tunnel)
            is IOException -> c.getString(R.string.vpn_error_network)
            else -> c.getString(R.string.vpn_error_generic, e.message ?: e.javaClass.simpleName)
        }
    }

    private val INTEGRITY_ERROR_KEYS = setOf(
        "PACKAGE_MISMATCH", "CERT_DIGEST_MISMATCH", "DEVICE_INTEGRITY_CHECK_FAILED", "BAD_INTEGRITY_TOKEN",
        "ATTESTATION_REQUIRED", "ATTESTATION_INVALID", "NONCE_EXPIRED", "NONCE_REUSED", "NONCE_MISMATCH",
        "VALIDATION_FAILED", "INTEGRITY_BACKOFF_ACTIVE", "PLAY_INTEGRITY_BACKOFF_ACTIVE"
    )
}
