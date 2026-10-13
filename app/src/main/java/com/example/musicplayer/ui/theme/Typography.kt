package com.example.musicplayer.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * LogaRhythm's type system: one geometric sans for everything, in the spirit of the streaming
 * apps people know.
 *
 * - Latin UI: Poppins (bundled in res/font, SIL Open Font License). Round, geometric, friendly,
 *   very close in feel to Circular. It replaces the old serif display face, so titles and lists
 *   now share one voice.
 * - Persian / Arabic-script UI: Vazirmatn (SIL OFL) when its files are present in
 *   `assets/fonts/`. The Gradle task `downloadPersianFont` fetches them automatically on the first
 *   build; if they are missing the app falls back to Poppins plus the system's Arabic-script
 *   font, so nothing ever breaks.
 * - Letter spacing is always zero. Arabic-script text is cursive: tracking breaks the joins
 *   between letters, which is the most common reason Persian looks "off".
 * - Persian glyphs are taller and carry dots above and below the line, so line heights are
 *   generous and trimming of the first/last line is disabled.
 */
object AppFonts {
    /** Poppins mapped onto the weights the type scale uses (SemiBold shares the Medium cut). */
    val Poppins: FontFamily = FontFamily(
        Font(R.font.poppins_light, FontWeight.Light),
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_medium, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold)
    )

    private val PersianFiles = listOf(
        "Vazirmatn-Light.ttf" to FontWeight.Light,
        "Vazirmatn-Regular.ttf" to FontWeight.Normal,
        "Vazirmatn-Medium.ttf" to FontWeight.Medium,
        "Vazirmatn-SemiBold.ttf" to FontWeight.SemiBold,
        "Vazirmatn-Bold.ttf" to FontWeight.Bold
    )

    /** True for a real font file (TrueType / OpenType / TrueType collection), not an error page. */
    private fun looksLikeFont(context: Context, path: String): Boolean = try {
        context.assets.open(path).use { stream ->
            val head = ByteArray(4)
            val read = stream.read(head)
            read == 4 && (
                (head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0) ||
                    String(head, Charsets.ISO_8859_1) == "OTTO" ||
                    String(head, Charsets.ISO_8859_1) == "true"
                )
        }
    } catch (_: Exception) {
        false
    }

    /** Vazirmatn from `assets/fonts/`, or null when it has not been downloaded / added. */
    fun persian(context: Context): FontFamily? {
        val available = try {
            context.assets.list("fonts")?.toSet().orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
        if ("Vazirmatn-Regular.ttf" !in available) return null
        if (!looksLikeFont(context, "fonts/Vazirmatn-Regular.ttf")) return null
        val fonts = PersianFiles
            .filter { (name, _) -> name in available && looksLikeFont(context, "fonts/$name") }
            .map { (name, weight) -> Font(path = "fonts/$name", assetManager = context.assets, weight = weight) }
        return if (fonts.isEmpty()) null else FontFamily(fonts)
    }
}

/** Kept for source compatibility with older imports; the display face is now the UI face. */
val DisplayFontFamily: FontFamily = AppFonts.Poppins

private val Roomy = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    line: Int
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = Roomy
)

fun appTypography(family: FontFamily = AppFonts.Poppins): Typography = Typography(
    displayLarge = style(family, FontWeight.Bold, 48, 60),
    displayMedium = style(family, FontWeight.Bold, 40, 52),
    displaySmall = style(family, FontWeight.Bold, 32, 44),
    headlineLarge = style(family, FontWeight.Bold, 30, 42),
    headlineMedium = style(family, FontWeight.Bold, 26, 38),
    headlineSmall = style(family, FontWeight.SemiBold, 22, 32),
    titleLarge = style(family, FontWeight.Bold, 21, 30),
    titleMedium = style(family, FontWeight.Medium, 16, 24),
    titleSmall = style(family, FontWeight.Medium, 14, 22),
    bodyLarge = style(family, FontWeight.Normal, 16, 26),
    bodyMedium = style(family, FontWeight.Normal, 14, 22),
    bodySmall = style(family, FontWeight.Normal, 12, 18),
    labelLarge = style(family, FontWeight.Medium, 14, 20),
    labelMedium = style(family, FontWeight.Medium, 12, 18),
    labelSmall = style(family, FontWeight.Medium, 11, 16)
)

val Typography = appTypography()
