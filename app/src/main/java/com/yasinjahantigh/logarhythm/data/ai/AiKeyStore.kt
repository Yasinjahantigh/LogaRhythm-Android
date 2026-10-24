package com.yasinjahantigh.logarhythm.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's own API key encrypted with a hardware-backed Android Keystore key.
 * Only the ciphertext touches SharedPreferences; the key never goes to DataStore, logs or backups.
 */
@Singleton
class AiKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasKey(): Boolean = !prefs.getString(KEY_CIPHERTEXT, null).isNullOrBlank()

    fun save(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            SEPARATOR + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(KEY_CIPHERTEXT, payload).apply()
    }

    fun load(): String? {
        val payload = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val parts = payload.split(SEPARATOR)
        if (parts.size != 2) {
            clear()
            return null
        }
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            // A wiped Keystore (factory reset, backup restore) makes old ciphertext unreadable.
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).apply()
    }

    private fun secretKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey? = try {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val PREFS_NAME = "ai_secrets"
        const val KEY_CIPHERTEXT = "api_key_ciphertext"
        const val KEY_ALIAS = "logarhythm_ai_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = ":"
    }
}
