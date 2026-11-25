package com.yasinjahantigh.logarhythm.data.download

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-imported Netscape cookies for age-restricted or sign-in-only YouTube videos.
 * Stored encrypted at rest via Android KeyStore AES-256-GCM and exposed to yt-dlp
 * only inside a short-lived 0600 temp file that is wiped and deleted immediately after use.
 */
@Singleton
class DownloadCookies @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ioMutex = Mutex()
    private val encryptedFile = File(context.noBackupFilesDir, "youtube-cookies.enc")
    private val legacyPlainFile = File(context.noBackupFilesDir, "youtube-cookies.txt")
    private val tempDir = File(context.noBackupFilesDir, "cookie_tmp")

    private val _imported = MutableStateFlow(hasStoredCookies())
    val imported: StateFlow<Boolean> = _imported.asStateFlow()

    private fun hasStoredCookies(): Boolean =
        (encryptedFile.isFile && encryptedFile.length() > GCM_IV_BYTES) ||
            (legacyPlainFile.isFile && legacyPlainFile.length() > 0)

    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
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
        importRawText(text)
    }

    internal suspend fun importRawText(text: String): Boolean = withContext(Dispatchers.IO) {
        if (!looksLikeNetscapeCookies(text)) return@withContext false
        ioMutex.withLock {
            val saved = encryptToStorage(text.toByteArray(Charsets.UTF_8))
            if (saved) {
                secureDelete(legacyPlainFile)
                _imported.value = true
            }
            saved
        }
    }

    /**
     * Decrypts cookies into an owner-only (0600) temporary file for [block] and wipes it in finally.
     * Passes `null` when no cookies are imported.
     */
    suspend fun <T> withTempCookieFile(block: suspend (String?) -> T): T {
        val plainBytes = withContext(Dispatchers.IO) {
            ioMutex.withLock {
                migrateLegacyLocked()
                decryptFromStorage()
            }
        }
        if (plainBytes == null || plainBytes.isEmpty()) {
            return block(null)
        }
        val tempFile = withContext(Dispatchers.IO) {
            tempDir.mkdirs()
            tempDir.setExecutable(true, true)
            File.createTempFile("ck_", ".txt", tempDir).apply {
                setReadable(false, false)
                setWritable(false, false)
                setExecutable(false, false)
                setReadable(true, true)
                setWritable(true, true)
                outputStream().use { it.write(plainBytes) }
            }
        }
        Arrays.fill(plainBytes, 0.toByte())
        return try {
            block(tempFile.absolutePath)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                secureDelete(tempFile)
            }
        }
    }

    fun clear() {
        secureDelete(encryptedFile)
        secureDelete(legacyPlainFile)
        tempDir.listFiles()?.forEach { secureDelete(it) }
        _imported.value = false
    }

    private fun migrateLegacyLocked() {
        if (!legacyPlainFile.isFile || legacyPlainFile.length() == 0L) return
        val bytes = runCatching { legacyPlainFile.readBytes() }.getOrNull()
        if (bytes != null && bytes.isNotEmpty() && !encryptedFile.isFile) {
            encryptToStorage(bytes)
        }
        if (bytes != null) Arrays.fill(bytes, 0.toByte())
        secureDelete(legacyPlainFile)
    }

    private fun encryptToStorage(plainBytes: ByteArray): Boolean {
        val encoded = runCatching {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plainBytes)
            ByteArray(1 + iv.size + ciphertext.size).apply {
                this[0] = iv.size.toByte()
                System.arraycopy(iv, 0, this, 1, iv.size)
                System.arraycopy(ciphertext, 0, this, 1 + iv.size, ciphertext.size)
            }
        }.getOrNull() ?: return false

        val tmp = File(context.noBackupFilesDir, "youtube-cookies.enc.tmp")
        val ok = runCatching {
            tmp.writeBytes(encoded)
            tmp.setReadable(false, false)
            tmp.setWritable(false, false)
            tmp.setReadable(true, true)
            tmp.setWritable(true, true)
            if (encryptedFile.exists() && !encryptedFile.delete()) return@runCatching false
            if (!tmp.renameTo(encryptedFile)) {
                encryptedFile.writeBytes(encoded)
                secureDelete(tmp)
            }
            true
        }.getOrDefault(false)
        if (!ok) secureDelete(tmp)
        return ok
    }

    private fun decryptFromStorage(): ByteArray? {
        if (!encryptedFile.isFile || encryptedFile.length() <= GCM_IV_BYTES) return null
        return runCatching {
            val payload = encryptedFile.readBytes()
            val ivLen = payload[0].toInt() and 0xFF
            if (ivLen != GCM_IV_BYTES || payload.size <= 1 + ivLen) return@runCatching null
            val iv = payload.copyOfRange(1, 1 + ivLen)
            val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun secureDelete(target: File) {
        if (!target.exists()) return
        runCatching {
            if (target.isFile && target.length() > 0) {
                val len = target.length().coerceAtMost(MAX_BYTES.toLong()).toInt()
                target.outputStream().use { out ->
                    out.write(ByteArray(len))
                    out.flush()
                }
            }
        }
        target.delete()
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
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "logarhythm_youtube_cookies_aes256"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
