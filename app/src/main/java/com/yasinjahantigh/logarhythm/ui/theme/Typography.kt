package com.yasinjahantigh.logarhythm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.yasinjahantigh.logarhythm.R

object AppFonts {
    /** Vazirmatn covers Persian and Latin, so Arabic-script text no longer falls back to the device font. */
    val Vazirmatn: FontFamily = FontFamily(
        Font(R.font.vazirmatn_light, FontWeight.Light),
        Font(R.font.vazirmatn_regular, FontWeight.Normal),
        Font(R.font.vazirmatn_medium, FontWeight.Medium),
        Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
        Font(R.font.vazirmatn_bold, FontWeight.Bold)
    )
}

val DisplayFontFamily: FontFamily = AppFonts.Vazirmatn

// Zero tracking and untrimmed line boxes keep Arabic-script joins and diacritics intact.
private val Roomy = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    family: FontFamily = AppFonts.Vazirmatn
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = Roomy
)

fun typographyForFont(appFont: String): Typography {
    val family = when (appFont) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> AppFonts.Vazirmatn
    }
    return Typography(
        displayLarge = style(FontWeight.Bold, 48, 60, family),
        displayMedium = style(FontWeight.Bold, 40, 52, family),
        displaySmall = style(FontWeight.Bold, 32, 44, family),
        headlineLarge = style(FontWeight.Bold, 30, 42, family),
        headlineMedium = style(FontWeight.Bold, 26, 38, family),
        headlineSmall = style(FontWeight.SemiBold, 22, 32, family),
        titleLarge = style(FontWeight.Bold, 21, 30, family),
        titleMedium = style(FontWeight.Medium, 16, 24, family),
        titleSmall = style(FontWeight.Medium, 14, 22, family),
        bodyLarge = style(FontWeight.Normal, 16, 26, family),
        bodyMedium = style(FontWeight.Normal, 14, 22, family),
        bodySmall = style(FontWeight.Normal, 12, 18, family),
        labelLarge = style(FontWeight.Medium, 14, 20, family),
        labelMedium = style(FontWeight.Medium, 12, 18, family),
        labelSmall = style(FontWeight.Medium, 11, 16, family)
    )
}

val Typography = typographyForFont("default")
