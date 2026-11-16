package com.yasinjahantigh.logarhythm.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePresetsTest {

    @Test
    fun allPresetsProduceValidUniqueColorSchemes() {
        val ids = ThemePresets.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ThemePresets.all.size >= 8)

        ThemePresets.all.forEach { preset ->
            val scheme = preset.toColorScheme()
            assertEquals(preset.secondaryHex, scheme.secondary.toArgb().toLong() and 0xFFFFFFFFL)
            if (preset.amoled) {
                assertEquals(0xFF000000L, scheme.background.toArgb().toLong() and 0xFFFFFFFFL)
            }
        }
        assertFalse(ThemePresets.byId("sakura_daylight").isDark)
        assertEquals(ThemePresets.DEFAULT_ID, ThemePresets.byId("nonexistent").id)
    }
}
