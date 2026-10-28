package com.yasinjahantigh.logarhythm.domain.repository

import com.yasinjahantigh.logarhythm.data.ai.AiProvider
import com.yasinjahantigh.logarhythm.data.ai.AiSettings
import com.yasinjahantigh.logarhythm.domain.model.AiFailure
import com.yasinjahantigh.logarhythm.domain.model.MagicPlaylistResult
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.flow.Flow

/** Optional bring-your-own-key AI. No call happens unless the user explicitly asks for one. */
interface AiPlaylistRepository {
    fun observeSettings(): Flow<AiSettings>

    /** A null [apiKey] keeps the stored key; a blank string is ignored for the same reason. */
    suspend fun saveSettings(provider: AiProvider, baseUrl: String, model: String, apiKey: String?)
    suspend fun clearKey()

    /** Returns null when the connection works, otherwise the failure to explain in the UI. */
    suspend fun testConnection(
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String?
    ): AiFailure?

    suspend fun generatePlaylist(prompt: String, trackCount: Int): MagicPlaylistResult

    /** Keeps [baseTracks] and asks for [additions] new songs from a locally prefiltered pool. */
    suspend fun generateMix(
        prompt: String,
        mixName: String,
        baseTracks: List<Track>,
        additions: Int
    ): MagicPlaylistResult

    suspend fun savePlaylist(name: String, trackIds: List<String>): Long
}
