package com.yasinjahantigh.logarhythm.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeniusLyricsSourceTest {

    @Test
    fun `extracts nested lyric containers and decodes entities`() {
        val html = """
            <html><body>
            <div class="Lyrics__Container" data-lyrics-container="true">
              <div class="LyricsHeader__Container">You might also like</div>
              First line &amp; more<br/>
              <i>Second line</i><br>
              Third &#39;line&#39;
            </div>
            <div data-lyrics-container="true">Fourth line<br/>Fifth line</div>
            </body></html>
        """.trimIndent()

        val text = parseLyricsHtml(html)

        assertTrue(text!!.contains("First line & more"))
        assertTrue(text.contains("Second line"))
        assertTrue(text.contains("Third 'line'"))
        assertTrue(text.contains("Fourth line"))
        assertTrue(text.contains("Fifth line"))
        assertFalse(text.contains("You might also like"))
    }

    @Test
    fun `removes excluded header blocks with nested markup`() {
        val html = """
            <div data-lyrics-container="true">
            <div data-exclude-from-selection="true" class="LyricsHeader">
              <div>Translations</div><span>English</span>
            </div>
            Real lyric line one<br>
            Real lyric line two
            </div>
        """.trimIndent()

        val text = parseLyricsHtml(html)!!

        assertTrue(text.contains("Real lyric line one"))
        assertFalse(text.contains("Translations"))
        assertFalse(text.contains("English"))
    }

    @Test
    fun `returns null without lyric containers`() {
        assertNull(parseLyricsHtml("<html><body><p>No lyrics here</p></body></html>"))
    }

    @Test
    fun `drops embed noise and keeps long lyrics`() {
        val html = """
            <div data-lyrics-container="true">
            Verse one is here<br>
            Embed<br>
            Verse two is here<br>
            <div>Verse three is here</div><br>
            Verse four is here
            </div>
        """.trimIndent()

        val text = parseLyricsHtml(html)!!

        assertTrue(text.contains("Verse one"))
        assertTrue(text.contains("Verse four"))
        assertFalse(text.lines().any { it.trim() == "Embed" })
        assertFalse(text.contains("\n\n\n"))
    }
}
