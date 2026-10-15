package com.example.musicplayer.data.tagger

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.musicplayer.data.tagger.engine.AudioTags
import com.example.musicplayer.data.tagger.engine.TagEdit
import com.example.musicplayer.data.tagger.engine.TagEngines
import com.example.musicplayer.data.tagger.engine.UnsupportedTagException
import com.example.musicplayer.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Result of an embedded-tag write. Only [Success] means the file on disk was changed and verified. */
sealed interface TagWriteOutcome {
    data object Success : TagWriteOutcome
    /** The container/format cannot be rewritten safely (or this is not a local file). */
    data class Unsupported(val reason: String) : TagWriteOutcome
    /** Android refused write access. */
    data object Denied : TagWriteOutcome
    data object Failed : TagWriteOutcome

    val isSuccess: Boolean get() = this is Success
}

/**
 * Writes embedded tags (title, artist, album, genre, lyrics) into the user's audio files through
 * their MediaStore URI. MP3 (ID3v2.2/2.3/2.4), FLAC, M4A/MP4 and Ogg Vorbis/Opus are handled by
 * the pure-JVM engines in `data.tagger.engine`.
 *
 * Every write is staged: the file is copied, rewritten off to the side, parsed again to prove the
 * new tags are really there, and only then streamed back. The result is compared byte-for-byte
 * with the staged file, and the original bytes are restored if anything goes wrong.
 */
