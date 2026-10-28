# LogaRhythm features

Technical details for each feature. Back to the [README](../README.md) · [فارسی](FEATURES.fa.md)

## Playback

- Cross-fade between songs (speed-aware) and skip silence
- Playback resumes if headphones reconnect within 10 minutes
- Per-song playback speed
- Sleep timer by minutes or by track count
- Swipe right for next, left for previous, with haptics (direction is switchable in Settings)
- Resume position for anything longer than 20 minutes: saved every 10 seconds, restored on reopen

## Audio

- ReplayGain for MP3, FLAC, M4A and Ogg, in track or album mode
- User preamp, 5-band equalizer, bass boost

## Lyrics

Sources: embedded in the file, a `.lrc` beside it, or fetched from LRCLIB, NetEase, Kugou, lyrics.ovh and Genius.

- Sources run in parallel inside a fixed time budget
- Blocked providers cool down instead of slowing every song
- Results are scored against title, artist, album and duration before they are accepted
- If the best match is not convincing, a candidate picker lets you search with your own title and artist (Latin or Persian), preview the first line, and lock the result as manual
- Genius is fetched directly, without a VPN. If a network challenges the request, the app reports it and uses the other sources
- Per-source switches, a test screen, and a custom HTTPS endpoint for your own lyrics server
- Synced scrolling, per-song timing offset, manual editing, import and export

## Tag and cover editor

- The format is detected from the first 12 bytes of the file, not from the extension: ID3/MP3, FLAC, Ogg or MP4
- Editable fields: title, artist, album, album artist, genre, year, track and disc number, composer
- Cover art: choose a new image (square crop and zoom), replace it, or remove it
- Writes go to a staged copy, are read back and verified (including a SHA-256 check of the cover), mirrored into MediaStore, and only then replace the original
- Notification and lock screen refresh immediately

## Library

- Multi-select: long-press a song, select more, then play next, queue, add to a playlist, favorite, hide, or remove from a playlist
- Swipe to play next: drag a song row to the right past the threshold (a single haptic tick confirms it) and release. If nothing is queued, the song starts immediately; if something is playing, it is inserted after the current track without interrupting playback. Below the threshold the row springs back. It is disabled during multi-select, has a TalkBack custom action, and works in the Songs tab, playlists, albums, artists, folders, genres, search results, smart mixes and the tools screens (duplicates, missing tags, hidden songs)
- Sorting: title, artist, album, year, date added or modified, duration, play count, last played, random. The choice is remembered per tab
- Filters: favorites, downloaded, recently added
- Tools: duplicate finder, missing tags view, hidden songs, excluded folders, minimum track length
- Artists: collaborations are split on commas, `&`, `feat.` and the Persian comma `،`, while names like *Tyler, The Creator* or *Earth, Wind & Fire* stay whole. You can add your own never-split names. Each artist has a page with top songs, albums and "Appears on"
- Ringtones and voice recordings are excluded by default (Settings → Library)

## Discovery

Everything is computed on the device.

- Daily mixes
- Mood queues: late-night calm, deep focus, energy, rainy day, acoustic morning
- Artist radio
- Listening stats by week, month or all time
- Smart mixes: Recently added, Recently played, Most played, Forgotten favorites, Songs without lyrics, Long songs, Downloaded. They update from the library and can be saved as a normal playlist. The playing queue can be saved as a playlist as well

## Music download

Sources: YouTube, SoundCloud and MusicMan. The download screen has source chips (All, MusicMan, YouTube, SoundCloud) and the choice is remembered after restarting the app. With "All", MusicMan results come first.

- Original-quality audio (M4A or MP3 when the source provides it) is saved into `Music/LogaRhythm`
- Artwork and tags are applied to the final file with the same pipeline for every source (Deezer, MusicBrainz and iTunes)
- An optional `cookies.txt` can unlock age-restricted YouTube videos
- Downloads use range requests and a temporary `.part` file, show progress and estimated remaining time, and can be cancelled
- Each file is checked to be real audio, and its extension (MP3, FLAC, Ogg or M4A) is detected from the file header
- If a source only offers WebM, the file is saved with a clear "saved without tags" note
- The player shows the file's container, codec, bitrate and sample rate

### MusicMan

- Only songs for which the server already has a file ready are listed. Songs that are still pending are hidden, so you are never left waiting
- Cover art is upgraded to 600×600 when possible
- Preparing a file: lookup, status check, queueing on the server if needed, then polling every 2.5 seconds for up to 60 seconds. Failed, cancelled, or completed-without-file states show a specific error
- Telegram audio links that carry a masked token are opened through a proxy; direct links are used as they are. If a direct download fails with a network error, it is retried once through the proxy
- No yt-dlp preparation is needed for this source

## Optional AI playlist

Disabled by default. No provider is contacted until you press Generate.

**Setup**

1. Open **Settings → AI (optional)**.
2. Choose **OpenAI-compatible** or **Google Gemini**, then keep or change the Base URL and model.
3. Paste your API key, tap **Test connection**, and save.

**Create a playlist**

Tap the **Magic playlist** banner on the Home screen (or open **Library → Playlists → Magic playlist**), describe the mood, choose the song count, generate, then preview, rename and save. If no API key is set, the banner opens the AI settings.

**Regenerate a daily mix**

Open a daily mix and tap **Regenerate with AI**. The songs already in the mix stay; the model only picks new ones from your library. The default is 10 new songs, adjustable from 5 to 50. The mix keeps its name (Daily Mix 1, 2, 3). Afterwards you can play the regenerated mix or save it as a playlist.

**What is sent**

The catalog is trimmed to keep requests small, even for libraries of thousands of songs:

- Playlists: at most 150 songs. Songs matching words in your request come first (up to 80), the rest is filled with a varied sample with at most 3 songs per artist
- Mix regeneration: up to 20 songs from the current mix and 80 candidates, ranked by shared artist, shared genre and closeness in year. The model is told not to return songs that are already in the mix, and the app merges and de-duplicates the result itself
- Each line is compact (`id | title | artist | genre | year`), with title, artist and genre cut to 80, 60 and 40 characters. Album names and file paths are never sent

The key is encrypted with an Android Keystore key on the device and can be removed at any time. No API key ships with the app.

## Backup and upgrading

- Backup and restore of playlists, favorites, history and lyrics in one file (**Settings → Backup**)
- The library database migrates to v10 on first launch of 1.6.0 and existing data is preserved. Making a backup before a major update is still recommended

## Appearance

- Default palette **Ink & Ember**: warm near-black (`#0E1013`) with an amber accent (`#F4B266`)
- Theme Studio: true-black OLED, colors from the artwork, the wallpaper's Material You palette, or a custom theme (accent hue, saturation, dark, light or OLED base)
- Persian and English, full RTL, switchable inside the app
