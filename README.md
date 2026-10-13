# LogaRhythm

LogaRhythm is an offline-first Android music player built with Kotlin, Jetpack Compose, Material 3, Media3, Room, and DataStore. It is designed around a calm, Pixel-inspired interface while keeping playback and personal library data on-device.

## Product capabilities

- Local audio library with song, album, artist, folder, genre and playlist browsing from a single Library screen (category rail with live counts), with search one tap away from Home and Library
- Background playback through Media3, with a persistent mini player, full player, queue, and home-screen widget
- Playlists, favorites, sorting, search history, and listening statistics
- Synced lyrics from local files and optional online sources, plus manual editing and `.lrc` export
- A taste engine that learns from how you actually listen: completed plays, skips, songs heard together, and the hours you play them. It powers a time-of-day “Right now” queue, “On repeat”, “Because you played…”, smarter radio, and daily mixes that stop resurfacing songs you keep skipping
- Honest listening history: plays log real listened time, and skips are recorded separately so they never inflate play counts
- Offline mood mixes, three distinct daily-mix intents, and a one-tap “radio from this song” queue built from lightweight metadata similarity, now re-ranked by your own history
- One-tap shuffle across the entire library, time-boxed 15/30/60-minute listening sessions, and a true Recently Added row driven by MediaStore's added timestamp
- Safe library refreshes: a failed scan keeps the last successful index instead of treating the error as an empty library
- Equalizer, bass boost, virtualizer, playback speed, and sleep timer where supported by the device
- English and Persian UI with an in-app language override, explicit RTL/LTR direction, localized search metadata, resilient artwork fallbacks, theme preferences, and a signature “Ink & Ember” palette with optional wallpaper and album-art color

All personalization is computed on-device from your own play history; nothing is uploaded. Mood suggestions are intentionally lightweight and private. They infer approximate energy, mood, acoustic character, and similarity from local metadata; they do not perform signal-level audio analysis or send your listening library to a recommendation service. Radio deliberately replaces the current queue, keeps the currently playing song and seek position where possible, then sequences relevant tracks with artist/album diversity.

## What is new in 1.3

- **Playback**: fade between songs, ReplayGain (read from the file's own tags), skip silence, resume after the headphones you just unplugged come back, per-file position memory for recordings over 20 minutes, a gentle fade at the end of the sleep timer, short buffers and a CPU wake lock for screen-off playback.
- **Lyrics**: per-song timing offset, word-by-word highlight for enhanced LRC, a warning when synced lyrics cannot fit the song's length, and a Hidden lyrics list in Settings (plus a Restore button on the song) so wrong lyrics never trap you.
- **Library**: split multi-artist tags with editable separators, album-artist grouping, duplicate finder, missing-tags view, M3U import/export, one-file backup and restore of playlists, favourites, listening history and edited lyrics.
- **Speed and size**: indexed Room queries, scans skipped when Android's media index is unchanged, a real decoded waveform cached on disk, a baseline profile, English/Persian-only resources, no Firebase or build-time secrets.

## Screenshots

Add screenshots to `docs/screenshots/` and link them here (Home, Library, Now Playing, Lyrics, Settings).

## Releases

Pushing a tag such as `v1.3.0` runs `.github/workflows/release.yml`: unit tests, a signed release APK and a GitHub Release. Add the repository secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`; without them the APK is signed with the debug key.

## Storage and metadata support

- Embedded tag editing (title, artist, album, genre, lyrics) works for **MP3 (ID3v2.2/2.3/2.4), FLAC, M4A/MP4 and Ogg Vorbis/Opus** through a pure-JVM engine in `data/tagger/engine`. Artwork and every tag LogaRhythm does not edit are preserved; audio bytes are never re-encoded. Other containers (WAV, WMA, ADTS AAC) fail closed with an honest message.
- Every write is staged, re-parsed, streamed back, compared byte-for-byte, and rolled back from a backup if anything fails. Fields the user did not change are not rewritten, and placeholders such as "Unknown Artist" are never written into files.
- Lyrics are saved to and deleted from the file itself (ID3 `USLT`, Vorbis `LYRICS`, MP4 `©lyr`). If the user declines file access, deletion falls back to hiding the lyrics in-app and says so.
- Android 11+ uses the native MediaStore write/delete consent sheet (reusing grants already held); Android 10 and older request `WRITE_EXTERNAL_STORAGE` on first use. SAF document-provider files are still not supported.
- The library is indexed whenever the app comes to the foreground and when Android's media index changes, not only while the Library tab is open.

## Build

Recommended setup: Android Studio with a compatible JDK, Android SDK platform matching `compileSdk` in `app/build.gradle.kts`, and Gradle 9.3.1.

The source archive includes wrapper configuration, but does **not** include the `gradlew` launchers or `gradle-wrapper.jar`. After installing Gradle 9.3.1, generate the standard wrapper once from the project root:

```bash
gradle wrapper --gradle-version 9.3.1
```

Then build and test with the wrapper:

```bash
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
```

On Windows, use `gradlew.bat` for the same tasks. You can also open the project in Android Studio after the wrapper has been generated.

## Configuration

The app is local-first. `.env.example` documents the optional Gemini key convention, but local playback, library browsing, and the metadata-based mood mixes do not require an API key. Never commit `.env`, signing keys, or release credentials.

## Architecture

The project follows a UI → domain ← data dependency direction. Compose screens consume ViewModels; domain interfaces describe repositories and sources; Room, MediaStore, DataStore, and Media3 handle persistence and playback. New features should preserve those boundaries and keep offline paths usable.
