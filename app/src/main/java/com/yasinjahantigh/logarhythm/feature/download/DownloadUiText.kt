package com.yasinjahantigh.logarhythm.feature.download

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus

@Composable
fun downloadStatusLabel(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> stringResource(R.string.download_status_queued)
    DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading, item.progress)
    DownloadStatus.SAVING -> stringResource(R.string.download_status_saving)
    DownloadStatus.SAVED -> stringResource(R.string.download_status_saved)
    DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
    DownloadStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
}

@Composable
fun downloadErrorText(item: DownloadItem): String = when (item.errorKind) {
    DownloadErrorKind.NETWORK -> stringResource(R.string.download_error_network_message)
    DownloadErrorKind.STORAGE -> stringResource(R.string.download_error_storage)
    DownloadErrorKind.RUNTIME, DownloadErrorKind.UPDATE -> stringResource(R.string.download_error_runtime)
    DownloadErrorKind.CANCELLED -> stringResource(R.string.download_status_cancelled)
    DownloadErrorKind.CONTENT, DownloadErrorKind.UNKNOWN, null ->
        stringResource(R.string.download_error_content)
}

@StringRes
fun downloadSourceLabelRes(source: DownloadSource): Int = when (source) {
    DownloadSource.YOUTUBE -> R.string.download_source_youtube
    DownloadSource.SOUNDCLOUD -> R.string.download_source_soundcloud
    DownloadSource.MUSICMAN -> R.string.download_source_musicman
}
