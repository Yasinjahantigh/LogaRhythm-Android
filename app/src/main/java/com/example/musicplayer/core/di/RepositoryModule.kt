package com.example.musicplayer.core.di

import com.example.musicplayer.data.repository.LyricsRepositoryImpl
import com.example.musicplayer.data.repository.PlaylistRepositoryImpl
import com.example.musicplayer.data.repository.StatsRepositoryImpl
import com.example.musicplayer.data.repository.TrackRepositoryImpl
import com.example.musicplayer.data.sources.LocalMusicSource
import com.example.musicplayer.domain.repository.LyricsRepository
import com.example.musicplayer.domain.repository.PlaylistRepository
import com.example.musicplayer.domain.repository.StatsRepository
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.domain.source.MusicSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicSource(
        impl: LocalMusicSource
    ): MusicSource

    @Binds
    @Singleton
    abstract fun bindTrackRepository(
        impl: TrackRepositoryImpl
    ): TrackRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(
        impl: StatsRepositoryImpl
    ): StatsRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(
        impl: PlaylistRepositoryImpl
    ): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(
        impl: LyricsRepositoryImpl
    ): LyricsRepository
}
