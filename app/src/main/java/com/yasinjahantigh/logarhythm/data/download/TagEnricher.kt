package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEdit
import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEngines
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Writes resolved tags and cover art into the downloaded audio using the in-app tagger. */
@Singleton
class TagEnricher @Inject constructor() {

    suspend fun enrich(
        file: File,
        title: String?,
        artist: String?,
        album: String?,
        genre: String?,
        artwork: ByteArray? = null,
        year: String? = null,
        trackNumber: Int? = null,
        discNumber: Int? = null,
        composer: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val engine = TagEngines.detect(file) ?: return@runCatching false
            val current = engine.read(file)
            val edit = TagEdit(
                title = improvement(current.title, title),
                artist = improvement(current.artist, artist),
                album = improvement(current.album, album),
                genre = improvement(current.genre, genre),
                artwork = artwork,
                year = year?.let { improvement(current.year, it) },
                trackNumber = trackNumber?.takeIf { it > 0 && it != current.trackNumber },
                discNumber = discNumber?.takeIf { it > 0 && it != current.discNumber },
                composer = improvement(current.composer, composer)
            )
            if (edit.isEmpty) return@runCatching false

            val temp = File(file.parentFile, "${file.nameWithoutExtension}.tagged")
            try {
                engine.rewrite(file, temp, edit)
                engine.read(temp)
                if (!file.delete()) {
                    temp.delete()
                    return@runCatching false
                }
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
                true
            } catch (_: Exception) {
                temp.delete()
                false
            }
        }.getOrDefault(false)
    }

    private fun improvement(current: String?, candidate: String?): String? {
        val value = candidate?.trim()?.takeIf {
            it.isNotBlank() && !DownloadMetadataMerge.isPlaceholder(it)
        } ?: return null
        return if (value.equals(current?.trim(), ignoreCase = true)) null else value
    }
}
