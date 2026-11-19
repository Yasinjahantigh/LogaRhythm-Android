package com.yasinjahantigh.logarhythm.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontVariationAxis
import android.os.Build
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.yasinjahantigh.logarhythm.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle

object AppFonts {
    /** Vazirmatn covers Persian and Latin with full variable weights. */
    val Vazirmatn: FontFamily = FontFamily(
        Font(R.font.vazirmatn_light, FontWeight.Light),
        Font(R.font.vazirmatn_regular, FontWeight.Normal),
        Font(R.font.vazirmatn_medium, FontWeight.Medium),
        Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
        Font(R.font.vazirmatn_bold, FontWeight.Bold)
    )
}

data class AppFontPreset(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int
)

val APP_FONT_PRESETS: List<AppFontPreset> = listOf(
    AppFontPreset("vazirmatn", R.string.theme_font_vazirmatn, R.string.theme_font_vazirmatn_desc),
    AppFontPreset("system", R.string.theme_font_system, R.string.theme_font_system_desc),
    AppFontPreset("jakarta_vazir", R.string.theme_font_jakarta_vazir, R.string.theme_font_jakarta_vazir_desc),
    AppFontPreset("space_kufi", R.string.theme_font_space_kufi, R.string.theme_font_space_kufi_desc),
    AppFontPreset("playfair_naskh", R.string.theme_font_playfair_naskh, R.string.theme_font_playfair_naskh_desc),
    AppFontPreset("mono", R.string.theme_font_mono, R.string.theme_font_mono_desc),
    AppFontPreset("lalezar_jakarta", R.string.theme_font_lalezar_jakarta, R.string.theme_font_lalezar_jakarta_desc)
)

val DisplayFontFamily: FontFamily = FontFamily.Default

// Zero tracking and untrimmed line boxes keep Arabic-script joins and diacritics intact.
private val Roomy = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private val typefaceCache = ConcurrentHashMap<String, Pair<FontFamily, FontFamily>>()

fun normalizeFontKey(raw: String): String = when (raw) {
    "default" -> "vazirmatn"
    "sans" -> "jakarta_vazir"
    "serif" -> "playfair_naskh"
    "mono_lalezar" -> "mono"
    else -> raw
}

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    family: FontFamily
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = Roomy
)

fun typographyForFont(
    appFont: String,
    context: Context? = null,
    customFontPath: String? = null
): Typography {
    val key = normalizeFontKey(appFont)
    val (regular, bold) = resolveFontFamilies(key, context, customFontPath)
    return Typography(
        displayLarge = style(FontWeight.Bold, 48, 60, bold),
        displayMedium = style(FontWeight.Bold, 40, 52, bold),
        displaySmall = style(FontWeight.Bold, 32, 44, bold),
        headlineLarge = style(FontWeight.Bold, 30, 42, bold),
        headlineMedium = style(FontWeight.Bold, 26, 38, bold),
        headlineSmall = style(FontWeight.SemiBold, 22, 32, bold),
        titleLarge = style(FontWeight.Bold, 21, 30, bold),
        titleMedium = style(FontWeight.Medium, 16, 24, regular),
        titleSmall = style(FontWeight.Medium, 14, 22, regular),
        bodyLarge = style(FontWeight.Normal, 16, 26, regular),
        bodyMedium = style(FontWeight.Normal, 14, 22, regular),
        bodySmall = style(FontWeight.Normal, 12, 18, regular),
        labelLarge = style(FontWeight.Medium, 14, 20, regular),
        labelMedium = style(FontWeight.Medium, 12, 18, regular),
        labelSmall = style(FontWeight.Medium, 11, 16, regular)
    )
}

fun resolveFontFamilies(
    appFont: String,
    context: Context?,
    customFontPath: String?
): Pair<FontFamily, FontFamily> {
    val key = normalizeFontKey(appFont)
    if (key == "system") return FontFamily.Default to FontFamily.Default
    if (key == "vazirmatn") return AppFonts.Vazirmatn to AppFonts.Vazirmatn
    if (context == null) return AppFonts.Vazirmatn to AppFonts.Vazirmatn

    val cacheKey = "$key|${customFontPath.orEmpty()}"
    typefaceCache[cacheKey]?.let { return it }

    val built = when (key) {
        "jakarta_vazir" -> buildPairedFamily(
            context = context,
            latinRes = R.font.plus_jakarta_sans,
            persianRegularRes = R.font.vazirmatn_regular,
            persianBoldRes = R.font.vazirmatn_bold
        )
        "space_kufi" -> buildPairedFamily(
            context = context,
            latinRes = R.font.space_grotesk,
            persianRegularRes = R.font.noto_kufi_arabic,
            persianBoldRes = R.font.noto_kufi_arabic
        )
        "playfair_naskh" -> buildPairedFamily(
            context = context,
            latinRes = R.font.playfair_display,
            persianRegularRes = R.font.noto_naskh_arabic,
            persianBoldRes = R.font.noto_naskh_arabic
        )
        "mono" -> buildPairedFamily(
            context = context,
            latinRes = R.font.jetbrains_mono,
            persianRegularRes = R.font.vazirmatn_regular,
            persianBoldRes = R.font.vazirmatn_bold
        )
        "lalezar_jakarta" -> buildPairedFamily(
            context = context,
            latinRes = R.font.plus_jakarta_sans,
            persianRegularRes = R.font.vazirmatn_medium,
            persianBoldRes = R.font.lalezar
        )
        "custom" -> buildCustomFontFamily(context, customFontPath)
        else -> FontFamily.Default to FontFamily.Default
    }
    typefaceCache[cacheKey] = built
    return built
}

