package com.yasinjahantigh.logarhythm.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtistInternetPhotoSearchTest {

    @Test
    fun parseBingImageHtml_extractsEscapedAndRawWebUrlsAndFiltersBadAssets() {
        val html = """
            <html><body>
            <a class="iusc" m="{&quot;murl&quot;:&quot;https://example.com/shadmehr_portrait.jpg&quot;,&quot;t&quot;:&quot;Shadmehr Aghili \uE000Concert\uE001&quot;,&quot;mw&quot;:900,&quot;mh&quot;:900}"></a>
            <a class="iusc" m="{&quot;murl&quot;:&quot;https://example.com/icon.svg&quot;,&quot;t&quot;:&quot;Logo&quot;,&quot;mw&quot;:500,&quot;mh&quot;:500}"></a>
            <a class="iusc" m="{&quot;murl&quot;:&quot;https://www.shutterstock.com/photo.jpg&quot;,&quot;t&quot;:&quot;Watermark&quot;,&quot;mw&quot;:800,&quot;mh&quot;:600}"></a>
            <a class="iusc" m="{&quot;murl&quot;:&quot;https://example.com/tiny.jpg&quot;,&quot;t&quot;:&quot;Tiny&quot;,&quot;mw&quot;:80,&quot;mh&quot;:80}"></a>
            <script>var x = {"murl":"https://cdn.site.ir/yas-rapper.webp"};</script>
            </body></html>
        """.trimIndent()

        val hits = ArtistInternetPhotoSearch.parseBingImageHtml(html, fallbackLabel = "Shadmehr")
        assertEquals(2, hits.size)
        assertEquals("https://example.com/shadmehr_portrait.jpg", hits[0].url)
        assertEquals("Shadmehr Aghili Concert", hits[0].label)
        assertEquals("Web", hits[0].source)
        assertEquals("https://cdn.site.ir/yas-rapper.webp", hits[1].url)
    }

    @Test
    fun parseWikipediaJson_prioritizesMusicianDescriptionsAndSkipsSvg() {
        val json = """
            {
              "query": {
                "pages": {
                  "10": {
                    "title": "Shadmehr (Name)",
                    "thumbnail": {"source": "https://upload.wikimedia.org/wiki/icon.svg"},
                    "terms": {"description": ["given name"]}
                  },
                  "20": {
                    "title": "Some Place",
                    "thumbnail": {"source": "https://upload.wikimedia.org/wiki/town.jpg"},
                    "terms": {"description": ["village in Iran"]}
                  },
                  "30": {
                    "title": "Shadmehr Aghili",
                    "thumbnail": {"source": "https://upload.wikimedia.org/wiki/shadmehr_800px.jpg"},
                    "original": {"source": "https://upload.wikimedia.org/wiki/shadmehr_full.jpg"},
                    "terms": {"description": ["Iranian pop singer and musician"]}
                  }
                }
              }
            }
        """.trimIndent()

        val results = ArtistInternetPhotoSearch.parseWikipediaJson(json)
        assertEquals(2, results.size)
        assertEquals("Shadmehr Aghili", results[0].title)
        assertTrue(results[0].isMusician)
        assertEquals("https://upload.wikimedia.org/wiki/shadmehr_800px.jpg", results[0].url)
        assertFalse(results[1].isMusician)
    }

    @Test
    fun parseWikimediaCommonsJson_filtersNonImagesAndTooSmallThumbs() {
        val json = """
            {
              "query": {
                "pages": {
                  "1": {
                    "title": "File:Googoosh live.jpg",
                    "imageinfo": [{"url": "https://upload.wikimedia.org/raw.jpg", "thumburl": "https://upload.wikimedia.org/800px.jpg", "mime": "image/jpeg", "width": 1200, "height": 900}]
                  },
                  "2": {
                    "title": "File:Vector.svg",
                    "imageinfo": [{"url": "https://upload.wikimedia.org/vec.svg", "mime": "image/svg+xml", "width": 500, "height": 500}]
                  },
                  "3": {
                    "title": "File:Small.png",
                    "imageinfo": [{"url": "https://upload.wikimedia.org/small.png", "mime": "image/png", "width": 64, "height": 64}]
                  }
                }
              }
            }
        """.trimIndent()

        val results = ArtistInternetPhotoSearch.parseWikimediaCommonsJson(json)
        assertEquals(1, results.size)
        assertEquals("https://upload.wikimedia.org/800px.jpg", results[0].url)
        assertEquals("Googoosh live", results[0].label)
        assertEquals("Commons", results[0].source)
    }
}
