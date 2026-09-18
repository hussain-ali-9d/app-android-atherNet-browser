package com.jhaiian.clint.browser.delegates
import com.jhaiian.clint.browser.*
import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.webkit.WebView
import androidx.core.graphics.ColorUtils
import com.jhaiian.clint.R
import com.jhaiian.clint.history.SearchHistoryManager

internal const val HOME_PAGE_URL = "clint://home"
internal const val HOME_PAGE_SEARCH_URL = "clint://search"
private const val HOME_PAGE_RECENT_SITES = 5

internal fun MainActivity.renderHomePage(webView: WebView, isIncognito: Boolean) {
    tabManager.tabs.find { it.webView === webView }?.url = HOME_PAGE_URL
    val appContext = applicationContext
    Thread {
        val sites = if (isIncognito) emptyList() else recentSiteOrigins(appContext, HOME_PAGE_RECENT_SITES)
        runOnUiThread {
            val html = buildHomePageHtml(sites, isIncognito)
            webView.loadDataWithBaseURL(HOME_PAGE_URL, html, "text/html", "UTF-8", HOME_PAGE_URL)
        }
    }.start()
}

internal fun MainActivity.openSearchOverlayFromHomePage() {
    openSearchOverlay(isBottom = uiState.addressBarPosition == AddressBarPosition.BOTTOM)
}

internal fun MainActivity.loadUrlOrHomePage(webView: WebView, url: String, isIncognito: Boolean) {
    if (url == HOME_PAGE_URL) renderHomePage(webView, isIncognito) else webView.loadUrl(url)
}

private fun recentSiteOrigins(context: Context, limit: Int): List<String> {
    val seenHosts = HashSet<String>()
    val origins = mutableListOf<String>()
    for (item in SearchHistoryManager.getAll(context)) {
        val uri = Uri.parse(item.query)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") continue
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: continue
        if (!seenHosts.add(host.removePrefix("www."))) continue
        val port = if (uri.port != -1) ":${uri.port}" else ""
        origins.add("$scheme://$host$port/")
        if (origins.size == limit) break
    }
    return origins
}

private var wordmarkFontBase64: String? = null

private fun Context.wordmarkFontDataUri(): String {
    val encoded = wordmarkFontBase64 ?: runCatching {
        assets.open("fonts/sora_aethernet.woff2").use { android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP) }
    }.getOrDefault("").also { wordmarkFontBase64 = it }
    return "data:font/woff2;base64,$encoded"
}

// Animated AetherNet mark from the "C4 v3 · Motion" design; `knockout` must match the page background.
private fun animatedMarkSvg(ink: String, signal: String, knockout: String): String = """
    <svg class="mark" viewBox="0 0 200 200" role="img" aria-label="AetherNet"><defs><clipPath id="mark-clip"><circle cx="100" cy="100" r="78"/></clipPath></defs>
    <g clip-path="url(#mark-clip)" fill="none" stroke="$ink" stroke-width="2.4"><line x1="20" y1="42" x2="180" y2="42"/><line x1="20" y1="58" x2="180" y2="58"/><line x1="20" y1="79" x2="180" y2="79"/><line x1="20" y1="100" x2="180" y2="100"/><line x1="20" y1="121" x2="180" y2="121"/><line x1="20" y1="142" x2="180" y2="142"/><line x1="20" y1="158" x2="180" y2="158"/><circle class="mer m0" cx="100" cy="100" r="78"/><circle class="mer m1" cx="100" cy="100" r="78"/><circle class="mer m2" cx="100" cy="100" r="78"/><circle class="mer m3" cx="100" cy="100" r="78"/></g>
    <path d="M16 100 A84 84 0 1 0 184 100 A84 84 0 1 0 16 100 Z M23 100 A77 77 0 1 0 177 100 A77 77 0 1 0 23 100 Z" fill="$ink" fill-rule="evenodd"/>
    <g class="bob"><path d="M100 64 C121 64 134 79 134 98 C134 117 120 133 100 138 C80 133 66 117 66 98 C66 79 79 64 100 64 Z" fill="$knockout" stroke="$knockout" stroke-width="10"/><g class="flutter"><path d="M128 82 L186 56 L150 90 Z M129 93 L162 100 L145 103 Z" fill="$knockout" stroke="$knockout" stroke-width="10" stroke-linejoin="miter"/></g><path d="M100 64 C121 64 134 79 134 98 C134 117 120 133 100 138 C80 133 66 117 66 98 C66 79 79 64 100 64 Z" fill="$signal"/><g class="flutter"><path d="M128 82 L186 56 L150 90 Z M129 93 L162 100 L145 103 Z" fill="$signal"/></g><path d="M72 91 L128 86 L126 104 L74 107 Z" fill="$knockout"/><g class="blink"><path d="M80 96 L96 100 L95 103 L81 102 Z M104 100 L120 95 L119 101 L105 103 Z" fill="$signal"/></g></g>
    </svg>
""".trimIndent()

