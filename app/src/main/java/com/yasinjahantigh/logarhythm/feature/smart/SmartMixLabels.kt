package com.yasinjahantigh.logarhythm.feature.smart

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.smart.ListeningTimeSlot
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType

@StringRes
fun SmartMixType.titleRes(): Int = when (this) {
    SmartMixType.FOR_YOU -> R.string.smart_mix_for_you
    SmartMixType.TIME_OF_DAY_VIBE -> R.string.smart_mix_time_vibe
    SmartMixType.HIDDEN_GEMS -> R.string.smart_mix_hidden_gems
    SmartMixType.RECENTLY_ADDED -> R.string.smart_mix_recently_added
    SmartMixType.RECENTLY_PLAYED -> R.string.smart_mix_recently_played
    SmartMixType.MOST_PLAYED -> R.string.smart_mix_most_played
    SmartMixType.FORGOTTEN_FAVORITES -> R.string.smart_mix_forgotten_favorites
    SmartMixType.NO_LYRICS -> R.string.smart_mix_no_lyrics
    SmartMixType.LONG_SONGS -> R.string.smart_mix_long_songs
    SmartMixType.DOWNLOADED -> R.string.smart_mix_downloaded
}

@StringRes
fun SmartMixType.descriptionRes(): Int = when (this) {
    SmartMixType.FOR_YOU -> R.string.smart_mix_for_you_desc
    SmartMixType.TIME_OF_DAY_VIBE -> R.string.smart_mix_time_vibe_desc
    SmartMixType.HIDDEN_GEMS -> R.string.smart_mix_hidden_gems_desc
    SmartMixType.RECENTLY_ADDED -> R.string.smart_mix_recently_added_desc
    SmartMixType.RECENTLY_PLAYED -> R.string.smart_mix_recently_played_desc
    SmartMixType.MOST_PLAYED -> R.string.smart_mix_most_played_desc
    SmartMixType.FORGOTTEN_FAVORITES -> R.string.smart_mix_forgotten_favorites_desc
    SmartMixType.NO_LYRICS -> R.string.smart_mix_no_lyrics_desc
    SmartMixType.LONG_SONGS -> R.string.smart_mix_long_songs_desc
    SmartMixType.DOWNLOADED -> R.string.smart_mix_downloaded_desc
}

fun SmartMixType.icon(): ImageVector = when (this) {
    SmartMixType.FOR_YOU -> Icons.Filled.AutoAwesome
    SmartMixType.TIME_OF_DAY_VIBE -> Icons.Filled.Schedule
    SmartMixType.HIDDEN_GEMS -> Icons.Filled.Explore
    SmartMixType.RECENTLY_ADDED -> Icons.Filled.NewReleases
    SmartMixType.RECENTLY_PLAYED -> Icons.Filled.History
    SmartMixType.MOST_PLAYED -> Icons.Filled.TrendingUp
    SmartMixType.FORGOTTEN_FAVORITES -> Icons.Filled.FavoriteBorder
    SmartMixType.NO_LYRICS -> Icons.Filled.Lyrics
    SmartMixType.LONG_SONGS -> Icons.Filled.Timer
    SmartMixType.DOWNLOADED -> Icons.Filled.DownloadDone
}

@StringRes
fun ListeningTimeSlot.labelRes(): Int = when (this) {
    ListeningTimeSlot.MORNING -> R.string.smart_taste_slot_morning
    ListeningTimeSlot.AFTERNOON -> R.string.smart_taste_slot_afternoon
    ListeningTimeSlot.EVENING -> R.string.smart_taste_slot_evening
    ListeningTimeSlot.NIGHT -> R.string.smart_taste_slot_night
}
