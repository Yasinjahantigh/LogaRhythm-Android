package com.yasinjahantigh.logarhythm.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SourceRoutingStatus(
    val isVpnActive: Boolean = false,
    val fastestSource: DownloadSource = DownloadSource.MUSICMAN,
    val measuredLatenciesMs: Map<DownloadSource, Long> = emptyMap(),
    val isBenchmarking: Boolean = false
)

/**
 * Dynamically ranks download & search sources based on live VPN state and measured latency:
 * - VPN OFF: MusicMan is always prioritized first (direct Iranian Bale mirror, works without VPN).
 * - VPN ON: Probes fast HTTP sources concurrently and tracks live observed latencies so the
 *   fastest reachable source on the user's current tunnel is used first.
 */
@Singleton
class DownloadSourceRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    baseClient: OkHttpClient
) {
    private val probeClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MS + 400L, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val probeMutex = Mutex()
    private val latencyMs = ConcurrentHashMap<DownloadSource, Long>()
    private val failedSources = ConcurrentHashMap<DownloadSource, Long>()

    @Volatile
    private var lastBenchmarkedAtMs: Long = 0L

    @Volatile
    private var lastVpnState: Boolean? = null

    private val _status = MutableStateFlow(SourceRoutingStatus())
    val status: StateFlow<SourceRoutingStatus> = _status.asStateFlow()

    /**
     * Checks whether the device currently routes traffic through an active VPN transport.
     * Inspects both activeNetwork and allNetworks (to cover split-tunnel VPNs on various OEMs).
     */
    fun isVpnActive(): Boolean {
        val cm = runCatching {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()

        if (cm != null) {
            val activeCaps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                return true
            }
            @Suppress("DEPRECATION")
            val anyVpn = runCatching {
                cm.allNetworks.any { network ->
                    val caps = cm.getNetworkCapabilities(network)
                    caps != null &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }.getOrDefault(false)
            if (anyVpn) return true
        }

        // Defensive fallback for VPN apps that register a tun/wg interface not surfaced as activeNetwork.
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name?.lowercase().orEmpty()
                if (name.startsWith("tun") || name.startsWith("wg") || name.startsWith("ppp") || name.startsWith("utun")) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Records observed real-world latency from an actual search or download call so routing
     * adapts continuously without requiring extra probe traffic.
     */
    fun recordLatency(source: DownloadSource, elapsedMs: Long, succeeded: Boolean) {
        val vpnNow = isVpnActive()
        resetCacheIfVpnChanged(vpnNow)
        val clamped = elapsedMs.coerceIn(20L, 15_000L)
        if (succeeded) {
            failedSources.remove(source)
            latencyMs.compute(source) { _, prev ->
                if (prev == null) clamped else ((prev * 0.45) + (clamped * 0.55)).toLong()
            }
        } else {
            failedSources[source] = System.currentTimeMillis()
            latencyMs[source] = FAILED_PENALTY_MS
        }
        publishStatus(vpnNow, isBenchmarking = false)
    }

    /**
     * Runs a fast parallel latency probe across direct HTTP sources when VPN is active.
     * When VPN is OFF, skips probing and immediately locks MusicMan as primary.
     */
    suspend fun ensureFreshRanking(force: Boolean = false) {
        val vpnNow = isVpnActive()
        resetCacheIfVpnChanged(vpnNow)
        if (!vpnNow) {
            publishStatus(isVpnActive = false, isBenchmarking = false)
            return
        }

        val now = System.currentTimeMillis()
        if (!force && now - lastBenchmarkedAtMs < BENCHMARK_TTL_MS && latencyMs.isNotEmpty()) {
            publishStatus(isVpnActive = true, isBenchmarking = false)
            return
        }

        probeMutex.withLock {
            val recheckNow = System.currentTimeMillis()
            if (!force && recheckNow - lastBenchmarkedAtMs < BENCHMARK_TTL_MS && latencyMs.isNotEmpty()) {
                publishStatus(isVpnActive = true, isBenchmarking = false)
                return
            }

            publishStatus(isVpnActive = true, isBenchmarking = true)
            val targets = listOf(
                DownloadSource.SAAVN to "https://saavn.dev/api/search/songs?query=a&limit=1",
                DownloadSource.DEEZER to "https://api.deezer.com/chart/0/tracks?limit=1",
                DownloadSource.RADIOJAVAN to "https://api.radiojavan.com/api2/search?query=a",
                DownloadSource.MUSICMAN to "https://mm.3rah.ir/api/search?term=a&limit=1&entity=song"
            )

            withContext(Dispatchers.IO) {
                coroutineScope {
                    targets.map { (source, url) ->
                        async {
                            val measured = probeEndpoint(url)
                            if (measured != null) {
                                failedSources.remove(source)
                                latencyMs[source] = measured
                            } else {
                                failedSources[source] = System.currentTimeMillis()
                                latencyMs[source] = FAILED_PENALTY_MS
                            }
                        }
                    }.awaitAll()
                }
            }
            lastBenchmarkedAtMs = System.currentTimeMillis()
            publishStatus(isVpnActive = true, isBenchmarking = false)
        }
    }

    /**
     * Bucket order for DownloadSearchService result presentation.
     * - VPN OFF -> "musicman" is ALWAYS first.
     * - VPN ON  -> fastest measured fast-API bucket first, followed by "ytdlp".
     */
    suspend fun rankedSearchBuckets(): List<String> {
        val vpnNow = isVpnActive()
        resetCacheIfVpnChanged(vpnNow)
        if (!vpnNow) {
            publishStatus(isVpnActive = false, isBenchmarking = false)
            return NO_VPN_SEARCH_BUCKETS
        }

        if (latencyMs.isEmpty() || System.currentTimeMillis() - lastBenchmarkedAtMs >= BENCHMARK_TTL_MS) {
            withTimeoutOrNull(PROBE_TIMEOUT_MS + 500L) {
                ensureFreshRanking(force = false)
            }
        }

        val rankedSources = sortByMeasuredSpeed(
            candidates = listOf(
                DownloadSource.DEEZER,
                DownloadSource.SAAVN,
                DownloadSource.RADIOJAVAN,
                DownloadSource.MUSICMAN
            ),
            vpnActive = true
        )
        return rankedSources.map { it.raw } + "ytdlp"
    }

    /**
     * Ordered list of direct audio-capable sources for DownloadWorker mirror resolution
     * (used for Deezer tracks and cross-source fallbacks).
     * - VPN OFF -> MusicMan first, then RadioJavan, Saavn.
     * - VPN ON  -> Fastest reachable audio source first (Saavn / RadioJavan / MusicMan).
     */
    suspend fun rankedAudioDownloadSources(): List<DownloadSource> {
        val vpnNow = isVpnActive()
        resetCacheIfVpnChanged(vpnNow)
        if (!vpnNow) {
            publishStatus(isVpnActive = false, isBenchmarking = false)
            return NO_VPN_AUDIO_SOURCES
        }

        if (latencyMs.isEmpty() || System.currentTimeMillis() - lastBenchmarkedAtMs >= BENCHMARK_TTL_MS) {
            withTimeoutOrNull(PROBE_TIMEOUT_MS + 500L) {
                ensureFreshRanking(force = false)
            }
        }

        return sortByMeasuredSpeed(
            candidates = listOf(
                DownloadSource.SAAVN,
                DownloadSource.RADIOJAVAN,
                DownloadSource.MUSICMAN
            ),
            vpnActive = true
        )
    }

    /**
     * Returns true if [candidate] should replace [existing] when deduplicating tracks
     * from multiple sources (e.g., in Artist Online tab).
     */
    fun isPreferredOver(candidate: DownloadSource, existing: DownloadSource): Boolean {
        if (candidate == existing) return false
        val vpnNow = isVpnActive()
        if (!vpnNow) {
            return candidate == DownloadSource.MUSICMAN && existing != DownloadSource.MUSICMAN
        }
        val candScore = effectiveLatency(candidate)
        val existScore = effectiveLatency(existing)
        return candScore + 80L < existScore
    }

    internal fun sortByMeasuredSpeed(
        candidates: List<DownloadSource>,
        vpnActive: Boolean
    ): List<DownloadSource> {
        if (!vpnActive) {
            return candidates.sortedBy { source ->
                when (source) {
                    DownloadSource.MUSICMAN -> 0
                    DownloadSource.RADIOJAVAN -> 1
                    DownloadSource.DEEZER -> 2
                    DownloadSource.SAAVN -> 3
                    else -> 4
                }
            }
        }
        return candidates.sortedBy { effectiveLatency(it) }
    }

    private fun effectiveLatency(source: DownloadSource): Long {
        val failedAt = failedSources[source]
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_COOLDOWN_MS) {
            return FAILED_PENALTY_MS
        }
        return latencyMs[source] ?: DEFAULT_VPN_PRIOR_MS[source] ?: 1_500L
    }

    private fun resetCacheIfVpnChanged(vpnNow: Boolean) {
        val prev = lastVpnState
        if (prev != null && prev != vpnNow) {
            latencyMs.clear()
            failedSources.clear()
            lastBenchmarkedAtMs = 0L
        }
        lastVpnState = vpnNow
    }

    private fun publishStatus(isVpnActive: Boolean, isBenchmarking: Boolean) {
        val fastest = if (!isVpnActive) {
            DownloadSource.MUSICMAN
        } else {
            sortByMeasuredSpeed(
                listOf(
                    DownloadSource.SAAVN,
                    DownloadSource.RADIOJAVAN,
                    DownloadSource.DEEZER,
                    DownloadSource.MUSICMAN
                ),
                vpnActive = true
            ).firstOrNull() ?: DownloadSource.DEEZER
        }
        _status.value = SourceRoutingStatus(
            isVpnActive = isVpnActive,
            fastestSource = fastest,
            measuredLatenciesMs = latencyMs.toMap().filterValues { it < FAILED_PENALTY_MS },
            isBenchmarking = isBenchmarking
        )
    }

    private suspend fun probeEndpoint(url: String): Long? = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
        val started = System.nanoTime()
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) LogaRhythm/1.0")
                .header("Accept", "application/json")
                .build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withTimeoutOrNull null
                // Drain a small prefix to ensure response bytes actually arrive through the tunnel.
                response.body?.source()?.request(64L)
                val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
                elapsed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PROBE_TIMEOUT_MS = 2_600L
        private const val BENCHMARK_TTL_MS = 5 * 60 * 1000L
        private const val FAILURE_COOLDOWN_MS = 90 * 1000L
        private const val FAILED_PENALTY_MS = 30_000L

        private val NO_VPN_SEARCH_BUCKETS = listOf("musicman", "radiojavan", "deezer", "saavn", "ytdlp")
        private val NO_VPN_AUDIO_SOURCES = listOf(
            DownloadSource.MUSICMAN,
            DownloadSource.RADIOJAVAN,
            DownloadSource.SAAVN
        )

        // Reasonable defaults before the first VPN probe finishes (overridden by live timings).
        private val DEFAULT_VPN_PRIOR_MS = mapOf(
            DownloadSource.SAAVN to 420L,
            DownloadSource.DEEZER to 460L,
            DownloadSource.RADIOJAVAN to 520L,
            DownloadSource.MUSICMAN to 950L
        )
    }
}
