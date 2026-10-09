package com.example.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// Primary Teal / Cyan tone suitable for modern audio players
val PrimaryTealLight = Color(0xFF006A60)
val OnPrimaryTealLight = Color(0xFFFFFFFF)
val PrimaryContainerTealLight = Color(0xFF73F8E5)
val OnPrimaryContainerTealLight = Color(0xFF00201C)

val SecondaryVioletLight = Color(0xFF4A635F)
val OnSecondaryVioletLight = Color(0xFFFFFFFF)
val SecondaryContainerVioletLight = Color(0xFFCCE8E2)
val OnSecondaryContainerVioletLight = Color(0xFF05201C)

val TertiaryCoralLight = Color(0xFF456179)
val OnTertiaryCoralLight = Color(0xFFFFFFFF)
val TertiaryContainerCoralLight = Color(0xFFCCE5FF)
val OnTertiaryContainerCoralLight = Color(0xFF001E31)

val BackgroundLight = Color(0xFFFAFDFB)
val OnBackgroundLight = Color(0xFF191C1B)
val SurfaceLight = Color(0xFFFAFDFB)
val OnSurfaceLight = Color(0xFF191C1B)
val SurfaceVariantLight = Color(0xFFDAE5E1)
val OnSurfaceVariantLight = Color(0xFF3F4946)
val OutlineLight = Color(0xFF6F7976)

// Dark Theme Colors
val PrimaryTealDark = Color(0xFF53DBC9)
val OnPrimaryTealDark = Color(0xFF003731)
val PrimaryContainerTealDark = Color(0xFF005048)
val OnPrimaryContainerTealDark = Color(0xFF73F8E5)

val SecondaryVioletDark = Color(0xFFB1CCC6)
val OnSecondaryVioletDark = Color(0xFF1C3531)
val SecondaryContainerVioletDark = Color(0xFF334B47)
val OnSecondaryContainerVioletDark = Color(0xFFCCE8E2)

val TertiaryCoralDark = Color(0xFFAECDE6)
val OnTertiaryCoralDark = Color(0xFF143349)
val TertiaryContainerCoralDark = Color(0xFF2D4A60)
val OnTertiaryContainerCoralDark = Color(0xFFCCE5FF)

val BackgroundDark = Color(0xFF0E1514)
val OnBackgroundDark = Color(0xFFE0E3E1)
val SurfaceDark = Color(0xFF0E1514)
val OnSurfaceDark = Color(0xFFE0E3E1)
val SurfaceVariantDark = Color(0xFF3F4946)
val OnSurfaceVariantDark = Color(0xFFBFC9C5)
val OutlineDark = Color(0xFF899390)

val LightColorScheme = lightColorScheme(
    primary = PrimaryTealLight,
    onPrimary = OnPrimaryTealLight,
    primaryContainer = PrimaryContainerTealLight,
    onPrimaryContainer = OnPrimaryContainerTealLight,
    secondary = SecondaryVioletLight,
    onSecondary = OnSecondaryVioletLight,
    secondaryContainer = SecondaryContainerVioletLight,
    onSecondaryContainer = OnSecondaryContainerVioletLight,
    tertiary = TertiaryCoralLight,
    onTertiary = OnTertiaryCoralLight,
    tertiaryContainer = TertiaryContainerCoralLight,
    onTertiaryContainer = OnTertiaryContainerCoralLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryTealDark,
    onPrimary = OnPrimaryTealDark,
    primaryContainer = PrimaryContainerTealDark,
    onPrimaryContainer = OnPrimaryContainerTealDark,
    secondary = SecondaryVioletDark,
    onSecondary = OnSecondaryVioletDark,
    secondaryContainer = SecondaryContainerVioletDark,
    onSecondaryContainer = OnSecondaryContainerVioletDark,
    tertiary = TertiaryCoralDark,
    onTertiary = OnTertiaryCoralDark,
    tertiaryContainer = TertiaryContainerCoralDark,
    onTertiaryContainer = OnTertiaryContainerCoralDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

/**
 * Dynamically computes an authentic Material 3 ColorScheme from a seed Color.
 */
fun generateDynamicSchemeFromSeed(seed: Color, isDark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)

    val hue = hsl[0]
    val sat = hsl[1].coerceIn(0.3f, 0.85f)

    if (isDark) {
        val primaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, sat, 0.75f))
        val primaryContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, sat, 0.25f))
        val surfaceArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.12f, 0.08f))
        val surfaceVariantArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.15f, 0.20f))

        return darkColorScheme(
            primary = Color(primaryArgb),
            onPrimary = Color(0xFF00201C),
            primaryContainer = Color(primaryContainerArgb),
            onPrimaryContainer = Color(primaryArgb),
            surface = Color(surfaceArgb),
            onSurface = Color(0xFFE5E5E5),
            surfaceVariant = Color(surfaceVariantArgb),
            onSurfaceVariant = Color(0xFFC4C7C5),
            background = Color(surfaceArgb),
            onBackground = Color(0xFFE5E5E5)
        )
    } else {
        val primaryArgb = ColorUtils.HSLToColor(floatArrayOf(hue, sat, 0.38f))
        val primaryContainerArgb = ColorUtils.HSLToColor(floatArrayOf(hue, sat, 0.88f))
        val surfaceArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.10f, 0.98f))
        val surfaceVariantArgb = ColorUtils.HSLToColor(floatArrayOf(hue, 0.15f, 0.90f))

        return lightColorScheme(
            primary = Color(primaryArgb),
            onPrimary = Color.White,
            primaryContainer = Color(primaryContainerArgb),
            onPrimaryContainer = Color(primaryArgb),
            surface = Color(surfaceArgb),
            onSurface = Color(0xFF1B1B1B),
            surfaceVariant = Color(surfaceVariantArgb),
            onSurfaceVariant = Color(0xFF444746),
            background = Color(surfaceArgb),
            onBackground = Color(0xFF1B1B1B)
        )
    }
}
