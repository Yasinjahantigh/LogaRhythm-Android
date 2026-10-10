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

    @Test
    fun normalize_normalizesAlefsAndZwnj() {
        assertEquals("اهنگ من", SearchUtils.normalize("آهنگ\u200Cمن"))
    }

    @Test
    fun matchesTokens_multiWordAndCrossField() {
        org.junit.Assert.assertTrue(SearchUtils.matchesTokens("چارتار - هندسه (باران)", "هندسه چارتار"))
        org.junit.Assert.assertTrue(SearchUtils.matchesTokens("Chaartaar Hendeseh", "chaartaar hendeseh"))
        org.junit.Assert.assertTrue(SearchUtils.matchesTokens("آهنگ زیبا", "اهنگ"))
    }
}
