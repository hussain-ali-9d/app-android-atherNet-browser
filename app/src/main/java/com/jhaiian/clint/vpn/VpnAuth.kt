package com.jhaiian.clint.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.jhaiian.clint.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate

/** Tokens and WireGuard keys, encrypted at rest (same scheme as OneApp's stores). */
internal class VpnSessionStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "aethernet_vpn_session",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }
    val accessTokenFresh: Boolean
        get() = accessToken != null && System.currentTimeMillis() < prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L) - REFRESH_MARGIN_MS

    fun saveTokens(signIn: VpnApi.SignIn) {
        if (signIn.accessToken.isBlank() || signIn.refreshToken.isBlank()) return
        prefs.edit()
            .putString(KEY_ACCESS, signIn.accessToken)
            .putString(KEY_REFRESH, signIn.refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, System.currentTimeMillis() + signIn.accessExpiresInSeconds * 1000L)
            .apply()
    }

    fun clearTokens() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_ACCESS_EXPIRES_AT).apply()
    }

    var wireGuardPrivateKey: String?
        get() = prefs.getString(KEY_WG_PRIVATE, null)
        set(value) { prefs.edit().putString(KEY_WG_PRIVATE, value).apply() }

    /** Set while the backend holds a session we opened, so a killed process can close it next launch. */
    var sessionOpen: Boolean
        get() = prefs.getBoolean(KEY_SESSION_OPEN, false)
        set(value) { prefs.edit().putBoolean(KEY_SESSION_OPEN, value).apply() }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEY_WG_PRIVATE = "wg_private_key"
        const val KEY_SESSION_OPEN = "session_open"
        const val REFRESH_MARGIN_MS = 60_000L
    }
}

/**
 * Guest sign-in through Play Integrity, as OneApp's `PlayIntegrityRepositoryImpl`:
 * nonce -> standard integrity token -> key attestation -> validate -> tokens.
 */
internal class VpnAuthenticator(
    private val context: Context,
    private val api: VpnApi,
    private val store: VpnSessionStore
) {
    private val mutex = Mutex()
    private var tokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    /** Makes sure a usable access token exists, refreshing or signing in again as needed. */
    suspend fun ensureSignedIn() = mutex.withLock {
        if (store.accessTokenFresh) return@withLock
        if (tryRefresh()) return@withLock
        signIn()
    }

    /** Called after a 401 on a protected route: the stored access token is no good. */
    suspend fun invalidateAccessToken() = mutex.withLock {
        if (tryRefresh()) return@withLock
        signIn()
    }

    private fun tryRefresh(): Boolean {
        val refresh = store.refreshToken ?: return false
        return try {
            store.saveTokens(api.refresh(refresh))
            true
        } catch (e: VpnApiException) {
            Log.w(TAG, "Token refresh rejected (${e.errorKey}); signing in again")
            store.clearTokens()
            false
        }
    }

    private suspend fun signIn() {
        val deviceId = deviceId()
        val appVersion = BuildConfig.VERSION_NAME
        if (BuildConfig.DEBUG) Log.d(TAG, "Signing in with device_id=$deviceId")
        val nonce = api.generateNonce(deviceId, appVersion)
        val integrityToken = requestIntegrityToken(nonce.nonce)
        val attestation = buildAttestation(nonce, integrityToken, deviceId, appVersion)
        val signIn = api.validateIntegrity(
            integrityToken = integrityToken,
            nonce = nonce.nonce,
            deviceId = deviceId,
            keyAttestation = attestation?.first,
            installationProof = attestation?.second,
            deviceInfo = JSONObject()
                .put("device_name", Build.MODEL)
                .put("device_type", "android")
                .put("device_model", Build.MODEL)
                .put("os_name", "Android")
                .put("os_version", Build.VERSION.RELEASE)
                .put("app_version", appVersion)
        )
        if (signIn.accessToken.isBlank()) {
            throw VpnApiException(200, "VALIDATION_EMPTY_TOKENS", "Sign-in returned no session")
        }
        store.saveTokens(signIn)
        Log.i(TAG, "Signed in as guest, group=${signIn.group}")
    }

    /** The backend's `device_id`: ANDROID_ID, which Android scopes per signing key. */
    @SuppressLint("HardwareIds")
    fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    private suspend fun requestIntegrityToken(nonce: String): String {
        repeat(2) { attempt ->
            val provider = tokenProvider ?: IntegrityManagerFactory.createStandard(context.applicationContext)
                .prepareIntegrityToken(
                    StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(BuildConfig.VPN_CLOUD_PROJECT_NUMBER.toLong())
                        .build()
                )
                .await()
                .also { tokenProvider = it }
            try {
                return provider.request(
                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(nonce).build()
                ).await().token()
            } catch (e: StandardIntegrityException) {
                // -19: the cached provider went stale; prepare a new one once.
                if (e.statusCode != PROVIDER_INVALID || attempt > 0) throw e
                tokenProvider = null
            }
        }
        error("unreachable")
    }

    /**
     * Key attestation bound to the nonce. `hmac_nonce` challenges need a pepper OneApp reads from
     * Firebase Remote Config, which this app does not have, so that mode sends no attestation and
     * leaves the verdict to the backend (it waives attestation for allow-listed test devices).
     */
    private fun buildAttestation(
        nonce: VpnApi.Nonce,
        integrityToken: String,
        deviceId: String,
        appVersion: String
    ): Pair<List<String>, String>? {
        // TODO(vpn-attestation): temporary. Flip VPN_SKIP_KEY_ATTESTATION back to false in
        // app/build.gradle.kts once the backend accepts this package's attestation.
        if (BuildConfig.VPN_SKIP_KEY_ATTESTATION) {
            Log.w(TAG, "Key attestation skipped: VPN_SKIP_KEY_ATTESTATION is set (debug build only)")
            return null
        }
        if (nonce.challengeMode.equals("hmac_nonce", ignoreCase = true)) {
            Log.w(TAG, "hmac_nonce attestation requested; no pepper available, sending none")
            return null
        }
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAttestationChallenge(nonce.nonce.toByteArray(Charsets.UTF_8))
                        .build()
                )
                generateKeyPair()
            }
            val chain = keyStore.getCertificateChain(KEY_ALIAS).orEmpty()
                .mapNotNull { (it as? X509Certificate)?.toPem() }
            check(chain.isNotEmpty()) { "Attestation certificate chain missing" }

            val tokenDigest = MessageDigest.getInstance("SHA-256")
                .digest(integrityToken.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val message = listOf(INSTALL_PROOF_VERSION, deviceId, nonce.nonce, tokenDigest, appVersion)
                .joinToString(PROOF_SEPARATOR)
                .toByteArray(Charsets.UTF_8)
            val privateKey = (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
            val proof = Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(message)
                Base64.encodeToString(sign(), Base64.NO_WRAP)
            }
            chain to proof
        }.onFailure { Log.w(TAG, "Key attestation unavailable, sending none", it) }.getOrNull()
    }

    private fun X509Certificate.toPem(): String =
        "-----BEGIN CERTIFICATE-----\n" +
            Base64.encodeToString(encoded, Base64.NO_WRAP).chunked(64).joinToString("\n") +
            "\n-----END CERTIFICATE-----\n"

    private companion object {
        const val TAG = "AetherNetVpn"
        const val PROVIDER_INVALID = -19
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "aethernet_play_integrity_device_key"
        const val INSTALL_PROOF_VERSION = "BB_INSTALL_PROOF_V1"
        /** OneApp's `InstallationProofMessageBuilder` joins the fields with a NUL character. */
        val PROOF_SEPARATOR = Char(0).toString()
    }
}
