package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.LyricsDao
import com.yasinjahantigh.logarhythm.data.room.LyricsEntity
import com.yasinjahantigh.logarhythm.data.sources.LocalFileLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.LrclibLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.LyricsOvhLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.NeteaseLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.KugouLyricsSource
import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.LyricsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves lyrics from the Room cache, embedded/local .lrc data, then online sources
 * (synced results before plain ones), caching every hit for offline use.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lyricsDao: LyricsDao,
    private val localFileLyricsSource: LocalFileLyricsSource,
    private val lrclibLyricsSource: LrclibLyricsSource,
    private val neteaseLyricsSource: NeteaseLyricsSource,
    private val kugouLyricsSource: KugouLyricsSource,
    private val lyricsOvhLyricsSource: LyricsOvhLyricsSource,
    private val preferencesManager: PreferencesManager
) : LyricsRepository {

    override fun getLyrics(track: Track): Flow<Lyrics?> {
        return lyricsDao.getLyricsForTrack(track.id).map { entity ->
            if (entity == null || entity.source == "deleted" || entity.content.isBlank()) {
                null
            } else {
                LrcParser.parse(entity.content).copy(source = entity.source)
            }
        }
    }

    override suspend fun loadLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        // Cached result
        val cached = lyricsDao.getLyricsForTrackOnce(track.id)
        if (cached != null) {
            if (cached.source == "deleted") {
                return@withContext null
            }
            val parsed = LrcParser.parse(cached.content).copy(source = cached.source)
            if (!parsed.isEmpty) return@withContext parsed
        }

        // Companion .lrc or embedded tags
        val localLyrics = localFileLyricsSource.getLyrics(track)
        if (localLyrics != null && !localLyrics.isEmpty) {
            val source = localLyrics.source ?: "local_file"
            cacheLyrics(track.id, localLyrics, source)
            return@withContext localLyrics
        }

        // Online banks, synced results first
        val isOnlineEnabled = preferencesManager.isLrclibEnabled.first()
        if (isOnlineEnabled) {
            val foundLyrics = searchAllOnlineSources(track)
            if (foundLyrics != null && !foundLyrics.lyrics.isEmpty) {
                val sourcedLyrics = foundLyrics.lyrics.copy(source = foundLyrics.sourceName)
                cacheLyrics(track.id, sourcedLyrics, foundLyrics.sourceName)
                // Loading/searching must never mutate shared audio files in the background.
                // Embedded writes are reserved for explicit user save actions after consent.
                return@withContext sourcedLyrics
            }
        }

        null
    }

    override suspend fun fetchOnlineLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val foundLyrics = searchAllOnlineSources(track)
        if (foundLyrics != null && !foundLyrics.lyrics.isEmpty) {
            val sourcedLyrics = foundLyrics.lyrics.copy(source = foundLyrics.sourceName)
            cacheLyrics(track.id, sourcedLyrics, foundLyrics.sourceName)
            return@withContext sourcedLyrics
        }
        null
    }

    override suspend fun fetchOnlineLyricsWithCustomQuery(
        track: Track,
        title: String,
        artist: String
    ): Lyrics? = withContext(Dispatchers.IO) {
        val queryTrack = track.copy(title = title.trim(), artist = artist.trim())
        val foundLyrics = searchAllOnlineSources(queryTrack)
        if (foundLyrics != null && !foundLyrics.lyrics.isEmpty) {
            val sourcedLyrics = foundLyrics.lyrics.copy(source = foundLyrics.sourceName)
            cacheLyrics(track.id, sourcedLyrics, foundLyrics.sourceName)
            return@withContext sourcedLyrics
        }
        null
    }

    override suspend fun searchLyricsCandidates(
        title: String,
        artist: String
    ): List<com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate> = withContext(Dispatchers.IO) {
        try {
            lrclibLyricsSource.searchCandidates(title, artist)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class LyricsSearchResult(val lyrics: Lyrics, val sourceName: String)

    /**
     * Searches multiple online lyrics banks with strict priority:
     * 1. High priority: SYNCED lyrics (LRCLIB, Netease, Kugou)
     * 2. Low priority: PLAIN/UNSYNCED lyrics (LRCLIB plain, Lyrics.ovh)
     */
    private suspend fun searchAllOnlineSources(track: Track): LyricsSearchResult? {
        var fallbackPlainLyrics: LyricsSearchResult? = null

        // --- Bank 1: LRCLIB (Fast, exact synced tags) ---
        try {
            val lrclibRes = lrclibLyricsSource.getLyrics(track)
            if (lrclibRes != null && !lrclibRes.isEmpty) {
                if (lrclibRes.isSynced) {
                    return LyricsSearchResult(lrclibRes, "lrclib")
                } else if (fallbackPlainLyrics == null) {
                    fallbackPlainLyrics = LyricsSearchResult(lrclibRes, "lrclib")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }

        // --- Bank 2: Netease Cloud Music (Massive synced LRC bank) ---
        try {
            val neteaseRes = neteaseLyricsSource.getLyrics(track)
            if (neteaseRes != null && !neteaseRes.isEmpty) {
                if (neteaseRes.isSynced) {
                    return LyricsSearchResult(neteaseRes, "netease")
                } else if (fallbackPlainLyrics == null) {
                    fallbackPlainLyrics = LyricsSearchResult(neteaseRes, "netease")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }

        // --- Bank 3: Kugou Music (Massive synced LRC bank) ---
        try {
            val kugouRes = kugouLyricsSource.getLyrics(track)
            if (kugouRes != null && !kugouRes.isEmpty) {
                if (kugouRes.isSynced) {
                    return LyricsSearchResult(kugouRes, "kugou")
                } else if (fallbackPlainLyrics == null) {
                    fallbackPlainLyrics = LyricsSearchResult(kugouRes, "kugou")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }

        // If synced lyrics found, we already returned. If not, return plain lyrics if any bank had them
        if (fallbackPlainLyrics != null) {
            return fallbackPlainLyrics
        }

        // --- Bank 4: Lyrics.ovh (Plain lyrics fallback) ---
        try {
            val ovhRes = lyricsOvhLyricsSource.getLyrics(track)
            if (ovhRes != null && !ovhRes.isEmpty) {
                return LyricsSearchResult(ovhRes, "lyrics_ovh")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }

        return null
    }

    /**
     * Saves lyrics to app storage first, then attempts embedded MP3 tags. The return value
     * is true only when embedded lyrics were written and verified. Room remains a fallback,
     * and its presence is never described as an embedded-file write.
     */
    override suspend fun saveLyrics(track: Track, content: String, source: String): Boolean =
        withContext(Dispatchers.IO) {
            val cleanContent = content.trim()
            if (cleanContent.isBlank()) return@withContext false
            val parsed = LrcParser.parse(cleanContent)
            // Cache regardless of file writability; the return value reports tag writing.
            // The return value specifically reports whether embedded audio tags were verified.
            lyricsDao.insertLyrics(
                LyricsEntity(
                    trackId = track.id,
                    content = cleanContent,
                    isSynced = parsed.isSynced,
                    source = source
                )
            )
            // A sidecar file is only written by the explicit companion save below.
            localFileLyricsSource.saveEmbeddedLyrics(track, cleanContent)
        }

    /** Explicit companion-file fallback, separate from embedded tag writing. */
    suspend fun saveLyricsCompanion(track: Track, content: String): Boolean = withContext(Dispatchers.IO) {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return@withContext false
        val parsed = LrcParser.parse(cleanContent)
        lyricsDao.insertLyrics(
            LyricsEntity(trackId = track.id, content = cleanContent, isSynced = parsed.isSynced, source = "companion_file")
        )
        localFileLyricsSource.saveCompanionLrc(track, cleanContent)
    }

    override suspend fun deleteLyrics(trackId: String) = withContext(Dispatchers.IO) {
        lyricsDao.insertLyrics(
            LyricsEntity(
                trackId = trackId,
                content = "",
                isSynced = false,
                source = "deleted"
            )
        )
    }

    override suspend fun deleteLyrics(track: Track, removeFromFile: Boolean): Boolean = withContext(Dispatchers.IO) {
        // The tombstone keeps lyrics from being re-discovered online or from stale caches.
        lyricsDao.insertLyrics(
            LyricsEntity(trackId = track.id, content = "", isSynced = false, source = "deleted")
        )
        localFileLyricsSource.deleteCompanionLrc(track)
        if (!removeFromFile) return@withContext false
        localFileLyricsSource.clearEmbeddedLyrics(track).isSuccess
    }

    override fun getOffset(trackId: String): Flow<Long> =
        lyricsDao.getOffset(trackId).map { it ?: 0L }

    override suspend fun setOffset(trackId: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        if (offsetMs == 0L) lyricsDao.clearOffset(trackId)
        else lyricsDao.setOffset(com.yasinjahantigh.logarhythm.data.room.LyricsOffsetEntity(trackId, offsetMs))
    }

    override fun getHiddenTrackIds(): Flow<List<String>> = lyricsDao.getHiddenTrackIds()

    /** Removes the app's override/tombstone so normal local/embedded/default lookup resumes. */
    override suspend fun resetLyricsToDefault(trackId: String) = withContext(Dispatchers.IO) {
        lyricsDao.deleteLyricsForTrack(trackId)
    }

    private suspend fun cacheLyrics(trackId: String, lyrics: Lyrics, source: String) {
        val raw = formatLyricsToLrc(lyrics)
        lyricsDao.insertLyrics(
            LyricsEntity(
                trackId = trackId,
                content = raw,
                isSynced = lyrics.isSynced,
                source = source
            )
        )
    }

    private fun formatLyricsToLrc(lyrics: Lyrics): String {
        return if (lyrics.isSynced) {
            fun stamp(ms: Long) = String.format(
                java.util.Locale.ROOT, "%02d:%02d.%02d", ms / 60_000L, (ms % 60_000L) / 1_000L, (ms % 1_000L) / 10L
            )
            lyrics.lines.joinToString("\n") { line ->
                val body = if (line.words.isEmpty()) line.text
                else line.words.joinToString("") { "<${stamp(it.startMs)}>${it.text}" }
                "[${stamp(line.timestampMs)}]$body"
            }
        } else {
            lyrics.plainText.orEmpty()
        }
    }
}
