package com.yasinjahantigh.logarhythm

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.core.library.LibrarySync
import com.yasinjahantigh.logarhythm.core.utils.ArtworkColorExtractor
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
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
        enableEdgeToEdge()
        // Synchronous SharedPreferences mirror written by PreferencesManager, so the first
        // composed frame already knows whether onboarding is done.
        val onboardingBootstrap = getSharedPreferences(
            PreferencesManager.BOOTSTRAP_PREFS, Context.MODE_PRIVATE
        ).getBoolean(PreferencesManager.BOOTSTRAP_ONBOARDING_KEY, false)
        setContent {
            val themeMode by preferencesManager.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val themeStyle by preferencesManager.themeStyle.collectAsStateWithLifecycle(initialValue = "default")
            val customAccent by preferencesManager.customAccentColor.collectAsStateWithLifecycle(initialValue = 0xFFF4B266)
            val customBase by preferencesManager.customBase.collectAsStateWithLifecycle(initialValue = "dark")
            val cornerRoundness by preferencesManager.cornerRoundness.collectAsStateWithLifecycle(initialValue = "standard")
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
                customAccentColor = Color(customAccent),
                customBase = customBase,
                cornerRoundness = cornerRoundness,
                seedArtworkColor = seedArtworkColor
            ) {
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
                        MainScreen(seedTint = seedArtworkColor)
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
