package com.yasinjahantigh.logarhythm.core.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    /**
     * Determines required audio permission based on API level.
     * Android 13+ (API 33) requires READ_MEDIA_AUDIO.
     * Android 12 and below requires READ_EXTERNAL_STORAGE.
     */
    fun getRequiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Checks if the required audio read permission is granted.
     */
    fun hasAudioPermission(context: Context): Boolean {
        val permission = getRequiredAudioPermission()
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
