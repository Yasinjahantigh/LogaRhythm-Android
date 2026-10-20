# LogaRhythm

LogaRhythm is an offline-first music player for Android, built with Kotlin, Jetpack Compose, Material 3, Media3, Room and DataStore. Playback and listening history stay on the device.

## Features

- Local library with songs, albums, artists, folders, genres and playlists
- Background playback, queue management, mini/full player and a home-screen widget
- Synced lyrics from embedded tags, local `.lrc` files and optional online sources
- Tag editing for MP3, FLAC, M4A/MP4 and Ogg Vorbis/Opus, with staged writes and rollback
- Equalizer, bass boost, virtualizer, playback speed, sleep timer and ReplayGain
- On-device listening statistics, daily mixes, mood queues and time-of-day suggestions
- Duplicate finder, missing-tag view, M3U import/export and full backup/restore
- English and Persian UI, RTL support, and Material You theming with wallpaper or album-art colors

All personalization is computed locally from the user's own listening history. The app has no analytics, tracking or account system. Online lyrics lookup is the only optional network feature.

## Build

Requirements:

- JDK 21
- Android SDK with platform 36 and build tools 36.1.0
- The Gradle wrapper is included; no separate Gradle installation is needed

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

On Windows use `gradlew.bat` instead. You can also open the project directly in Android Studio.

## Release

Signed releases are built by the `Build and release APK` workflow on a `v*` tag or by manual dispatch. The workflow runs the unit tests, builds the release APK, verifies its signature and publishes a GitHub release.

Signing setup and the required repository secrets are documented in [`docs/RELEASING.md`](docs/RELEASING.md).

## Architecture

The code follows a `UI -> domain <- data` dependency direction. Compose screens observe ViewModels; the domain layer exposes repository and source interfaces; Room, MediaStore, DataStore and Media3 provide persistence and playback.
