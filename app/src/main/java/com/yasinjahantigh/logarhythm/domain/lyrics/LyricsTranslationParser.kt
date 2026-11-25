package com.yasinjahantigh.logarhythm.domain.lyrics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure Kotlin helpers for free, keyless lyrics translation parsing, script detection,
 * and batch line alignment.
 */
object LyricsTranslationParser {

    fun parseGtxResponse(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("[")) return null
        return runCatching {
            val root = JSONArray(trimmed)
            val segments = root.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i) ?: continue
                val piece = seg.optString(0)
                if (piece.isNotEmpty() && piece != "null") {
                    sb.append(piece)
                }
            }
            sb.toString().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun parseMyMemoryResponse(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val root = JSONObject(trimmed)
            val status = root.optInt("responseStatus", 200)
            if (status != 200) return null
            val translated = root.optJSONObject("responseData")
                ?.optString("translatedText")
                ?.trim()
                .orEmpty()
            if (translated.isBlank() || translated.startsWith("INVALID LANGUAGE PAIR", ignoreCase = true)) {
                null
            } else {
                translated
            }
        }.getOrNull()
    }

    fun alignBatchTranslations(inputLines: List<String>, translatedBlob: String): List<String>? {
        if (inputLines.isEmpty()) return emptyList()
        val split = translatedBlob.split('\n').map { it.trim() }
        if (split.size == inputLines.size) return split
        return null
    }

    fun detectSourceLanguage(text: String, targetLang: String): String {
        var arabicPersian = 0
        var persianSpecific = 0
        var cyrillic = 0
        var hangul = 0
        var kana = 0
        var cjk = 0
        var latin = 0

        for (ch in text) {
            val code = ch.code
            when {
                ch in setOf('پ', 'چ', 'ژ', 'گ', 'ک', 'ی') -> {
                    persianSpecific++
                    arabicPersian++
                }
                code in 0x0600..0x06FF -> arabicPersian++
                code in 0x0400..0x04FF -> cyrillic++
                code in 0xAC00..0xD7AF -> hangul++
                code in 0x3040..0x30FF -> kana++
                code in 0x4E00..0x9FFF -> cjk++
                ch in 'A'..'Z' || ch in 'a'..'z' -> latin++
            }
        }

        val detected = when {
            persianSpecific > 0 -> "fa"
            arabicPersian > latin && arabicPersian > 2 -> if (targetLang == "fa") "ar" else "fa"
            cyrillic > latin && cyrillic > 2 -> "ru"
            hangul > 2 -> "ko"
            kana > 2 -> "ja"
            cjk > 2 -> "zh"
            else -> "en"
        }
        return if (detected == targetLang) {
            if (targetLang == "en") "fa" else "en"
        } else {
            detected
        }
    }
}
