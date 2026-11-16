package com.yasinjahantigh.logarhythm.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.yasinjahantigh.logarhythm.R

/**
 * Curated ready-to-use theme presets so users can pick a cohesive palette in one tap
 * without tuning custom hue/saturation sliders.
 */
data class CuratedThemePreset(
    val id: String,
    @StringRes val nameRes: Int,
    val primaryHex: Long,
    val secondaryHex: Long,
    val surfaceHex: Long,
    val isDark: Boolean,
    val amoled: Boolean = false
) {
    fun toColorScheme(): ColorScheme {
        val base = generateDynamicSchemeFromSeed(Color(primaryHex), isDark = isDark)
        val tinted = if (isDark) {
            base.copy(
                secondary = Color(secondaryHex),
                surface = Color(surfaceHex),
                background = Color(surfaceHex)
            )
        } else {
            base.copy(
                secondary = Color(secondaryHex),
                surface = Color(surfaceHex),
                background = Color(surfaceHex)
            )
        }
        return if (amoled) tinted.withBlackSurfaces() else tinted
    }
}

object ThemePresets {
    const val DEFAULT_ID = "midnight_emerald"

    val all: List<CuratedThemePreset> = listOf(
        CuratedThemePreset(
            id = "midnight_emerald",
            nameRes = R.string.theme_curated_midnight_emerald,
            primaryHex = 0xFF34D399,
            secondaryHex = 0xFF6EE7B7,
            surfaceHex = 0xFF0B1110,
            isDark = true
        ),
        CuratedThemePreset(
            id = "sunset_ember",
            nameRes = R.string.theme_curated_sunset_ember,
            primaryHex = 0xFFFF784F,
            secondaryHex = 0xFFFBBF24,
            surfaceHex = 0xFF140D0B,
            isDark = true
        ),
        CuratedThemePreset(
            id = "oceanic_abyss",
            nameRes = R.string.theme_curated_oceanic_abyss,
            primaryHex = 0xFF38BDF8,
            secondaryHex = 0xFF818CF8,
            surfaceHex = 0xFF0A111A,
            isDark = true
        ),
        CuratedThemePreset(
            id = "cyber_neon",
            nameRes = R.string.theme_curated_cyber_neon,
            primaryHex = 0xFFE879F9,
            secondaryHex = 0xFF22D3EE,
            surfaceHex = 0xFF000000,
            isDark = true,
            amoled = true
        ),
        CuratedThemePreset(
            id = "nord_frost",
            nameRes = R.string.theme_curated_nord_frost,
            primaryHex = 0xFF88C0D0,
            secondaryHex = 0xFF81A1C1,
            surfaceHex = 0xFF12161E,
            isDark = true
        ),
        CuratedThemePreset(
            id = "velvet_violet",
            nameRes = R.string.theme_curated_velvet_violet,
            primaryHex = 0xFFA78BFA,
            secondaryHex = 0xFFF472B6,
            surfaceHex = 0xFF110D18,
            isDark = true
        ),
        CuratedThemePreset(
            id = "rose_quartz",
            nameRes = R.string.theme_curated_rose_quartz,
            primaryHex = 0xFFFB7185,
            secondaryHex = 0xFFFDA4AF,
            surfaceHex = 0xFF160E11,
            isDark = true
        ),
        CuratedThemePreset(
            id = "mocha_espresso",
            nameRes = R.string.theme_curated_mocha_espresso,
            primaryHex = 0xFFD9A066,
            secondaryHex = 0xFFC48B52,
            surfaceHex = 0xFF15110E,
            isDark = true
        ),
        CuratedThemePreset(
            id = "sakura_daylight",
            nameRes = R.string.theme_curated_sakura_daylight,
            primaryHex = 0xFFD64576,
            secondaryHex = 0xFF8B5CF6,
            surfaceHex = 0xFFFFF7F9,
            isDark = false
        ),
        CuratedThemePreset(
            id = "solar_paper",
            nameRes = R.string.theme_curated_solar_paper,
            primaryHex = 0xFFB45309,
            secondaryHex = 0xFF0F766E,
            surfaceHex = 0xFFFDF8EE,
            isDark = false
        )
    )

    fun byId(id: String?): CuratedThemePreset =
        all.firstOrNull { it.id == id } ?: all.first()
}
