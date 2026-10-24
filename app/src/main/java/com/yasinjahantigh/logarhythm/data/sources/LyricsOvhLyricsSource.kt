package com.yasinjahantigh.logarhythm.data.sources

import com.yasinjahantigh.logarhythm.data.lyrics.LyricsHttpClient
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsSearchResult
import com.yasinjahantigh.logarhythm.data.lyrics.OnlineLyricsSource
import com.yasinjahantigh.logarhythm.data.lyrics.encode
import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsQuery
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceStatus
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsTextNormalizer
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** lyrics.ovh: plain international lyrics, no API key. */
@Singleton
class LyricsOvhLyricsSource @Inject constructor(
    private val http: LyricsHttpClient
) : OnlineLyricsSource {

    override val id: String = "lyrics_ovh"
    override val displayName: String = "lyrics.ovh"

    override suspend fun search(query: LyricsQuery): LyricsSearchResult = withContext(Dispatchers.IO) {
        val title = LyricsTextNormalizer.queryTitle(query.title)
        val artist = LyricsTextNormalizer.queryArtist(query.artist)
        if (title.isBlank() || artist.isBlank()) {
            return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        }
        val response = http.get(
            "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}",
            mapOf("Accept" to "application/json")
        )
        when {
            response.blocked -> return@withContext LyricsSearchResult(status = LyricsSourceStatus.BLOCKED, detail = response.error)
            response.timedOut -> return@withContext LyricsSearchResult(status = LyricsSourceStatus.TIMEOUT, detail = response.error)
            !response.ok -> return@withContext LyricsSearchResult(
                status = if (response.code == 404) LyricsSourceStatus.EMPTY else LyricsSourceStatus.ERROR,
                detail = response.error
            )
        }

        val text = runCatching {
            LyricsResponseFields.text(JSONObject(response.body), "lyrics")
        }.getOrNull()
        if (text.isNullOrBlank()) return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        val parsed = LrcParser.parse(text)
        if (parsed.isEmpty) return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        LyricsSearchResult(
            candidates = listOf(
                LyricsCandidate(
                    id = "ovh_${title}_$artist",
                    trackName = title,
                    artistName = artist,
                    isSynced = parsed.isSynced,
                    sourceName = displayName,
                    providerId = id,
                    lyrics = parsed
                )
            )
        )
    }
}
