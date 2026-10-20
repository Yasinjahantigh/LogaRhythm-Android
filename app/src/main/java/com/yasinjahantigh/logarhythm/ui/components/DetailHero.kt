package com.yasinjahantigh.logarhythm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yasinjahantigh.logarhythm.R

/**
 * One header for every detail page (album, artist, genre, folder, playlist).
 *
 * A soft colour wash fades into the page, the cover floats with a shadow, and Play / Shuffle are
 * big pill buttons right under the title. The back button floats on top, so the page has no app
 * bar and the list gets the whole screen.
 */
@Composable
fun DetailHero(
    title: String,
    meta: String,
    artworkUri: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    round: Boolean = false,
    icon: ImageVector? = null,
    actionsEnabled: Boolean = true,
    backTag: String = "detail_back_button",
    playTag: String = "detail_play_button",
    shuffleTag: String = "detail_shuffle_button",
    topEndAction: (@Composable () -> Unit)? = null
) {
    val surface = MaterialTheme.colorScheme.surface
    val tint = artColorsFor(title).first
    val coverShape = if (round) CircleShape else RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(tint.copy(alpha = 0.42f), tint.copy(alpha = 0.12f), surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag(backTag)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                if (topEndAction != null) {
                    topEndAction()
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            ArtworkImage(
                uri = artworkUri,
                fallbackKey = title,
                shape = coverShape,
                icon = icon,
                glyphSize = 72.sp,
                contentDescription = title,
                modifier = Modifier
                    .size(208.dp)
                    .shadow(24.dp, coverShape, ambientColor = tint, spotColor = tint)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(enabled = onSubtitleClick != null) { onSubtitleClick?.invoke() }
                )
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = actionsEnabled,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag(playTag)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        text = stringResource(R.string.btn_play),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onShuffle,
                    enabled = actionsEnabled,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag(shuffleTag)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.btn_shuffle),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
