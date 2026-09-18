package com.jhaiian.clint.settings.supportupstream

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.base.AetherNetActivity
import com.jhaiian.clint.ui.DocumentViewer
import com.jhaiian.clint.ui.OverlayHostActivity
import com.jhaiian.clint.ui.theme.AetherNetComposeTheme

class SupportUpstreamActivity : AetherNetActivity(), OverlayHostActivity {

    override var overlayContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "dark") ?: "dark"

        setContent {
            AetherNetComposeTheme(theme = theme) {
                Box {
                    SupportUpstreamScreen(
                        onBack = { finish() },
                        onViewSupportersClick = {
                            DocumentViewer.show(
                                this@SupportUpstreamActivity,
                                getString(R.string.document_viewer_supporters_title),
                                DocumentViewer.SUPPORTERS_URL
                            )
                        }
                    )
                    overlayContent?.invoke()
                }
            }
        }
    }
}
