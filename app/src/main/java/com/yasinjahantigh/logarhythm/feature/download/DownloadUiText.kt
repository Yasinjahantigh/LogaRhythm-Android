package com.yasinjahantigh.logarhythm.feature.download

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_CRAWLING_DETAIL
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus

@Composable
fun downloadStatusLabel(item: DownloadItem): String = when {
    item.status == DownloadStatus.DOWNLOADING && item.statusDetail == DOWNLOAD_CRAWLING_DETAIL ->
        stringResource(R.string.download_status_crawling, item.progress)
    item.status == DownloadStatus.QUEUED -> stringResource(R.string.download_status_queued)
    item.status == DownloadStatus.DOWNLOADING -> stringResource(R.string.download_status_downloading, item.progress)
    item.status == DownloadStatus.SAVING -> stringResource(R.string.download_status_saving)
    item.status == DownloadStatus.SAVED -> stringResource(R.string.download_status_saved)
    item.status == DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
    item.status == DownloadStatus.CANCELLED -> stringResource(R.string.download_status_cancelled)
    else -> stringResource(R.string.download_status_queued)
}

@Composable
fun downloadErrorText(item: DownloadItem): String = when (item.errorKind) {
    DownloadErrorKind.STORAGE -> stringResource(R.string.download_error_storage)
    DownloadErrorKind.CANCELLED -> stringResource(R.string.download_status_cancelled)
    DownloadErrorKind.NETWORK -> {
        if (item.source != DownloadSource.MUSICMAN) {
            stringResource(R.string.download_error_vpn_or_musicman)
        } else {
            stringResource(R.string.download_error_network_message)
        }
    }
    DownloadErrorKind.RUNTIME, DownloadErrorKind.UPDATE,
    DownloadErrorKind.CONTENT, DownloadErrorKind.UNKNOWN, null -> {
        if (item.source != DownloadSource.MUSICMAN) {
            stringResource(R.string.download_error_vpn_or_musicman)
        } else {
            item.errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.download_error_content)
        }
    }
}

@StringRes
fun downloadSourceLabelRes(source: DownloadSource): Int = when (source) {
    DownloadSource.RADIOJAVAN -> R.string.download_source_radiojavan
    DownloadSource.DEEZER -> R.string.download_source_deezer
    DownloadSource.MUSICMAN -> R.string.download_source_musicman
    DownloadSource.SAAVN -> R.string.download_source_saavn
    DownloadSource.YOUTUBE -> R.string.download_source_youtube
    DownloadSource.SOUNDCLOUD -> R.string.download_source_soundcloud
    DownloadSource.BANDCAMP -> R.string.download_source_bandcamp
}
