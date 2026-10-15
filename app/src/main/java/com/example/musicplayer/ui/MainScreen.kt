package com.example.musicplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.feature.player.FullPlayerScreen
import com.example.musicplayer.feature.player.MiniPlayer
import com.example.musicplayer.feature.player.MiniPlayerContent
import com.example.musicplayer.feature.player.PlayerViewModel
import com.example.musicplayer.ui.designsystem.AppMotion
import com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding
import com.example.musicplayer.ui.navigation.MusicDock
import com.example.musicplayer.ui.navigation.MusicNavGraph
import com.example.musicplayer.ui.navigation.MusicNavRail
import com.example.musicplayer.ui.navigation.NavDestination

private fun NavHostController.navigateTopLevel(destination: NavDestination, currentRoute: String?) {
    if (currentRoute != destination.route) {
        navigate(destination.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    /** Artwork-derived tint (decoded once in MainActivity, cached). Tints player chrome only. */
    seedTint: androidx.compose.ui.graphics.Color? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    // Kept as State objects (never read here) so the 500 ms position ticks only invalidate the
    // mini player's progress ring instead of recomposing the whole app shell.
    val positionState = playerViewModel.position.collectAsStateWithLifecycle()
    val durationState = playerViewModel.duration.collectAsStateWithLifecycle()
    val miniProgress: () -> Float = remember(positionState, durationState) {
        {
            val d = durationState.value
            if (d > 0L) (positionState.value.toFloat() / d.toFloat()).coerceIn(0f, 1f) else 0f
        }
    }
    val isExpanded by playerViewModel.isExpanded.collectAsStateWithLifecycle()
    val isCompact by playerViewModel.isCompactMiniPlayer.collectAsStateWithLifecycle()
    val isDismissed by playerViewModel.isDismissed.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val useNavigationRail = configuration.screenWidthDp >= 700 || configuration.screenHeightDp < configuration.screenWidthDp

    val topLevelRoutes = NavDestination.bottomNavDestinations.map { it.route }
    val isTopLevelRoute = currentRoute in topLevelRoutes

    val showMiniPlayer = currentTrack != null && !isExpanded && !isDismissed
    val showDock = isTopLevelRoute && !useNavigationRail

    // Heights the floating chrome occupies, so every list can scroll clear of it.
    val navInset: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockNavHeight = 66.dp
    val miniHeight = if (isCompact) 52.dp else 64.dp
    val chromeHeight = when {
        showDock -> dockNavHeight + (if (showMiniPlayer) miniHeight else 0.dp) + 10.dp + navInset
        showMiniPlayer -> miniHeight + 12.dp + navInset
        else -> 0.dp
    }
    val totalBottomPadding = if (chromeHeight > 0.dp) chromeHeight + 8.dp else 0.dp

    val onMiniNext = playerViewModel::next
    val onMiniPrevious = playerViewModel::previous

    CompositionLocalProvider(LocalPlaybackBottomPadding provides totalBottomPadding) {
        Box(modifier = modifier.fillMaxSize()) {
            if (useNavigationRail && isTopLevelRoute) {
                Row(modifier = Modifier.fillMaxSize()) {
                    MusicNavRail(
                        currentDestination = currentRoute,
                        onNavigateToDestination = { navController.navigateTopLevel(it, currentRoute) }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        MusicNavGraph(navController = navController, modifier = Modifier.fillMaxSize())
                        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                            AnimatedVisibility(
                                visible = showMiniPlayer,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                currentTrack?.let { track ->
                                    MiniPlayer(
                                        track = track, isPlaying = isPlaying, progress = miniProgress,
                                        onTogglePlayPause = playerViewModel::togglePlayPause,
                                        onNext = onMiniNext, onPrevious = onMiniPrevious,
                                        onExpand = playerViewModel::expand,
                                        isCompact = isCompact, onDismiss = playerViewModel::dismissMiniPlayer,
                                        modifier = Modifier.navigationBarsPadding(),
                                        tint = seedTint
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                MusicNavGraph(navController = navController, modifier = Modifier.fillMaxSize())
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    if (showDock) {
                        // One surface: mini player on top, navigation pills below.
                        MusicDock(
                            currentDestination = currentRoute,
                            onNavigateToDestination = { navController.navigateTopLevel(it, currentRoute) },
                            modifier = Modifier.padding(bottom = 10.dp).navigationBarsPadding(),
                            miniPlayer = if (showMiniPlayer && currentTrack != null) {
                                {
                                    currentTrack?.let { track ->
                                        MiniPlayerContent(
                                            track = track, isPlaying = isPlaying, progress = miniProgress,
                                            onTogglePlayPause = playerViewModel::togglePlayPause,
                                            onNext = onMiniNext, onPrevious = onMiniPrevious,
                                            onExpand = playerViewModel::expand,
                                            isCompact = isCompact, onDismiss = playerViewModel::dismissMiniPlayer,
                                            tint = seedTint
                                        )
                                    }
                                }
                            } else null
                        )
                    } else {
                        AnimatedVisibility(
                            visible = showMiniPlayer,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            currentTrack?.let { track ->
                                MiniPlayer(
                                    track = track, isPlaying = isPlaying, progress = miniProgress,
                                    onTogglePlayPause = playerViewModel::togglePlayPause,
                                    onNext = onMiniNext, onPrevious = onMiniPrevious,
                                    onExpand = playerViewModel::expand,
                                    isCompact = isCompact, onDismiss = playerViewModel::dismissMiniPlayer,
                                    modifier = Modifier.navigationBarsPadding(),
                                    tint = seedTint
                                )
                            }
                        }
                    }
                }
            }

            // Full-screen player overlay (covers everything when expanded)
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
