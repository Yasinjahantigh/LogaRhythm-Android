package com.example.musicplayer.data.sources

import org.json.JSONObject

/** Strict JSON text-field reader: JSON null and the text "null" are treated as absent. */
internal object LyricsResponseFields {
    fun text(json: JSONObject, key: String): String {
        if (json.isNull(key)) return ""
        val value = json.opt(key) ?: return ""
        if (value == JSONObject.NULL) return ""
        return value.toString().trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    }
}