@Singleton
class AudioFileTagWriter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Compatibility wrapper used by the repository: fields equal to [track]'s current values are left alone. */
    suspend fun writeTagsToFileAndMediaStore(
        track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String
    ): Boolean = applyEdit(track, editFor(track, title, artist, album, genre)).isSuccess

    suspend fun writeEmbeddedLyrics(track: Track, content: String): Boolean =
        content.isNotBlank() && applyEdit(track, TagEdit(lyrics = content.trim())).isSuccess

    /** Removes the embedded lyrics frame/atom/comment from the audio file itself. */
    suspend fun clearEmbeddedLyrics(track: Track): TagWriteOutcome =
        applyEdit(track, TagEdit(lyrics = ""))

    /** Reads embedded tags (including lyrics) without copying the song. Null when unreadable/unsupported. */
    suspend fun readEmbeddedTags(track: Track): AudioTags? = withContext(Dispatchers.IO) {
        val uri = mediaStoreUri(track) ?: return@withContext null
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val file = File("/proc/self/fd/${pfd.fd}")
                TagEngines.detect(file)?.read(file)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /** Which formats can be written, for user-facing messages. */
    fun supportedFormats(): List<String> = TagEngines.supportedLabels

    private fun editFor(track: Track, title: String, artist: String, album: String, genre: String): TagEdit {
        fun field(new: String, old: String, placeholder: String): String? {
            val n = new.trim()
            val o = old.trim()
            if (n == o) return null
            return if (n.isBlank() || n.equals(placeholder, ignoreCase = true)) "" else n
        }
        return TagEdit(
            title = field(title, track.title, "Unknown Title"),
            artist = field(artist, track.artist, "Unknown Artist"),
            album = field(album, track.album, "Unknown Album"),
            genre = field(genre, track.genre, "Unknown Genre")
        )
    }

    suspend fun applyEdit(track: Track, edit: TagEdit): TagWriteOutcome = withContext(Dispatchers.IO) {
        val uri = mediaStoreUri(track) ?: return@withContext TagWriteOutcome.Unsupported("Not a local library file")
        if (edit.isEmpty) return@withContext TagWriteOutcome.Success

        val stamp = System.nanoTime()
        val original = File(context.noBackupFilesDir, "tag_original_$stamp")
        val staged = File(context.cacheDir, "tag_stage_$stamp")
        var outputAttempted = false
        var verified = false
        var keepBackup = false
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext TagWriteOutcome.Failed
            input.use { original.outputStream().use { out -> it.copyTo(out) } }
            if (original.length() < 16L) return@withContext TagWriteOutcome.Failed

            val engine = TagEngines.detect(original)
                ?: return@withContext TagWriteOutcome.Unsupported("Unrecognised audio container")
            engine.rewrite(original, staged, edit)
            if (!staged.isFile || staged.length() < 16L) return@withContext TagWriteOutcome.Failed
            if (TagEngines.detect(staged) !== engine || !matches(edit, engine.read(staged))) {
                return@withContext TagWriteOutcome.Failed
            }

            outputAttempted = true
            val stream = context.contentResolver.openOutputStream(uri, "rwt")
                ?: return@withContext TagWriteOutcome.Denied
            stream.use { out -> staged.inputStream().use { it.copyTo(out) } }

            if (!contentMatches(uri, staged)) return@withContext TagWriteOutcome.Failed
            verified = true
            runCatching { context.contentResolver.notifyChange(uri, null) }
            requestRescan(uri)
            TagWriteOutcome.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: UnsupportedTagException) {
            TagWriteOutcome.Unsupported(e.message ?: "Unsupported tag layout")
        } catch (_: SecurityException) {
            TagWriteOutcome.Denied
        } catch (e: IOException) {
            Log.w(TAG, "Tag write failed", e)
            TagWriteOutcome.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Tag write failed", e)
            TagWriteOutcome.Failed
        } finally {
            if (outputAttempted && !verified && original.isFile) {
                val restored = withContext(NonCancellable) { restore(uri, original) }
                if (!restored) {
                    keepBackup = true
                    Log.e(TAG, "Could not verify restoration; recovery copy kept at ${original.absolutePath}")
                }
            }
            runCatching { staged.delete() }
            if (!keepBackup) runCatching { original.delete() }
        }
    }

    // ---- verification ---------------------------------------------------------------------

    private fun matches(edit: TagEdit, actual: AudioTags): Boolean {
        fun ok(wanted: String?, got: String?, lyrics: Boolean = false): Boolean {
            if (wanted == null) return true
            if (wanted.isBlank()) return got.isNullOrBlank()
            fun norm(s: String) = if (lyrics) s.replace("\r\n", "\n").trim() else s.trim()
            return got != null && norm(got) == norm(wanted)
        }
        return ok(edit.title, actual.title) && ok(edit.artist, actual.artist) &&
            ok(edit.album, actual.album) && ok(edit.genre, actual.genre) &&
            ok(edit.lyrics, actual.lyrics, lyrics = true)
    }

    private fun contentMatches(uri: Uri, expected: File): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { actual -> streamsEqual(actual, expected.inputStream()) } ?: false
    } catch (_: Exception) {
        false
    }

    private fun streamsEqual(a: InputStream, b: InputStream): Boolean {
        a.buffered().use { left ->
            b.buffered().use { right ->
                val x = ByteArray(64 * 1024)
                val y = ByteArray(64 * 1024)
                while (true) {
                    val n = fill(left, x)
                    val m = fill(right, y)
                    if (n != m) return false
                    if (n <= 0) return true
                    if (!java.util.Arrays.equals(x, 0, n, y, 0, n)) return false
                }
            }
        }
    }

    private fun fill(input: InputStream, buf: ByteArray): Int {
        var count = 0
        while (count < buf.size) {
            val r = input.read(buf, count, buf.size - count)
            if (r < 0) break
            count += r
        }
        return count
    }

    private fun restore(uri: Uri, original: File): Boolean {
        return try {
            if (contentMatches(uri, original)) {
                true
            } else {
                val out = context.contentResolver.openOutputStream(uri, "rwt")
                if (out == null) {
                    false
                } else {
                    out.use { stream -> original.inputStream().use { it.copyTo(stream) } }
                    contentMatches(uri, original)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    // ---- MediaStore plumbing ---------------------------------------------------------------

    /** The exact MediaStore item URI for a local track, or null if the identity does not line up. */
    private fun mediaStoreUri(track: Track): Uri? {
        if (!track.id.startsWith("local:")) return null
        val id = track.id.removePrefix("local:").toLongOrNull() ?: return null
        val uri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return null
        val ok = uri.scheme.equals("content", ignoreCase = true) &&
            uri.authority.equals("media", ignoreCase = true) &&
            uri.lastPathSegment?.toLongOrNull() == id
        return uri.takeIf { ok }
    }

    /** Best effort: ask the platform to re-index the file so MediaStore catches up with the new tags. */
    private fun requestRescan(uri: Uri) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val path = c.getString(0)
                    if (!path.isNullOrBlank()) MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                }
            }
        }
    }

    private companion object {
        const val TAG = "AudioFileTagWriter"
    }
}
