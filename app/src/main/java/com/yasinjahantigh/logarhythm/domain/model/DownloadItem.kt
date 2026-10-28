package com.yasinjahantigh.logarhythm.domain.model

/** Stored in statusDetail while a network failure waits for WorkManager's automatic retry. */
const val DOWNLOAD_NETWORK_RETRY_DETAIL = "network_retry"

/** Saved successfully, but the container (usually WebM) cannot carry the requested tags. */
const val DOWNLOAD_SAVED_WITHOUT_TAGS_DETAIL = "saved_without_tags"

/** The file was saved, but writing/verifying its tags failed. */
const val DOWNLOAD_TAG_WRITE_FAILED_DETAIL = "tag_write_failed"

/** Where a downloadable search result comes from. */
enum class DownloadSource(val raw: String) {
    YOUTUBE("youtube"),
    SOUNDCLOUD("soundcloud"),
    MUSICMAN("musicman");

    val isYouTube: Boolean get() = this == YOUTUBE

    val usesYtDlp: Boolean get() = this != MUSICMAN

    companion object {
        fun fromRaw(value: String?): DownloadSource =
            entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: YOUTUBE
    }
}

/** Kept in the stored row for schema compatibility; downloads use the original source audio. */
enum class DownloadQuality(val prefValue: String, val ytdlpValue: String) {
    BEST("0", "0"),
    Q128("128", "128K"),
    Q192("192", "192K"),
    Q256("256", "256K"),
    Q320("320", "320K");

    companion object {
        fun fromPref(value: String?): DownloadQuality =
            entries.firstOrNull { it.prefValue == value } ?: BEST
    }
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    SAVING,
    SAVED,
    FAILED,
    CANCELLED;

    val raw: String get() = name.lowercase()

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING || this == SAVING

    val isFinished: Boolean
        get() = this == SAVED || this == FAILED || this == CANCELLED

    companion object {
        fun fromRaw(value: String?): DownloadStatus =
            entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: QUEUED
    }
}

enum class DownloadErrorKind {
    NETWORK,
    CONTENT,
    UPDATE,
    RUNTIME,
    STORAGE,
    CANCELLED,
    UNKNOWN;

    val raw: String get() = name.lowercase()

    /** Only connection/filtering failures get the VPN hint. */
    val showsVpnHint: Boolean get() = this == NETWORK

    companion object {
        fun fromRaw(value: String?): DownloadErrorKind? =
            entries.firstOrNull { it.raw.equals(value, ignoreCase = true) }
    }
}

/** One row returned by a yt-dlp search, labeled with its source. */
data class DownloadSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val source: DownloadSource,
    val url: String,
    val durationMs: Long,
    val thumbnailUrl: String?
)

/** UI/worker view of a persisted download. */
data class DownloadItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val source: DownloadSource,
    val sourceUrl: String,
    val videoId: String,
    val quality: DownloadQuality,
    val status: DownloadStatus,
    val progress: Int,
    val etaSeconds: Long,
    val artworkUrl: String?,
    val mediaStoreId: Long?,
    val contentUri: String?,
    val filePath: String?,
    val fileSizeBytes: Long,
    val errorKind: DownloadErrorKind?,
    val errorMessage: String?,
    val statusDetail: String?,
    val usedFallback: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isActive: Boolean get() = status.isActive
    val isFinished: Boolean get() = status.isFinished
}
