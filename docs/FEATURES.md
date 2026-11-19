# LogaRhythm — All Features

A simple rundown of what the app can do. Back to [README](../README.md) · [فارسی](FEATURES.fa.md)

## Playback & Vinyl Mode

- **Endless Playback**: when you reach the end of a playlist or queue, the app keeps the music going by picking similar songs from your library based on artist, genre, album, and what you usually listen to.
- **Vinyl Turntable Mode**: switch between regular cover art and a spinning vinyl player.
  - Swipe left or right on the cover or vinyl to skip to the next or previous song (the album sleeve slides in and the vinyl slides out calmly onto the platter).
  - Press and hold briefly on the record — or drag along its outer edge — to spin it by hand and scrub forward or backward.
- **Everyday controls**: double-tap the left/right side of the artwork to jump ±10 seconds, crossfade between songs, skip silent gaps, per-song speed, A–B loop, sleep timer (by minutes or song count), and a clean **Car Mode** for driving.
- **Smart resume**: picks up where you left off on long audio files (>20 minutes), resumes playback if headphones reconnect within 10 minutes, and switches EQ profiles automatically per Bluetooth or wired device (**Auto-EQ**).
- **Queue**: split into Played, Now Playing, and Up Next. Drag to reorder, swipe to remove (with undo), or save a queue as a playlist. Shuffle only reshuffles what's coming up next and keeps your history intact.

## Sound & Equalizer

- ReplayGain support for MP3, FLAC, M4A, and Ogg (track or album mode).
- Preamp, 5-band equalizer, left/right balance, mono downmix, pitch shift, bass boost, virtualizer, and a one-tap **Slowed + Reverb** preset.
- Separate Auto-EQ profiles remembered per headphone or speaker.

## Lyrics & Share Cards

- Reads embedded lyrics and local `.lrc` files, or fetches synced lyrics online from LRCLIB, NetEase, Kugou, lyrics.ovh, and Genius.
- Search manually with a custom Persian or English title/artist, preview candidates before picking, adjust timing offset, or edit `.lrc` timestamps line by line.
- **Share Card Studio** (for songs, lyric lines, and listening reports): live preview where you can pick a theme, accent color, card size (`1:1`, `4:5`, `9:16`), and text size before saving or sharing.
- Selected lyric lines stay locked while the song keeps playing, and Persian/English lines render cleanly without overlapping.

## Tag Editor, Batch Fixer & Ringtone Maker

- Full tag editor (title, artist, album, album artist, genre, year, track/disc number, composer, cover art crop/replace) for MP3, FLAC, Ogg, and M4A.
- Writes safely to a temporary copy and verifies the file (including artwork checksum) before replacing the original.
- **Batch Tag & Cover Fixer**: cleans up messy filenames from download sites (URLs, bitrates, extra brackets) and searches Deezer, iTunes, MusicBrainz, RadioJavan, and MusicMan step by step to fix tags and high-res covers. You can edit any field or re-search a single track before saving.
- **Audio Trimmer & Ringtone Maker**: trim any part of a song and save it or set it directly as your ringtone.

## Library, Artist Online Tab & Release Radar

- Multi-select actions, swipe right on a track to play next, per-tab sort and filter memory, folder whitelist/blacklist, and Private Session (pauses listening history).
- **Unified Artist Profiles & Smart Deduplication**: Artist searches automatically normalize names and aliases across Deezer, Radio Javan, MusicMan, and Saavn, merging duplicate cards into a single profile with source badges.
- **Aggregated Discography**: Tapping an artist loads their full collection across all sources in parallel—handling track duplicates cleanly and streaming results progressively.
- **Release Radar**: follow your favorite artists and see their newest releases right on the Home screen, with artist filters and one-tap download (checks iTunes, Deezer, and MusicMan).
- Helpful VPN hints wherever international sources might be blocked on your network.

## Listening Stats & Mixes

Everything offline stays on your phone:

- **Listening Stats**: see your top songs, top artists, and top albums for any custom date range. You can sort rankings by **Play count** or by **Listening time (minutes)**, and export your report as a shareable card.
- **Smart Mixes**: daily mixes, artist radio, Recently Added, Recently Played, Most Played, Forgotten Favorites, Songs Without Lyrics, Long Tracks, and Downloaded.

## Music Downloads

- Search and download from MusicMan, RadioJavan, YouTube, SoundCloud, and Deezer metadata.
- Saves files into `Music/LogaRhythm` (or any folder you pick) and fills in tags and cover art automatically.
- Supports album and playlist batch downloads (pick which tracks to grab), skips duplicates, and supports `cookies.txt` for age-restricted YouTube videos.

## Optional AI Playlists

Off by default — nothing is sent anywhere unless you turn it on and hit Generate.

- Works with **Google Gemini** or any **OpenAI-compatible** API (`Settings → AI`).
- Only sends compact track info (title, artist, genre, year — never file paths). Your API key is stored encrypted on-device with Android Keystore.

## Backup & Updates

- Export or restore playlists, favorites, listening history, and lyrics in a single backup file (`Settings → Backup & Restore`), plus M3U playlist import/export.
- Built-in updater checks GitHub Releases and downloads the right APK for your phone's CPU.
- Optional anonymous first-launch install counter (runs once per install; public count at [logarhythm-installs.yasinjahantigh.workers.dev/stats](https://logarhythm-installs.yasinjahantigh.workers.dev/stats)).

## Home Screen Widgets

Five widgets that stay in sync with playback:

| Widget | Size | What it shows |
| :--- | :--- | :--- |
| Small | 2×2 | Cover art and play/pause |
| Strip | 4×1 | Compact title and controls |
| Medium | 4×2 | Cover, progress bar, shuffle, prev, play, next, repeat |
| Large | 4×4 | Full controls plus Up Next queue |
| Player | 4×4 | Full cover art with title, artist, album, and themed buttons |

## Themes, Fonts & Tablets

- **Album Art** dynamic theme by default, plus Ink & Ember, Pure Black OLED, Material You wallpaper colors, built-in presets, and a Custom Theme Studio.
- **Vazirmatn** as the default font, plus System Font, paired English + Persian font options, and the ability to import your own `.ttf` / `.otf` font files.
- Smooth floating bottom bar on phones, and Navigation Rail + two-pane Settings on tablets and foldables.
- Full English and Persian (RTL) interface, switchable inside the app.