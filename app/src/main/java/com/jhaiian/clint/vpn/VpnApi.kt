package com.jhaiian.clint.vpn

import com.jhaiian.clint.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cruise VPN backend (`user_api` + `server_api` behind the `/api/v2` gateway).
 * Blocking calls; callers run them on an IO dispatcher.
 */
internal class VpnApi(private val session: VpnSessionStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.VPN_API_BASE_URL.trimEnd('/')

    data class Nonce(val nonce: String, val attestationMode: String?, val challengeMode: String?)

    data class SignIn(
        val accessToken: String,
        val refreshToken: String,
        val accessExpiresInSeconds: Int,
        val group: String?
    )

    fun generateNonce(deviceId: String, appVersion: String): Nonce {
        val data = post("/play-integrity/nonce", JSONObject().put("device_id", deviceId).put("app_version", appVersion), auth = false)
            .getJSONObject("data")
        val policy = data.optJSONObject("policy")
        return Nonce(
            nonce = data.getString("nonce"),
            attestationMode = policy?.optStringOrNull("attestation_mode"),
            challengeMode = policy?.optStringOrNull("attestation_challenge_mode")
        )
    }

    fun validateIntegrity(
        integrityToken: String,
        nonce: String,
        deviceId: String,
        keyAttestation: List<String>?,
        installationProof: String?,
        deviceInfo: JSONObject
    ): SignIn {
        val body = JSONObject()
            .put("integrity_token", integrityToken)
            .put("device_id", deviceId)
            .put("nonce", nonce)
            .put("device_info", deviceInfo)
        if (keyAttestation != null) body.put("key_attestation", JSONArray(keyAttestation))
        if (installationProof != null) body.put("installation_proof", installationProof)
        val data = post("/play-integrity/validate", body, auth = false).getJSONObject("data")
        return SignIn(
            accessToken = data.optStringOrNull("access_token").orEmpty(),
            refreshToken = data.optStringOrNull("refresh_token").orEmpty(),
            accessExpiresInSeconds = data.optInt("access_token_expires_in", 3600),
            group = data.optJSONObject("user")?.optStringOrNull("group")
        )
    }

    fun refresh(refreshToken: String): SignIn {
        val data = post("/auth/refresh", JSONObject().put("refresh_token", refreshToken), auth = false)
            .getJSONObject("data")
        return SignIn(
            accessToken = data.getString("access_token"),
            refreshToken = data.getString("refresh_token"),
            accessExpiresInSeconds = data.optInt("access_token_expires_in", 3600),
            group = null
        )
    }

    fun activeServers(): List<VpnCountry> {
        val list = execute("GET", "/servers/active_servers", null, auth = true).optJSONArray("data") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val c = list.getJSONObject(i)
            val hostings = c.optJSONArray("hostings") ?: JSONArray()
            VpnCountry(
                id = c.getLong("country_id"),
                isoCode = c.optString("iso_code"),
                name = c.optString("country_name"),
                order = c.optInt("order", 0),
                hostings = (0 until hostings.length()).map { j ->
                    val h = hostings.getJSONObject(j)
                    VpnHosting(
                        hostName = h.optString("host_name"),
                        protocol = h.optString("protocol"),
                        protocolId = h.optInt("protocol_id"),
                        port = h.optInt("port"),
                        benchmarkScore = h.optInt("benchmark_score"),
                        isHealthy = h.optBoolean("health_check"),
                        isActive = h.optBoolean("status")
                    )
                }
            )
        }
    }

    fun connect(countryId: Long, protocolId: Int, clientPublicKey: String): WireGuardParams {
        val body = JSONObject()
            .put("country_id", countryId)
            .put("protocol_id", protocolId)
            .put("client_public_key", clientPublicKey)
        val data = post("/vpn/connect", body, auth = true).getJSONObject("data")
        val assignedIp = data.optString("assigned_ip")
        val config = data.optJSONObject("config")
        val iface = config?.optJSONObject("interface")
        val peer = config?.optJSONObject("peer")
        val fallbackEndpoint = listOfNotNull(
            data.optStringOrNull("host_name") ?: data.optStringOrNull("server_name"),
            data.optInt("port", 0).takeIf { it > 0 }
        ).joinToString(":")
        return WireGuardParams(
            sessionId = data.optString("session_id"),
            address = iface?.optStringOrNull("address") ?: "$assignedIp/32",
            dns = iface?.optStringOrNull("dns") ?: "10.0.0.53",
            mtu = iface?.optInt("mtu", 0)?.takeIf { it > 0 } ?: 1420,
            peerPublicKey = peer?.optStringOrNull("public_key").orEmpty(),
            presharedKey = peer?.optStringOrNull("preshared_key").orEmpty(),
            endpoint = peer?.optStringOrNull("endpoint") ?: fallbackEndpoint,
            allowedIps = peer?.optStringOrNull("allowed_ips") ?: "0.0.0.0/0",
            persistentKeepalive = peer?.optInt("persistent_keepalive", 0)?.takeIf { it > 0 } ?: 25
        )
    }

    fun disconnect() {
        post("/vpn/disconnect", JSONObject(), auth = true)
    }

    /** True while the backend still holds a session for this device. */
    fun hasSession(): Boolean {
        val data = execute("GET", "/vpn/session", null, auth = true).opt("data")
        return data is JSONObject && data.length() > 0
    }

    private fun post(path: String, body: JSONObject, auth: Boolean): JSONObject =
        execute("POST", path, body, auth)

    private fun execute(method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject {
        val builder = Request.Builder()
            .url("$baseUrl$API_VERSION$path")
            // Production nginx only admits the play-integrity routes for this exact agent.
            .header("User-Agent", USER_AGENT)
            .header("Device-Type", DEVICE_TYPE)
            .header("Accept", "application/json")
        if (auth) session.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        val requestBody = body?.toString()?.toRequestBody(JSON)
        builder.method(method, if (method == "GET") null else requestBody ?: "{}".toRequestBody(JSON))

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful) {
                val key = response.header("x-error-key") ?: json?.optStringOrNull("error")
                val message = json?.optStringOrNull("message") ?: "HTTP ${response.code}"
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("AetherNetVpn", "$method $path -> ${response.code} key=$key headers=${response.headers.names()} body=${text.take(400)}")
                }
                throw VpnApiException(response.code, key, message)
            }
            return json ?: JSONObject()
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private companion object {
        const val API_VERSION = "/api/v2"
        const val USER_AGENT = "okhttp/5.3.2"
        const val DEVICE_TYPE = "android"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
