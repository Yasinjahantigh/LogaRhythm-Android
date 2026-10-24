package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEdit
import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEditVerifier
import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEngines
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class TagEnrichStatus {
    TAGGED,
    NO_CHANGES,
    UNSUPPORTED,
    FAILED
}

data class TagEnrichOutcome(
    val status: TagEnrichStatus,
    val artworkWritten: Boolean = false
)

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
    ): TagEnrichOutcome = withContext(Dispatchers.IO) {
        try {
            val engine = TagEngines.detect(file)
                ?: return@withContext TagEnrichOutcome(TagEnrichStatus.UNSUPPORTED)
            val current = engine.read(file)
            val edit = TagEdit(
                title = improvement(current.title, title),
                artist = improvement(current.artist, artist),
                album = improvement(current.album, album),
                genre = improvement(current.genre, genre),
                artwork = artwork?.takeIf { it.isNotEmpty() },
                artworkMime = artwork?.takeIf { it.isNotEmpty() }?.let { "image/jpeg" },
                year = year?.let { improvement(current.year, it) },
                trackNumber = trackNumber?.takeIf { it > 0 && it != current.trackNumber },
                discNumber = discNumber?.takeIf { it > 0 && it != current.discNumber },
                composer = improvement(current.composer, composer)
            )
            if (edit.isEmpty) {
                return@withContext TagEnrichOutcome(TagEnrichStatus.NO_CHANGES)
            }

            val temp = File(file.parentFile, "${file.nameWithoutExtension}.tagged")
            try {
                engine.rewrite(file, temp, edit)
                val written = engine.read(temp)
                if (!TagEditVerifier.matches(edit, written)) {
                    temp.delete()
                    return@withContext TagEnrichOutcome(TagEnrichStatus.FAILED)
                }
                if (!file.delete()) {
                    temp.delete()
                    return@withContext TagEnrichOutcome(TagEnrichStatus.FAILED)
                }
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
                TagEnrichOutcome(
                    status = TagEnrichStatus.TAGGED,
                    artworkWritten = edit.artwork != null
                )
            } catch (cancelled: CancellationException) {
                temp.delete()
                throw cancelled
            } catch (_: Exception) {
                temp.delete()
                TagEnrichOutcome(TagEnrichStatus.FAILED)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TagEnrichOutcome(TagEnrichStatus.FAILED)
        }
    }

    private fun improvement(current: String?, candidate: String?): String? {
        val value = candidate?.trim()?.takeIf {
            it.isNotBlank() && !DownloadMetadataMerge.isPlaceholder(it)
        } ?: return null
        return if (value.equals(current?.trim(), ignoreCase = true)) null else value
    }
}
