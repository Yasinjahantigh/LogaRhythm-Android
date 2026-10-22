package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import org.json.JSONObject

/** Parses `--dump-json --flat-playlist` output, one JSON object per line. */
object SearchJsonParser {

    fun parse(source: DownloadSource, output: String): List<DownloadSearchResult> =
        output.lineSequence()
            .mapNotNull { line -> line.trimStart().takeIf { it.startsWith("{") } }
            .mapNotNull { line -> parseLine(source, line) }
            .distinctBy { it.url }
            .toList()

    fun parseLine(source: DownloadSource, line: String): DownloadSearchResult? {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = listOf("title", "fulltitle")
            .map { json.optString(it) }
            .firstOrNull { it.isNotBlank() }
            ?: return null
        val uploader = listOf("artist", "creator", "uploader", "channel")
            .map { json.optString(it) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val url = listOf("webpage_url", "url")
            .map { json.optString(it) }
            .firstOrNull { it.startsWith("http") }
            ?: defaultUrl(source, id)
            ?: return null
        val durationSeconds = json.optDouble("duration", 0.0)
        val durationMs = if (durationSeconds.isFinite() && durationSeconds > 0) {
            (durationSeconds * 1000).toLong()
        } else {
            0L
        }
        val thumbnail = json.optString("thumbnail").takeIf { it.startsWith("http") }
            ?: lastThumbnail(json)
            ?: youtubeThumbnail(source, id)

        return DownloadSearchResult(
            id = id,
            title = title.trim(),
            artist = DownloadTitleParser.artistFromUploader(uploader),
            source = source,
            url = url,
            durationMs = durationMs,
            thumbnailUrl = thumbnail
        )
    }

    private fun lastThumbnail(json: JSONObject): String? {
        val thumbnails = json.optJSONArray("thumbnails") ?: return null
        for (index in thumbnails.length() - 1 downTo 0) {
            val url = thumbnails.optJSONObject(index)?.optString("url").orEmpty()
            if (url.startsWith("http")) return url
        }
        return null
    }

    private fun youtubeThumbnail(source: DownloadSource, id: String): String? =
        if (source == DownloadSource.YOUTUBE && id.length in 6..20) {
            "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        } else {
            null
        }

    private fun defaultUrl(source: DownloadSource, id: String): String? =
        if (source == DownloadSource.YOUTUBE) "https://www.youtube.com/watch?v=$id" else null
}
