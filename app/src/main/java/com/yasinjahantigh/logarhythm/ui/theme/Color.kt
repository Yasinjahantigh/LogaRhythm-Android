package com.yasinjahantigh.logarhythm.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/*
 * LogaRhythm's signature palette: "Ink & Ember".
 * Dark mode is warm ink with an ember-amber accent, like a stage lit from one side;
 * light mode is warm paper with burnt-amber type accents. Slate blue and sage supply the
 * secondary and tertiary voices so a single hue never carries the whole interface.
 * Every surface role is set explicitly so no tone leaks in from the Material baseline.
 */

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF4B266),
    onPrimary = Color(0xFF3A2100),
    primaryContainer = Color(0xFF5E3A0C),
    onPrimaryContainer = Color(0xFFFFDDB5),
    inversePrimary = Color(0xFF8A4F00),

    secondary = Color(0xFFB9C8D6),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF33424F),
    onSecondaryContainer = Color(0xFFD5E4F3),

    tertiary = Color(0xFFA8CFB5),
    onTertiary = Color(0xFF123824),
    tertiaryContainer = Color(0xFF2C4F3A),
    onTertiaryContainer = Color(0xFFC3EBD0),

    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE8E4DC),
    surface = Color(0xFF0E1013),
    onSurface = Color(0xFFE8E4DC),
    surfaceVariant = Color(0xFF3A3A3D),
    onSurfaceVariant = Color(0xFFC2BDB3),
    surfaceTint = Color(0xFFF4B266),
    inverseSurface = Color(0xFFE8E4DC),
    inverseOnSurface = Color(0xFF2F3033),

    surfaceDim = Color(0xFF0E1013),
    surfaceBright = Color(0xFF2A2D33),
    surfaceContainerLowest = Color(0xFF0A0C0E),
    surfaceContainerLow = Color(0xFF15181C),
    surfaceContainer = Color(0xFF191C21),
    surfaceContainerHigh = Color(0xFF202429),
    surfaceContainerHighest = Color(0xFF2A2E34),

    outline = Color(0xFF8C877E),
    outlineVariant = Color(0xFF3F3C37),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** Pure-black surfaces for OLED panels; the amber ink stays the same, only the canvas changes. */
val OledColorScheme = DarkColorScheme.copy(
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
    outlineVariant = Color(0xFF2A2A2A),
    inverseSurface = Color(0xFFE8E4DC)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8A4F00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB5),
    onPrimaryContainer = Color(0xFF2C1700),
    inversePrimary = Color(0xFFF4B266),

    secondary = Color(0xFF4A6174),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE5FA),
    onSecondaryContainer = Color(0xFF041E30),

    tertiary = Color(0xFF3F6A50),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1EFD0),
    onTertiaryContainer = Color(0xFF00210F),

    background = Color(0xFFFBF8F2),
    onBackground = Color(0xFF1D1B17),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF1D1B17),
    surfaceVariant = Color(0xFFE9E2D3),
    onSurfaceVariant = Color(0xFF4C4639),
    surfaceTint = Color(0xFF8A4F00),
    inverseSurface = Color(0xFF32302B),
    inverseOnSurface = Color(0xFFF6F0E7),

    surfaceDim = Color(0xFFDDD9D1),
    surfaceBright = Color(0xFFFBF8F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2EA),
    surfaceContainer = Color(0xFFF1ECE3),
    surfaceContainerHigh = Color(0xFFEBE6DC),
    surfaceContainerHighest = Color(0xFFE5E0D6),

    outline = Color(0xFF7E7667),
    outlineVariant = Color(0xFFD0C9B9),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private fun tone(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))

/**
 * Builds a complete Material 3 scheme from the playing album's dominant color. Surface-container
 * roles are derived from the same hue, so cards and sheets are tinted by the artwork instead of
 * falling back to the Material baseline's purple-grey.
 */
