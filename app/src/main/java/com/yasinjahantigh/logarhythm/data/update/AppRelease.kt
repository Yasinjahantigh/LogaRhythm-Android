package com.yasinjahantigh.logarhythm.data.update

/** The newest published APK that matches this device. */
data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSizeBytes: Long,
    val abi: String,
    val downloads: Long
)

sealed interface UpdateState {
    data object Hidden : UpdateState
    data object Checking : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** [fraction] is negative while the response size is still unknown. */
    data class Downloading(val release: AppRelease, val fraction: Float) : UpdateState
    data class Failed(val release: AppRelease?) : UpdateState
}
