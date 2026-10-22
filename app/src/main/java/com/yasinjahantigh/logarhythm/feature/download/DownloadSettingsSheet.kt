package com.yasinjahantigh.logarhythm.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.download.YtdlpUpdateState
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsSheet(
    version: String?,
    updateState: YtdlpUpdateState,
    onUpdate: () -> Unit,
    cookiesImported: Boolean,
    onImportCookies: () -> Unit,
    onClearCookies: () -> Unit,
    onDismiss: () -> Unit
) {
    val updating = updateState is YtdlpUpdateState.Updating
    val versionText = version ?: stringResource(R.string.download_settings_version_unknown)
    var showCookiesHelp by remember { mutableStateOf(false) }

    if (showCookiesHelp) {
        CookiesHelpDialog(onDismiss = { showCookiesHelp = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.download_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.download_settings_audio_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.download_settings_audio_value),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.download_settings_audio_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.download_settings_update_ytdlp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.download_settings_version, versionText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            updateMessage(updateState)?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (updateState is YtdlpUpdateState.Failed &&
                        updateState.kind == DownloadErrorKind.NETWORK
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onUpdate,
                enabled = !updating,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("download_update_button")
            ) {
                if (updating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.download_settings_update_ytdlp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.download_settings_save_location),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.download_settings_save_location_value),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.download_settings_save_location_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.download_settings_cookies_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (cookiesImported) R.string.download_settings_cookies_imported
                    else R.string.download_settings_cookies_not_imported
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (cookiesImported) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.download_settings_cookies_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = onImportCookies,
                    modifier = Modifier.testTag("download_cookies_import")
                ) {
                    Text(text = stringResource(R.string.download_settings_cookies_import))
                }
                if (cookiesImported) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onClearCookies,
                        modifier = Modifier.testTag("download_cookies_remove")
                    ) {
                        Text(text = stringResource(R.string.download_settings_cookies_remove))
                    }
                }
            }
            TextButton(
                onClick = { showCookiesHelp = true },
                modifier = Modifier.testTag("download_cookies_help")
            ) {
                Text(text = stringResource(R.string.download_settings_cookies_help))
            }
        }
    }
}

@Composable
private fun CookiesHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.download_cookies_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.download_cookies_help_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.download_cookies_help_step_1),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.download_cookies_help_step_2),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.download_cookies_help_step_3),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.download_cookies_help_step_4),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.download_cookies_help_step_5),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.download_cookies_help_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.download_cookies_help_done))
            }
        }
    )
}

@Composable
private fun updateMessage(state: YtdlpUpdateState): String? = when (state) {
    YtdlpUpdateState.Idle -> null
    YtdlpUpdateState.Updating -> stringResource(R.string.download_settings_updating)
    is YtdlpUpdateState.Ready -> stringResource(R.string.download_settings_updated)
    is YtdlpUpdateState.UpToDate -> stringResource(R.string.download_settings_up_to_date)
    is YtdlpUpdateState.Failed -> if (state.kind == DownloadErrorKind.NETWORK) {
        stringResource(R.string.download_error_network_message)
    } else {
        stringResource(R.string.download_settings_update_failed)
    }
    is YtdlpUpdateState.RolledBack -> stringResource(R.string.download_settings_rollback)
}
