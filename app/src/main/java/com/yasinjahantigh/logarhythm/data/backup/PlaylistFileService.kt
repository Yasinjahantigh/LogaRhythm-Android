package com.yasinjahantigh.logarhythm.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.data.room.MusicDatabase
import com.yasinjahantigh.logarhythm.data.room.PlaylistDao
import com.yasinjahantigh.logarhythm.data.room.PlaylistEntity
import com.yasinjahantigh.logarhythm.data.room.PlaylistTrackEntity
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.playlist.M3uCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class M3uImportSummary(val playlists: Int, val matched: Int, val missing: Int)

/** Import / export of `.m3u8` playlists through the system file picker. */
@Singleton
class PlaylistFileService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao
) {
    /** Writes every playlist (favourites included, if not empty) as its own file into [treeUri]. */
    suspend fun exportAll(treeUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            val byId = musicDao.getAllTracksOnce().associateBy { it.id }
            val rows = playlistDao.getAllPlaylistTracksOnce().groupBy { it.playlistId }
            var written = 0
            for (playlist in playlistDao.getAllPlaylistEntitiesOnce()) {
                val tracks = rows[playlist.id].orEmpty().sortedBy { it.orderIndex }
                    .mapNotNull { byId[it.trackId]?.toDomain() }
                if (tracks.isEmpty()) continue
                val safeName = playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "playlist" }
                val doc = DocumentsContract.createDocument(resolver, parent, "audio/x-mpegurl", "$safeName.m3u8") ?: continue
                resolver.openOutputStream(doc, "wt")?.use { it.write(M3uCodec.encode(tracks).toByteArray(Charsets.UTF_8)) }
                written++
            }
            written
        }
    }

    suspend fun importFiles(uris: List<Uri>): Result<M3uImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val library = musicDao.getAllTracksOnce().map { it.toDomain() }
            var playlists = 0
            var matched = 0
            var missing = 0
            for (uri in uris) {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: continue
                val result = M3uCodec.match(M3uCodec.parse(text), library)
                missing += result.missing.size
                if (result.matched.isEmpty()) continue
                val name = displayName(uri).substringBeforeLast('.').ifBlank { "Imported playlist" }
                database.withTransaction {
                    val id = playlistDao.insertPlaylist(PlaylistEntity(name = name))
                    val seen = HashSet<String>()
                    playlistDao.insertPlaylistTracks(
                        result.matched.filter { seen.add(it.id) }
                            .mapIndexed { i, t -> PlaylistTrackEntity(playlistId = id, trackId = t.id, orderIndex = i) }
                    )
                }
                playlists++
                matched += result.matched.size
            }
            M3uImportSummary(playlists, matched, missing)
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}