fun generateDynamicSchemeFromSeed(seed: Color, isDark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)

    val hue = hsl[0]
    val sat = hsl[1].coerceIn(0.32f, 0.85f)
    // Keep secondary harmonious with the artwork hue instead of shifting +24deg (which turns warm golds olive-green).
    val secondaryHue = hue
    val tertiaryHue = (hue + 36f) % 360f

    return if (isDark) {
        val accentSat = sat.coerceIn(0.42f, 0.84f)
        val primary = tone(hue, accentSat, 0.76f)
        val surface = tone(hue, 0.06f, 0.065f)
        darkColorScheme(
            primary = primary,
            onPrimary = tone(hue, accentSat, 0.10f),
            primaryContainer = tone(hue, (accentSat * 0.55f).coerceIn(0.22f, 0.45f), 0.22f),
            onPrimaryContainer = tone(hue, accentSat, 0.92f),
            secondary = tone(secondaryHue, accentSat * 0.65f, 0.78f),
            onSecondary = tone(secondaryHue, accentSat * 0.50f, 0.12f),
            secondaryContainer = tone(secondaryHue, (accentSat * 0.30f).coerceIn(0.12f, 0.32f), 0.20f),
            onSecondaryContainer = Color(0xFFF0F0F2),
            tertiary = tone(tertiaryHue, accentSat * 0.55f, 0.76f),
            onTertiary = tone(tertiaryHue, accentSat * 0.45f, 0.14f),
            tertiaryContainer = tone(tertiaryHue, (accentSat * 0.30f).coerceIn(0.12f, 0.32f), 0.22f),
            onTertiaryContainer = Color(0xFFF0F0F2),
            surface = surface,
            background = surface,
            onSurface = Color(0xFFF2F2F4),
            onBackground = Color(0xFFF2F2F4),
            surfaceVariant = tone(hue, 0.08f, 0.17f),
            onSurfaceVariant = Color(0xFFB8BCC2),
            surfaceTint = primary,
            surfaceDim = surface,
            surfaceBright = tone(hue, 0.07f, 0.18f),
            surfaceContainerLowest = tone(hue, 0.05f, 0.04f),
            surfaceContainerLow = tone(hue, 0.06f, 0.085f),
            surfaceContainer = tone(hue, 0.06f, 0.11f),
            surfaceContainerHigh = tone(hue, 0.07f, 0.14f),
            surfaceContainerHighest = tone(hue, 0.08f, 0.175f),
            outline = tone(hue, 0.06f, 0.48f),
            outlineVariant = tone(hue, 0.06f, 0.24f)
        )
    } else {
        val primary = tone(hue, sat, 0.38f)
        val surface = tone(hue, 0.08f, 0.98f)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = tone(hue, sat, 0.88f),
            onPrimaryContainer = tone(hue, sat, 0.14f),
            secondary = tone(secondaryHue, sat * 0.55f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = tone(secondaryHue, sat * 0.32f, 0.90f),
            onSecondaryContainer = tone(secondaryHue, sat * 0.45f, 0.16f),
            tertiary = tone(tertiaryHue, sat * 0.45f, 0.38f),
            onTertiary = Color.White,
            tertiaryContainer = tone(tertiaryHue, sat * 0.32f, 0.90f),
            onTertiaryContainer = tone(tertiaryHue, sat * 0.45f, 0.16f),
            surface = surface,
            background = surface,
            onSurface = Color(0xFF1B1B1B),
            onBackground = Color(0xFF1B1B1B),
            surfaceVariant = tone(hue, 0.10f, 0.90f),
            onSurfaceVariant = Color(0xFF444746),
            surfaceTint = primary,
            surfaceDim = tone(hue, 0.06f, 0.87f),
            surfaceBright = surface,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(hue, 0.08f, 0.96f),
            surfaceContainer = tone(hue, 0.08f, 0.94f),
            surfaceContainerHigh = tone(hue, 0.08f, 0.92f),
            surfaceContainerHighest = tone(hue, 0.08f, 0.90f),
            outline = tone(hue, 0.06f, 0.46f),
            outlineVariant = tone(hue, 0.08f, 0.82f)
        )
    }
}
