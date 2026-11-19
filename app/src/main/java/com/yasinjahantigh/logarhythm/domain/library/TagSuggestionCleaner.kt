package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.Track

object TagSuggestionCleaner {
    data class Tags(
        val title: String,
        val artist: String,
        val album: String,
        val genre: String = "",
        val year: Int = 0,
        val albumArtist: String = ""
    ) {
        val canLookup: Boolean
            get() = title.length >= 2 &&
                artist.length >= 2 &&
                !MetadataNormalizer.isPlaceholderField(title) &&
                !MetadataNormalizer.isPlaceholderField(artist)
    }

    private val audioExtensionSuffix = Regex("""(?i)\.(mp3|m4a|flac|ogg|opus|wav|aac|wma)$""")
    private val genericTitleRegex = Regex("""^(track|audio|aud|recording|song|voice|music|unknown|ahang|new)[ _-]?\d*$""", RegexOption.IGNORE_CASE)
    private val siteNoisePattern = Regex(
        """(?i)(?:\.(?:ir|com|net|org|fm|cc)\b|@\w{3,}|radio\s*javan|music-?fa|nex1music|bia2music|tehranmusic|upmusics?|download1music|golsarmusic|sakhamusic|ahangify|musicsweb|musicdel|rozmusic|sultanmusic|tabanmusic|apmusic|irantune|farskids|fazmusic|javanmusic|\b(?:320|128)\s*kbps\b|\[\s*(?:320|128)\s*\]|دانلود\s+آهنگ)"""
    )

    fun isNoisyField(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return true
        return MetadataNormalizer.isPlaceholderField(trimmed) ||
            genericTitleRegex.matches(trimmed) ||
            siteNoisePattern.containsMatchIn(trimmed)
    }

    fun parseFilenameFallback(filePath: String): Pair<String, String> {
        val stem = filePath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .trim()
        if (stem.isBlank()) return "" to ""
        return DownloadTitleParser.parse(stem, null, "")
    }

    fun clean(
        title: String,
        artist: String,
        album: String,
        genre: String = "",
        year: Int = 0,
        albumArtist: String = ""
    ): Tags {
        val displayTitle = MetadataNormalizer.cleanDisplayText(title)
        val cleanTitle = DownloadTitleParser.clean(displayTitle.replace(audioExtensionSuffix, ""))
            .ifBlank { displayTitle }

        val displayArtist = MetadataNormalizer.cleanDisplayText(artist)
        val strippedArtist = DownloadTitleParser.artistFromUploader(DownloadTitleParser.clean(displayArtist))
        val cleanArtist = strippedArtist.ifBlank { if (siteNoisePattern.containsMatchIn(displayArtist)) "" else displayArtist }
        val artistUnknown = cleanArtist.isBlank() || MetadataNormalizer.isPlaceholderField(cleanArtist)

        val (parsedArtist, parsedTitle) = DownloadTitleParser.parse(
            cleanTitle,
            null,
            if (artistUnknown) "" else cleanArtist
        )
        val sameArtist = parsedArtist.isNotBlank() &&
            MetadataNormalizer.normalizeForComparison(parsedArtist) ==
            MetadataNormalizer.normalizeForComparison(cleanArtist)
        val split = parsedTitle != cleanTitle && parsedArtist.isNotBlank() && (artistUnknown || sameArtist)

        val displayAlbum = MetadataNormalizer.cleanDisplayText(album)
        val strippedAlbum = DownloadTitleParser.clean(displayAlbum)
        val cleanAlbum = when {
            strippedAlbum.isNotBlank() -> strippedAlbum
            siteNoisePattern.containsMatchIn(displayAlbum) -> ""
            else -> displayAlbum
        }

        val cleanGenre = if (genre.isBlank()) {
            ""
        } else {
            MetadataNormalizer.canonicalizeGenre(genre, "Unknown Genre")
        }

        val displayAlbumArtist = MetadataNormalizer.cleanDisplayText(albumArtist)
        val cleanAlbumArtist = if (displayAlbumArtist.isBlank() || MetadataNormalizer.isPlaceholderField(displayAlbumArtist)) {
            ""
        } else {
            DownloadTitleParser.artistFromUploader(DownloadTitleParser.clean(displayAlbumArtist))
                .ifBlank { if (siteNoisePattern.containsMatchIn(displayAlbumArtist)) "" else displayAlbumArtist }
        }

        return Tags(
            title = if (split) parsedTitle else cleanTitle,
            artist = if (split) parsedArtist else cleanArtist,
            album = cleanAlbum,
            genre = cleanGenre,
            year = year.coerceAtLeast(0),
            albumArtist = cleanAlbumArtist
        )
    }

    fun cleanTrack(track: Track): Tags {
        val base = clean(
            title = track.title,
            artist = track.artist,
            album = track.album,
            genre = track.genre,
            year = track.year,
            albumArtist = track.albumArtist
        )
        val stem = track.filePath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .trim()
        if (stem.isBlank()) return base

        val needsArtistRecovery = base.artist.isBlank() ||
            MetadataNormalizer.isPlaceholderField(base.artist) ||
            siteNoisePattern.containsMatchIn(track.artist)
        val needsTitleRecovery = base.title.isBlank() ||
            MetadataNormalizer.isPlaceholderField(base.title) ||
            genericTitleRegex.matches(base.title) ||
            siteNoisePattern.containsMatchIn(track.title)
        if (!needsArtistRecovery && !needsTitleRecovery) return base

        val fromFile = clean(
            title = stem,
            artist = if (needsArtistRecovery) "" else base.artist,
            album = base.album,
            genre = base.genre,
            year = base.year,
            albumArtist = base.albumArtist
        )
        return base.copy(
            title = if (needsTitleRecovery && fromFile.title.isNotBlank() && !MetadataNormalizer.isPlaceholderField(fromFile.title)) {
                fromFile.title
            } else {
                base.title
            },
            artist = if (needsArtistRecovery && fromFile.artist.isNotBlank() && !MetadataNormalizer.isPlaceholderField(fromFile.artist)) {
                fromFile.artist
            } else {
                base.artist
            }
        )
    }
}
