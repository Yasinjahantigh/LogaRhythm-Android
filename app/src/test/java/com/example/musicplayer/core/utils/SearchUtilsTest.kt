package com.example.musicplayer.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchUtilsTest {
    @Test
    fun normalize_mapsArabicYehAndKafToPersianForms() {
        assertEquals("کیف یاس", SearchUtils.normalize("كیف ياس"))
    }

    @Test
    fun normalize_trimsAndRemovesHarakat() {
        assertEquals("سلام", SearchUtils.normalize("  سَلامٌ  "))
    }
}
