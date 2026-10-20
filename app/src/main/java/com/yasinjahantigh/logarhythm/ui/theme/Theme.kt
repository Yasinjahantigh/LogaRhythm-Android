package com.yasinjahantigh.logarhythm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun LogaRhythmTheme(
    themeMode: String = "system",
    colorSource: String = "album_art",
    cornerRoundness: String = "standard",
    seedArtworkColor: Color? = null,
    darkTheme: Boolean = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = colorSource == "wallpaper",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        colorSource == "album_art" && seedArtworkColor != null -> {
            generateDynamicSchemeFromSeed(seedArtworkColor, darkTheme)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = getAppShapes(cornerRoundness),
        content = content
    )
}
