# LogaRhythm — Features Reference

Back to [README](../README.md) · [فارسی](FEATURES.fa.md)

## Playback & Vinyl Mode

- **Regular & Vinyl views**: Toggle between album artwork and a spinning vinyl player. Swipe left/right to switch tracks (in vinyl mode the next disc slides out of an album sleeve), or press and hold / drag along the outer edge to scrub by hand.
- **Everyday controls**: Double-tap left or right on the artwork to jump ±10 seconds, crossfade between songs, skip silent gaps, per-song speed and pitch, **Slowed + Reverb** preset, A–B loop, sleep timer (minutes, remaining tracks, or end of current track), and **Car Mode** (portrait and two-column landscape).
- **Smart resume**: Remembers position on audio longer than 20 minutes, resumes playback if headphones reconnect within 10 minutes, and applies saved Auto-EQ profiles per Bluetooth or wired audio device.
- **Queue & Endless playback**: Split into Played, Now Playing, and Up Next. Drag to reorder, swipe to remove with undo, save multiple queues or export as playlists, and keep music playing at the end of a queue with similar tracks from your library.
- **Audio Output picker**: Switch active audio output routes (`MediaRouter2` / `ExoPlayer`) directly from the player and adjust media volume.

## Equalizer & Audio Processing

- ReplayGain track and album normalization (with adjustable preamp) for MP3, FLAC, M4A, and OGG.
- 5-band equalizer with frequency curve preview, presets, bass boost, virtualizer, left/right stereo balance, and mono downmix.
- Separate Auto-EQ profiles remembered per headphone or speaker.

## Lyrics, Translation & Share Cards

- **Multi-source lyrics**: Reads embedded USLT/SYLT tags and local `.lrc` files, or searches LRCLIB, Genius, NetEase, Kugou, lyrics.ovh, and an optional custom HTTPS endpoint. Includes built-in source diagnostics, timing offset nudge, and a line-by-line `.lrc` timestamp editor.
- **Free bilingual translation**: Translates synced or plain lyrics line-by-line into your chosen target language and caches translations offline without any API key.
- **Instrumental silence animation**: Displays a subtle pulse in the lyrics view and player glow during long instrumental intros, breaks, or outros.
- **Share Card Studio**: Export songs, selected lyric lines, or listening reports in `1:1`, `4:5`, or `9:16` with live theme, accent color, and text size controls. Selected lyric lines stay locked while playback continues, and Persian/English lines render cleanly together.

## Home, Playlists & Online Discovery

- **Home**: 2-column Quick Picks grid, time-of-day Right Now card, jump-back shelves (Recent, New, On Repeat), daily mixes, mood chips, and Release Radar.
- **In-player context**: Peek at **Up Next**, open **About Artist & Audio Specs**, or delete the playing track directly from the player menu.
- **Playlists & Library**: Inline search inside any playlist, favorite/pinned playlists with a heart badge, swipe right on any track to play next, multi-select actions, per-tab sort and filter memory, folder blacklist or whitelist, minimum track duration filter, and Private Session.
- **Release Radar & Artist Online tab**: Follow artists to track genuinely recent releases (verified release dates via iTunes + Deezer), dismiss unwanted items, automatically hide tracks you already own, and browse an artist's merged online catalog across Deezer, Radio Javan, MusicMan, and Saavn.
- **Cover Detail Sheets**: Tap any result cover in Downloads or Release Radar for full release details, audio preview, sharing, or download.

## Downloads

- Search and download single tracks, albums, or playlists from **MusicMan**, **Radio Javan**, **Deezer**, **Saavn**, **YouTube**, **SoundCloud**, and **Bandcamp**.
- Automatic source routing based on live VPN status and measured latency, with VPN hints where sources may be region-restricted.
- Saves files into `Music/LogaRhythm` (or a folder you choose) with automatic tags and artwork, detects existing tracks to skip duplicates, and supports AES-256-GCM encrypted `cookies.txt` for restricted YouTube videos.

## Tag Editor, Batch Fixer & Audio Trimmer

- Safe tag editor (title, artist, album, album artist, genre, year, track/disc, composer, cover crop or online cover search) for MP3, FLAC, M4A, and OGG. Writes to a temporary file and verifies checksums before replacing the original.
- **Library Tools**: Bulk Tag & Cover Fixer (cleans site/bitrate clutter from filenames and searches Deezer, iTunes, MusicBrainz, Radio Javan, and MusicMan step-by-step), Missing Tags list, Duplicate Finder (matching by cleaned metadata or duration + file size), and Hidden Songs manager.
- **Audio Trimmer & Ringtone Maker**: Cut any segment of a track and save it or set it directly as your ringtone.

## Listening Stats & Smart Mixes

Computed locally on your device:

- Top songs, artists, and albums for presets or custom date ranges, ranked by **play count** or **listening time (minutes)**, with shareable Listening Report cards.
- Built-in Smart Mixes: Daily Mixes, Artist Radio, Recently Added, Recently Played, Most Played, Forgotten Favorites, Songs Without Lyrics, Long Tracks, and Downloaded.

## Optional AI Playlists

Off by default; nothing leaves your device unless you configure a provider and generate a mix.

- Supports **Google Gemini** and **OpenAI-compatible** APIs (**Settings → AI**).
- Sends only compact metadata (title, artist, genre, year — never file paths). API keys are encrypted on-device with Android KeyStore.

## Backup, Privacy, Feedback & Updates

- Export and restore playlists, favorites, listening history, and lyrics in one backup file (**Settings → Backup and playlists**), plus M3U import/export.
- **Privacy & Diagnostics**: Transparent first-run privacy sheet and **Settings → Privacy** switches for sanitized crash reports and optional anonymous weekly summaries, plus **Shake-to-Report**, **Settings → Suggestions**, a one-time random-ID install counter, and an in-app **Open-Source Licenses (GPL-3.0)** viewer.
- **Updates**: GitHub builds check GitHub Releases and verify APK host, package name, and signing certificate before installing. Myket builds (`-PdistributionChannel=myket`) strip `REQUEST_INSTALL_PACKAGES` and open Myket instead.

## Home Screen Widgets

| Widget | Size | Contents |
| :--- | :--- | :--- |
| Small | 2×2 | Cover art and play/pause |
| Strip | 4×1 | Compact title and playback buttons |
| Medium | 4×2 | Cover, progress bar, shuffle, prev, play, next, repeat |
| Large | 4×4 | Full controls plus Up Next queue |
| Player | 4×4 | Full-bleed cover art with track info and themed buttons |

## Themes, Fonts & Tablets

- Dynamic colors extracted from the current album cover by default, plus 10 curated presets, Material You wallpaper colors, Pure Black OLED, custom accent color, custom background image with opacity slider, and corner roundness options.
- Default **Vazirmatn** font, System Font, paired English + Persian typography presets, and custom `.ttf` / `.otf` import.
- Floating bottom bar on phones; Navigation Rail and two-pane Settings on tablets and foldables; full English and Persian (RTL) UI switchable in Settings.
