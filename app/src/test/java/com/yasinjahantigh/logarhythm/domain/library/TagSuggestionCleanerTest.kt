package com.yasinjahantigh.logarhythm.domain.library

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
}
