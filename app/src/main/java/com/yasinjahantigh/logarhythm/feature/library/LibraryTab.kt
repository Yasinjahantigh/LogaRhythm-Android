package com.yasinjahantigh.logarhythm.feature.library

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.yasinjahantigh.logarhythm.R

/**
 * The library's categories, in the order people reach for them. Each one carries its own icon so
 * the category rail can double as a compact "what's in my library" overview.
 */
enum class LibraryTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    SONGS(R.string.tab_songs, Icons.Filled.MusicNote),
    ALBUMS(R.string.tab_albums, Icons.Filled.Album),
    ARTISTS(R.string.tab_artists, Icons.Filled.Person),
    PLAYLISTS(R.string.tab_playlists, Icons.AutoMirrored.Filled.QueueMusic),
    GENRES(R.string.tab_genres, Icons.Filled.Category),
    FOLDERS(R.string.tab_folders, Icons.Filled.Folder)
}
