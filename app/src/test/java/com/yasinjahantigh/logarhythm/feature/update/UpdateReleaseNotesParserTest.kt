package com.yasinjahantigh.logarhythm.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseNotesParserTest {

    private val bilingualNotes = """
        ## What's New
        - Fixed mini-player disappearance after launching from home widgets
        - Added free bilingual lyrics translation with offline caching

        ---

        ## فارسی
        - رفع مشکل ناپدید شدن مینی‌پلیر پس از باز کردن از ویجت
        - اضافه شدن ترجمه رایگان دوزبانه متن ترانه با کش آفلاین
    """.trimIndent()

    @Test
    fun formatReleaseNotes_selectsEnglishSectionForEnglishUi() {
        val parsed = formatReleaseNotes(bilingualNotes, preferPersian = false)
        assertTrue(parsed.contains("Fixed mini-player disappearance"))
        assertTrue(parsed.contains("Added free bilingual lyrics translation"))
        assertFalse(parsed.contains("مینی‌پلیر"))
        assertEquals(
            "•  Fixed mini-player disappearance after launching from home widgets\n" +
                "•  Added free bilingual lyrics translation with offline caching",
            parsed
        )
    }

    @Test
    fun formatReleaseNotes_selectsPersianSectionForPersianUi() {
        val parsed = formatReleaseNotes(bilingualNotes, preferPersian = true)
        assertTrue(parsed.contains("مینی‌پلیر"))
        assertTrue(parsed.contains("ترجمه رایگان"))
        assertFalse(parsed.contains("Fixed mini-player"))
        assertEquals(
            "•  رفع مشکل ناپدید شدن مینی‌پلیر پس از باز کردن از ویجت\n" +
                "•  اضافه شدن ترجمه رایگان دوزبانه متن ترانه با کش آفلاین",
            parsed
        )
    }

    @Test
    fun formatReleaseNotes_cleansLegacyTelegramFooterAndBullets() {
        val legacy = """
            🎵 LogaRhythm v2.1.0

            • پخش بی‌پایان اضافه شد
            - رفع باگ شمارش تکرار

            📥 دانلود از گیت‌هاب:
            https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest
            📢 @LogaRhythmApp
        """.trimIndent()

        val parsed = formatReleaseNotes(legacy, preferPersian = true)
        assertEquals(
            "🎵 LogaRhythm v2.1.0\n•  پخش بی‌پایان اضافه شد\n•  رفع باگ شمارش تکرار",
            parsed
        )
    }
}
