package com.yasinjahantigh.logarhythm.core.di

import com.yasinjahantigh.logarhythm.data.repository.DownloadRepositoryImpl
import com.yasinjahantigh.logarhythm.data.repository.LyricsRepositoryImpl
import com.yasinjahantigh.logarhythm.data.repository.PlaylistRepositoryImpl
import com.yasinjahantigh.logarhythm.data.repository.StatsRepositoryImpl
import com.yasinjahantigh.logarhythm.data.repository.TrackRepositoryImpl
import com.yasinjahantigh.logarhythm.data.sources.LocalMusicSource
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import com.yasinjahantigh.logarhythm.domain.repository.LyricsRepository
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.domain.source.MusicSource
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

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository
}
