package com.jhaiian.clint.browser.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.MainActivity
import com.jhaiian.clint.browser.delegates.requestVpnConnect
import com.jhaiian.clint.ui.theme.LocalClintColors
import com.jhaiian.clint.vpn.BrowserVpn
import com.jhaiian.clint.vpn.BrowserVpnState
import com.jhaiian.clint.vpn.VpnCountry

private val ConnectedGreen = Color(0xFF2E9E5B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnSheet(activity: MainActivity, onDismiss: () -> Unit) {
    val colors = LocalClintColors.current
    val state by BrowserVpn.state.collectAsState()
    val countries by BrowserVpn.countries.collectAsState()
    val countriesError by BrowserVpn.countriesError.collectAsState()
    var selectedId by remember { mutableLongStateOf(BrowserVpn.selectedCountryId(activity)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    LaunchedEffect(Unit) { BrowserVpn.refreshCountries() }

    val selectedCountry = countries.firstOrNull { it.id == selectedId } ?: countries.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.popupBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) }
    ) {
        com.jhaiian.clint.ui.ClintDialogStatusBarEffect(
            prefs.getBoolean("hide_status_bar", false),
            prefs.getBoolean("hide_system_navigation", false)
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusBadge(state)
                    Text(
                        text = statusTitle(state),
                        color = colors.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = statusDetail(state),
                        color = if (state is BrowserVpnState.Failed) colors.colorError else colors.secondaryText,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    val busy = state is BrowserVpnState.Connecting || state is BrowserVpnState.Disconnecting
                    val on = state is BrowserVpnState.Connected || state is BrowserVpnState.Connecting
                    Button(
                        onClick = {
                            if (on) BrowserVpn.disconnect() else activity.requestVpnConnect(selectedCountry)
                        },
                        enabled = state !is BrowserVpnState.Disconnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (on) colors.surfaceVariant else colors.primary,
                            contentColor = if (on) colors.onSurface else colors.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(top = 20.dp).fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            text = stringResource(
                                when {
                                    state is BrowserVpnState.Connecting -> R.string.vpn_cancel
                                    on || busy -> R.string.vpn_disconnect
                                    state is BrowserVpnState.Failed -> R.string.vpn_retry
                                    else -> R.string.vpn_connect
                                }
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = stringResource(R.string.vpn_scope_note),
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.vpn_locations),
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
                )
                // A sign-in failure blocks both the connect and the list; the status above already says it.
                val listErrorShownAbove = (state as? BrowserVpnState.Failed)?.message == countriesError
                when {
                    countries.isEmpty() && countriesError != null && listErrorShownAbove -> Text(
                        stringResource(R.string.vpn_retry),
                        color = colors.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable { BrowserVpn.refreshCountries(force = true) }
                            .padding(8.dp)
                    )
                    countries.isEmpty() && countriesError != null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(countriesError.orEmpty(), color = colors.colorError, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.vpn_retry),
                            color = colors.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { BrowserVpn.refreshCountries(force = true) }.padding(8.dp)
                        )
                    }
                    countries.isEmpty() -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.vpn_locations_loading), color = colors.secondaryText, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            items(countries, key = { it.id }) { country ->
                CountryRow(
                    country = country,
                    selected = country.id == selectedCountry?.id,
                    onClick = {
                        val changed = country.id != selectedCountry?.id
                        selectedId = country.id
                        val live = state is BrowserVpnState.Connected || state is BrowserVpnState.Connecting
                        if (changed && live) {
                            activity.requestVpnConnect(country)
                        } else {
                            prefs.edit().putLong("vpn_country_id", country.id).apply()
                        }
                    }
                )
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatusBadge(state: BrowserVpnState) {
    val colors = LocalClintColors.current
    val tint = when (state) {
        is BrowserVpnState.Connected -> ConnectedGreen
        is BrowserVpnState.Failed -> colors.colorError
        else -> colors.secondaryText
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(72.dp).background(tint.copy(alpha = 0.14f), CircleShape)
    ) {
        if (state is BrowserVpnState.Connecting || state is BrowserVpnState.Disconnecting) {
            CircularProgressIndicator(color = colors.primary, strokeWidth = 3.dp, modifier = Modifier.size(72.dp))
        }
        Icon(Icons.Filled.VpnLock, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun CountryRow(country: VpnCountry, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClintColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(country.flagEmoji.ifEmpty { country.isoCode.uppercase() }, fontSize = 22.sp, modifier = Modifier.width(32.dp))
        Text(country.name, color = colors.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary)
    }
}

@Composable
private fun statusTitle(state: BrowserVpnState): String = stringResource(
    when (state) {
        is BrowserVpnState.Connected -> R.string.vpn_status_connected
        is BrowserVpnState.Connecting -> R.string.vpn_status_connecting
        BrowserVpnState.Disconnecting -> R.string.vpn_status_disconnecting
        is BrowserVpnState.Failed -> R.string.vpn_status_failed
        BrowserVpnState.Disconnected -> R.string.vpn_status_off
    }
)

@Composable
private fun statusDetail(state: BrowserVpnState): String = when (state) {
    is BrowserVpnState.Connected -> stringResource(R.string.vpn_status_connected_detail, state.country.name)
    is BrowserVpnState.Connecting -> state.country?.name.orEmpty()
    is BrowserVpnState.Failed -> state.message
    else -> stringResource(R.string.vpn_status_off_detail)
}
