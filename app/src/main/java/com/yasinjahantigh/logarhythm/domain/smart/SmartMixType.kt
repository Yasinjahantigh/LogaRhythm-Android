package com.yasinjahantigh.logarhythm.domain.smart

/** Built-in dynamic collections. Nothing is stored per mix; every list is derived from the library. */
enum class SmartMixType(val raw: String) {
    FOR_YOU("for_you"),
    TIME_OF_DAY_VIBE("time_of_day_vibe"),
    HIDDEN_GEMS("hidden_gems"),
    RECENTLY_ADDED("recently_added"),
    RECENTLY_PLAYED("recently_played"),
    MOST_PLAYED("most_played"),
    FORGOTTEN_FAVORITES("forgotten_favorites"),
    NO_LYRICS("no_lyrics"),
    LONG_SONGS("long_songs"),
    DOWNLOADED("downloaded");

    val isAdaptiveTasteMix: Boolean
        get() = this == FOR_YOU || this == TIME_OF_DAY_VIBE || this == HIDDEN_GEMS

    companion object {
        fun fromRaw(value: String?): SmartMixType? = entries.firstOrNull { it.raw == value }
    }
}