private fun cssColor(color: Int): String = "#%06X".format(0xFFFFFF and color)

private fun MainActivity.buildHomePageHtml(origins: List<String>, isIncognito: Boolean): String {
    val colors = uiColors(isIncognito)
    val surface = colors.surface
    val onSurface = colors.onSurface
    val tileColor = colors.surfaceVariant
    val primary = colors.primary
    val isDark = ColorUtils.calculateLuminance(surface) < 0.5
    val brandInk = if (isDark) "#F7F7F8" else "#0A0A0D"
    val brandSignal = if (isDark) "#FF3346" else "#E11D2E"

    val tiles = origins.joinToString("") { origin ->
        val host = Uri.parse(origin).host.orEmpty()
        val label = host.removePrefix("www.")
        val letter = label.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val fallbackIcon = "https://icons.duckduckgo.com/ip3/$host.ico"
        """
        <a class="tile" href="${TextUtils.htmlEncode(origin)}">
          <span class="icon"><span class="letter">${TextUtils.htmlEncode(letter)}</span><img alt="" src="${TextUtils.htmlEncode(origin)}favicon.ico" onerror="if(this.dataset.f){this.remove()}else{this.dataset.f=1;this.src='${TextUtils.htmlEncode(fallbackIcon)}'}"></span>
          <span class="label">${TextUtils.htmlEncode(label)}</span>
        </a>
        """.trimIndent()
    }
    val searchBox = """
        <a class="search" href="$HOME_PAGE_SEARCH_URL">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
          <span>${TextUtils.htmlEncode(searchBarHint())}</span>
        </a>
    """.trimIndent()
    val vpnJson = vpnStateJson(com.jhaiian.clint.vpn.BrowserVpn.state.value)
    val vpn = org.json.JSONObject(vpnJson)
    val vpnCard = """
        <div id="vpn" class="vpn ${vpn.getString("status")}">
          <a class="vpn-main" href="$HOME_PAGE_VPN_URL">
            <span class="vpn-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2 4 5v6c0 5.25 3.4 10.15 8 11.35 4.6-1.2 8-6.1 8-11.35V5l-8-3zm0 10h6c-.53 4.12-3.28 7.79-6 8.6V12H6V6.3l6-2.25V12z"/></svg></span>
            <span class="vpn-text"><b id="vpn-title">${TextUtils.htmlEncode(vpn.getString("title"))}</b><small id="vpn-detail">${TextUtils.htmlEncode(vpn.getString("detail"))}</small></span>
          </a>
          <a class="vpn-action" id="vpn-action" href="$HOME_PAGE_VPN_TOGGLE_URL">${TextUtils.htmlEncode(vpn.getString("action"))}</a>
        </div>
        <script>
          window.aethernetVpn = function (s) {
            var card = document.getElementById('vpn');
            if (!card) return;
            card.className = 'vpn ' + s.status;
            document.getElementById('vpn-title').textContent = s.title;
            document.getElementById('vpn-detail').textContent = s.detail;
            document.getElementById('vpn-action').textContent = s.action;
          };
        </script>
    """.trimIndent()
    val body = when {
        isIncognito -> """<p class="empty">${TextUtils.htmlEncode(getString(R.string.home_page_incognito))}</p>"""
        origins.isEmpty() -> """<p class="empty">${TextUtils.htmlEncode(getString(R.string.home_page_no_recent_sites))}</p>"""
        else -> """<h2>${TextUtils.htmlEncode(getString(R.string.home_page_recent_sites))}</h2><div class="grid">$tiles</div>"""
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="color-scheme" content="${if (isDark) "dark" else "light"}">
        <title>${TextUtils.htmlEncode(getString(R.string.home))}</title>
        <style>
          html, body { margin: 0; background: ${cssColor(surface)}; color: ${cssColor(onSurface)}; font-family: sans-serif; }
          main { max-width: 480px; margin: 0 auto; padding: 72px 16px 24px; }
          @font-face { font-family: 'AetherNet Sora'; font-weight: 600; src: url(${wordmarkFontDataUri()}) format('woff2'); }
          .brand { display: flex; flex-direction: column; align-items: center; gap: 14px; margin: 0 0 36px; }
          .mark { width: 112px; height: 112px; }
          .wordmark { margin: 0; font-family: 'AetherNet Sora', sans-serif; font-weight: 600; font-size: 30px; letter-spacing: -0.02em; line-height: 1; color: $brandInk; }
          .wordmark span { color: $brandSignal; }
          .mer, .bob, .flutter, .blink { transform-box: view-box; }
          .mer { transform-origin: 100px 100px; animation: an-spin 9s ease-in-out infinite; }
          .m1 { animation-delay: -1.125s; } .m2 { animation-delay: -2.25s; } .m3 { animation-delay: -3.375s; }
          @keyframes an-spin { 0% { transform: scaleX(1); } 50% { transform: scaleX(-1); } 100% { transform: scaleX(1); } }
          .bob { transform-origin: 100px 100px; animation: an-bob 3.2s ease-in-out infinite; }
          @keyframes an-bob { 0%, 100% { transform: translateY(0) rotate(-2.5deg); } 50% { transform: translateY(-3px) rotate(2.5deg); } }
          .flutter { transform-origin: 130px 88px; animation: an-flutter 0.9s ease-in-out infinite alternate; }
          @keyframes an-flutter { from { transform: rotate(-5deg); } to { transform: rotate(6deg); } }
          .blink { transform-origin: 100px 99px; animation: an-blink 4.2s infinite; }
          @keyframes an-blink { 0%, 90%, 100% { transform: scaleY(1); } 93% { transform: scaleY(0.1); } 96% { transform: scaleY(1); } }
          @media (prefers-reduced-motion: reduce) { .mer, .bob, .flutter, .blink { animation: none; } }
          .vpn { display: flex; align-items: center; gap: 8px; margin: 0 0 36px; padding: 8px 8px 8px 12px; border-radius: 20px; background: ${cssColor(tileColor)}; }
          .vpn-main { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12px; color: inherit; text-decoration: none; -webkit-tap-highlight-color: transparent; }
          .vpn-icon { flex: none; width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: ${cssColor(surface)}; }
          .vpn-icon svg { width: 20px; height: 20px; fill: currentColor; opacity: .6; }
          .vpn-text { min-width: 0; display: flex; flex-direction: column; }
          .vpn-text b { font-size: 15px; font-weight: 600; }
          .vpn-text small { font-size: 12px; opacity: .7; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
          .vpn-action { flex: none; padding: 9px 16px; border-radius: 16px; font-size: 14px; font-weight: 600; text-decoration: none; background: ${cssColor(primary)}; color: ${if (isDark) "#000" else "#fff"}; -webkit-tap-highlight-color: transparent; }
          .vpn.on .vpn-icon svg { fill: #2E9E5B; opacity: 1; }
          .vpn.on .vpn-action, .vpn.busy .vpn-action { background: ${cssColor(surface)}; color: inherit; }
          .vpn.busy .vpn-icon svg { fill: ${cssColor(primary)}; opacity: 1; animation: vpn-pulse 1s ease-in-out infinite alternate; }
          .vpn.error small { color: ${if (isDark) "#FF6B6B" else "#C62828"}; opacity: 1; white-space: normal; }
          @keyframes vpn-pulse { from { opacity: .35; } to { opacity: 1; } }
          .search { display: flex; align-items: center; gap: 12px; height: 48px; margin: 0 0 16px; padding: 0 16px; border-radius: 24px; background: ${cssColor(tileColor)}; color: inherit; text-decoration: none; -webkit-tap-highlight-color: transparent; }
          .search svg { flex: none; width: 20px; height: 20px; fill: currentColor; opacity: .7; }
          .search span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 16px; opacity: .6; }
          h2 { margin: 0 0 16px; font-size: 14px; font-weight: 500; opacity: .7; }
          .grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
          .tile { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 8px 0; border-radius: 12px; color: inherit; text-decoration: none; -webkit-tap-highlight-color: transparent; }
          .tile:active { background: ${cssColor(tileColor)}; }
          .icon { position: relative; width: 48px; height: 48px; border-radius: 50%; background: ${cssColor(tileColor)}; display: flex; align-items: center; justify-content: center; overflow: hidden; }
          .letter { font-size: 20px; font-weight: 600; color: ${cssColor(primary)}; }
          .icon img { position: absolute; width: 24px; height: 24px; top: 12px; left: 12px; background: ${cssColor(tileColor)}; }
          .label { max-width: 100%; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
          .empty { text-align: center; font-size: 14px; opacity: .7; }
        </style>
        </head>
        <body><main><div class="brand">${animatedMarkSvg(brandInk, brandSignal, cssColor(surface))}<h1 class="wordmark">Aether<span>Net</span></h1></div>$searchBox$vpnCard$body</main></body>
        </html>
    """.trimIndent()
}
