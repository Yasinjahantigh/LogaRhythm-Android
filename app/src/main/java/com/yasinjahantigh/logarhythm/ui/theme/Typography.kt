package com.yasinjahantigh.logarhythm.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontVariationAxis
import android.os.Build
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
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
private val rawTypefaceCache = ConcurrentHashMap<String, Pair<Typeface, Typeface>>()

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
        "jakarta_vazir",
        "space_kufi",
        "playfair_naskh",
        "mono",
        "lalezar_jakarta",
        "custom" -> {
            val (regTf, boldTf) = resolveAppTypefacePair(key, context, customFontPath)
            FontFamily(androidx.compose.ui.text.font.Typeface(regTf)) to
                FontFamily(androidx.compose.ui.text.font.Typeface(boldTf))
        }
        else -> FontFamily.Default to FontFamily.Default
    }
    typefaceCache[cacheKey] = built
    return built
}

/**
 * Resolves cached regular/bold native Android [Typeface] instances for Canvas renderers
 * (such as lyric, song, and listening report share cards) so rendered bitmaps match the active app font.
 */
fun resolveAppTypefacePair(
    appFont: String,
    context: Context,
    customFontPath: String? = null
): Pair<Typeface, Typeface> {
    val key = normalizeFontKey(appFont)
    if (key == "system") return Typeface.DEFAULT to Typeface.DEFAULT_BOLD

    val cacheKey = "$key|${customFontPath.orEmpty()}"
    rawTypefaceCache[cacheKey]?.let { return it }

    val built = when (key) {
        "vazirmatn" -> buildNativePair(
            context = context,
            latinRegularRes = R.font.vazirmatn_regular,
            latinBoldRes = R.font.vazirmatn_bold,
            persianRegularRes = R.font.vazirmatn_regular,
            persianBoldRes = R.font.vazirmatn_bold
        )
        "jakarta_vazir" -> buildNativePair(
            context = context,
            latinRegularRes = R.font.plus_jakarta_sans,
            latinBoldRes = R.font.plus_jakarta_sans,
            persianRegularRes = R.font.vazirmatn_regular,
            persianBoldRes = R.font.vazirmatn_bold
        )
        "space_kufi" -> buildNativePair(
            context = context,
            latinRegularRes = R.font.space_grotesk,
            latinBoldRes = R.font.space_grotesk,
            persianRegularRes = R.font.noto_kufi_arabic,
            persianBoldRes = R.font.noto_kufi_arabic
        )
        "playfair_naskh" -> buildNativePair(
            context = context,
            latinRegularRes = R.font.playfair_display,
            latinBoldRes = R.font.playfair_display,
            persianRegularRes = R.font.noto_naskh_arabic,
            persianBoldRes = R.font.noto_naskh_arabic
        )
        "mono" -> buildNativePair(
            context = context,
            latinRegularRes = R.font.jetbrains_mono,
            latinBoldRes = R.font.jetbrains_mono,
            persianRegularRes = R.font.vazirmatn_regular,
            persianBoldRes = R.font.vazirmatn_bold
        )
        "lalezar_jakarta" -> buildNativePair(
            context = context,
            latinRegularRes = R.font.plus_jakarta_sans,
            latinBoldRes = R.font.plus_jakarta_sans,
            persianRegularRes = R.font.vazirmatn_medium,
            persianBoldRes = R.font.lalezar
        )
        "custom" -> buildCustomTypefacePair(context, customFontPath)
        else -> buildNativePair(
            context = context,
            latinRegularRes = R.font.vazirmatn_regular,
            latinBoldRes = R.font.vazirmatn_bold,
            persianRegularRes = R.font.vazirmatn_regular,
            persianBoldRes = R.font.vazirmatn_bold
        )
    }
    rawTypefaceCache[cacheKey] = built
    return built
}

private fun buildNativePair(
    context: Context,
    @FontRes latinRegularRes: Int,
    @FontRes latinBoldRes: Int,
    @FontRes persianRegularRes: Int,
    @FontRes persianBoldRes: Int
): Pair<Typeface, Typeface> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val reg = runCatching { ResourcesCompat.getFont(context, persianRegularRes) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(context, R.font.vazirmatn_regular) }.getOrNull()
            ?: Typeface.DEFAULT
        val bold = runCatching { ResourcesCompat.getFont(context, persianBoldRes) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(context, R.font.vazirmatn_bold) }.getOrNull()
            ?: Typeface.DEFAULT_BOLD
        return reg to bold
    }
    val reg = buildFallbackTypeface(
        context = context,
        latinRes = latinRegularRes,
        latinWeight = 400,
        persianRes = persianRegularRes,
        persianWeight = 400
    )
    val bold = buildFallbackTypeface(
        context = context,
        latinRes = latinBoldRes,
        latinWeight = 700,
        persianRes = persianBoldRes,
        persianWeight = 700
    )
    return reg to bold
}

private fun buildFallbackTypeface(
    context: Context,
    @FontRes latinRes: Int,
    latinWeight: Int,
    @FontRes persianRes: Int,
    persianWeight: Int
): Typeface {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return runCatching { ResourcesCompat.getFont(context, persianRes) }.getOrNull() ?: Typeface.DEFAULT
    }
    return runCatching {
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
        val latinFamily = PlatformFontFamily.Builder(latinFont).build()
        val builder = Typeface.CustomFallbackBuilder(latinFamily)
        if (persianRes != latinRes || persianWeight != latinWeight) {
            val persianFont = runCatching {
                PlatformFont.Builder(res, persianRes)
                    .setWeight(persianWeight)
                    .setSlant(PlatformFontStyle.FONT_SLANT_UPRIGHT)
                    .setFontVariationSettings(arrayOf(FontVariationAxis("wght", persianWeight.toFloat())))
                    .build()
            }.getOrElse {
                PlatformFont.Builder(res, persianRes).setWeight(persianWeight).build()
            }
            builder.addCustomFallback(PlatformFontFamily.Builder(persianFont).build())
        }
        builder.setSystemFallback("sans-serif").build()
    }.getOrElse {
        runCatching { ResourcesCompat.getFont(context, persianRes) }.getOrNull()
            ?: runCatching { ResourcesCompat.getFont(context, latinRes) }.getOrNull()
            ?: if (latinWeight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}

private fun buildCustomTypefacePair(
    context: Context,
    customFontPath: String?
): Pair<Typeface, Typeface> {
    val fallback = buildNativePair(
        context = context,
        latinRegularRes = R.font.vazirmatn_regular,
        latinBoldRes = R.font.vazirmatn_bold,
        persianRegularRes = R.font.vazirmatn_regular,
        persianBoldRes = R.font.vazirmatn_bold
    )
    val file = customFontPath?.let(::File)?.takeIf { it.isFile } ?: return fallback
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
            reg to bold
        } else {
            val tf = Typeface.createFromFile(file)
            tf to tf
        }
    }.getOrElse { fallback }
}

val Typography = typographyForFont("vazirmatn")
