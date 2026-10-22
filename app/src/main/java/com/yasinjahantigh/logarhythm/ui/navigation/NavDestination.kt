package com.yasinjahantigh.logarhythm.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yasinjahantigh.logarhythm.R

sealed class NavDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val contentDescriptionRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : NavDestination(
        route = "home",
        labelRes = R.string.nav_home,
        contentDescriptionRes = R.string.cd_home_icon,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Search : NavDestination(
        route = "search",
        labelRes = R.string.nav_search,
        contentDescriptionRes = R.string.cd_search_icon,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    )

    data object Library : NavDestination(
        route = "library",
        labelRes = R.string.nav_library,
        contentDescriptionRes = R.string.cd_library_icon,
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    )

    data object Settings : NavDestination(
        route = "settings",
        labelRes = R.string.nav_settings,
        contentDescriptionRes = R.string.cd_settings_icon,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        /** Search is deliberately not a tab: it opens from the search pills on Home and Library. */
        val bottomNavDestinations: List<NavDestination>
            get() = listOf(Home, Library, Settings)

        const val ROUTE_ALBUM_DETAIL = "album_detail/{albumName}"
        const val ROUTE_ARTIST_DETAIL = "artist_detail/{artistName}"
        const val ROUTE_FOLDER_DETAIL = "folder_detail/{folderName}"
        const val ROUTE_GENRE_DETAIL = "genre_detail/{genreName}"
        const val ROUTE_PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
        const val ROUTE_STATS = "stats"
        const val ROUTE_EQUALIZER = "equalizer"
        const val ROUTE_LIBRARY_TOOLS = "library_tools"
        const val ROUTE_THEME = "theme_studio"
        const val ROUTE_DOWNLOAD = "download"

        fun createAlbumDetailRoute(albumName: String): String =
            "album_detail/${Uri.encode(albumName)}"

        fun createArtistDetailRoute(artistName: String): String =
            "artist_detail/${Uri.encode(artistName)}"

        fun createFolderDetailRoute(folderName: String): String =
            "folder_detail/${Uri.encode(folderName)}"

        fun createGenreDetailRoute(genreName: String): String =
            "genre_detail/${Uri.encode(genreName)}"

        fun createPlaylistDetailRoute(playlistId: Long): String =
            "playlist_detail/$playlistId"
    }
}
