package com.yasinjahantigh.logarhythm.domain.playlist

import com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder
import com.yasinjahantigh.logarhythm.domain.model.Track

data class M3uEntry(
    val path: String,
    val title: String? = null,
    val artist: String? = null,
    val durationSec: Int = -1
)

data class M3uMatch(val matched: List<Track>, val missing: List<M3uEntry>)

/** Reads and writes extended M3U/M3U8 playlists and maps their entries onto the local library. */
object M3uCodec {

    fun encode(tracks: List<Track>): String = buildString {
        append("#EXTM3U\n")
        for (track in tracks) {
            val seconds = (track.durationMs / 1000L).toInt()
            append("#EXTINF:").append(seconds).append(',')
                .append(track.artist).append(" - ").append(track.title).append('\n')
            append(track.filePath.ifBlank { track.title }).append('\n')
        }
    }

    fun parse(text: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingDuration = -1
        for (rawLine in text.removePrefix("\uFEFF").lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val body = line.substringAfter(':')
                pendingDuration = body.substringBefore(',').trim().toDoubleOrNull()?.toInt() ?: -1
                val label = body.substringAfter(',', "").trim()
                val dash = label.indexOf(" - ")
                if (dash > 0) {
                    pendingArtist = label.substring(0, dash).trim().ifEmpty { null }
                    pendingTitle = label.substring(dash + 3).trim().ifEmpty { null }
                } else {
                    pendingArtist = null
                    pendingTitle = label.ifEmpty { null }
                }
            } else if (!line.startsWith("#")) {
                entries += M3uEntry(line, pendingTitle, pendingArtist, pendingDuration)
                pendingTitle = null
                pendingArtist = null
                pendingDuration = -1
            }
        }
        return entries
    }

    private fun stem(path: String): String =
        DuplicateFinder.normalize(path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.'))

    /** Matches by file name first, then artist+title, then a title that is unique in the library. */
    fun match(entries: List<M3uEntry>, library: List<Track>): M3uMatch {
        val byStem = HashMap<String, Track>()
        val byArtistTitle = HashMap<String, Track>()
        val byTitle = HashMap<String, MutableList<Track>>()
        for (track in library) {
            if (track.filePath.isNotBlank()) byStem.putIfAbsent(stem(track.filePath), track)
            byArtistTitle.putIfAbsent(
                DuplicateFinder.normalize(track.artist) + "|" + DuplicateFinder.normalize(track.title), track
            )
            byTitle.getOrPut(DuplicateFinder.normalize(track.title)) { mutableListOf() }.add(track)
        }
        val matched = mutableListOf<Track>()
        val missing = mutableListOf<M3uEntry>()
        for (entry in entries) {
            val title = entry.title?.let(DuplicateFinder::normalize).orEmpty()
            val found = byStem[stem(entry.path)]
                ?: if (title.isNotEmpty() && entry.artist != null) {
                    byArtistTitle[DuplicateFinder.normalize(entry.artist) + "|" + title]
                } else null
                ?: byTitle[title.ifEmpty { stem(entry.path) }]?.singleOrNull()
            if (found != null) matched += found else missing += entry
        }
        return M3uMatch(matched, missing)
    }
}
