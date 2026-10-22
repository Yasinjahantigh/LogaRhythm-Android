package com.yasinjahantigh.logarhythm.core.library

/**
 * Recognises system sounds and recorder output that phones mark as music.
 * MediaStore's IS_RECORDING flag (Android 12+) and the standard folders cover most devices;
 * recorder apps that write `is_music=1` outside those folders are caught by their file names.
 */
object AudioLibraryFilter {

    private val standardSystemFolders = listOf(
        "/ringtones/", "/notifications/", "/alarms/",
        "/recordings/", "/voice recorder/", "/call recordings/"
    )

    private val systemFolders = setOf(
        "ringtones", "notifications", "alarms", "ui", "sounds"
    )

    private val recordingFolderPrefix = Regex(
        """(?i)^(record|voice|call|sound[ _-]?record|ضبط|ویس)"""
    )

    /** "WhatsApp Voice Notes" and similar recordings sit inside a messenger folder. */
    private val recordingFolderTokens = listOf(
        "recordings", "voice notes", "voice memo", "voice rec"
    )

    private val recordingFilePrefix = Regex(
        """(?i)^(voice[ _-]?\d{4,}|voice[ _-]?(note|memo|rec)|""" +
            """recording[ _-]?\d|record[ _-]?\d|phone[ _-]|""" +
            """(aud|rec|call)[ _-]?\d{4,}|ویس|ضبط)"""
    )

    private val monthDateTimeName = Regex(
        """(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+""" +
            """\d{1,2},\s+\d{1,2}[.:]\d{2}\s*(am|pm)?"""
    )

    private val isoDateTimeName = Regex(
        """^\d{4}[-_]\d{2}[-_]\d{2}[ _T-]\d{2}[-_.:]\d{2}"""
    )

    /** Telegram voice messages are `.oga` files with a numeric message id. */
    private val numericVoiceMessage = Regex("""^-?\d{10,}(_\d+)?\.oga$""")

    fun isHidden(filePath: String?, folderName: String?, fileName: String?): Boolean {
        val path = filePath.orEmpty().replace('\\', '/').lowercase()
        if (standardSystemFolders.any(path::contains)) return true

        val folder = folderName?.trim()?.lowercase().orEmpty()
        if (folder in systemFolders) return true
        if (recordingFolderPrefix.containsMatchIn(folder)) return true
        if (recordingFolderTokens.any(folder::contains)) return true

        val file = fileName?.trim()?.lowercase().orEmpty()
        if (file.isBlank()) return false
        return recordingFilePrefix.containsMatchIn(file) ||
            monthDateTimeName.containsMatchIn(file) ||
            isoDateTimeName.containsMatchIn(file) ||
            numericVoiceMessage.matches(file)
    }
}
