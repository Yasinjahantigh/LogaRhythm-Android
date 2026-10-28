package com.yasinjahantigh.logarhythm.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetaTextTest {

    @Test
    fun collapsesWhitespaceAndQuotes() {
        assertEquals("Daft Punk", "  \"Daft   Punk\"  ".cleanMetaOrNull())
    }

    @Test
    fun hidesPlaceholdersAndDashValues() {
        assertNull("Unknown Artist".cleanMetaOrNull())
        assertNull("<unknown>".cleanMetaOrNull())
        assertNull("  -  ".cleanMetaOrNull())
        assertNull("N/A".cleanMetaOrNull())
    }

    @Test
    fun keepsRealNamesWithPunctuation() {
        assertEquals("AC/DC", "AC/DC".cleanMetaOrNull())
        assertEquals("Tyler, The Creator", "Tyler, The Creator".cleanMetaOrNull())
    }
}
