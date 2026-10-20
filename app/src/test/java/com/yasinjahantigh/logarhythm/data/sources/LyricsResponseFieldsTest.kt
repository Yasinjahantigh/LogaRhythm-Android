package com.yasinjahantigh.logarhythm.data.sources

import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class LyricsResponseFieldsTest {
    @Test
    fun jsonNullAndLiteralNullAreTreatedAsMissing() {
        val json = JSONObject("""{"trackName":null,"artistName":"null","albumName":"  Album  ","empty":""}""")
        assertEquals("", LyricsResponseFields.text(json, "trackName"))
        assertEquals("", LyricsResponseFields.text(json, "artistName"))
        assertEquals("Album", LyricsResponseFields.text(json, "albumName"))
        assertEquals("", LyricsResponseFields.text(json, "empty"))
        assertEquals("", LyricsResponseFields.text(json, "notPresent"))
    }
}
