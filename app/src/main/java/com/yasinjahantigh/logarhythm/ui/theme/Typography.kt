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
    /** Poppins mapped onto the weights the type scale uses (SemiBold shares the Medium cut). */
    val Poppins: FontFamily = FontFamily(
        Font(R.font.poppins_light, FontWeight.Light),
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_medium, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold)
    )
}

val DisplayFontFamily: FontFamily = AppFonts.Poppins

// Zero tracking and untrimmed line boxes keep Arabic-script joins and diacritics intact.
private val Roomy = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int
) = TextStyle(
    fontFamily = AppFonts.Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = Roomy
)

val Typography = Typography(
    displayLarge = style(FontWeight.Bold, 48, 60),
    displayMedium = style(FontWeight.Bold, 40, 52),
    displaySmall = style(FontWeight.Bold, 32, 44),
    headlineLarge = style(FontWeight.Bold, 30, 42),
    headlineMedium = style(FontWeight.Bold, 26, 38),
    headlineSmall = style(FontWeight.SemiBold, 22, 32),
    titleLarge = style(FontWeight.Bold, 21, 30),
    titleMedium = style(FontWeight.Medium, 16, 24),
    titleSmall = style(FontWeight.Medium, 14, 22),
    bodyLarge = style(FontWeight.Normal, 16, 26),
    bodyMedium = style(FontWeight.Normal, 14, 22),
    bodySmall = style(FontWeight.Normal, 12, 18),
    labelLarge = style(FontWeight.Medium, 14, 20),
    labelMedium = style(FontWeight.Medium, 12, 18),
    labelSmall = style(FontWeight.Medium, 11, 16)
)
