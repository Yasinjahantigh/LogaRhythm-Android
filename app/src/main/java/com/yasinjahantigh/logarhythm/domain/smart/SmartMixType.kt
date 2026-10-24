package com.yasinjahantigh.logarhythm.domain.smart

/** Built-in dynamic collections. Nothing is stored per mix; every list is derived from the library. */
enum class SmartMixType(val raw: String) {
    RECENTLY_ADDED("recently_added"),
    RECENTLY_PLAYED("recently_played"),
    MOST_PLAYED("most_played"),
    FORGOTTEN_FAVORITES("forgotten_favorites"),
    NO_LYRICS("no_lyrics"),
    LONG_SONGS("long_songs"),
    DOWNLOADED("downloaded");

    companion object {
        fun fromRaw(value: String?): SmartMixType? = entries.firstOrNull { it.raw == value }
    }
}
