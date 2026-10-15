package com.example.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember

@Composable
fun MusicPlayerTheme(
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

    // Arabic-script UIs get Vazirmatn (when its files are present); everything else gets Poppins.
    val language = LocalConfiguration.current.locales.get(0)?.language.orEmpty()
    val isArabicScript = language in setOf("fa", "ar", "ur", "ps", "ckb", "ku")
    val typography = remember(isArabicScript) {
        val family = if (isArabicScript) AppFonts.persian(context) ?: AppFonts.Poppins else AppFonts.Poppins
        appTypography(family)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = getAppShapes(cornerRoundness),
        content = content
    )
}
