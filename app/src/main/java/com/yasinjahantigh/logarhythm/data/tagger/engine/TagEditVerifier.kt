package com.yasinjahantigh.logarhythm.data.tagger.engine

import java.security.MessageDigest

/** Read-back check shared by the MediaStore writer and the download enricher. */
object TagEditVerifier {

    fun matches(edit: TagEdit, actual: AudioTags): Boolean {
        fun ok(wanted: String?, got: String?, lyrics: Boolean = false): Boolean {
            if (wanted == null) return true
            if (wanted.isBlank()) return got.isNullOrBlank()
            fun norm(s: String) = if (lyrics) s.replace("\r\n", "\n").trim() else s.trim()
            return got != null && norm(got) == norm(wanted)
        }
        fun okInt(wanted: Int?, got: Int?): Boolean = when {
            wanted == null -> true
            wanted <= 0 -> got == null || got <= 0
            else -> got == wanted
        }
        val artworkOk = when {
            edit.artwork == null -> true
            edit.artwork.isEmpty() -> actual.artwork == null || actual.artwork.isEmpty()
            actual.artwork == null -> false
            else -> MessageDigest.isEqual(sha256(edit.artwork), sha256(actual.artwork))
        }
        return ok(edit.title, actual.title) && ok(edit.artist, actual.artist) &&
            ok(edit.album, actual.album) && ok(edit.albumArtist, actual.albumArtist) &&
            ok(edit.genre, actual.genre) && ok(edit.lyrics, actual.lyrics, lyrics = true) &&
            ok(edit.year, actual.year) && okInt(edit.trackNumber, actual.trackNumber) &&
            okInt(edit.discNumber, actual.discNumber) && ok(edit.composer, actual.composer) &&
            artworkOk
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
