package com.yasinjahantigh.logarhythm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.yasinjahantigh.logarhythm.core.utils.AudioArtworkFetcher
import com.yasinjahantigh.logarhythm.data.analytics.InstallReporter
import com.yasinjahantigh.logarhythm.data.download.DownloadNotifications
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarNotifications
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
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
        ReleaseRadarNotifications.createChannel(this)
        ReleaseRadarWorker.schedule(this)
        installReporter.reportOnce()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@LogaRhythmApp))
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil_artwork_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(4))
            .decoderDispatcher(Dispatchers.Default.limitedParallelism(2))
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }
}
