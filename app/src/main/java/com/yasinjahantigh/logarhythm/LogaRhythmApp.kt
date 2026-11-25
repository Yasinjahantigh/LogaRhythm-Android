package com.yasinjahantigh.logarhythm

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.yasinjahantigh.logarhythm.core.utils.AudioArtworkFetcher
import com.yasinjahantigh.logarhythm.data.analytics.AppLogBuffer
import com.yasinjahantigh.logarhythm.data.analytics.CrashReportHandler
import com.yasinjahantigh.logarhythm.data.analytics.CrashUploadWorker
import com.yasinjahantigh.logarhythm.data.analytics.InstallReporter
import com.yasinjahantigh.logarhythm.data.analytics.WeeklyListeningStatsWorker
import com.yasinjahantigh.logarhythm.data.download.DownloadNotifications
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarNotifications
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarWorker
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class LogaRhythmApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var installReporter: Lazy<InstallReporter>

    @Inject
    lateinit var appLogBuffer: AppLogBuffer

    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashReportHandler.install(this, appLogBuffer, installReporter.get())
        // Move notification channels, WorkManager initialization, and install ping off the main thread
        // so cold startup reaches the first Compose frame without stalling on disk or binder I/O.
        appInitScope.launch {
            appLogBuffer.log("App", "Started v${BuildConfig.VERSION_NAME} (${BuildConfig.DISTRIBUTION_CHANNEL})")
            DownloadNotifications.createChannel(this@LogaRhythmApp)
            ReleaseRadarNotifications.createChannel(this@LogaRhythmApp)
            ReleaseRadarWorker.schedule(this@LogaRhythmApp)
            CrashUploadWorker.enqueueIfPending(this@LogaRhythmApp)
            WeeklyListeningStatsWorker.schedule(this@LogaRhythmApp)
            installReporter.get().reportOnce()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val memPercent = if (isLowRam) 0.14 else 0.20
        val diskBytes = if (isLowRam) 40L * 1024 * 1024 else 64L * 1024 * 1024
        val fetchConcurrency = if (isLowRam) 2 else 4
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@LogaRhythmApp))
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(memPercent).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil_artwork_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(fetchConcurrency))
            .decoderDispatcher(Dispatchers.Default.limitedParallelism(2))
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }
}
