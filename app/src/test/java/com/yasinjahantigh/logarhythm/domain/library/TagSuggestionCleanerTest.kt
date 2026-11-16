package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.*
import org.junit.Test

class TagSuggestionCleanerTest {
    @Test fun removesDownloadNoiseAndFindsArtist() {
        assertEquals(TagSuggestionCleaner.Tags("Song", "Artist", "Album"),
            TagSuggestionCleaner.clean("Artist - Song_(Official Video).m4a", "Unknown Artist", "Album [HD]"))
    }
    @Test fun stripsRepeatedArtistWithoutChangingKnownArtist() {
        assertEquals(TagSuggestionCleaner.Tags("Song", "Artist", "Album"),
            TagSuggestionCleaner.clean("Artist - Song", "Artist - Topic", "Album"))
    }
    @Test fun keepsLegitimateHyphenatedTitleAndVersion() {
        assertEquals("Love - Hate (Live)", TagSuggestionCleaner.clean("Love - Hate (Live)", "Band", "Album").title)
    }
    @Test fun leavesCleanTagsAlone() {
        assertEquals(TagSuggestionCleaner.Tags("Song", "Artist", "Album"), TagSuggestionCleaner.clean("Song", "Artist", "Album"))
    }
    @Test fun doesNotEraseFieldsOrLookupUnknownArtist() {
        val tags = TagSuggestionCleaner.clean("[Official Audio]", "Unknown Artist", "")
        assertEquals("[Official Audio]", tags.title)
        assertFalse(tags.canLookup)
    }
    @Test fun canonicalizesId3v1GenresAndNormalizesBilingualFamilies() {
        assertEquals("Pop", MetadataNormalizer.canonicalizeGenre("(13)"))
        assertEquals("Rock", MetadataNormalizer.canonicalizeGenre("17"))
        assertEquals("Unknown Genre", MetadataNormalizer.canonicalizeGenre("<unknown>"))
        assertEquals("pop", MetadataNormalizer.genreGroupKey("پاپ"))
        assertEquals("pop", MetadataNormalizer.genreGroupKey("Pop"))
        assertEquals("rap", MetadataNormalizer.genreGroupKey("رپ فارسی"))
        assertEquals("rap", MetadataNormalizer.genreGroupKey("Hip-Hop"))
    }
    @Test fun cleanTrackSplitsDirtyPersianFilenameAndNormalizesScriptAndGenre() {
        val dirtyTrack = Track(
            id = "local:99",
            title = "02 - محسن يگانه - رگ خواب [320] @TehranMusic.mp3",
            artist = "<unknown>",
            album = "Album [RadioJavan.com]",
            durationMs = 210_000L,
            artworkUri = null,
            sourceType = SourceType.LOCAL,
            uri = "content://media/99",
            genre = "13",
            year = 2016
        )
        val cleaned = TagSuggestionCleaner.cleanTrack(dirtyTrack)
        assertEquals("رگ خواب", cleaned.title)
        assertEquals("محسن یگانه", cleaned.artist)
        assertEquals("Album", cleaned.album)
        assertEquals("Pop", cleaned.genre)
        assertEquals(2016, cleaned.year)
    }
}
