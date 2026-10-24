package com.yasinjahantigh.logarhythm.feature.ai

import androidx.annotation.StringRes
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.AiFailure

@StringRes
fun AiFailure.messageRes(): Int = when (this) {
    AiFailure.NOT_CONFIGURED -> R.string.ai_error_not_configured
    AiFailure.INVALID_KEY -> R.string.ai_error_invalid_key
    AiFailure.RATE_LIMITED -> R.string.ai_error_rate_limited
    AiFailure.NETWORK -> R.string.ai_error_network
    AiFailure.SERVER -> R.string.ai_error_server
    AiFailure.BAD_RESPONSE -> R.string.ai_error_bad_response
    AiFailure.NO_TRACKS -> R.string.ai_error_no_tracks
}
