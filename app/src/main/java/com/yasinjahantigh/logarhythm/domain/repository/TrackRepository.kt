package com.yasinjahantigh.logarhythm.domain.repository

import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Artist
import com.yasinjahantigh.logarhythm.domain.model.ArtistProfile
import com.yasinjahantigh.logarhythm.domain.model.Folder
import com.yasinjahantigh.logarhythm.domain.model.Genre
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackEdit
import com.yasinjahantigh.logarhythm.domain.model.TrackEditResult
import com.yasinjahantigh.logarhythm.domain.model.TrackPlayStat
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getAllTracks(): Flow<List<Track>>
    fun getTrackById(id: String): Flow<Track?>
    fun searchTracks(query: String): Flow<List<Track>>
    fun searchAlbums(query: String): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>
    fun searchGenres(query: String): Flow<List<Genre>>
    fun getAlbums(): Flow<List<Album>>
    fun getArtists(): Flow<List<Artist>>
    fun getFolders(): Flow<List<Folder>>
    fun getGenres(): Flow<List<Genre>>
    fun getTracksByAlbum(album: String): Flow<List<Track>>
    fun getTracksByArtist(artist: String): Flow<List<Track>>
    fun getTracksByFolder(folder: String): Flow<List<Track>>
    fun getTracksByGenre(genre: String): Flow<List<Track>>
    fun getAlbumByName(album: String): Flow<Album?>
    fun getArtistByName(artist: String): Flow<Artist?>
    fun getArtistProfile(artist: String): Flow<ArtistProfile?>
    fun getGenreByName(genre: String): Flow<Genre?>
    /** Finished plays keyed by track id; drives play-count sorting, top tracks and smart mixes. */
    fun getPlayStats(): Flow<Map<String, TrackPlayStat>>
    fun getFavoriteTrackIds(): Flow<Set<String>>
    fun getDownloadedTrackUris(): Flow<Set<String>>
    suspend fun refreshTracks()
    suspend fun updateTrackMetadata(id: String, title: String, artist: String, album: String, genre: String): Track?
    /** Full tag/cover edit; writes the file, verifies it, mirrors MediaStore and updates Room. */
    suspend fun updateTrackDetails(id: String, edit: TrackEdit): TrackEditResult
    suspend fun deleteTrack(track: Track): Boolean
    fun getHiddenTracks(): Flow<List<Track>>
    fun getAllDeviceFolderNames(): Flow<List<String>>
    suspend fun hideTrack(trackId: String)
    suspend fun unhideTrack(trackId: String)
    suspend fun unhideAllTracks()
    suspend fun excludeFolder(folder: String)
}
