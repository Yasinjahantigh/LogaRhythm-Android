package com.example.musicplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.feature.player.FullPlayerScreen
import com.example.musicplayer.feature.player.MiniPlayer
import com.example.musicplayer.feature.player.PlayerViewModel
import com.example.musicplayer.ui.designsystem.AppDimensions
import com.example.musicplayer.ui.designsystem.AppMotion
import com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding
import com.example.musicplayer.ui.navigation.MusicBottomBar
import com.example.musicplayer.ui.navigation.MusicNavGraph
import com.example.musicplayer.ui.navigation.NavDestination

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by playerViewModel.position.collectAsStateWithLifecycle()
    val durationMs by playerViewModel.duration.collectAsStateWithLifecycle()
    val isExpanded by playerViewModel.isExpanded.collectAsStateWithLifecycle()
    val isCompact by playerViewModel.isCompactMiniPlayer.collectAsStateWithLifecycle()
    val isDismissed by playerViewModel.isDismissed.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val useNavigationRail = configuration.screenWidthDp >= 700 || configuration.screenHeightDp < configuration.screenWidthDp

    val topLevelRoutes = listOf(
        NavDestination.Home.route,
        NavDestination.Search.route,
        NavDestination.Library.route,
        NavDestination.Settings.route
    )
    val isTopLevelRoute = currentRoute in topLevelRoutes

    val showMiniPlayer = currentTrack != null && !isExpanded && !isDismissed
    val miniPlayerHeight = if (isCompact) 48.dp else AppDimensions.miniPlayerHeight
    val bottomBarHeight = if (isTopLevelRoute && !useNavigationRail) AppDimensions.bottomBarHeight else 0.dp
    val totalBottomPadding = bottomBarHeight + (if (showMiniPlayer) miniPlayerHeight else 0.dp)

    CompositionLocalProvider(LocalPlaybackBottomPadding provides totalBottomPadding) {
        Box(modifier = modifier.fillMaxSize()) {
            // Layer 1: Nav Graph fills the whole screen so lists scroll smoothly under bottom elements
            MusicNavGraph(
                navController = navController,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (useNavigationRail && isTopLevelRoute) Modifier.padding(start = 80.dp) else Modifier)
            )

            // Layer 2: Adaptive shell. Wide/landscape windows use a rail and keep playback controls at the bottom of the content region.
            if (useNavigationRail && isTopLevelRoute) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    MusicBottomBar(
                        currentDestination = currentRoute,
                        onNavigateToDestination = { destination ->
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                            AnimatedVisibility(
                                visible = showMiniPlayer,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                currentTrack?.let { track ->
                                    MiniPlayer(
                                        track = track, isPlaying = isPlaying, positionMs = positionMs, durationMs = durationMs,
                                        onTogglePlayPause = playerViewModel::togglePlayPause, onNext = playerViewModel::next,
                                        onPrevious = playerViewModel::previous, onExpand = playerViewModel::expand,
                                        isCompact = isCompact, onDismiss = playerViewModel::dismissMiniPlayer,
                                        modifier = Modifier.navigationBarsPadding()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = showMiniPlayer,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        currentTrack?.let { track ->
                            MiniPlayer(
                                track = track, isPlaying = isPlaying, positionMs = positionMs, durationMs = durationMs,
                                onTogglePlayPause = playerViewModel::togglePlayPause, onNext = playerViewModel::next,
                                onPrevious = playerViewModel::previous, onExpand = playerViewModel::expand,
                                isCompact = isCompact, onDismiss = playerViewModel::dismissMiniPlayer,
                                modifier = if (!isTopLevelRoute) Modifier.navigationBarsPadding() else Modifier
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = isTopLevelRoute,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MusicBottomBar(
                            currentDestination = currentRoute,
                            onNavigateToDestination = { destination ->
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Layer 3: Full Screen Player Overlay (Completely covers everything when expanded)
            AnimatedVisibility(
                visible = isExpanded && currentTrack != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = AppMotion.durationSlow, easing = AppMotion.fastOutSlowEasing)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = AppMotion.durationNormal, easing = FastOutLinearInEasing)
                ) + fadeOut()
            ) {
                FullPlayerScreen(
                    viewModel = playerViewModel,
                    onCollapse = playerViewModel::collapse
                )
            }
        }
    }
}
