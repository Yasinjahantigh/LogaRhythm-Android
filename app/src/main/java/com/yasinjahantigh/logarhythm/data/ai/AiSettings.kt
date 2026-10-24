package com.yasinjahantigh.logarhythm.data.ai

enum class AiProvider(val raw: String) {
    OPENAI_COMPATIBLE("openai"),
    GEMINI("gemini");

    companion object {
        fun fromRaw(value: String?): AiProvider =
            entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: OPENAI_COMPATIBLE
    }
}

/** User-owned AI configuration. The API key itself never lives in this object or in DataStore. */
data class AiSettings(
    val provider: AiProvider = AiProvider.OPENAI_COMPATIBLE,
    val baseUrl: String = DEFAULT_OPENAI_BASE,
    val model: String = DEFAULT_OPENAI_MODEL,
    val hasKey: Boolean = false
) {
    val isConfigured: Boolean
        get() = hasKey && baseUrl.startsWith("https://") && model.isNotBlank()

    companion object {
        const val DEFAULT_OPENAI_BASE = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        const val DEFAULT_GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

        fun defaultBase(provider: AiProvider): String = when (provider) {
            AiProvider.OPENAI_COMPATIBLE -> DEFAULT_OPENAI_BASE
            AiProvider.GEMINI -> DEFAULT_GEMINI_BASE
        }

        fun defaultModel(provider: AiProvider): String = when (provider) {
            AiProvider.OPENAI_COMPATIBLE -> DEFAULT_OPENAI_MODEL
            AiProvider.GEMINI -> DEFAULT_GEMINI_MODEL
        }
    }
}
