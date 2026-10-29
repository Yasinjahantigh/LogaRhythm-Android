package com.yasinjahantigh.logarhythm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.yasinjahantigh.logarhythm.core.utils.AudioArtworkFetcher
import com.yasinjahantigh.logarhythm.data.analytics.InstallReporter
import com.yasinjahantigh.logarhythm.data.download.DownloadNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LogaRhythmApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var installReporter: InstallReporter

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannel(this)
        installReporter.reportOnce()
    }

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
