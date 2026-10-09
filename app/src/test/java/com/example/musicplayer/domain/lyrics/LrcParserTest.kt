package com.example.musicplayer.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_standardTimestamps_parsesCorrectly() {
        val lrc = """
            [00:10.50]First line
            [01:05.20]Second line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertTrue(result.isSynced)
        assertEquals(2, result.lines.size)
        assertEquals(10500L, result.lines[0].timestampMs)
        assertEquals("First line", result.lines[0].text)
        assertEquals(65200L, result.lines[1].timestampMs)
        assertEquals("Second line", result.lines[1].text)
    }

    @Test
    fun parse_threeDigitMilliseconds_parsesCorrectly() {
        val lrc = "[00:03.125]Exact milliseconds"

        val result = LrcParser.parse(lrc)

        assertTrue(result.isSynced)
        assertEquals(1, result.lines.size)
        assertEquals(3125L, result.lines[0].timestampMs)
        assertEquals("Exact milliseconds", result.lines[0].text)
    }

    @Test
    fun parse_multipleTimestampsOnSingleLine_expandsAndSorts() {
        val lrc = """
            [00:05.00]Intro
            [00:15.00][00:45.00]Chorus line repeats
            [00:30.00]Verse line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertTrue(result.isSynced)
        assertEquals(4, result.lines.size)
        assertEquals(5000L, result.lines[0].timestampMs)
        assertEquals("Intro", result.lines[0].text)

        assertEquals(15000L, result.lines[1].timestampMs)
        assertEquals("Chorus line repeats", result.lines[1].text)

        assertEquals(30000L, result.lines[2].timestampMs)
        assertEquals("Verse line", result.lines[2].text)

        assertEquals(45000L, result.lines[3].timestampMs)
        assertEquals("Chorus line repeats", result.lines[3].text)
    }

    @Test
    fun parse_positiveOffsetTag_shiftsTimestampsForward() {
        val lrc = """
            [offset:500]
            [00:10.00]Shifted line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertTrue(result.isSynced)
        assertEquals(500L, result.offsetMs)
        assertEquals(10500L, result.lines[0].timestampMs)
        assertEquals("Shifted line", result.lines[0].text)
    }

    @Test
    fun parse_negativeOffsetTag_shiftsTimestampsBackward() {
        val lrc = """
            [offset:-200]
            [00:10.00]Earlier line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertTrue(result.isSynced)
        assertEquals(-200L, result.offsetMs)
        assertEquals(9800L, result.lines[0].timestampMs)
        assertEquals("Earlier line", result.lines[0].text)
    }

    @Test
    fun parse_metadataTags_ignoredFromLyricsText() {
        val lrc = """
            [ti:Bohemian Rhapsody]
            [ar:Queen]
            [al:A Night at the Opera]
            [by:Author]
            [00:02.00]Is this the real life?
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertTrue(result.isSynced)
        assertEquals(1, result.lines.size)
        assertEquals("Is this the real life?", result.lines[0].text)
    }

    @Test
    fun parse_plainUnsyncedText_markedAsNotSynced() {
        val plain = """
            Just a singer in a smokey room
            A smell of wine and cheap perfume
        """.trimIndent()

        val result = LrcParser.parse(plain)

        assertFalse(result.isSynced)
        assertTrue(result.lines.isEmpty())
        assertNotNull(result.plainText)
        assertTrue(result.plainText!!.contains("Just a singer"))
        assertTrue(result.plainText!!.contains("cheap perfume"))
    }

    @Test
    fun parse_blankContent_returnsEmptyLyrics() {
        val result = LrcParser.parse("")
        assertTrue(result.isEmpty)
        assertFalse(result.isSynced)
        assertEquals(0, result.lines.size)
    }
}
