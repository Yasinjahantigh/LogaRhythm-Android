package com.example.musicplayer.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var musicDao: MusicDao
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        musicDao = database.musicDao()
        playlistDao = database.playlistDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun createPlaylistAndAddTracks_returnsTracksInOrder() = runBlocking {
        val track1 = TrackEntity(
            id = "local:1",
            title = "First Song",
            artist = "Artist",
            album = "Album",
            durationMs = 180000,
            artworkUri = null,
            sourceType = SourceType.LOCAL.prefix,
            uri = "content://media/1"
        )
        val track2 = TrackEntity(
            id = "local:2",
            title = "Second Song",
            artist = "Artist",
            album = "Album",
            durationMs = 200000,
            artworkUri = null,
            sourceType = SourceType.LOCAL.prefix,
            uri = "content://media/2"
        )
        musicDao.insertTracks(listOf(track1, track2))

        val playlistId = playlistDao.insertPlaylist(PlaylistEntity(name = "My Chill Mix"))
        playlistDao.insertPlaylistTrack(PlaylistTrackEntity(playlistId = playlistId, trackId = "local:1", orderIndex = 0))
        playlistDao.insertPlaylistTrack(PlaylistTrackEntity(playlistId = playlistId, trackId = "local:2", orderIndex = 1))

        val tracks = playlistDao.getPlaylistTracks(playlistId).first()
        assertEquals(2, tracks.size)
        assertEquals("First Song", tracks[0].title)
        assertEquals("Second Song", tracks[1].title)

        // Reorder
        playlistDao.reorderPlaylist(playlistId, 0, 1)
        val reordered = playlistDao.getPlaylistTracks(playlistId).first()
        assertEquals("Second Song", reordered[0].title)
        assertEquals("First Song", reordered[1].title)
    }

    @Test
    fun favoritesPlaylist_checksFavoriteState() = runBlocking {
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = Playlist.FAVORITES_ID,
                name = Playlist.FAVORITES_NAME,
                isBuiltIn = true
            )
        )
        val isFavBefore = playlistDao.isTrackInPlaylist(Playlist.FAVORITES_ID, "local:99").first()
        assertEquals(false, isFavBefore)

        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = Playlist.FAVORITES_ID,
                trackId = "local:99",
                orderIndex = 0
            )
        )
        val isFavAfter = playlistDao.isTrackInPlaylist(Playlist.FAVORITES_ID, "local:99").first()
        assertTrue(isFavAfter)
    }
}
