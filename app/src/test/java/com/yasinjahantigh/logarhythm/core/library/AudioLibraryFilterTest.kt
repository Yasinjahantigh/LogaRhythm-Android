package com.yasinjahantigh.logarhythm.core.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLibraryFilterTest {

    @Test
    fun hidesStandardRecordingsDirectory() {
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Recordings/interview.mp3",
                "Recordings",
                "interview.mp3"
            )
        )
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Recordings/Sub/interview.mp3",
                "Sub",
                "interview.mp3"
            )
        )
    }

    @Test
    fun hidesRecorderAppDateNamesOutsideRecordingsFolder() {
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Download/Recoe/Apr 26, 5.46 PM.mp3",
                "Recoe",
                "Apr 26, 5.46 PM.mp3"
            )
        )
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/7/Aug 5, 8.44 AM.mp3",
                "7",
                "Aug 5, 8.44 AM.mp3"
            )
        )
    }

    @Test
    fun hidesCallRecorderAndVoiceMemoNames() {
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Download/phone_20260814-202518__989134145974.m4a",
                "Download",
                "phone_20260814-202518__989134145974.m4a"
            )
        )
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Download/ضبط‌های صوتی/Voice 260418_232601.m4a",
                "ضبط‌های صوتی",
                "Voice 260418_232601.m4a"
            )
        )
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Download/ویس آزمون.ogg",
                "Download",
                "ویس آزمون.ogg"
            )
        )
    }

    @Test
    fun hidesNumericTelegramVoiceMessages() {
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Download/-8250114057068863742_108982494889725.oga",
                "Download",
                "-8250114057068863742_108982494889725.oga"
            )
        )
    }

    @Test
    fun hidesRecorderFoldersButKeepsMixedMusicFolder() {
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Download/ضبط‌های صوتی/take 1.m4a",
                "ضبط‌های صوتی",
                "take 1.m4a"
            )
        )
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/a.opus",
                "WhatsApp Voice Notes",
                "a.opus"
            )
        )
        assertFalse(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Download/موزیک‌ها و ضبط صدا/Ookh - Jafar.the.mp3",
                "موزیک‌ها و ضبط صدا",
                "Ookh - Jafar.the.mp3"
            )
        )
    }

    @Test
    fun hidesSystemSoundFolders() {
        assertTrue(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Ringtones/marimba.mp3",
                "Ringtones",
                "marimba.mp3"
            )
        )
    }

    @Test
    fun keepsOrdinaryMusicTitles() {
        assertFalse(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Music/Telegram/Mese Sam.mp3",
                "Telegram",
                "Mese Sam.mp3"
            )
        )
        assertFalse(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Music/Telegram/Voice of the Soul.mp3",
                "Telegram",
                "Voice of the Soul.mp3"
            )
        )
        assertFalse(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Music/Telegram/Call Me Maybe.mp3",
                "Telegram",
                "Call Me Maybe.mp3"
            )
        )
        assertFalse(
            AudioLibraryFilter.isHidden(
                "/storage/emulated/0/Music/Record Player.mp3",
                "Music",
                "Record Player.mp3"
            )
        )
    }
}
