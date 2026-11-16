package com.yasinjahantigh.logarhythm.core.library

/**
 * Recognises system sounds, messenger voice notes, call recordings, and suspicious non-music
 * clips that phones often store alongside user audio.
 * MediaStore's IS_RECORDING flag (Android 12+) and the standard folders cover most devices;
 * recorder apps and messengers that write `is_music=1` or slip past duration thresholds are
 * caught by path, extension, metadata, and file-name heuristics.
 */
object AudioLibraryFilter {

    private val standardSystemFolders = listOf(
        "/ringtones/", "/notifications/", "/alarms/",
        "/recordings/", "/voice recorder/", "/call recordings/",
        "/callrecordings/", "/soundrecorder/", "/sound_recorder/",
        "/miui/sound_recorder/", "/samsung/voice recorder/",
        "/acrcalls/", "/cubecallrecorder/", "/automatic call recorder/",
        "/whatsapp voice notes/", "/whatsapp/media/whatsapp voice notes/",
        "/whatsapp audio/sent/", "/whatsapp audio/private/",
        "/telegram/telegram voice/", "/telegram voice/",
        "/system/media/audio/", "/product/media/audio/", "/vendor/media/audio/"
    )

    private val systemFolders = setOf(
        "ringtones", "notifications", "alarms", "ui", "sounds",
        "call", "calls", "callrecordings", "soundrecorder", "sound_recorder",
        "whatsapp voice notes", "telegram voice", "acrcalls", "cubecallrecorder"
    )

    private val recordingFolderPrefix = Regex(
        """(?i)^(record|voice|call|sound[ _-]?record|ضبط|ویس)"""
    )

    /** "WhatsApp Voice Notes" and similar recordings sit inside a messenger folder. */
    private val recordingFolderTokens = listOf(
        "recordings", "voice notes", "voice memo", "voice rec", "call rec"
    )

    private val recordingFilePrefix = Regex(
        """(?i)^(voice[ _-]?\d{4,}|voice[ _-]?(note|memo|rec|message)|""" +
            """recording[ _-]?\d|record[ _-]?\d|phone[ _-]|""" +
            """(aud|rec|call)[ _-]?\d{4,}|""" +
            """ptt-\d{8}-wa\d+|aud-\d{8}-wa\d+|""" +
            """callrec|call_rec|acr_|vn_\d+|voicenote|voice_message|audioclip|""" +
            """ringtone[_-]\d+|notif[_-]\d+|ویس|ضبط)"""
    )

    private val monthDateTimeName = Regex(
        """(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+""" +
            """\d{1,2},\s+\d{1,2}[.:]\d{2}\s*(am|pm)?"""
    )

    private val isoDateTimeName = Regex(
        """^\d{4}[-_]\d{2}[-_]\d{2}[ _T-]\d{2}[-_.:]\d{2}"""
    )

    /** Telegram voice messages are `.oga` / `.ogg` files with a numeric message id. */
    private val numericVoiceMessage = Regex("""^-?\d{8,}(_\d+)?\.(oga|ogg|opus)$""")

    private val voiceOnlyExtensions = setOf("amr", "awb", "3gp", "3gpp", "qcp")

    private val voiceNoteExtensions = setOf("oga", "ogg", "opus", "m4a", "aac", "wav")

    fun isHidden(
        filePath: String?,
        folderName: String?,
        fileName: String?,
        isMusic: Boolean? = null,
        durationMs: Long? = null,
        hasArtistOrAlbum: Boolean = true
    ): Boolean {
        val path = filePath.orEmpty().replace('\\', '/').lowercase()
        if (standardSystemFolders.any(path::contains)) return true

        val folder = folderName?.trim()?.lowercase().orEmpty()
        if (folder in systemFolders) return true
        if (recordingFolderPrefix.containsMatchIn(folder)) return true
        if (recordingFolderTokens.any(folder::contains)) return true

        val file = fileName?.trim()?.lowercase().orEmpty()
        if (file.isNotBlank()) {
            val ext = file.substringAfterLast('.', "")
            if (ext in voiceOnlyExtensions) return true
            if (recordingFilePrefix.containsMatchIn(file) ||
                monthDateTimeName.containsMatchIn(file) ||
                isoDateTimeName.containsMatchIn(file) ||
                numericVoiceMessage.matches(file)
            ) {
                return true
            }

            if (isMusic == false && !hasArtistOrAlbum) {
                val dur = durationMs ?: Long.MAX_VALUE
                if (dur < 60_000L) return true
                if (ext in voiceNoteExtensions && dur < 120_000L) return true
            }
        } else if (isMusic == false && !hasArtistOrAlbum && (durationMs ?: Long.MAX_VALUE) < 60_000L) {
            return true
        }

        return false
    }
}
