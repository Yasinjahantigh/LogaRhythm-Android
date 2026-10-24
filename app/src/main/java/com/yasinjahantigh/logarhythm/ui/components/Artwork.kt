package com.yasinjahantigh.logarhythm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yasinjahantigh.logarhythm.core.artwork.ArtworkVersionStore

/** Deterministic warm gradients for artists, genres and songs without embedded artwork. */
private val ArtPalette: List<Pair<Color, Color>> = listOf(
    Color(0xFFF4B266) to Color(0xFFB8572B),
    Color(0xFF7FB7BE) to Color(0xFF3A6EA5),
    Color(0xFFA8CFB5) to Color(0xFF3F6A50),
    Color(0xFFE59FB5) to Color(0xFF9B4A7A),
    Color(0xFFB9A3E3) to Color(0xFF5B4B9A),
    Color(0xFFE8C468) to Color(0xFF9C6B1F),
    Color(0xFF8FD3C1) to Color(0xFF2F7F73),
    Color(0xFFEE9B8A) to Color(0xFFA8412E)
)

/** The two colours that belong to [key]. */
fun artColorsFor(key: String): Pair<Color, Color> {
    val index = (key.lowercase().hashCode() and Int.MAX_VALUE) % ArtPalette.size
    return ArtPalette[index]
}

/** A diagonal gradient that belongs to [key]. */
fun artBrushFor(key: String): Brush {
    val (start, end) = artColorsFor(key)
    return Brush.linearGradient(listOf(start, end))
}

/** First readable character of [name], used as a monogram on generated artwork. */
fun monogramOf(name: String): String =
    name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "♪"

/** Draws the real artwork over its gradient fallback, so missing or broken images stay invisible. */
@Composable
fun ArtworkImage(
    uri: String?,
    fallbackKey: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    icon: ImageVector? = null,
    glyphSize: TextUnit = 22.sp,
    contentDescription: String? = null
) {
    val brush = remember(fallbackKey) { artBrushFor(fallbackKey) }
    val monogram = remember(fallbackKey) { monogramOf(fallbackKey) }
    val context = LocalContext.current
    val versions by ArtworkVersionStore.versions.collectAsState()
    val version = uri?.let { versions[it] } ?: 0L

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.fillMaxSize(0.46f)
            )
        } else {
            Text(
                text = monogram,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = glyphSize,
                fontWeight = FontWeight.Bold
            )
        }
        if (!uri.isNullOrBlank()) {
            val request = remember(uri, version) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCacheKey("$uri#v$version")
                    .diskCacheKey("$uri#v$version")
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
