package com.yasinjahantigh.logarhythm

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.yasinjahantigh.logarhythm.core.utils.AudioArtworkFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LogaRhythmApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@LogaRhythmApp))
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .allowRgb565(false)
            .crossfade(150)
            .build()
    }
}
