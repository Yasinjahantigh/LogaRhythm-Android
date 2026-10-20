package com.yasinjahantigh.logarhythm.data.backup

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import com.yasinjahantigh.logarhythm.data.room.LyricsDao
import com.yasinjahantigh.logarhythm.data.room.LyricsEntity
import com.yasinjahantigh.logarhythm.data.room.LyricsOffsetEntity
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.data.room.MusicDatabase
import com.yasinjahantigh.logarhythm.data.room.PlayEventEntity
import com.yasinjahantigh.logarhythm.data.room.PlaylistDao
import com.yasinjahantigh.logarhythm.data.room.PlaylistEntity
import com.yasinjahantigh.logarhythm.data.room.PlaylistTrackEntity
import com.yasinjahantigh.logarhythm.data.room.ResumePositionEntity
import com.yasinjahantigh.logarhythm.data.room.TrackEntity
import com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

data class BackupSummary(
    val playlists: Int,
    val playEvents: Int,
    val lyrics: Int,
    val skippedSongs: Int = 0
)

/**
 * One-file backup of everything that cannot be re-created from the music files: playlists,
 * favourites, listening history, edited / hidden lyrics, lyrics offsets and long-audio resume
 * points. Songs are stored by a small reference (id + title + artist + duration) and matched to
 * the library again on restore, so a backup also works on a different phone where MediaStore ids
 * differ. Streaming JSON via the platform's own parser keeps it dependency-free and light on RAM.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao,
    private val lyricsDao: LyricsDao
) {
    private data class Ref(val id: String, val title: String, val artist: String, val durationMs: Long)

    private fun refKey(title: String, artist: String, durationMs: Long): String =
        DuplicateFinder.normalize(title) + "|" + DuplicateFinder.normalize(artist) + "|" + (durationMs / 2000L)

    // ------------------------------------------------------------------------------ export

    suspend fun export(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = musicDao.getAllTracksOnce().associateBy { it.id }
            val playlists = playlistDao.getAllPlaylistEntitiesOnce()
            val playlistTracks = playlistDao.getAllPlaylistTracksOnce().groupBy { it.playlistId }
            val events = musicDao.getAllPlayEventsOnce()
            val lyrics = lyricsDao.getAllLyricsOnce().filter { it.source.startsWith("user") || it.source == "companion_file" || it.source == "deleted" }
            val offsets = lyricsDao.getAllOffsetsOnce()
            val resumes = musicDao.getAllResumePositionsOnce()

            // Build the song table first so every section can point at it by index.
            val index = LinkedHashMap<String, Int>()
            fun idx(id: String): Int? = if (tracks.containsKey(id)) index.getOrPut(id) { index.size } else null
            playlistTracks.values.flatten().forEach { idx(it.trackId) }
            events.forEach { idx(it.trackId) }
            lyrics.forEach { idx(it.trackId) }
            offsets.forEach { idx(it.trackId) }
            resumes.forEach { idx(it.trackId) }

            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Cannot open backup file")
            JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { w ->
                w.setIndent("")
                w.beginObject()
                w.name("format").value("logarhythm-backup")
                w.name("version").value(1)
                w.name("createdAt").value(System.currentTimeMillis())

                w.name("songs").beginArray()
                for (id in index.keys) {
                    val t = tracks.getValue(id)
                    w.beginObject()
                    w.name("id").value(t.id)
                    w.name("t").value(t.title)
                    w.name("a").value(t.artist)
                    w.name("d").value(t.durationMs)
                    w.endObject()
                }
                w.endArray()

                w.name("playlists").beginArray()
                for (p in playlists) {
                    w.beginObject()
                    w.name("name").value(p.name)
                    w.name("builtIn").value(p.isBuiltIn)
                    w.name("tracks").beginArray()
                    playlistTracks[p.id].orEmpty().sortedBy { it.orderIndex }
                        .forEach { pt -> idx(pt.trackId)?.let { w.value(it.toLong()) } }
                    w.endArray()
                    w.endObject()
                }
                w.endArray()

                w.name("events").beginArray()
                for (e in events) {
                    val i = idx(e.trackId) ?: continue
                    w.beginArray()
                    w.value(i.toLong()); w.value(e.timestamp); w.value(e.listenedMs); w.value(if (e.skipped) 1L else 0L)
                    w.endArray()
                }
                w.endArray()

                w.name("lyrics").beginArray()
                for (l in lyrics) {
                    val i = idx(l.trackId) ?: continue
                    w.beginObject()
                    w.name("i").value(i.toLong())
                    w.name("content").value(l.content)
                    w.name("synced").value(l.isSynced)
                    w.name("source").value(l.source)
                    w.endObject()
                }
                w.endArray()

                w.name("offsets").beginArray()
                for (o in offsets) {
                    val i = idx(o.trackId) ?: continue
                    w.beginArray(); w.value(i.toLong()); w.value(o.offsetMs); w.endArray()
                }
                w.endArray()

                w.name("resume").beginArray()
                for (r in resumes) {
                    val i = idx(r.trackId) ?: continue
                    w.beginArray(); w.value(i.toLong()); w.value(r.positionMs); w.endArray()
                }
                w.endArray()
                w.endObject()
            }
            BackupSummary(playlists.count { !it.isBuiltIn || playlistTracks[it.id].orEmpty().isNotEmpty() }, events.size, lyrics.size)
        }
    }

    // ------------------------------------------------------------------------------ restore

    private class Parsed {
        val songs = ArrayList<Ref>()
        val playlists = ArrayList<Triple<String, Boolean, List<Int>>>()
        val events = ArrayList<LongArray>()
        val lyrics = ArrayList<Pair<Int, LyricsEntity>>()
        val offsets = ArrayList<LongArray>()
        val resume = ArrayList<LongArray>()
    }

    suspend fun restore(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = context.contentResolver.openInputStream(uri)?.use { input ->
                parse(JsonReader(InputStreamReader(input, Charsets.UTF_8)))
            } ?: error("Cannot open backup file")

            val library = musicDao.getAllTracksOnce()
            val byId = library.associateBy { it.id }
            val byKey = HashMap<String, TrackEntity>()
            library.forEach { byKey.putIfAbsent(refKey(it.title, it.artist, it.durationMs), it) }
            val resolved: List<String?> = parsed.songs.map { ref ->
                (byId[ref.id]?.takeIf { refKey(it.title, it.artist, it.durationMs) == refKey(ref.title, ref.artist, ref.durationMs) }
                    ?: byKey[refKey(ref.title, ref.artist, ref.durationMs)]
                    ?: byId[ref.id])?.id
            }
            fun at(i: Int): String? = resolved.getOrNull(i)

            var playlistCount = 0
            var eventCount = 0
            var lyricsCount = 0
            database.withTransaction {
                val existingPlaylists = playlistDao.getAllPlaylistEntitiesOnce()
                for ((name, builtIn, refs) in parsed.playlists) {
                    val targetId: Long = if (builtIn) {
                        playlistDao.insertPlaylist(PlaylistEntity(id = Playlist.FAVORITES_ID, name = Playlist.FAVORITES_NAME, isBuiltIn = true))
                        Playlist.FAVORITES_ID
                    } else {
                        existingPlaylists.firstOrNull { !it.isBuiltIn && it.name.equals(name, ignoreCase = true) }?.id
                            ?: playlistDao.insertPlaylist(PlaylistEntity(name = name))
                    }
                    val present = playlistDao.getPlaylistTrackIds(targetId).toHashSet()
                    var order = (playlistDao.getMaxOrderIndex(targetId) ?: -1) + 1
                    val rows = ArrayList<PlaylistTrackEntity>()
                    for (ref in refs) {
                        val id = at(ref) ?: continue
                        if (present.add(id)) rows += PlaylistTrackEntity(playlistId = targetId, trackId = id, orderIndex = order++)
                    }
                    if (rows.isNotEmpty()) playlistDao.insertPlaylistTracks(rows)
                    playlistCount++
                }

                val known = musicDao.getAllPlayEventsOnce().mapTo(HashSet()) { it.trackId + "@" + it.timestamp }
                val newEvents = ArrayList<PlayEventEntity>()
                for (e in parsed.events) {
                    val id = at(e[0].toInt()) ?: continue
                    if (known.add(id + "@" + e[1])) {
                        newEvents += PlayEventEntity(trackId = id, timestamp = e[1], listenedMs = e[2], skipped = e[3] == 1L)
                    }
                }
                newEvents.chunked(500).forEach { musicDao.insertPlayEvents(it) }
                eventCount = newEvents.size

                for ((ref, entity) in parsed.lyrics) {
                    val id = at(ref) ?: continue
                    lyricsDao.insertLyrics(entity.copy(trackId = id))
                    lyricsCount++
                }
                for (o in parsed.offsets) at(o[0].toInt())?.let { lyricsDao.setOffset(LyricsOffsetEntity(it, o[1])) }
                for (r in parsed.resume) {
                    at(r[0].toInt())?.let { musicDao.upsertResumePosition(ResumePositionEntity(it, r[1], System.currentTimeMillis())) }
                }
            }
            BackupSummary(playlistCount, eventCount, lyricsCount, skippedSongs = resolved.count { it == null })
        }
    }

    private fun parse(r: JsonReader): Parsed {
        val out = Parsed()
        r.beginObject()
        var recognised = false
        while (r.hasNext()) {
            when (r.nextName()) {
                "format" -> recognised = r.nextString() == "logarhythm-backup"
                "songs" -> r.readArray {
                    var id = ""; var t = ""; var a = ""; var d = 0L
                    r.readObject { name ->
                        when (name) { "id" -> id = nextString(); "t" -> t = nextString(); "a" -> a = nextString(); "d" -> d = nextLong(); else -> skipValue() }
                    }
                    out.songs += Ref(id, t, a, d)
                }
                "playlists" -> r.readArray {
                    var name = ""; var builtIn = false; val refs = ArrayList<Int>()
                    r.readObject { key ->
                        when (key) {
                            "name" -> name = nextString()
                            "builtIn" -> builtIn = nextBoolean()
                            "tracks" -> { beginArray(); while (hasNext()) refs += nextInt(); endArray() }
                            else -> skipValue()
                        }
                    }
                    out.playlists += Triple(name, builtIn, refs)
                }
                "events" -> r.readArray { out.events += r.readLongRow() }
                "lyrics" -> r.readArray {
                    var i = -1; var content = ""; var synced = false; var source = "user_manual"
                    r.readObject { key ->
                        when (key) { "i" -> i = nextInt(); "content" -> content = nextString(); "synced" -> synced = nextBoolean(); "source" -> source = nextString(); else -> skipValue() }
                    }
                    out.lyrics += Pair(i, LyricsEntity(trackId = "", content = content, isSynced = synced, source = source))
                }
                "offsets" -> r.readArray { out.offsets += r.readLongRow() }
                "resume" -> r.readArray { out.resume += r.readLongRow() }
                else -> r.skipValue()
            }
        }
        r.endObject()
        require(recognised) { "Not a LogaRhythm backup" }
        return out
    }

    private inline fun JsonReader.readArray(item: () -> Unit) {
        beginArray(); while (hasNext()) item(); endArray()
    }

    private inline fun JsonReader.readObject(entry: JsonReader.(String) -> Unit) {
        beginObject(); while (hasNext()) entry(nextName()); endObject()
    }

    private fun JsonReader.readLongRow(): LongArray {
        val values = ArrayList<Long>(4)
        beginArray()
        while (hasNext()) values += if (peek() == JsonToken.NULL) { nextNull(); 0L } else nextLong()
        endArray()
        return LongArray(maxOf(4, values.size)) { values.getOrElse(it) { 0L } }
    }
}
