# LogaRhythm

LogaRhythm is an offline-first Android music player built with Kotlin, Jetpack Compose, Material 3, Media3, Room, and DataStore. It is designed around a calm, Pixel-inspired interface while keeping playback and personal library data on-device.

## Product capabilities

- Local audio library with song, album, artist, folder, and genre browsing
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

## Storage and metadata support

- Embedded audio-tag editing is currently limited to MP3 / ID3v2.3 text tags. Unsupported formats, ID3v2.4 tags, malformed/truncated tags, nonzero ID3v2.3 revisions, and tags with unsupported header/frame flags fail closed instead of claiming a database-only change succeeded.
- User-initiated lyrics saves attempt to write an ID3 `USLT` frame into supported MP3 files and read it back for verification. If that fails, the lyrics may be retained in Room for LogaRhythm playback, but the file is not reported as updated. Companion `.lrc` reading/deletion is separate; an automatic sidecar write is not used as a silent fallback.
- Android 11+ shared-file writes/deletes use the native MediaStore consent flow for supported items, and the app verifies the resulting file state before reporting success. The API 29 recoverable-consent flow and writes through arbitrary SAF document providers are not implemented yet.
- The lyrics delete action hides cached lyrics in LogaRhythm and attempts to remove a sibling `.lrc`; it does not yet remove a pre-existing embedded `USLT` frame, and the UI warns that a file copy may remain.

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
