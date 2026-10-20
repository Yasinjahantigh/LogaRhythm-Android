package com.yasinjahantigh.logarhythm.domain.playlist

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uCodecTest {
    private fun t(id: String, title: String, artist: String, path: String) =
        Track(id, title, artist, "Al", 200_000, null, SourceType.LOCAL, "u", filePath = path)

    @Test fun roundTrip() {
        val tracks = listOf(t("1", "Song", "Band", "/Music/Band/song.mp3"))
        val parsed = M3uCodec.parse(M3uCodec.encode(tracks))
        assertEquals(1, parsed.size)
        assertEquals("Song", parsed[0].title)
        assertEquals("Band", parsed[0].artist)
        assertEquals(200, parsed[0].durationSec)
    }
    @Test fun matchesByFileNameThenTags() {
        val lib = listOf(t("1", "Song", "Band", "/sd/Music/song.mp3"), t("2", "Other", "Act", "/sd/o.mp3"))
        val entries = M3uCodec.parse("#EXTM3U\n#EXTINF:1,X - Y\nC:\\Old\\SONG.MP3\n#EXTINF:1,Act - Other\nmissing/path.flac\nnope.mp3\n")
        val result = M3uCodec.match(entries, lib)
        assertEquals(listOf("1", "2"), result.matched.map { it.id })
        assertEquals(1, result.missing.size)
    }
}
