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
}
