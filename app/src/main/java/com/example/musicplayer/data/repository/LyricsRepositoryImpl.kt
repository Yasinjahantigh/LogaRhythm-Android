package com.example.musicplayer.data.repository

import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.data.room.LyricsDao
import com.example.musicplayer.data.room.LyricsEntity
import com.example.musicplayer.data.sources.LocalFileLyricsSource
import com.example.musicplayer.data.sources.LrclibLyricsSource
import com.example.musicplayer.data.sources.LyricsOvhLyricsSource
import com.example.musicplayer.data.sources.NeteaseLyricsSource
import com.example.musicplayer.data.sources.KugouLyricsSource
import com.example.musicplayer.domain.lyrics.LrcParser
import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.LyricsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust Multi-Bank Lyrics Repository:
 *
 * Priority Rule:
 * 1. User manual input / cached database lyrics (highest priority).
 * 2. Embedded lyrics inside audio file tags & local companion (.lrc) files.
 * 3. Online Search Bank Hierarchy:
 *    a. SYNCED LYRICS FIRST:
 *       - LRCLIB (synced lyrics with timestamps)
 *       - Netease Cloud Music (world-scale database of synchronized .lrc)
 *    b. UNSYNCED / PLAIN LYRICS FALLBACK:
 *       - LRCLIB (plainLyrics fallback)
 *       - Lyrics.ovh (plain lyrics)
 * 4. Lyrics are cached in Room for reliable offline access. For supported local MP3 files,
 *    callers can explicitly request embedded ID3 lyrics; companion .lrc files remain a separate fallback.
 * 5. If no lyrics found anywhere: Allows manual entry via UI or external file import.
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
        // Step 1: Check Room database cache
        val cached = lyricsDao.getLyricsForTrackOnce(track.id)
        if (cached != null) {
            if (cached.source == "deleted") {
                return@withContext null
            }
            val parsed = LrcParser.parse(cached.content).copy(source = cached.source)
            if (!parsed.isEmpty) return@withContext parsed
        }

        // Step 2: Check local audio file (companion .lrc or embedded tags in ID3/MP4)
        val localLyrics = localFileLyricsSource.getLyrics(track)
        if (localLyrics != null && !localLyrics.isEmpty) {
            val source = localLyrics.source ?: "local_file"
            cacheLyrics(track.id, localLyrics, source)
            return@withContext localLyrics
        }

        // Step 3: Online search across lyrics banks (synced first, then plain)
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
    ): List<com.example.musicplayer.domain.model.LyricsCandidate> = withContext(Dispatchers.IO) {
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
            // Cache is the reliable app-level fallback even when the file is read-only.
            // The return value specifically reports whether embedded audio tags were verified.
            lyricsDao.insertLyrics(
                LyricsEntity(
                    trackId = track.id,
                    content = cleanContent,
                    isSynced = parsed.isSynced,
                    source = source
                )
            )
            // Do not silently create a sidecar file when embedding fails. The caller can
            // report cached-only honestly; companion .lrc saving is a separate operation.
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

    override suspend fun deleteLyrics(track: Track): Unit = withContext(Dispatchers.IO) {
        lyricsDao.insertLyrics(
            LyricsEntity(
                trackId = track.id,
                content = "",
                isSynced = false,
                source = "deleted"
            )
        )
        // Lyrics metadata is a separate user asset; preserve the cache tombstone even
        // when a provider denies companion-file deletion.
        localFileLyricsSource.deleteCompanionLrc(track)
        Unit
    }

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
            lyrics.lines.joinToString("\n") { line ->
                val min = line.timestampMs / 60_000L
                val sec = (line.timestampMs % 60_000L) / 1_000L
                val frac = (line.timestampMs % 1_000L) / 10L
                String.format("[%02d:%02d.%02d]%s", min, sec, frac, line.text)
            }
        } else {
            lyrics.plainText.orEmpty()
        }
    }
}
