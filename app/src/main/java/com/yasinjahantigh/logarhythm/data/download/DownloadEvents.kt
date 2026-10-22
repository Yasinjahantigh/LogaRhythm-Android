package com.yasinjahantigh.logarhythm.data.download

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadSavedEvent(val title: String, val usedFallback: Boolean)

/** In-process events for the screen's snackbar and haptic feedback. */
@Singleton
class DownloadEvents @Inject constructor() {
    private val _saved = MutableSharedFlow<DownloadSavedEvent>(extraBufferCapacity = 8)
    val saved: SharedFlow<DownloadSavedEvent> = _saved.asSharedFlow()

    fun emitSaved(event: DownloadSavedEvent) {
        _saved.tryEmit(event)
    }
}
