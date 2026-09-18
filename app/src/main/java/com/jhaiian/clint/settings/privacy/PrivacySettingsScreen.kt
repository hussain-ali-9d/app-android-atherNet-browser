package com.jhaiian.clint.settings.privacy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock

import androidx.compose.foundation.layout.padding
import com.jhaiian.clint.ui.AetherNetSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jhaiian.clint.R
import com.jhaiian.clint.settings.common.RowDivider
import com.jhaiian.clint.settings.common.SettingsRow
import com.jhaiian.clint.settings.common.SettingsScreenScaffold
import com.jhaiian.clint.settings.common.SettingsSection
import com.jhaiian.clint.setup.SectionLabel
import com.jhaiian.clint.ui.theme.LocalAetherNetColors

@Composable
fun PrivacySettingsScreen(
    state: PrivacySettingsUiState,
    onBlockThirdPartyCookiesClick: () -> Unit,
    onCustomUserAgentClick: () -> Unit,
    onHttpsOnlyClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val colors = LocalAetherNetColors.current

    SettingsScreenScaffold {

        SectionLabel(stringResource(R.string.privacy_section_privacy), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Cookie,
                title = stringResource(R.string.block_third_party_cookies),
                summary = stringResource(R.string.block_third_party_cookies_summary),
                colors = colors,
                onClick = onBlockThirdPartyCookiesClick,
                trailing = {
                    AetherNetSwitch(checked = state.blockThirdPartyCookies)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Language,
                title = stringResource(R.string.custom_user_agent),
                summary = stringResource(R.string.custom_user_agent_summary),
                colors = colors,
                onClick = onCustomUserAgentClick,
                trailing = {
                    AetherNetSwitch(checked = state.customUserAgent)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Lock,
                title = stringResource(R.string.https_only),
                summary = stringResource(R.string.https_only_summary),
                colors = colors,
                onClick = onHttpsOnlyClick,
                trailing = {
                    AetherNetSwitch(checked = state.httpsOnly)
                }
            )
        }

        SectionLabel(stringResource(R.string.privacy_section_history), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.History,
                title = stringResource(R.string.history_title),
                summary = stringResource(R.string.history_summary),
                colors = colors,
                onClick = onHistoryClick
            )
        }
    }
}
