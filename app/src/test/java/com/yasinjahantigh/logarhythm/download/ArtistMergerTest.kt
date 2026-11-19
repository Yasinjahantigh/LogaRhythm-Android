package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.domain.download.ArtistMerger
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMergerTest {

    @Test
    fun testArtistNormalizationBasics() {
        val n1 = ArtistMerger.normalizeArtistName("  The Beatles  ")
        val n2 = ArtistMerger.normalizeArtistName("the beatles")
        assertEquals("beatles", n1)
        assertEquals("beatles", n2)
        assertEquals("the beatles", ArtistMerger.normalizeRawString("  The Beatles  "))
        assertTrue(ArtistMerger.areArtistsMatching("The Beatles", "beatles"))
    }

    @Test
    fun testPunctuationAndSymbols() {
        // P!nk -> pink, Ke$ha -> kesha, AC/DC -> acdc
        assertTrue(ArtistMerger.areArtistsMatching("P!nk", "Pink"))
        assertTrue(ArtistMerger.areArtistsMatching("Ke\$ha", "Kesha"))
        assertTrue(ArtistMerger.areArtistsMatching("AC/DC", "ACDC"))
        assertTrue(ArtistMerger.areArtistsMatching("A\$AP Rocky", "ASAP Rocky"))
    }

    @Test
    fun testPrefixAndSuffixStripping() {
        // Bands & prefixes
        assertTrue(ArtistMerger.areArtistsMatching("گروه پالت", "پالت"))
        assertTrue(ArtistMerger.areArtistsMatching("Pallett Band", "Pallett"))
        assertTrue(ArtistMerger.areArtistsMatching("ماکان بند", "ماکان"))
        assertTrue(ArtistMerger.areArtistsMatching("Macan Band", "Macan"))
        assertTrue(ArtistMerger.areArtistsMatching("Hoorosh Band", "هوروش بند"))
    }

    @Test
    fun testPersianEnglishAliasMatching() {
        // Ebi
        assertTrue(ArtistMerger.areArtistsMatching("Ebi", "ابی"))
        assertTrue(ArtistMerger.areArtistsMatching("Ebrahim Hamedi", "ابی"))
        assertTrue(ArtistMerger.areArtistsMatching("ebi", "Ebrahim Hamedi"))

        // Shadmehr
        assertTrue(ArtistMerger.areArtistsMatching("Shadmehr Aghili", "شادمهر عقیلی"))
        assertTrue(ArtistMerger.areArtistsMatching("Shadmehr", "شادمهر"))

        // Mohsen Chavoshi
        assertTrue(ArtistMerger.areArtistsMatching("Mohsen Chavoshi", "محسن چاوشی"))
        assertTrue(ArtistMerger.areArtistsMatching("Mohsen Chavoshi", "محسن چاووشی"))

        // Googoosh
        assertTrue(ArtistMerger.areArtistsMatching("Googoosh", "گوگوش"))
        assertTrue(ArtistMerger.areArtistsMatching("Gougoush", "گوگوش"))

        // Homayoun Shajarian
        assertTrue(ArtistMerger.areArtistsMatching("Homayoun Shajarian", "همایون شجریان"))

        // Chaartaar
        assertTrue(ArtistMerger.areArtistsMatching("Chaartaar", "چارتار"))
        assertTrue(ArtistMerger.areArtistsMatching("Chartaar", "چارتار"))
    }

    @Test
    fun testPreventsFalsePositiveCollisions() {
        // Shared first names must not merge
        assertFalse(ArtistMerger.areArtistsMatching("Mohsen Chavoshi", "Mohsen Yeganeh"))
        assertFalse(ArtistMerger.areArtistsMatching("محسن چاوشی", "محسن یگانه"))
        assertFalse(ArtistMerger.areArtistsMatching("Reza Sadeghi", "Reza Bahram"))
        assertFalse(ArtistMerger.areArtistsMatching("رضا صادقی", "رضا بهرام"))

        // Similar prefixes or single tokens
        assertFalse(ArtistMerger.areArtistsMatching("Ebi", "Eminem"))
        assertFalse(ArtistMerger.areArtistsMatching("ابی", "امیر تتلو"))
        assertFalse(ArtistMerger.areArtistsMatching("Ali Yasini", "Sirvan Khosravi"))
    }

    @Test
    fun testCanonicalArtistKey() {
        val keyEnglish = ArtistMerger.getCanonicalArtistKey("Ebi")
        val keyPersian = ArtistMerger.getCanonicalArtistKey("ابی")
        val keyFull = ArtistMerger.getCanonicalArtistKey("Ebrahim Hamedi")

        assertEquals(keyEnglish, keyPersian)
        assertEquals(keyEnglish, keyFull)

        val chavoshiKey = ArtistMerger.getCanonicalArtistKey("Mohsen Chavoshi")
        val yeganehKey = ArtistMerger.getCanonicalArtistKey("Mohsen Yeganeh")
        assertNotEquals(chavoshiKey, yeganehKey)
    }

    @Test
    fun testMergeArtistResultsAcrossSources() {
        val deezerItem = DownloadSearchResult(
            id = "101",
            title = "Ebi",
            artist = "",
            source = DownloadSource.DEEZER,
            url = "https://www.deezer.com/artist/101",
            durationMs = 0L,
            thumbnailUrl = "https://cdn.deezer.com/images/artist/101/500x500.jpg",
            kind = DownloadResultKind.ARTIST,
            artistId = "101"
        )

        val rjItem = DownloadSearchResult(
            id = "ebi",
            title = "ابی",
            artist = "",
            source = DownloadSource.RADIOJAVAN,
            url = "https://www.radiojavan.com/artist/ebi",
            durationMs = 0L,
            thumbnailUrl = "https://assets.rjassets.com/static/artists/ebi_photo_player.jpg",
            kind = DownloadResultKind.ARTIST,
            artistId = "ebi"
        )

        val mmItem = DownloadSearchResult(
            id = "mm_ebi",
            title = "Ebi",
            artist = "",
            source = DownloadSource.MUSICMAN,
            url = "https://mm.3rah.ir/artist/mm_ebi",
            durationMs = 0L,
            thumbnailUrl = "https://mm.3rah.ir/art/ebi.jpg",
            kind = DownloadResultKind.ARTIST,
            artistId = "mm_ebi"
        )

        val trackItem = DownloadSearchResult(
            id = "track_1",
            title = "Peechack",
            artist = "Ebi",
            source = DownloadSource.RADIOJAVAN,
            url = "https://www.radiojavan.com/mp3s/mp3/Ebi-Peechack",
            durationMs = 210000L,
            thumbnailUrl = null,
            kind = DownloadResultKind.TRACK
        )

        val rawList = listOf(deezerItem, rjItem, trackItem, mmItem)
        val mergedList = ArtistMerger.mergeArtistResults(rawList)

        // The 3 artist items must merge into 1 single artist card; track is unaffected
        assertEquals(2, mergedList.size)

        val mergedArtist = mergedList.first { it.kind == DownloadResultKind.ARTIST }
        val remainingTrack = mergedList.first { it.kind == DownloadResultKind.TRACK }

        assertEquals("track_1", remainingTrack.id)

        // Verify matched sources
        assertEquals(3, mergedArtist.matchedSources.size)
        assertTrue(mergedArtist.matchedSources.contains(DownloadSource.DEEZER))
        assertTrue(mergedArtist.matchedSources.contains(DownloadSource.RADIOJAVAN))
        assertTrue(mergedArtist.matchedSources.contains(DownloadSource.MUSICMAN))

        // Verify sourceArtistIds map
        assertEquals("101", mergedArtist.sourceArtistIds[DownloadSource.DEEZER])
        assertEquals("ebi", mergedArtist.sourceArtistIds[DownloadSource.RADIOJAVAN])
        assertEquals("mm_ebi", mergedArtist.sourceArtistIds[DownloadSource.MUSICMAN])

        // Verify subtitle formatted
        assertTrue(mergedArtist.artist.contains("MusicMan"))
        assertTrue(mergedArtist.artist.contains("Radio Javan"))
        assertTrue(mergedArtist.artist.contains("Deezer"))
    }
}
