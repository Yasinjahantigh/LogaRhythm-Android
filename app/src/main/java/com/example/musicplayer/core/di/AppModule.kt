package com.example.musicplayer.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.musicplayer.data.room.LyricsDao
import com.example.musicplayer.data.room.MusicDao
import com.example.musicplayer.data.room.MusicDatabase
import com.example.musicplayer.data.room.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "music_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(
        @ApplicationContext context: Context
    ): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "music_player.db"
        )
            .addMigrations(MusicDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMusicDao(database: MusicDatabase): MusicDao {
        return database.musicDao()
    }

    @Provides
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideLyricsDao(database: MusicDatabase): LyricsDao {
        return database.lyricsDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore
}
