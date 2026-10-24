package com.yasinjahantigh.logarhythm.data.tagger

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.yasinjahantigh.logarhythm.data.tagger.engine.AudioTags
import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEdit

/**
 * Mirrors verified file tags into the MediaStore row. Without this the app writes the file and
 * Room, but other players and the next scan keep seeing the old title/year until the scanner
 * happens to re-read the file.
 */
internal object MediaStoreTags {

    fun update(context: Context, uri: Uri, edit: TagEdit, tags: AudioTags) {
        val values = ContentValues()
        if (edit.title != null) putText(values, MediaStore.Audio.Media.TITLE, tags.title)
        if (edit.artist != null) putText(values, MediaStore.Audio.Media.ARTIST, tags.artist)
        if (edit.album != null) putText(values, MediaStore.Audio.Media.ALBUM, tags.album)
        if (edit.year != null) {
            val year = tags.year?.take(4)?.toIntOrNull()
            if (year != null && year > 0) values.put(MediaStore.Audio.Media.YEAR, year)
            else values.putNull(MediaStore.Audio.Media.YEAR)
        }
        if (edit.trackNumber != null || edit.discNumber != null) {
            val track = tags.trackNumber ?: 0
            val disc = tags.discNumber ?: 0
            val packed = disc * 1000 + track
            if (packed > 0) values.put(MediaStore.Audio.Media.TRACK, packed)
            else values.putNull(MediaStore.Audio.Media.TRACK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (edit.albumArtist != null) {
                putText(values, MediaStore.Audio.Media.ALBUM_ARTIST, tags.albumArtist)
            }
            if (edit.composer != null) {
                putText(values, MediaStore.Audio.Media.COMPOSER, tags.composer)
            }
        }
        if (values.size() == 0) return
        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (exception: Exception) {
            // The embedded tags are already verified; a provider that rejects a column must not
            // turn a successful file edit into a failure.
            Log.w("MediaStoreTags", "Could not mirror tags into MediaStore", exception)
        }
    }

    private fun putText(values: ContentValues, column: String, value: String?) {
        if (value.isNullOrBlank()) values.putNull(column) else values.put(column, value)
    }
}
