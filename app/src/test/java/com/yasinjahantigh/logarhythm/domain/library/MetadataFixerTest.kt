package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataFixerTest {

    @Test
    fun cleanTrackRecoversArtistAndTitleFromFilenameWhenTagsArePlaceholders() {
        val raw = Track(
            id = "local:10",
            title = "AUD-001",
            artist = "<unknown>",
            album = "Unknown Album",
            durationMs = 210_000L,
            artworkUri = null,
            sourceType = SourceType.LOCAL,
            uri = "content://media/external/audio/media/10",
            filePath = "/storage/emulated/0/Music/Shadmehr Aghili - Taghdir (320).mp3"
        )

        val cleaned = TagSuggestionCleaner.cleanTrack(raw)
        assertEquals("Shadmehr Aghili", cleaned.artist)
        assertEquals("Taghdir", cleaned.title)
    }

    @Test
    fun cleanTrackPreservesValidMetadataAndStripsDownloadNoise() {
        val messy = Track(
            id = "local:11",
            title = "Nafas [Official Video] (320)",
            artist = "Mohsen Yeganeh",
            album = "Negah",
            durationMs = 200_000L,
            artworkUri = null,
            sourceType = SourceType.LOCAL,
            uri = "content://media/external/audio/media/11",
            filePath = "/storage/emulated/0/Music/unknown_file.mp3"
        )

        val cleaned = TagSuggestionCleaner.cleanTrack(messy)
        assertEquals("Mohsen Yeganeh", cleaned.artist)
        assertEquals("Nafas", cleaned.title)
    }
}
