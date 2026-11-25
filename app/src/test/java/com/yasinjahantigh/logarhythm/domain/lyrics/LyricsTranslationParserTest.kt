package com.yasinjahantigh.logarhythm.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsTranslationParserTest {

    @Test
    fun parseGtxResponse_joinsMultiSegmentResponse() {
        val raw = """
            [
              [
                ["سلام دنیا\n", "Hello world\n", null, null, 1],
                ["خط دوم", "Second line", null, null, 1]
              ],
              null,
              "en"
            ]
        """.trimIndent()
        assertEquals("سلام دنیا\nخط دوم", LyricsTranslationParser.parseGtxResponse(raw))
    }

    @Test
    fun parseGtxResponse_rejectsInvalidOrEmpty() {
        assertNull(LyricsTranslationParser.parseGtxResponse(""))
        assertNull(LyricsTranslationParser.parseGtxResponse("<html>error</html>"))
        assertNull(LyricsTranslationParser.parseGtxResponse("[[[null, \"x\"]]]"))
    }

    @Test
    fun parseMyMemoryResponse_extractsValidTranslationAndRejectsErrors() {
        val ok = """{"responseStatus":200,"responseData":{"translatedText":"سلام"}}"""
        assertEquals("سلام", LyricsTranslationParser.parseMyMemoryResponse(ok))

        val errStatus = """{"responseStatus":403,"responseData":{"translatedText":"NO QUERY"}}"""
        assertNull(LyricsTranslationParser.parseMyMemoryResponse(errStatus))

        val invalidPair = """{"responseStatus":200,"responseData":{"translatedText":"INVALID LANGUAGE PAIR SPECIFIED"}}"""
        assertNull(LyricsTranslationParser.parseMyMemoryResponse(invalidPair))
    }

    @Test
    fun alignBatchTranslations_matchesExactLineCountOnly() {
        val inputs = listOf("Line one", "Line two", "Line three")
        assertEquals(
            listOf("یک", "دو", "سه"),
            LyricsTranslationParser.alignBatchTranslations(inputs, "یک\nدو\nسه")
        )
        assertNull(LyricsTranslationParser.alignBatchTranslations(inputs, "یک\nدو"))
    }

    @Test
    fun detectSourceLanguage_identifiesScriptsAndAvoidsTargetMatch() {
        assertEquals("en", LyricsTranslationParser.detectSourceLanguage("Hello darkness my old friend", "fa"))
        assertEquals("fa", LyricsTranslationParser.detectSourceLanguage("سلام ای صبح روشن", "en"))
        assertEquals("ru", LyricsTranslationParser.detectSourceLanguage("Привет мир", "en"))
        // If detected equals targetLang, falls back away from targetLang
        assertEquals("fa", LyricsTranslationParser.detectSourceLanguage("Hello", "en"))
    }
}
