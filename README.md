# LogaRhythm

LogaRhythm is an offline-first Android music player built with Kotlin, Jetpack Compose, Material 3, Media3, Room, and DataStore. It is designed around a calm, Pixel-inspired interface while keeping playback and personal library data on-device.

## Product capabilities

- Local audio library with song, album, artist, folder, and genre browsing
- Background playback through Media3, with a persistent mini player, full player, queue, and home-screen widget
- Playlists, favorites, sorting, search history, and listening statistics
- Synced lyrics from local files and optional online sources, plus manual editing and `.lrc` export
- Offline mood mixes, three distinct daily-mix intents, and a one-tap “radio from this song” queue built from lightweight metadata similarity
- One-tap shuffle across the entire library, time-boxed 15/30/60-minute listening sessions, and a true Recently Added row driven by MediaStore's added timestamp
- Safe library refreshes: a failed scan keeps the last successful index instead of treating the error as an empty library
- Equalizer, bass boost, virtualizer, playback speed, and sleep timer where supported by the device
- English and Persian UI with RTL support, localized search metadata, resilient artwork fallbacks, theme preferences, and dynamic color options

Mood suggestions are intentionally lightweight and private. They infer approximate energy, mood, acoustic character, and similarity from local metadata; they do not perform signal-level audio analysis or send your listening library to a recommendation service. Radio deliberately replaces the current queue, keeps the currently playing song and seek position where possible, then sequences relevant tracks with artist/album diversity.

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
