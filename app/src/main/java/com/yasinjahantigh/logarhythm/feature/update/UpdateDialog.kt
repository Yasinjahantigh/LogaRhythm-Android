package com.yasinjahantigh.logarhythm.feature.update

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.update.AppRelease
import com.yasinjahantigh.logarhythm.data.update.UpdateState
import com.yasinjahantigh.logarhythm.ui.components.formatCount

@Composable
fun UpdateDialogHost(
    state: UpdateState,
    installedVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        UpdateState.Hidden, UpdateState.Checking -> Unit
        is UpdateState.Available -> AvailableDialog(state.release, installedVersion, onDownload, onDismiss)
        is UpdateState.Downloading -> DownloadingDialog(state, onDismiss)
        is UpdateState.Failed -> FailedDialog(onDownload, onDismiss)
    }
}

@Composable
private fun AvailableDialog(
    release: AppRelease,
    installedVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, release.apkSizeBytes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title, release.versionName)) },
        text = {
            Column {
                Text(stringResource(R.string.update_installed_version, installedVersion))
                Text(
                    stringResource(R.string.update_meta, size, formatCount(release.downloads.toInt()))
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = release.notes,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.update_download_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}

@Composable
private fun DownloadingDialog(state: UpdateState.Downloading, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_downloading_title)) },
        text = {
            Column {
                if (state.fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_downloading_percent, (state.fraction * 100).toInt()))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_downloading_wait))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.update_downloading_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_hide))
            }
        }
    )
}

@Composable
private fun FailedDialog(onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_failed_title)) },
        text = { Text(stringResource(R.string.update_failed_body)) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.update_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_close))
            }
        }
    )
}
