package com.yasinjahantigh.logarhythm.domain.download

import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind

/** A failure that already carries the user-facing category. */
class DownloadPipelineException(
    val kind: DownloadErrorKind,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
