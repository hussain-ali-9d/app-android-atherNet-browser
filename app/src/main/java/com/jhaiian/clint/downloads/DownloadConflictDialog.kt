package com.jhaiian.clint.downloads
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Save

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jhaiian.clint.R
import com.jhaiian.clint.browser.sheets.ActionSheetRow
import com.jhaiian.clint.ui.AetherNetDialog
import com.jhaiian.clint.ui.AetherNetDialogCancelFooter

data class DownloadConflictDialogRequest(
    val onAddDuplicate: () -> Unit,
    val onOverride: () -> Unit,
    val onRename: () -> Unit
)

@Composable
internal fun DownloadConflictDialog(request: DownloadConflictDialogRequest, hideStatusBar: Boolean, hideSystemNavigation: Boolean, onDismiss: () -> Unit) {
    AetherNetDialog(
        title = stringResource(R.string.download_conflict_title),
        hideStatusBar = hideStatusBar, hideSystemNavigation = hideSystemNavigation,
        onDismiss = onDismiss,
        footer = { AetherNetDialogCancelFooter(onDismiss) }
    ) {
        ActionSheetRow(androidx.compose.material.icons.Icons.Filled.Download, stringResource(R.string.download_conflict_add_duplicate)) { onDismiss(); request.onAddDuplicate() }
        ActionSheetRow(androidx.compose.material.icons.Icons.Filled.Save, stringResource(R.string.download_conflict_override)) { onDismiss(); request.onOverride() }
        ActionSheetRow(androidx.compose.material.icons.Icons.Filled.FormatSize, stringResource(R.string.download_conflict_rename)) { onDismiss(); request.onRename() }
    }
}
