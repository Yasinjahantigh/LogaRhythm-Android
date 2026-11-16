package com.yasinjahantigh.logarhythm.feature.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull

/**
 * High-contrast, large-target driving overlay. Keeps screen awake while visible.
 */
@Composable
fun CarModeOverlay(
    track: Track?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onExit: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    val title = track?.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val artist = track?.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)

    val configuration = LocalConfiguration.current

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0B0F),
            contentColor = Color.White
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                val boxMaxHeight = maxHeight
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                    (maxWidth > boxMaxHeight && boxMaxHeight < 520.dp)

                Column(modifier = Modifier.fillMaxSize()) {
                    CarModeTopBar(
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onExit = onExit
                    )

                    if (isLandscape) {
                        val artSize = (boxMaxHeight - 84.dp).coerceIn(120.dp, 220.dp)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(artSize),
                                contentAlignment = Alignment.Center
                            ) {
                                ArtworkImage(
                                    uri = track?.artworkUri,
                                    fallbackKey = title,
                                    shape = RoundedCornerShape(28.dp),
                                    glyphSize = 68.sp,
                                    contentDescription = title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.78f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                CarModeTransportControls(
                                    isPlaying = isPlaying,
                                    sideButtonSize = 72.dp,
                                    playButtonSize = 92.dp,
                                    onPrevious = onPrevious,
                                    onTogglePlayPause = onTogglePlayPause,
                                    onNext = onNext
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(0.5f))

                            val artSize = (boxMaxHeight * 0.30f).coerceIn(150.dp, 220.dp)
                            Box(
                                modifier = Modifier.size(artSize),
                                contentAlignment = Alignment.Center
                            ) {
                                ArtworkImage(
                                    uri = track?.artworkUri,
                                    fallbackKey = title,
                                    shape = RoundedCornerShape(32.dp),
                                    glyphSize = 84.sp,
                                    contentDescription = title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White.copy(alpha = 0.78f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            CarModeTransportControls(
                                isPlaying = isPlaying,
                                sideButtonSize = 84.dp,
                                playButtonSize = 108.dp,
                                onPrevious = onPrevious,
                                onTogglePlayPause = onTogglePlayPause,
                                onNext = onNext,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarModeTopBar(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_car_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Row {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun CarModeTransportControls(
    isPlaying: Boolean,
    sideButtonSize: Dp,
    playButtonSize: Dp,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(sideButtonSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFF1F222C),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                modifier = Modifier.size(sideButtonSize * 0.5f)
            )
        }

        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(playButtonSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                modifier = Modifier.size(playButtonSize * 0.5f)
            )
        }

        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(sideButtonSize),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFF1F222C),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                modifier = Modifier.size(sideButtonSize * 0.5f)
            )
        }
    }
}
