package com.yasinjahantigh.logarhythm.core.artwork

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

    fun versionOf(uri: String?): Long = uri?.let { _versions.value[it] } ?: 0L

    fun bump(uri: String?) {
        val key = uri?.takeIf { it.isNotBlank() } ?: return
        _versions.update { current ->
            current + (key to ((current[key] ?: 0L) + 1L))
        }
    }
}
