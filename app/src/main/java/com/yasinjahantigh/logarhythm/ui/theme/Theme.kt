package com.yasinjahantigh.logarhythm.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun LogaRhythmTheme(
    themeMode: String = "system",
    themeStyle: String = "default",
    customAccentColor: Color = Color(0xFFF4B266),
    customBase: String = "dark",
    cornerRoundness: String = "standard",
    seedArtworkColor: Color? = null,
    darkTheme: Boolean = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val effectiveDark = when (themeStyle) {
        "oled" -> true
        "custom" -> customBase != "light"
        else -> darkTheme
    }
    val fallback = if (effectiveDark) DarkColorScheme else LightColorScheme
    val colorScheme = when (themeStyle) {
        "oled" -> OledColorScheme
        "album_art" -> seedArtworkColor?.let { generateDynamicSchemeFromSeed(it, effectiveDark) } ?: fallback
        "wallpaper" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            fallback
        }
        "custom" -> {
            val generated = generateDynamicSchemeFromSeed(customAccentColor, effectiveDark)
            if (customBase == "oled") generated.withBlackSurfaces() else generated
        }
        else -> fallback
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = getAppShapes(cornerRoundness),
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun ColorScheme.withBlackSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF242424),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF060606),
    surfaceContainer = Color(0xFF0B0B0B),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF181818),
    surfaceVariant = Color(0xFF1F1F1F),
    outlineVariant = Color(0xFF2A2A2A)
)
