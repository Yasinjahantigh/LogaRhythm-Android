package com.yasinjahantigh.logarhythm.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.ShareCardAspect
import com.yasinjahantigh.logarhythm.core.utils.ShareCardGenerator
import com.yasinjahantigh.logarhythm.core.utils.ShareCardOptions
import com.yasinjahantigh.logarhythm.core.utils.ShareCardStyle
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.launch

private val SHARE_ACCENT_SWATCHES = listOf(
    0xFFF4B266.toInt(),
    0xFFE85D8A.toInt(),
    0xFF7B61FF.toInt(),
    0xFF42A5F5.toInt(),
    0xFF26A69A.toInt(),
    0xFF66BB6A.toInt(),
    0xFFEF6C57.toInt(),
    0xFFD9C67A.toInt()
)

@Composable
fun ShareCardLivePreviewBox(
    previewBitmap: Bitmap?,
    aspect: ShareCardAspect,
    isRendering: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 330.dp)
            .testTag("share_card_live_preview")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_card_preview_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .heightIn(max = 310.dp)
                        .aspectRatio(aspect.ratio)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(220.dp)
                        .aspectRatio(aspect.ratio)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }
            if (isRendering && previewBitmap != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(6.dp).size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ShareCardCustomizePanel(
    options: ShareCardOptions,
    onOptionsChange: (ShareCardOptions) -> Unit,
    defaultAccentColor: Int,
    modifier: Modifier = Modifier
) {
    val swatches = remember(defaultAccentColor) {
        (listOf(defaultAccentColor) + SHARE_ACCENT_SWATCHES).distinct()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Theme Style
        Text(
            text = stringResource(R.string.share_card_style_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ShareCardStyle.DARK_GLASS to R.string.share_style_dark_glass,
                ShareCardStyle.VIBRANT_GRADIENT to R.string.share_style_vibrant,
                ShareCardStyle.MINIMAL_LIGHT to R.string.share_style_minimal_light,
                ShareCardStyle.WARM_VINYL to R.string.share_style_warm_vinyl
            ).forEach { (style, labelRes) ->
                FilterChip(
                    selected = options.style == style,
                    onClick = { onOptionsChange(options.copy(style = style)) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.testTag("share_style_${style.name.lowercase()}")
                )
            }
        }

        // Aspect Ratio / Size
        Text(
            text = stringResource(R.string.share_card_size_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ShareCardAspect.STORY_9_16 to R.string.share_size_story,
                ShareCardAspect.POST_4_5 to R.string.share_size_post,
                ShareCardAspect.SQUARE_1_1 to R.string.share_size_square
            ).forEach { (aspect, labelRes) ->
                FilterChip(
                    selected = options.aspect == aspect,
                    onClick = { onOptionsChange(options.copy(aspect = aspect)) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.testTag("share_size_${aspect.name.lowercase()}")
                )
            }
        }

        // Accent Swatches
        Text(
            text = stringResource(R.string.share_card_color_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            swatches.forEach { argb ->
                val selected = options.accentColor == argb
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { onOptionsChange(options.copy(accentColor = argb)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Text Scale Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.share_card_text_size_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${(options.textScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = options.textScale,
            onValueChange = { onOptionsChange(options.copy(textScale = it)) },
            valueRange = 0.8f..1.3f,
            steps = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongShareStudioSheet(
    track: Track,
    defaultAccentColor: Int,
    onShareAudioFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember(track.id, defaultAccentColor) {
        mutableStateOf(ShareCardOptions(accentColor = defaultAccentColor))
    }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val cardData = remember(track, options) {
        ShareCardGenerator.SongCardData(
            songTitle = track.title.cleanMetaOrNull() ?: track.title,
            artist = track.artist.cleanMetaOrNull() ?: track.artist,
            album = track.album.cleanMetaOrNull(),
            durationLabel = track.durationMs.takeIf { it > 0L }?.let(TimeUtils::formatDuration),
            artworkUri = track.artworkUri,
            options = options
        )
    }

    LaunchedEffect(cardData) {
        rendering = true
        val bmp = runCatching {
            ShareCardGenerator.renderSongCardPreview(context, cardData)
        }.getOrNull()
        previewBitmap = bmp
        rendering = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("song_share_studio_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.share_song_studio_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            ShareCardLivePreviewBox(
                previewBitmap = previewBitmap,
                aspect = options.aspect,
                isRendering = rendering
            )

            ShareCardCustomizePanel(
                options = options,
                onOptionsChange = { options = it },
                defaultAccentColor = defaultAccentColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onShareAudioFile()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("share_audio_file_button")
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share_audio_file_action))
                }

                Button(
                    onClick = {
                        if (sharing) return@Button
                        sharing = true
                        scope.launch {
                            try {
                                val uri = ShareCardGenerator.createSongCard(context, cardData)
                                ShareCardGenerator.launchShareIntent(
                                    context,
                                    uri,
                                    context.getString(R.string.share_song_studio_title)
                                )
                                onDismiss()
                            } catch (_: Exception) {
                                Toast.makeText(context, context.getString(R.string.share_card_failed), Toast.LENGTH_SHORT).show()
                            } finally {
                                sharing = false
                            }
                        }
                    },
                    enabled = !sharing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("share_song_card_button")
                ) {
                    if (sharing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share_card_image_action))
                }
            }
        }
    }
}
