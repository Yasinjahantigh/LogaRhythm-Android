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
 * Animated cute equalizer bars that bounce playfully when playback is active.
 */
@Composable
fun CuteEqualizerVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 7,
    maxHeight: Dp = 24.dp,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cute_eq_anim")

    // Generate distinct bounce animations for each bar to look natural & playful
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(290, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.98f,
        animationSpec = infiniteRepeatable(tween(330, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq5"
    )
    val bar6 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(410, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq6"
    )
    val bar7 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq7"
    )

    val factors = listOf(bar1, bar2, bar3, bar4, bar5, bar6, bar7)

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        factors.take(barCount).forEachIndexed { index, factor ->
            val animatedHeight = if (isPlaying) {
                (maxHeight * factor).coerceAtLeast(4.dp)
            } else {
                4.dp
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                barColor,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
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
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_spin"
    )

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
