# Music Player

An independent, clean, offline-first Android music player built with Jetpack Compose and Material 3 (Material You).

## Features
- **Offline-First**: Native local playback with extensible source abstraction.
- **Material You**: Dynamic color support (Android 12+) with harmonious fallback palette, RTL support, and large rounded shapes.
- **Modern Architecture**: Clean Architecture with strict layer isolation (`ui -> domain <- data`).
- **Media3 Ready**: Built on AndroidX Media3 (ExoPlayer + MediaSessionService).

## Tech Stack
- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **DI**: Hilt with KSP
- **Data**: Room (KSP), DataStore Preferences
- **Media**: AndroidX Media3 (ExoPlayer, MediaSession)
- **Async**: Kotlin Coroutines & Flow
- **Images**: Coil Compose

## Getting Started
Build and install via Gradle:
```bash
gradle assembleDebug
```
To run local tests:
```bash
gradle :app:testDebugUnitTest
```
