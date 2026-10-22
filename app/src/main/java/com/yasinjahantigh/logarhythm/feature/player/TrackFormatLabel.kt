package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.AudioFormatInfo
import com.yasinjahantigh.logarhythm.core.utils.AudioFormatReader
import com.yasinjahantigh.logarhythm.domain.model.Track

/** "M4A · AAC · 256 kbps · 44.1 kHz" under the artwork, above the title. */
@Composable
fun TrackFormatLabel(
    track: Track?,
    modifier: Modifier = Modifier
) {
    if (track == null) return
    val context = LocalContext.current
    val info by produceState<AudioFormatInfo?>(
        initialValue = null,
        track.id,
        track.uri
    ) {
        value = AudioFormatReader.read(context, track)
    }
    val label = info?.label(stringResource(R.string.player_format_lossless))
    if (!label.isNullOrBlank()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.testTag("player_format_label")
        )
    }
}