private fun buildPairedFamily(
    context: Context,
    @FontRes latinRes: Int,
    @FontRes persianRegularRes: Int,
    @FontRes persianBoldRes: Int
): Pair<FontFamily, FontFamily> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val fallback = FontFamily(
            Font(latinRes, FontWeight.Normal),
            Font(latinRes, FontWeight.Bold)
        )
        return fallback to fallback
    }
    val regularTypeface = buildFallbackTypeface(
        context = context,
        latinRes = latinRes,
        latinWeight = 400,
        persianRes = persianRegularRes,
        persianWeight = 400
    )
    val boldTypeface = buildFallbackTypeface(
        context = context,
        latinRes = latinRes,
        latinWeight = 700,
        persianRes = persianBoldRes,
        persianWeight = 700
    )
    return FontFamily(androidx.compose.ui.text.font.Typeface(regularTypeface)) to
        FontFamily(androidx.compose.ui.text.font.Typeface(boldTypeface))
}

private fun buildFallbackTypeface(
    context: Context,
    @FontRes latinRes: Int,
    latinWeight: Int,
    @FontRes persianRes: Int,
    persianWeight: Int
): Typeface {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Typeface.DEFAULT
    val res = context.resources
    val latinFont = runCatching {
        PlatformFont.Builder(res, latinRes)
            .setWeight(latinWeight)
            .setSlant(PlatformFontStyle.FONT_SLANT_UPRIGHT)
            .setFontVariationSettings(arrayOf(FontVariationAxis("wght", latinWeight.toFloat())))
            .build()
    }.getOrElse {
        PlatformFont.Builder(res, latinRes).setWeight(latinWeight).build()
    }
    val persianFont = runCatching {
        PlatformFont.Builder(res, persianRes)
            .setWeight(persianWeight)
            .setSlant(PlatformFontStyle.FONT_SLANT_UPRIGHT)
            .setFontVariationSettings(arrayOf(FontVariationAxis("wght", persianWeight.toFloat())))
            .build()
    }.getOrElse {
        PlatformFont.Builder(res, persianRes).setWeight(persianWeight).build()
    }
    val latinFamily = PlatformFontFamily.Builder(latinFont).build()
    val persianFamily = PlatformFontFamily.Builder(persianFont).build()
    return Typeface.CustomFallbackBuilder(latinFamily)
        .addCustomFallback(persianFamily)
        .setSystemFallback("sans-serif")
        .build()
}

private fun buildCustomFontFamily(
    context: Context,
    customFontPath: String?
): Pair<FontFamily, FontFamily> {
    val file = customFontPath?.let(::File)?.takeIf { it.isFile }
        ?: return FontFamily.Default to FontFamily.Default
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val primaryFont = PlatformFont.Builder(file).build()
            val primaryFamily = PlatformFontFamily.Builder(primaryFont).build()
            val persianRegular = PlatformFontFamily.Builder(
                PlatformFont.Builder(context.resources, R.font.vazirmatn_regular).build()
            ).build()
            val persianBold = PlatformFontFamily.Builder(
                PlatformFont.Builder(context.resources, R.font.vazirmatn_bold).build()
            ).build()
            val reg = Typeface.CustomFallbackBuilder(primaryFamily)
                .addCustomFallback(persianRegular)
                .setSystemFallback("sans-serif")
                .build()
            val bold = Typeface.CustomFallbackBuilder(primaryFamily)
                .addCustomFallback(persianBold)
                .setSystemFallback("sans-serif")
                .build()
            FontFamily(androidx.compose.ui.text.font.Typeface(reg)) to
                FontFamily(androidx.compose.ui.text.font.Typeface(bold))
        } else {
            val tf = Typeface.createFromFile(file)
            val ff = FontFamily(androidx.compose.ui.text.font.Typeface(tf))
            ff to ff
        }
    }.getOrElse {
        FontFamily.Default to FontFamily.Default
    }
}

val Typography = typographyForFont("vazirmatn")
