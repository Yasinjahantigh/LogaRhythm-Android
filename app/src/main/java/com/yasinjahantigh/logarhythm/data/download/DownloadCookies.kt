package com.yasinjahantigh.logarhythm.data.download

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** User-imported Netscape cookies for age-restricted or sign-in-only YouTube videos. */
@Singleton
class DownloadCookies @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val file = File(context.noBackupFilesDir, "youtube-cookies.txt")
    private val _imported = MutableStateFlow(file.isFile && file.length() > 0)
    val imported: StateFlow<Boolean> = _imported.asStateFlow()

    fun path(): String? = file.takeIf { it.isFile && it.length() > 0 }?.absolutePath

    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return@use null
                    output.write(buffer, 0, read)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
        }.getOrNull() ?: return@withContext false
        if (!looksLikeNetscapeCookies(text)) return@withContext false

        val temp = File(context.noBackupFilesDir, "youtube-cookies.tmp")
        val saved = runCatching {
            temp.writeText(text)
            if (file.exists() && !file.delete()) return@runCatching false
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            true
        }.getOrDefault(false)
        if (!saved) {
            temp.delete()
            return@withContext false
        }
        _imported.value = true
        true
    }

    fun clear() {
        file.delete()
        _imported.value = false
    }

    private fun looksLikeNetscapeCookies(text: String): Boolean =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .any { line ->
                val fields = line.split('\t')
                fields.size >= 6 && fields.first().contains("youtube.com", ignoreCase = true)
            }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
