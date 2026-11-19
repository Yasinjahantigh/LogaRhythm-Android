package com.yasinjahantigh.logarhythm

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yasinjahantigh.logarhythm.core.library.LibrarySync
import com.yasinjahantigh.logarhythm.core.utils.ArtworkColorExtractor
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.datastore.ThemePreferences
import com.yasinjahantigh.logarhythm.data.download.ShareLinkResolver
import com.yasinjahantigh.logarhythm.data.update.UpdateManager
import com.yasinjahantigh.logarhythm.feature.onboarding.OnboardingScreen
import com.yasinjahantigh.logarhythm.feature.update.UpdateDialogHost
import com.yasinjahantigh.logarhythm.playback.PlayerController
import com.yasinjahantigh.logarhythm.ui.MainScreen
import com.yasinjahantigh.logarhythm.ui.theme.LogaRhythmTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var playerController: PlayerController

    @Inject
    lateinit var librarySync: LibrarySync

    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var shareLinkResolver: ShareLinkResolver

    private var shareEventSeq by mutableStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                shareLinkResolver.submitIncomingText(shared)
                shareEventSeq++
            }
        }
    }

    override fun onStart() {
        super.onStart()
        librarySync.onForeground()
    }

    override fun onStop() {
        librarySync.onBackground()
        super.onStop()
    }

    override fun attachBaseContext(newBase: Context) {
        // This preference lives in SharedPreferences so it is available before Hilt/DataStore
        // is initialized. It does not overwrite Android's system language setting.
        val language = newBase.getSharedPreferences("music_settings", Context.MODE_PRIVATE)
            .getString("app_locale_override", "system") ?: "system"
        val locale = when (language) {
            "fa" -> Locale.forLanguageTag("fa")
            "en" -> Locale.forLanguageTag("en")
            else -> null
        }
        val configured = if (locale == null) {
            Locale.setDefault(newBase.resources.configuration.locales[0])
            newBase
        } else {
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(configured)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // We handle configuration changes ourselves (so pending system dialogs and playback UI survive
        // rotation); re-apply the edge-to-edge bar styling so it follows a light/dark switch.
        enableEdgeToEdge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingShare(intent)
        enableEdgeToEdge()
        // Synchronous SharedPreferences mirror written by PreferencesManager, so the first
        // composed frame already knows whether onboarding is done.
        val onboardingBootstrap = getSharedPreferences(
            PreferencesManager.BOOTSTRAP_PREFS, Context.MODE_PRIVATE
        ).getBoolean(PreferencesManager.BOOTSTRAP_ONBOARDING_KEY, false)
        setContent {
            val themePrefs by preferencesManager.themePreferences.collectAsStateWithLifecycle(
                initialValue = ThemePreferences()
            )
            val themeMode = themePrefs.themeMode
            val themeStyle = themePrefs.themeStyle
            val themePresetId = themePrefs.themePresetId
            val customAccent = themePrefs.customAccentColor
            val customBase = themePrefs.customBase
            val cornerRoundness = themePrefs.cornerRoundness
            val customBackgroundUri = themePrefs.customBackgroundUri
            val customBackgroundAlpha = themePrefs.customBackgroundAlpha
            val appFont = themePrefs.appFont
            val customFontPath = themePrefs.customFontPath
            val isOnboardingCompleted by preferencesManager.isOnboardingCompleted.collectAsStateWithLifecycle(
                initialValue = onboardingBootstrap
            )
            val currentTrack by playerController.currentTrack.collectAsStateWithLifecycle()
            val updateState by updateManager.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            LaunchedEffect(isOnboardingCompleted) {
                if (isOnboardingCompleted) {
                    delay(3_000L)
                    updateManager.autoCheck()
                }
            }

            LaunchedEffect(Unit) {
                updateManager.messages.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            var seedArtworkColor by remember { mutableStateOf<Color?>(null) }

            // Decoded once per song (cached, 64px, debounced), drives the artwork color theme.
            LaunchedEffect(currentTrack?.artworkUri, themeStyle) {
                val uri = currentTrack?.artworkUri
                if (uri != null && themeStyle == "album_art") {
                    delay(120)
                    seedArtworkColor = ArtworkColorExtractor.extractDominantColor(
                        this@MainActivity,
                        uri
                    )
                } else {
                    seedArtworkColor = null
                }
            }

            LogaRhythmTheme(
                themeMode = themeMode,
                themeStyle = themeStyle,
                themePresetId = themePresetId,
                customAccentColor = Color(customAccent),
                customBase = customBase,
                cornerRoundness = cornerRoundness,
                appFont = appFont,
                customFontPath = customFontPath,
                seedArtworkColor = seedArtworkColor,
                hasCustomBackground = customBackgroundUri != null && isOnboardingCompleted
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (customBackgroundUri != null && isOnboardingCompleted) {
                            AsyncImage(
                                model = customBackgroundUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            val isDark = when (themeStyle) {
                                "oled" -> true
                                "custom" -> customBase != "light"
                                else -> when (themeMode) {
                                    "light" -> false
                                    "dark" -> true
                                    else -> isSystemInDarkTheme()
                                }
                            }
                            val overlayColor = if (isDark) Color.Black else Color.White
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(overlayColor.copy(alpha = 1f - customBackgroundAlpha))
                            )
                        }

                        Crossfade(targetState = isOnboardingCompleted, label = "onboarding_crossfade") { completed ->
                            if (!completed) {
                                OnboardingScreen(
                                    onFinish = {
                                        scope.launch {
                                            preferencesManager.setOnboardingCompleted(true)
                                            // Permission was just answered in onboarding; index the library now.
                                            librarySync.requestRefresh(force = true)
                                        }
                                    }
                                )
                            } else {
                                MainScreen(
                                    seedTint = seedArtworkColor,
                                    shareEventSeq = shareEventSeq
                                )
                            }
                        }
                    }
                }
                if (isOnboardingCompleted) {
                    UpdateDialogHost(
                        state = updateState,
                        installedVersion = updateManager.installedVersion,
                        onDownload = updateManager::download,
                        onDismiss = updateManager::dismiss
                    )
                }
            }
        }
    }
}
