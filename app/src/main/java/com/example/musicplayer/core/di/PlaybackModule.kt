package com.example.musicplayer.core.di

import com.example.musicplayer.playback.PlaybackController
import com.example.musicplayer.playback.PlayerController
import com.example.musicplayer.playback.PlayerControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindPlayerController(
        impl: PlayerControllerImpl
    ): PlayerController

    @Binds
    @Singleton
    abstract fun bindPlaybackController(
        impl: PlayerControllerImpl
    ): PlaybackController
}
