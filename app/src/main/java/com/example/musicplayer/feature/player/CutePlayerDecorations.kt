package com.example.musicplayer.feature.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.musicplayer.domain.model.Track

/**
 * Lightweight, high-performance equalizer visualizer bars.
 * Renders via a single Canvas pass and halts all animation clocks when paused.
 */
@Composable
fun CuteEqualizerVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    maxHeight: Dp = 20.dp,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val phase by if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq_anim")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.283185f, // 2*PI
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "eq_phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val barWidthDp = 3.5.dp
    val spacingDp = 3.dp
    val totalWidth = (barWidthDp * barCount) + (spacingDp * (barCount - 1))

    Canvas(
        modifier = modifier
            .size(width = totalWidth, height = maxHeight)
    ) {
        val barWidthPx = barWidthDp.toPx()
        val spacingPx = spacingDp.toPx()
        val maxHPx = size.height
        val minHPx = 3.dp.toPx()

        for (i in 0 until barCount) {
            val factor = if (isPlaying) {
                // Harmonic wave per bar derived from single phase
                val wave = kotlin.math.sin(phase.toDouble() * (1.0 + i * 0.4) + i * 1.2).toFloat()
                ((wave + 1f) / 2f).coerceIn(0.18f, 1.0f)
            } else {
                0.20f
            }

            val barHeight = (maxHPx * factor).coerceAtLeast(minHPx)
            val left = i * (barWidthPx + spacingPx)
            val top = maxHPx - barHeight

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, tertiaryColor),
                    startY = top,
                    endY = maxHPx
                ),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidthPx, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )
        }
    }
}

/**
 * Vinyl turntable record player view with smooth rotation and vinyl grooves.
 */
@Composable
fun CuteVinylTurntable(
    track: Track?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation by if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "vinyl_transition")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vinyl_spin"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 0f else -26f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "tonearm_angle"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer Shadow & Glowing Halo
        Box(
            modifier = Modifier
                .fillMaxSize(0.96f)
                .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .background(Color(0xFF101014))
        )

        // Vinyl grooves and shine drawn on Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.96f)
                .rotate(if (isPlaying) rotation else 0f)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            // Multiple concentric groove rings
            val grooveRadii = listOf(0.92f, 0.85f, 0.78f, 0.71f, 0.64f, 0.57f, 0.50f)
            grooveRadii.forEach { factor ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = maxR * factor,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
            }

            // Light sheen reflection arcs
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.08f)
                    ),
                    center = center
                ),
                radius = maxR * 0.94f,
                center = center
            )
        }

        // Center Artwork Label
        Box(
            modifier = Modifier
                .fillMaxSize(0.44f)
                .rotate(if (isPlaying) rotation else 0f)
                .clip(CircleShape)
                .border(3.dp, Color(0xFF26262B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track?.artworkUri,
                contentDescription = stringResource(R.string.cd_artwork, track?.title.orEmpty()),
                placeholder = painterResource(id = R.drawable.ic_music_placeholder),
                error = painterResource(id = R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Center spindle hole
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E22))
                    .border(2.dp, Color(0xFFC0C0C8), CircleShape)
            )
        }

        // Tonearm overlay in top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 8.dp)
                .rotate(tonearmAngle)
                .size(width = 48.dp, height = 90.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pivot = Offset(size.width * 0.75f, 10f)
                // Pivot base
                drawCircle(
                    color = Color(0xFF888890),
                    radius = 8f,
                    center = pivot
                )
                // Arm line
                drawLine(
                    color = Color(0xFFD0D0D8),
                    start = pivot,
                    end = Offset(16f, size.height - 12f),
                    strokeWidth = 4f
                )
                // Cartridge / headshell
                drawCircle(
                    color = Color(0xFFFF5252),
                    radius = 6f,
                    center = Offset(16f, size.height - 12f)
                )
            }
        }
    }
}
