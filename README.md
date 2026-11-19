<div align="center">

<img src="docs/icon.svg" width="96" height="96" alt="LogaRhythm Logo" />

# LogaRhythm

**A modern, offline-first Android music player built for people who still love owning their music.**  
No subscriptions, no accounts, no ads, and no telemetry. Your library belongs entirely to you.

<a href="https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-2ea44f?logo=android&logoColor=white" /></a>
<a href="https://github.com/Yasinjahantigh/LogaRhythm-Android/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/Yasinjahantigh/LogaRhythm-Android/total?label=downloads&color=2ea44f" /></a>
<a href="https://t.me/LogaRhythmApp"><img alt="Telegram" src="https://img.shields.io/badge/Telegram-@LogaRhythmApp-2CA5E0?logo=telegram&logoColor=white" /></a>
<a href="https://daramet.com/yasinjahantigh"><img alt="Donate" src="https://img.shields.io/badge/Donate-Daramet-f97316?logo=buymeacoffee&logoColor=white" /></a>
<img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
<img alt="License: Apache 2.0" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" />

[English](README.md) · [فارسی](README.fa.md)

</div>

---

<p align="center">
  <img src="docs/screenshots/01_home_screen.png" width="31%" alt="Home Screen" />
  <img src="docs/screenshots/02_now_playing_player.png" width="31%" alt="Now Playing Player" />
  <img src="docs/screenshots/03_vinyl_scrubbing_mode.png" width="31%" alt="Vinyl Turntable Mode" />
</p>
<p align="center">
  <img src="docs/screenshots/04_karaoke_synced_lyrics.png" width="31%" alt="Time-Synced Lyrics" />
  <img src="docs/screenshots/05_unified_artist_profile.png" width="31%" alt="Unified Artist Profile" />
  <img src="docs/screenshots/06_queue_drag_drop.png" width="31%" alt="Interactive Queue" />
</p>
<p align="center">
  <img src="docs/screenshots/07_share_card_studio.png" width="31%" alt="Share Card Studio" />
  <img src="docs/screenshots/08_metadata_tag_fixer.png" width="31%" alt="Tag & Cover Fixer" />
  <img src="docs/screenshots/09_listening_insights.png" width="31%" alt="Listening Habits & Stats" />
</p>

---

## Why I Built LogaRhythm

Most modern streaming apps treat music like disposable feed content—locked behind a monthly paywall, interrupted by promotional banners, and constantly phoning home your habits to advertiser dashboards.

If you have a collection of high-res FLACs, carefully tagged albums, or local MP3s, you know how hard it is to find a modern player that treats them with respect. Many older offline players feel abandoned in the Holo or early Material era, while modern ones often feel sluggish or stripped of practical audio features.

**LogaRhythm is my answer to that.** I built it to pair the convenience and visual polish of a modern streaming app with the tactile warmth, ownership, and deep audio control of a dedicated physical player.

Everything happens locally on your device. Your playlists, listening history, custom queues, lyrics, and tag changes stay strictly on your phone.

---

## What Makes It Special

### 🎧 The Player & Vinyl Turntable
- **Realistic Vinyl Mode**: Tap into the turntable view to watch your vinyl rotate on the platter. Hold your finger down to grab the record, scrub backward or forward across the grooves, and swipe to watch a new album slide gracefully out of its cardboard sleeve.
- **Waveform Timeline**: Scrubber with custom smoothed waveforms seeded from each track's unique identity. Scrubbing feels alive without triggering full-screen recompositions.
- **Living Play Button**: Springs and morphs between a squircle when playing and a circle when paused, backed by tactile haptics.
- **Endless Smart Auto-Queue**: When your active queue ends, LogaRhythm automatically picks the next tracks based on audio fingerprint similarity, genre harmony, and tempo so the music never abruptly cuts out.
- **Swipe-to-Play-Next**: Swipe right on any track in your library to slot it immediately as the next song up.

---

### 🎚️ Audio Sculpting Suite
- **ReplayGain Normalization**: True track and album gain support across MP3, FLAC, M4A, and Ogg formats to eliminate sudden volume jumps.
- **5-Band Parametric Equalizer**: Smooth Bézier frequency curve, bass booster, and 3D audio virtualizer.
- **Per-Device Auto-EQ**: Automatically remembers different equalizer curves for each Bluetooth speaker, car audio system, or pair of wired headphones you plug in.
- **Nighttime Slowed + Reverb**: A one-tap preset that shifts pitch, adds warm decay, and eases playback speed for relaxed late-night sessions.
- **Precision A–B Looper**: Pinpoint two timestamps to loop a specific guitar solo, vocal run, or difficult lyric.
- **Gapless & Silence Skipping**: Automatic crossfade and silent-intro trimmer to keep energy seamless between tracks.

---

