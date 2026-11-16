package com.yasinjahantigh.logarhythm.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkResolverTest {

    @Test
    fun parsesSpotifyYoutubeAndRadioJavanUrlsCleanly() {
        val spotify = ShareLinkResolver.parseShareText("Listen to this: https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc")
        assertNotNull(spotify)
        assertEquals(ParsedShareLink.Kind.SPOTIFY, spotify!!.kind)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", spotify.slugOrId)

        val ytShort = ShareLinkResolver.parseShareText("https://youtu.be/dQw4w9WgXcQ")
        assertNotNull(ytShort)
        assertEquals(ParsedShareLink.Kind.YOUTUBE, ytShort!!.kind)
        assertEquals("dQw4w9WgXcQ", ytShort.slugOrId)

        val ytWatch = ShareLinkResolver.parseShareText("https://music.youtube.com/watch?v=9bZkp7q19f0&feature=share")
        assertNotNull(ytWatch)
        assertEquals(ParsedShareLink.Kind.YOUTUBE, ytWatch!!.kind)
        assertEquals("9bZkp7q19f0", ytWatch.slugOrId)

        val rj = ShareLinkResolver.parseShareText("https://www.radiojavan.com/mp3s/mp3/Shadmehr-Aghili-Bi-Ehsas")
        assertNotNull(rj)
        assertEquals(ParsedShareLink.Kind.RADIOJAVAN, rj!!.kind)
        assertEquals("Shadmehr Aghili Bi Ehsas", rj.slugOrId)

        assertNull(ShareLinkResolver.parseShareText("Just plain artist name"))
    }
}
