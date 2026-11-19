package com.yasinjahantigh.logarhythm.core.artwork

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Artwork URIs stay the same when the embedded cover changes, so Coil and Media3 would keep
 * serving the old bitmap. A per-URI version lets both caches use a fresh key after a tag write.
 */
object ArtworkVersionStore {

    private val _versions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val versions: StateFlow<Map<String, Long>> = _versions.asStateFlow()

    /** Compose-observable mirror so scrolling rows read versions without allocating a Flow collector per row. */
    private val stateVersions = mutableStateMapOf<String, Long>()

    fun versionOf(uri: String?): Long = uri?.let { _versions.value[it] } ?: 0L

    fun stateVersionOf(uri: String?): Long = uri?.let { stateVersions[it] } ?: 0L

    fun bump(uri: String?) {
        val key = uri?.takeIf { it.isNotBlank() } ?: return
        var nextVersion = 1L
        _versions.update { current ->
            nextVersion = (current[key] ?: 0L) + 1L
            current + (key to nextVersion)
        }
        runCatching {
            Snapshot.withMutableSnapshot {
                stateVersions[key] = nextVersion
            }
        }
    }
}
