package com.yasinjahantigh.logarhythm.domain.smart

/** A smart mix as shown in the Playlists tab: its live size plus a little artwork to look at. */
data class SmartMix(
    val type: SmartMixType,
    val trackCount: Int,
    val artworkUris: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = trackCount == 0
}
