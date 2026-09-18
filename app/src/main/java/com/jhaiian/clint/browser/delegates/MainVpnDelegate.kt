package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.*

import android.net.Uri
import android.net.VpnService
import android.widget.Toast
import com.jhaiian.clint.R
import com.jhaiian.clint.vpn.BrowserVpn
import com.jhaiian.clint.vpn.BrowserVpnState
import com.jhaiian.clint.vpn.VpnCountry
import org.json.JSONObject

internal const val HOME_PAGE_VPN_URL = "aethernet://vpn"
internal const val HOME_PAGE_VPN_TOGGLE_URL = "aethernet://vpn-toggle"

internal fun MainActivity.openVpnSheet() {
    uiState.vpnSheetOpen = true
}

/** Connects, asking for the system VPN consent first when this app does not hold it yet. */
internal fun MainActivity.requestVpnConnect(country: VpnCountry?) {
    val consent = VpnService.prepare(this)
    if (consent == null) {
        BrowserVpn.connect(this, country)
    } else {
        pendingVpnCountry = country
        vpnPermissionLauncher.launch(consent)
    }
}

internal fun MainActivity.onVpnPermissionResult(granted: Boolean) {
    val country = pendingVpnCountry
    pendingVpnCountry = null
    if (granted) {
        BrowserVpn.connect(this, country)
    } else {
        Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
    }
}

/** Links on the home page (`aethernet://…`) that act on the browser instead of navigating. */
internal fun MainActivity.handleHomePageLink(uri: Uri) {
    when (uri.toString()) {
        HOME_PAGE_SEARCH_URL -> openSearchOverlayFromHomePage()
        HOME_PAGE_VPN_URL -> openVpnSheet()
        HOME_PAGE_VPN_TOGGLE_URL -> if (BrowserVpn.isActive) BrowserVpn.disconnect() else requestVpnConnect(null)
    }
}

/** State for the home page's VPN card, read by `window.aethernetVpn` in the page. */
internal fun MainActivity.vpnStateJson(state: BrowserVpnState): String {
    val (status, title, detail, action) = when (state) {
        is BrowserVpnState.Connected -> listOf(
            "on", getString(R.string.vpn_status_connected),
            getString(R.string.vpn_status_connected_detail, state.country.name), getString(R.string.vpn_disconnect)
        )
        is BrowserVpnState.Connecting -> listOf(
            "busy", getString(R.string.vpn_status_connecting), state.country?.name.orEmpty(), getString(R.string.vpn_cancel)
        )
        BrowserVpnState.Disconnecting -> listOf(
            "busy", getString(R.string.vpn_status_disconnecting), "", getString(R.string.vpn_disconnect)
        )
        is BrowserVpnState.Failed -> listOf(
            "error", getString(R.string.vpn_status_failed), state.message, getString(R.string.vpn_retry)
        )
        BrowserVpnState.Disconnected -> listOf(
            "off", getString(R.string.vpn_status_off), getString(R.string.vpn_status_off_detail), getString(R.string.vpn_connect)
        )
    }
    return JSONObject()
        .put("status", status)
        .put("title", title)
        .put("detail", detail)
        .put("action", action)
        .toString()
}

/** Updates the VPN card on every open home page in place, without reloading them. */
internal fun MainActivity.pushVpnStateToHomePages(state: BrowserVpnState) {
    val script = "window.aethernetVpn && window.aethernetVpn(${vpnStateJson(state)})"
    tabManager.tabs
        .filter { isHomePageUrl(it.webView.url) }
        .forEach { it.webView.evaluateJavascript(script, null) }
}
