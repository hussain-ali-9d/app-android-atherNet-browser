package com.jhaiian.clint.vpn

data class VpnHosting(
    val hostName: String,
    val protocol: String,
    val protocolId: Int,
    val port: Int,
    val benchmarkScore: Int,
    val isHealthy: Boolean,
    val isActive: Boolean
) {
    val isWireGuard: Boolean get() = protocol.equals("wireguard", ignoreCase = true)
}

data class VpnCountry(
    val id: Long,
    val isoCode: String,
    val name: String,
    val order: Int,
    val hostings: List<VpnHosting>
) {
    /** Same ranking as OneApp's `bestWireGuardHosting`: active, then healthy, then benchmark. */
    val bestWireGuardHosting: VpnHosting?
        get() = hostings
            .filter { it.isWireGuard }
            .sortedWith(
                compareByDescending<VpnHosting> { it.isActive }
                    .thenByDescending { it.isHealthy }
                    .thenByDescending { it.benchmarkScore }
            )
            .firstOrNull()

    val flagEmoji: String
        get() {
            val code = isoCode.uppercase()
            if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
            return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        }
}

/** Tunnel parameters from `POST /vpn/connect`, with OneApp's defaults for fields the backend omits. */
data class WireGuardParams(
    val sessionId: String,
    val address: String,
    val dns: String,
    val mtu: Int,
    val peerPublicKey: String,
    val presharedKey: String,
    val endpoint: String,
    val allowedIps: String,
    val persistentKeepalive: Int
)

sealed interface BrowserVpnState {
    data object Disconnected : BrowserVpnState
    data class Connecting(val country: VpnCountry?) : BrowserVpnState
    data class Connected(val country: VpnCountry, val sinceMillis: Long) : BrowserVpnState
    data object Disconnecting : BrowserVpnState
    data class Failed(val message: String) : BrowserVpnState
}

class VpnApiException(val httpCode: Int, val errorKey: String?, message: String) : Exception(message)
