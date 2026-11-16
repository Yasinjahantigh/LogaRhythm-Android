package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.ai.AiKeyStore
import com.yasinjahantigh.logarhythm.data.ai.AiPlaylistClient
import com.yasinjahantigh.logarhythm.data.ai.AiProvider
import com.yasinjahantigh.logarhythm.data.ai.AiSettings
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.ai.CatalogSelector
import com.yasinjahantigh.logarhythm.domain.model.AiFailure
import com.yasinjahantigh.logarhythm.domain.model.MagicPlaylistResult
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.AiPlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPlaylistRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val keyStore: AiKeyStore,
    private val client: AiPlaylistClient,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val statsRepository: StatsRepository
) : AiPlaylistRepository {

    /** Bumped whenever the encrypted key changes, so settings observers see the new state. */
    private val keyRevision = MutableStateFlow(0L)

    override fun observeSettings(): Flow<AiSettings> =
        combine(preferencesManager.aiPreferences, keyRevision) { prefs, _ ->
            val provider = AiProvider.fromRaw(prefs.provider)
            AiSettings(
                provider = provider,
                baseUrl = prefs.baseUrl?.takeIf { it.startsWith("https://") }
                    ?: AiSettings.defaultBase(provider),
                model = prefs.model?.takeIf { it.isNotBlank() }
                    ?: AiSettings.defaultModel(provider),
                hasKey = keyStore.hasKey()
            )
        }.flowOn(Dispatchers.IO)

    override suspend fun saveSettings(
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String?
    ) = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
            .takeIf { it.startsWith("https://") && it.length > "https://".length }
            ?: AiSettings.defaultBase(provider)
        val cleanModel = model.trim().ifBlank { AiSettings.defaultModel(provider) }
        preferencesManager.setAiPreferences(provider.raw, cleanBase, cleanModel)
        if (!apiKey.isNullOrBlank()) keyStore.save(apiKey)
        keyRevision.update { it + 1 }
    }

    override suspend fun clearKey() = withContext(Dispatchers.IO) {
        keyStore.clear()
        keyRevision.update { it + 1 }
    }

    override suspend fun testConnection(
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String?
    ): AiFailure? = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("https://")) return@withContext AiFailure.NOT_CONFIGURED
        val stored = keyStore.load()
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: stored
            ?: return@withContext AiFailure.NOT_CONFIGURED
        val settings = AiSettings(
            provider = provider,
            baseUrl = cleanBase,
            model = model.trim().ifBlank { AiSettings.defaultModel(provider) },
            hasKey = true
        )
        client.testConnection(settings, key)
    }

    override suspend fun generatePlaylist(prompt: String, trackCount: Int): MagicPlaylistResult =
        withContext(Dispatchers.IO) {
            if (prompt.isBlank()) return@withContext MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
            val settings = observeSettings().first()
            if (!settings.isConfigured) {
                return@withContext MagicPlaylistResult.Failure(AiFailure.NOT_CONFIGURED)
            }
            val apiKey = keyStore.load()
                ?: return@withContext MagicPlaylistResult.Failure(AiFailure.NOT_CONFIGURED)
            val library = trackRepository.getAllTracks().first()
            if (library.isEmpty()) {
                return@withContext MagicPlaylistResult.Failure(AiFailure.NO_TRACKS)
            }
            val ctx = buildTasteContext(library)
            val result = client.generate(
                settings = settings,
                apiKey = apiKey,
                prompt = prompt,
                trackCount = trackCount,
                catalog = CatalogSelector.forPlaylist(
                    library = library,
                    prompt = prompt,
                    profile = ctx.profile,
                    recentTrackIds = ctx.recentTrackIds,
                    seed = ctx.seed
                ),
                tasteSummary = ctx.summary
            )
            if (result is MagicPlaylistResult.Success) {
                preferencesManager.recordMixExposure(result.playlist.tracks.map { it.id }, ctx.today)
            }
            result
        }

    override suspend fun generateMix(
        prompt: String,
        mixName: String,
        baseTracks: List<Track>,
        additions: Int
    ): MagicPlaylistResult = withContext(Dispatchers.IO) {
        if (prompt.isBlank() || baseTracks.isEmpty()) {
            return@withContext MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
        }
        val settings = observeSettings().first()
        if (!settings.isConfigured) {
            return@withContext MagicPlaylistResult.Failure(AiFailure.NOT_CONFIGURED)
        }
        val apiKey = keyStore.load()
            ?: return@withContext MagicPlaylistResult.Failure(AiFailure.NOT_CONFIGURED)
        val library = trackRepository.getAllTracks().first()
        val ctx = buildTasteContext(library)
        val candidates = CatalogSelector.forMix(
            library = library,
            base = baseTracks,
            profile = ctx.profile,
            recentTrackIds = ctx.recentTrackIds,
            seed = ctx.seed
        )
        if (candidates.isEmpty()) {
            return@withContext MagicPlaylistResult.Failure(AiFailure.NO_TRACKS)
        }
        val result = client.generateMix(
            settings = settings,
            apiKey = apiKey,
            prompt = prompt,
            mixName = mixName,
            baseTracks = baseTracks,
            candidates = candidates,
            additions = additions,
            tasteSummary = ctx.summary
        )
        if (result is MagicPlaylistResult.Success) {
            preferencesManager.recordMixExposure(result.playlist.tracks.map { it.id }, ctx.today)
        }
        result
    }

    private data class TasteContext(
        val profile: TasteProfile,
        val summary: String,
        val recentTrackIds: Set<String>,
        val seed: Long,
        val today: Long
    )

    private suspend fun buildTasteContext(library: List<Track>): TasteContext {
        val nowMs = System.currentTimeMillis()
        val today = nowMs / TasteProfile.DAY_MS
        val events = statsRepository.getAllPlayEvents().first()
        val favorites = playlistRepository.getPlaylistTracks(1L).first().map { it.id }.toSet()
        val profile = TasteProfile.build(events, favorites, nowMs = nowMs, library = library)
        val recentIds = preferencesManager.mixExposureHistory.first()
            .filterValues { day -> today - day in 0L..7L }
            .keys
        return TasteContext(
            profile = profile,
            summary = profile.toSummary(),
            recentTrackIds = recentIds,
            seed = nowMs / 60_000L,
            today = today
        )
    }

    override suspend fun savePlaylist(name: String, trackIds: List<String>): Long =
        withContext(Dispatchers.IO) {
            if (name.isBlank() || trackIds.isEmpty()) return@withContext -1L
            val playlistId = playlistRepository.createPlaylist(name)
            if (playlistId > 0) {
                playlistRepository.addTracksToPlaylist(playlistId, trackIds)
            }
            playlistId
        }

}