### 🎤 Synchronized Lyrics & Share Studio
- **Multi-Source Synced Lyrics**: Reads embedded SYLT/USLT metadata, companion `.lrc` files, or fetches time-synced lyrics automatically from LRCLIB, Genius, NetEase, Kugou, and lyrics.ovh.
- **Built-in LRC Editor**: Tap-to-sync timestamps line-by-line while listening, or use ±100ms nudges to align lines perfectly.
- **Share Card Studio**: Select favorite lyric lines or album artwork and export them into beautiful, typography-driven story cards (`1:1`, `4:5`, or `9:16`) ready for Instagram Stories, Telegram, or Twitter.

---

### 🌐 Unified Artist Search & Multi-Source Deduplication
- **Single Artist Identity**: Searching for an artist normalizes names and aliases across Deezer, Radio Javan, MusicMan, and Saavn. Instead of seeing three duplicate cards for the same person, you get one unified artist card with subtle source badges.
- **Progressive Discography Loading**: Tapping an artist pulls albums, singles, and collaborations across all connected sources in parallel without waiting on slow endpoints.
- **One-Tap Downloads**: Save full albums or singles directly to your device with tags and artwork automatically embedded.

---

### 🏷️ Batch Tag Fixer & Audio Trimmer
- **Smart Filename Cleaner**: Downloaded tracks often come polluted with noise like `Artist_-_Track_Name_(320kbps_Exclusive_Site).mp3`. LogaRhythm cleans the junk and queries Deezer, iTunes, and MusicBrainz to match original tags and high-res album covers.
- **Safe Tag Writer**: Edits write to an isolated temporary copy and verify checksums before replacing the original audio file.
- **Audio Trimmer**: Cut lossless tracks or MP3s with millisecond precision to create customized ringtones and notification sounds.

---

### 📊 100% Offline Listening Insights
- **Weekly & Monthly Reports**: See your genuine top songs, artists, and albums for any custom date range.
- **Minutes vs. Plays**: Toggle between raw play counts and total time listened to see what you actually spent the most time with.
- **Private Session**: Pause history tracking whenever someone else is borrowing your phone or playing party tunes.

---

### 🎨 Craftsmanship, Fonts & Themes
- **Adaptive Ambient Glow**: The background subtly pulls ambient hues from whatever album cover is currently playing.
- **Themes for Any Screen**: Pure Pitch Black for OLED battery saving, Ink & Ember, Material You dynamic wallpaper colors, and a Custom Theme Studio.
- **Bilingual Persian & English Typography**: Built with full native RTL support, default Vazirmatn font paired with modern Latin typefaces, and the ability to import your own `.ttf` / `.otf` files.
- **5 Glance Home Screen Widgets**: From a compact 2×2 cover pill to a full 4×4 queue manager.
- **Distraction-Free Car Mode**: Large, high-contrast buttons in both landscape and portrait for safe road control.

---

## Performance & Optimization

LogaRhythm is tuned to run smoothly even on older devices with weak CPUs and limited memory:
- **Zero-Recomposition Playback Ticks**: Playback position updates are piped directly into Canvas draw passes or isolated leaves, preventing whole-screen recomposition loops.
- **Graphics-Layer Micro-Interactions**: Touch ripples, spring bounces, and scale animations run strictly on the GPU layer.
- **Lean Release Builds**: Full R8 code and resource shrinking with Compose compiler strong skipping mode enabled for snappy cold starts.

---

## Installation

LogaRhythm runs on **Android 8.0 (Oreo)** and above.

| Architecture | Recommended Devices | Download |
| :--- | :--- | :--- |
| **arm64-v8a** | Modern 64-bit phones (95%+ of Android phones) | [Latest Release](https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest) |
| **armeabi-v7a** | Older 32-bit Android phones | [Releases Archive](https://github.com/Yasinjahantigh/LogaRhythm-Android/releases) |
| **Universal APK** | Compatible with all devices | [GitHub Releases](https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest) |

You can also download direct APKs from the official [Telegram Channel (@LogaRhythmApp)](https://t.me/LogaRhythmApp).

---

## Building from Source

LogaRhythm is written in Kotlin using Jetpack Compose, Media3 ExoPlayer, and Room.

```bash
# Clone repository
git clone https://github.com/Yasinjahantigh/LogaRhythm-Android.git
cd LogaRhythm-Android

# Build debug APK
./gradlew :app:assembleDebug
```

Requirements:
- Android Studio Ladybug / Meerkat or Android SDK command-line tools
- JDK 17 or JDK 21
- Android SDK Platform 35+

---

## Privacy & Network Disclosure

- **Zero Tracking**: No advertising SDKs, no behavioral tracking pixels, and no crash reporters collecting your data.
- **Local-Only Data**: Your songs, playlists, favorites, listening stats, and tag edits stay on your device.
- **Network Requests**: The app only connects to the internet when you explicitly ask it to (searching for lyrics, downloading tracks, or looking up cover artwork). Outside of direct user requests, it makes an anonymous one-time installation count on first launch and checks for GitHub releases once daily.

---

## License

LogaRhythm is free, open-source software licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.
