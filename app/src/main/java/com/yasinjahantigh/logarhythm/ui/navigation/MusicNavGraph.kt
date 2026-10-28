package com.yasinjahantigh.logarhythm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.yasinjahantigh.logarhythm.feature.album.AlbumDetailScreen
import com.yasinjahantigh.logarhythm.feature.artist.ArtistDetailScreen
import com.yasinjahantigh.logarhythm.feature.download.DownloadScreen
import com.yasinjahantigh.logarhythm.feature.editor.TagEditorScreen
import com.yasinjahantigh.logarhythm.feature.equalizer.EqualizerScreen
import com.yasinjahantigh.logarhythm.feature.folder.FolderDetailScreen
import com.yasinjahantigh.logarhythm.feature.genre.GenreDetailScreen
import com.yasinjahantigh.logarhythm.feature.home.HomeScreen
import com.yasinjahantigh.logarhythm.feature.library.LibraryScreen
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistDetailScreen
import com.yasinjahantigh.logarhythm.feature.search.SearchScreen
import com.yasinjahantigh.logarhythm.feature.settings.SettingsScreen
import com.yasinjahantigh.logarhythm.feature.smart.SmartMixDetailScreen
import com.yasinjahantigh.logarhythm.feature.tools.LibraryToolsScreen
import com.yasinjahantigh.logarhythm.feature.stats.StatsScreen
import com.yasinjahantigh.logarhythm.feature.theme.ThemeScreen

@Composable
fun MusicNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavDestination.Home.route,
        modifier = modifier
    ) {
        composable(NavDestination.Home.route) {
            HomeScreen(
                onNavigateToStats = {
                    navController.navigate(NavDestination.ROUTE_STATS)
                },
                onNavigateToLibrary = {
                    navController.navigate(NavDestination.Library.route)
                },
                onNavigateToSearch = {
                    navController.navigate(NavDestination.Search.route)
                },
                onNavigateToDownload = {
                    navController.navigate(NavDestination.ROUTE_DOWNLOAD)
                },
                onNavigateToSettings = {
                    navController.navigate(NavDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToTagEditor = { trackId ->
                    navController.navigate(NavDestination.createTagEditorRoute(trackId))
                }
            )
        }
        composable(NavDestination.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumName, albumArtist ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName, albumArtist))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(NavDestination.createArtistDetailRoute(artistName))
                },
                onNavigateToTagEditor = { trackId ->
                    navController.navigate(NavDestination.createTagEditorRoute(trackId))
                }
            )
        }
        composable(NavDestination.Library.route) {
            LibraryScreen(
                onNavigateToAlbum = { albumName, albumArtist ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName, albumArtist))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(NavDestination.createArtistDetailRoute(artistName))
                },
                onNavigateToFolder = { folderName ->
                    navController.navigate(NavDestination.createFolderDetailRoute(folderName))
                },
                onNavigateToGenre = { genreName ->
                    navController.navigate(NavDestination.createGenreDetailRoute(genreName))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(NavDestination.createPlaylistDetailRoute(playlistId))
                },
                onNavigateToSmartMix = { type ->
                    navController.navigate(NavDestination.createSmartMixRoute(type))
                },
                onNavigateToSettings = {
                    navController.navigate(NavDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSearch = {
                    navController.navigate(NavDestination.Search.route)
                },
                onNavigateToTagEditor = { trackId ->
                    navController.navigate(NavDestination.createTagEditorRoute(trackId))
                }
            )
        }
        composable(NavDestination.Settings.route) {
            SettingsScreen(
                onNavigateToStats = {
                    navController.navigate(NavDestination.ROUTE_STATS)
                },
                onNavigateToEqualizer = {
                    navController.navigate(NavDestination.ROUTE_EQUALIZER)
                },
                onNavigateToLibraryTools = {
                    navController.navigate(NavDestination.ROUTE_LIBRARY_TOOLS)
                },
                onNavigateToTheme = {
                    navController.navigate(NavDestination.ROUTE_THEME)
                },
                onNavigateToDownload = {
                    navController.navigate(NavDestination.ROUTE_DOWNLOAD)
                }
            )
        }
        composable(NavDestination.ROUTE_THEME) {
            ThemeScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(NavDestination.ROUTE_DOWNLOAD) {
            DownloadScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTagEditor = { trackId ->
                    navController.navigate(NavDestination.createTagEditorRoute(trackId))
                }
            )
        }
        composable(NavDestination.ROUTE_LIBRARY_TOOLS) {
            LibraryToolsScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditTrack = { trackId ->
                    navController.navigate(NavDestination.createTagEditorRoute(trackId))
                }
            )
        }
        composable(NavDestination.ROUTE_EQUALIZER) {
            EqualizerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavDestination.ROUTE_STATS) {
            StatsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumName, albumArtist ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName, albumArtist))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(NavDestination.createArtistDetailRoute(artistName))
                }
            )
        }
        composable(
            route = NavDestination.ROUTE_ALBUM_DETAIL,
            arguments = listOf(
                navArgument("albumName") { type = NavType.StringType },
                navArgument("albumArtist") { type = NavType.StringType }
            )
        ) {
            AlbumDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToArtist = { artistName ->
                    navController.navigate(NavDestination.createArtistDetailRoute(artistName))
                }
            )
        }
        composable(
            route = NavDestination.ROUTE_ARTIST_DETAIL,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) {
            ArtistDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumName, albumArtist ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName, albumArtist))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(NavDestination.createArtistDetailRoute(artistName))
                }
            )
        }
        composable(
            route = NavDestination.ROUTE_FOLDER_DETAIL,
            arguments = listOf(navArgument("folderName") { type = NavType.StringType })
        ) {
            FolderDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = NavDestination.ROUTE_GENRE_DETAIL,
            arguments = listOf(navArgument("genreName") { type = NavType.StringType })
        ) {
            GenreDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = NavDestination.ROUTE_PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) {
            PlaylistDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = NavDestination.ROUTE_SMART_MIX,
            arguments = listOf(navArgument("mixType") { type = NavType.StringType })
        ) {
            SmartMixDetailScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = NavDestination.ROUTE_TAG_EDITOR,
            arguments = listOf(navArgument("trackId") { type = NavType.StringType })
        ) {
            TagEditorScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
