package com.yasinjahantigh.logarhythm.feature.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Supplies the current artist-splitting rules to shared menus such as "Go to artist". */
@HiltViewModel
class ArtistSplitViewModel @Inject constructor(
    preferencesManager: PreferencesManager
) : ViewModel() {

    val config: StateFlow<ArtistSplitConfig> = combine(
        preferencesManager.splitArtists,
        preferencesManager.artistSeparators,
        preferencesManager.artistNeverSplit
    ) { enabled, raw, neverSplit ->
        if (enabled) {
            ArtistSplitConfig(
                enabled = true,
                separators = ArtistSplitter.parseSeparators(raw),
                neverSplit = neverSplit
            )
        } else {
            ArtistSplitConfig.OFF
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ArtistSplitConfig()
    )
}
