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
import com.yasinjahantigh.logarhythm.feature.equalizer.EqualizerScreen
import com.yasinjahantigh.logarhythm.feature.folder.FolderDetailScreen
import com.yasinjahantigh.logarhythm.feature.genre.GenreDetailScreen
import com.yasinjahantigh.logarhythm.feature.home.HomeScreen
import com.yasinjahantigh.logarhythm.feature.library.LibraryScreen
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistDetailScreen
import com.yasinjahantigh.logarhythm.feature.search.SearchScreen
import com.yasinjahantigh.logarhythm.feature.settings.SettingsScreen
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
                }
            )
        }
        composable(NavDestination.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumName ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(NavDestination.createArtistDetailRoute(artistName))
                }
            )
        }
        composable(NavDestination.Library.route) {
            LibraryScreen(
                onNavigateToAlbum = { albumName ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName))
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
                onNavigateToSearch = {
                    navController.navigate(NavDestination.Search.route)
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
                }
            )
        }
        composable(NavDestination.ROUTE_THEME) {
            ThemeScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(NavDestination.ROUTE_LIBRARY_TOOLS) {
            LibraryToolsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(NavDestination.ROUTE_EQUALIZER) {
            EqualizerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavDestination.ROUTE_STATS) {
            StatsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumName ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(NavDestination.createArtistDetailRoute(artistName))
                }
            )
        }
        composable(
            route = NavDestination.ROUTE_ALBUM_DETAIL,
            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
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
                onNavigateToAlbum = { albumName ->
                    navController.navigate(NavDestination.createAlbumDetailRoute(albumName))
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
    }
}
