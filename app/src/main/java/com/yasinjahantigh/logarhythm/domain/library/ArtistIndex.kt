package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Artist
import com.yasinjahantigh.logarhythm.domain.model.ArtistProfile
import com.yasinjahantigh.logarhythm.domain.model.Track

/**
 * Computed artist model for one library snapshot. Splitting never rewrites files or needs a Room
 * join table: the index is rebuilt whenever tracks or the separator settings change, so changing
 * a separator immediately updates every artist page.
 */
class ArtistIndex private constructor(
    private val config: ArtistSplitConfig,
    val artists: List<Artist>,
    private val tracksByArtist: Map<String, List<Track>>,
    private val albumsByArtist: Map<String, List<Album>>,
    private val appearsOnByArtist: Map<String, List<Track>>
) {
    fun namesOf(track: Track): List<String> = config.namesOf(track.artist)

    fun tracksOf(name: String): List<Track> = tracksByArtist[name.lowercase()].orEmpty()

    fun albumsOf(name: String): List<Album> = albumsByArtist[name.lowercase()].orEmpty()

    fun appearsOn(name: String): List<Track> = appearsOnByArtist[name.lowercase()].orEmpty()

    fun artistOf(name: String): Artist? = artists.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun profileOf(name: String): ArtistProfile? {
        val key = name.lowercase()
        val artist = artists.firstOrNull { it.name.lowercase() == key } ?: return null
        return ArtistProfile(
            artist = artist,
            tracks = tracksByArtist[key].orEmpty(),
            albums = albumsByArtist[key].orEmpty(),
            appearsOn = appearsOnByArtist[key].orEmpty()
        )
    }

    companion object {
        private val TEXT = String.CASE_INSENSITIVE_ORDER

        fun build(tracks: List<Track>, config: ArtistSplitConfig): ArtistIndex {
            class AlbumAcc(
                val name: String,
                val artist: String,
                var artworkUri: String?,
                var year: Int
            ) {
                var trackCount: Int = 0
            }

            data class ArtistAcc(
                val name: String,
                val trackIds: LinkedHashMap<String, Track> = LinkedHashMap(),
                val albums: LinkedHashMap<String, AlbumAcc> = LinkedHashMap(),
                val appearsOn: LinkedHashMap<String, Track> = LinkedHashMap()
            )

            val albumAccs = LinkedHashMap<String, AlbumAcc>()
            for (track in tracks) {
                val albumArtist = track.albumArtist.ifBlank { track.artist }
                val key = "${track.album.lowercase()}|${albumArtist.lowercase()}"
                val acc = albumAccs.getOrPut(key) {
                    AlbumAcc(track.album, albumArtist, track.artworkUri, track.year)
                }
                acc.trackCount++
                if (acc.artworkUri.isNullOrBlank() && !track.artworkUri.isNullOrBlank()) {
                    acc.artworkUri = track.artworkUri
                }
                if (track.year > acc.year) acc.year = track.year
            }

            val artistAccs = LinkedHashMap<String, ArtistAcc>()
            for (track in tracks) {
                val names = config.namesOf(track.artist)
                val albumArtist = track.albumArtist.ifBlank { track.artist }
                val albumKey = "${track.album.lowercase()}|${albumArtist.lowercase()}"
                names.forEachIndexed { index, name ->
                    val acc = artistAccs.getOrPut(name.lowercase()) { ArtistAcc(name) }
                    acc.trackIds.putIfAbsent(track.id, track)
                    albumAccs[albumKey]?.let { acc.albums.putIfAbsent(albumKey, it) }
                    if (index > 0) acc.appearsOn.putIfAbsent(track.id, track)
                }
            }

            val artists = artistAccs.values
                .sortedWith(compareBy(TEXT) { it.name })
                .map {
                    Artist(
                        id = "artist:${it.name}",
                        name = it.name,
                        trackCount = it.trackIds.size,
                        albumCount = it.albums.size
                    )
                }

            return ArtistIndex(
                config = config,
                artists = artists,
                tracksByArtist = artistAccs.mapValues { (_, acc) ->
                    acc.trackIds.values.sortedWith(compareBy(TEXT) { it.title })
                },
                albumsByArtist = artistAccs.mapValues { (_, acc) ->
                    acc.albums.values
                        .map {
                            Album(
                                id = "album:${it.name}:${it.artist}",
                                name = it.name,
                                artist = it.artist,
                                trackCount = it.trackCount,
                                artworkUri = it.artworkUri,
                                year = it.year
                            )
                        }
                        .sortedWith(compareBy(TEXT) { it.name })
                },
                appearsOnByArtist = artistAccs.mapValues { (_, acc) ->
                    acc.appearsOn.values.sortedWith(compareBy(TEXT) { it.title })
                }
            )
        }
    }
}
