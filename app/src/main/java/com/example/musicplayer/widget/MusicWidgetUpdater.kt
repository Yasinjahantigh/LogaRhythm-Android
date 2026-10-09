package com.example.musicplayer.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MusicWidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.Default)

    fun update(
        context: Context,
        title: String?,
        artist: String?,
        artworkUri: String?,
        isPlaying: Boolean
    ) {
        scope.launch {
            try {
                val glanceManager = GlanceAppWidgetManager(context)
                val glanceIds = glanceManager.getGlanceIds(MusicWidget::class.java)
                if (glanceIds.isEmpty()) return@launch

                val widget = MusicWidget()
                for (glanceId in glanceIds) {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[booleanPreferencesKey("is_playing")] = isPlaying
                            this[stringPreferencesKey("title")] = title.orEmpty()
                            this[stringPreferencesKey("artist")] = artist.orEmpty()
                            this[stringPreferencesKey("artwork_uri")] = artworkUri.orEmpty()
                        }
                    }
                    widget.update(context, glanceId)
                }
            } catch (_: Exception) {
                // Ignore widget update errors during early process start
            }
        }
    }
}
