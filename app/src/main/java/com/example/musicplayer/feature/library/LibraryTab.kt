package com.example.musicplayer.feature.library

import androidx.annotation.StringRes
import com.example.R

enum class LibraryTab(@StringRes val titleRes: Int) {
    SONGS(R.string.tab_songs),
    ALBUMS(R.string.tab_albums),
    ARTISTS(R.string.tab_artists),
    GENRES(R.string.tab_genres),
    FOLDERS(R.string.tab_folders),
    PLAYLISTS(R.string.tab_playlists)
}
