package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import android.provider.MediaStore
import com.yasinjahantigh.logarhythm.domain.model.Track

object FileUtils {
    fun resolveTrackPath(context: Context, track: Track): String? {
        if (!track.id.startsWith("local:")) return track.uri
        val mediaStoreId = track.id.removePrefix("local:").toLongOrNull() ?: return null
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(mediaStoreId.toString())

        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (dataCol != -1 && cursor.moveToFirst()) {
                    cursor.getString(dataCol)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareTracks(context: Context, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        try {
            val uris = ArrayList(tracks.mapNotNull {
                runCatching { android.net.Uri.parse(it.uri) }.getOrNull()
            })
            val intent = if (uris.size == 1) {
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                    putExtra(android.content.Intent.EXTRA_TEXT, "${tracks.first().title} - ${tracks.first().artist}")
                }
            } else {
                android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                }
            }.apply {
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, context.getString(com.yasinjahantigh.logarhythm.R.string.action_share_song)))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, context.getString(com.yasinjahantigh.logarhythm.R.string.cannot_share_file), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
