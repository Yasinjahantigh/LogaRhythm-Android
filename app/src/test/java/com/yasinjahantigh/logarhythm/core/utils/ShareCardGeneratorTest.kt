package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yasinjahantigh.logarhythm.ui.theme.resolveAppTypefacePair
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareCardGeneratorTest {

    @Test
    fun renderAllCardsWithVazirmatnAndCustomOptionsProduceValidBitmaps() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val (regTf, boldTf) = resolveAppTypefacePair("vazirmatn", context)
        assertNotNull(regTf)
        assertNotNull(boldTf)

        val lyricBmp = ShareCardGenerator.renderLyricCardPreview(
            context = context,
            data = ShareCardGenerator.LyricCardData(
                songTitle = "تقدیر",
                artist = "شادمهر عقیلی",
                lines = listOf("با تو حالم خوبه", "حتی تو بارون و باد"),
                artworkUri = null,
                options = ShareCardOptions(
                    style = ShareCardStyle.DARK_GLASS,
                    aspect = ShareCardAspect.POST_4_5,
                    textScale = 1.4f,
                    lineSpacing = 1.5f,
                    fontKey = "vazirmatn",
                    centerLyrics = true
                )
            ),
            maxDim = 400
        )
        assertEquals(400, lyricBmp.height)
        assertEquals(320, lyricBmp.width)

        val reportBmp = ShareCardGenerator.renderListeningReportPreview(
            context = context,
            data = ShareCardGenerator.ReportData(
                headline = "Listening Report",
                periodLabel = "Last 30 Days",
                totalTimeLabel = "14h 22m",
                playsLabel = "184 plays",
                peakHourLabel = "Peak 22:00",
                vibeLabel = "Night Owl",
                topArtists = listOf("Shadmehr" to "42 plays", "یاس" to "31 plays"),
                topTracks = listOf("Taghdir" to "Shadmehr · 19 plays"),
                artworkUri = null,
                options = ShareCardOptions(
                    style = ShareCardStyle.WARM_VINYL,
                    aspect = ShareCardAspect.SQUARE_1_1,
                    textScale = 1.5f,
                    fontKey = "lalezar_jakarta"
                )
            ),
            maxDim = 360
        )
        assertEquals(360, reportBmp.width)
        assertEquals(360, reportBmp.height)
        assertTrue(reportBmp.byteCount > 0)

        val songBmp = ShareCardGenerator.renderSongCardPreview(
            context = context,
            data = ShareCardGenerator.SongCardData(
                songTitle = "Khodafez",
                artist = "Mehrad Hidden",
                album = "Toole Arze Zendegi",
                durationLabel = "3:30",
                artworkUri = null,
                options = ShareCardOptions(
                    style = ShareCardStyle.MINIMAL_LIGHT,
                    aspect = ShareCardAspect.STORY_9_16,
                    fontKey = "jakarta_vazir"
                )
            ),
            maxDim = 480
        )
        assertEquals(480, songBmp.height)
        assertEquals(270, songBmp.width)
    }
}
