package com.yasinjahantigh.logarhythm.data.tagger

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yasinjahantigh.logarhythm.data.download.Mp4Remuxer
import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEdit
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFileTagWriterTest {
    private fun box(type: String, body: ByteArray = byteArrayOf()): ByteArray {
        val n = body.size + 8
        return byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()) + type.toByteArray() + body
    }
    @Test fun failedFragmentRemuxNeverOpensOriginalForWriting() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://media/external/audio/media/42")
        val bytes = box("ftyp", "M4A ".toByteArray() + ByteArray(8)) + box("moov") + box("moof") + box("mdat", ByteArray(64))
        val output = ByteArrayOutputStream().apply { write(bytes) }
        shadowOf(context.contentResolver).registerInputStream(uri, bytes.inputStream())
        shadowOf(context.contentResolver).registerOutputStream(uri, output)
        val track = Track("local:42", "Old", "A", "B", 0, null, SourceType.LOCAL, uri.toString())
        val result = AudioFileTagWriter(context, Mp4Remuxer()).applyEdit(track, TagEdit(title = "New"))
        assertFalse(result.isSuccess)
        assertArrayEquals(bytes, output.toByteArray())
    }
}
