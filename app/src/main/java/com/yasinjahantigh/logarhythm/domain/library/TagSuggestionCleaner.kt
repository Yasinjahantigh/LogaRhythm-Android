package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser

object TagSuggestionCleaner {
    data class Tags(val title: String, val artist: String, val album: String) {
        val canLookup: Boolean get() = title.length >= 2 && artist.length >= 2 &&
            !title.equals("Unknown Title", true) && !artist.equals("Unknown Artist", true)
    }

    fun clean(title: String, artist: String, album: String): Tags {
        val cleanTitle = DownloadTitleParser.clean(title.replace(Regex("(?i)\\.(mp3|m4a|flac|ogg|opus|wav)$"), ""))
            .ifBlank { title }
        val cleanArtist = DownloadTitleParser.artistFromUploader(DownloadTitleParser.clean(artist)).ifBlank { artist }
        val (parsedArtist, parsedTitle) = DownloadTitleParser.parse(cleanTitle, null, cleanArtist)
        val split = parsedTitle != cleanTitle && (cleanArtist.isBlank() ||
            cleanArtist.equals("Unknown Artist", true) || parsedArtist.equals(cleanArtist, true))
        return Tags(
            if (split) parsedTitle else cleanTitle,
            if (split) parsedArtist else cleanArtist,
            DownloadTitleParser.clean(album).ifBlank { album }
        )
    }
}
