package com.yasinjahantigh.logarhythm.feature.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val themeStyle: StateFlow<String> = preferencesManager.themeStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val themeMode: StateFlow<String> = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val customAccent: StateFlow<Long> = preferencesManager.customAccentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFFF4B266)

    val customBase: StateFlow<String> = preferencesManager.customBase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "dark")

    val customBackgroundUri: StateFlow<String?> = preferencesManager.customBackgroundUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customBackgroundAlpha: StateFlow<Float> = preferencesManager.customBackgroundAlpha
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.35f)

    fun setThemeStyle(style: String) {
        viewModelScope.launch { preferencesManager.setThemeStyle(style) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setAccent(argb: Long) {
        viewModelScope.launch { preferencesManager.setCustomAccentColor(argb) }
    }

    fun setBase(base: String) {
        viewModelScope.launch { preferencesManager.setCustomBase(base) }
    }

    fun setBackgroundUri(uri: String?) {
        viewModelScope.launch { preferencesManager.setCustomBackgroundUri(uri) }
    }

    fun setBackgroundAlpha(alpha: Float) {
        viewModelScope.launch { preferencesManager.setCustomBackgroundAlpha(alpha) }
    }
}
