package com.example.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.musicplayer.core.utils.AudioArtworkFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@MusicApplication))
            }
            .crossfade(true)
            .build()
    }
}
