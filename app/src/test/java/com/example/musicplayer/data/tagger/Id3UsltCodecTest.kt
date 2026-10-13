package com.example.musicplayer.data.tagger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Id3UsltCodecTest {
    @Test
    fun roundTripsEnglishAndPersianLyrics() {
        val expected = "[00:01.20]Hello world\n[00:03.45]سلام دنیا"
        val payload = Id3UsltCodec.encodePayload(expected)
        assertEquals(expected, Id3UsltCodec.decodePayload(payload))
    }

    @Test
    fun usesId3v23Utf16WithBomAndEnglishLanguageCode() {
        val payload = Id3UsltCodec.encodePayload("سلام")
        assertEquals(1, payload[0].toInt() and 0xff)
        assertEquals("eng", String(payload.copyOfRange(1, 4), Charsets.ISO_8859_1))
        assertEquals(0xFF, payload[4].toInt() and 0xff)
        assertEquals(0xFE, payload[5].toInt() and 0xff)
        assertEquals(0, payload[6].toInt() and 0xff)
        assertEquals(0, payload[7].toInt() and 0xff)
        assertNotNull(Id3UsltCodec.decodePayload(payload))
    }

    @Test
    fun rejectsMalformedOrUnsupportedPayloads() {
        assertNull(Id3UsltCodec.decodePayload(byteArrayOf(1, 2, 3)))
        assertNull(Id3UsltCodec.decodePayload(byteArrayOf(9, 101, 110, 103, 0, 0, 0, 0)))
        assertNull(Id3UsltCodec.decodePayload(byteArrayOf(3, 101, 110, 103, 65, 66)))
    }
}
