package com.example.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.example.musicplayer.core.utils.AudioArtworkFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@MusicApplication))
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .allowRgb565(true)
            .crossfade(150)
            .build()
    }
}
