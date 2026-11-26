# Changelog

## 2.2.5 - 2026-11-26

### Player UI/UX & Interface Customization
- **Refined Full Player Layout**: Moved **Up Next** cleanly into the player header (tappable to open the queue), turned the artist row into a clear tappable button with an artist avatar/person icon that opens **About Artist**, and unified player quick actions into a single 4-button Material 3 dock (`Lyrics · Timer · Speed · Queue`)
- **Fixed Dynamic Artwork Colors & Bitrate Display**: Tuned `generateDynamicSchemeFromSeed` so warm/gold album covers no longer shift toward olive/muddy green, and fixed `AudioFormatReader` reporting `320000 kbps` instead of `320 kbps`
- **Settings → Interface & Sections**: Added 15 individual toggles (all enabled by default) to hide or show optional UI elements across the Player (next track in header, artist button, format/quality badge, seek ±10s buttons, waveform vs. Material 3 slider, shuffle, repeat, quick-action dock, output button, favorite button), Home (Quick Picks, Greeting, Daily Mixes, Mood Mixes), and Lyrics (translation toggle)

### Features & Bug Fixes
- **Add Songs Inside Playlists**: Added a `+` button in the Playlist Detail header, an empty-state CTA, and a searchable multi-select sheet to add songs directly while browsing a playlist
- **Instrumental Silence Threshold**: Updated instrumental/silence animation detection so it only triggers during breaks strictly longer than 4 seconds (`> 4s`) across intro, between-line gaps, and outro
- **Internet Artist Photo Search**: Added direct internet photo lookup (Bing Web Images, Wikipedia `fa`/`en`, Wikimedia Commons) for artist avatars without requiring a VPN
- **Share Card Studio Fonts & Controls**: Fixed bundled/selected fonts missing when rendering lyric cards and listening reports (`resolveAppTypefacePair`), and added font chips and text alignment controls
- **Downloader Reliability & Logging**: Fixed YouTube/SoundCloud channel/label uploader verification, MusicMan fallback URLs & ID cleanup, and unified `yt-dlp`/direct fallback recovery with detailed diagnostic logging
- **Duplicate Finder & Home Quick Picks**: Upgraded Duplicate Finder with decoded PCM audio fingerprinting alongside metadata matching, and added a minimize/expand toggle for Home Quick Picks
- **Diagnostics & Bug Reporting**: Added rolling in-app logs, diagnostic context tracking, and sanitized bug/crash snapshots for Shake-to-Report and Settings feedback

## 2.2.0 - 2026-11-25

### Security, Licensing & Store Compliance
- Unified project license under **GNU GPL-3.0** (`LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES`) with bundled license text and an in-app Open-Source Licenses viewer
- Removed TLS certificate bypasses, enforced HTTPS in `network_security_config`, encrypted YouTube `cookies.txt` via Android KeyStore (AES-256-GCM) with `0600` temp cleanup, and added host + package + signing-certificate verification for self-updates
- Added `-PdistributionChannel=myket` release build support: strips `REQUEST_INSTALL_PACKAGES` from the merged manifest and routes updates to Myket while keeping GitHub self-update for GitHub builds

### Privacy, Feedback & Backend
- Replaced the 24-hour device-info install ping with a true one-time minimal install record (`installId`, version, channel)
- Added a transparent first-run Privacy Consent sheet, **Settings → Privacy** controls, sanitized crash reporting, **Shake-to-Report**, **Settings → Suggestions**, and optional anonymous weekly listening stats backed by Cloudflare Worker + Telegram bot alerts

### Bug Fixes & Stability
- Fixed mini-player disappearance after launching playback from home screen widgets by hydrating live `MediaController` queue/metadata on attach and resetting dismissed state
- Fixed Audio Output picker applying routes directly via `MediaRouter2` / `ExoPlayer.setPreferredAudioDevice` and updating the player icon immediately
- Fixed AI Playlist generation failures on Gemini 2.5 thinking responses and OpenAI reasoning models, plus provider switching and clear server error surfacing
- Fixed `yt-dlp` runtime update vs. concurrent download race conditions, MusicMan/RadioJavan/Saavn `0..100%` download progress scaling, HTTP download cancellation, and Android 8–15 foreground service/Samsung & Xiaomi compatibility
- Fixed Release Radar dates by querying iTunes Artist Lookup + Deezer Artist Albums with strict recency filtering instead of relying on inaccurate source dates

### New Features & Spotify-Inspired UX
- Added Spotify-style 2-column **Quick Picks** on Home, **Up Next** & **About Artist / Audio Specs** inside the player, and direct **Delete from device** in the player 3-dot menu
- Added inline **Playlist Search** and **Favorite/Pinned Playlists** with heart indicator
- Added **Cover Detail Sheets** and automatic **Hide Owned Tracks** filtering across Downloads and Release Radar
- Added free **Bilingual Lyrics Translation** (no API key required, locally cached) and subtle **Instrumental Silence** animations

## 2.1.0 - 2026-11-18

### Playback & Vinyl
- Endless playback: queues automatically keep going with similar songs from your library instead of stopping after a few tracks
- Vinyl mode now supports horizontal swipes to switch tracks with a calm sleeve-unboxing animation, plus holding or dragging the grooves to spin and scrub
- Removed the square shadow outline behind playing album artwork

### Home, Navigation & Settings
- Brought back the animated floating bottom navigation bar and made transitions lighter across the app
- Cleaned up the Home screen: removed the 15/30/60 min session buttons, scaled down the Shuffle button, and placed Download and New Playlist near the top
- Split Settings into clear pages and added a two-pane layout for tablets
- Updated the Feature Guide so every item matches real features in the app

### Release Radar, Artist Online & Tag Fixer
- Rebuilt Release Radar with artist filter chips, release dates on every card, a dismiss button (`×`) for tracks you don't want, and iTunes fallback
- Added an Online tab on every artist page to preview and download tracks directly (songs already on your phone show cleanly without a download button and play locally)
- Artist photos now load automatically online and can be edited from your gallery or online search if a match is wrong
- Added a one-tap Tag & Cover Fixer shortcut in the Library header, with step-by-step filename cleanup and metadata/cover matching from Deezer, iTunes, MusicBrainz, RadioJavan, and MusicMan
- Fixed a crash when picking an online cover in the Tag Editor and saving the crop
- Added helpful VPN notices in online sections for restricted networks

### Share Cards, Lyrics & Stats
- Added live previews and customization (theme, colors, aspect ratio, text size) when sharing songs, lyrics, or listening reports
- Fixed overlapping Persian/English lines on lyric cards, added a Share button to the Lyrics top bar, and kept selected lines from resetting while the song plays
- Listening Stats can now rank top tracks, artists, and albums by either **play count** or **listening time (minutes)** (and Listening Report cards reflect your choice)

### Theme & Fonts
- Album Art dynamic colors are now the default theme
- Vazirmatn remains the default font, with System Font, paired English + Persian font options, and custom `.ttf`/`.otf` font import

## 2.0.0 - 2026-11-16

### Release Radar & Followed Artists
- Follow artists from any artist page and receive notifications when new tracks or albums drop (powered by MusicMan, Cloudflare Worker `/releases` edge relay with iTunes + Deezer fallback, and old-catalog date filtering)
- Manual refresh button with VPN hint when blocked, plus a dedicated Release Radar section on Home

### Pro Audio DSP & Player
- Stereo Balance, Mono downmix, per-device Auto-EQ profiles, Pitch shift, Slowed + Reverb preset, and A–B Loop
- Car Mode with high-contrast portrait and two-column landscape layouts
- Double-tap left/right artwork to seek ±10s, and vinyl turntable angle preservation on pause
- Audio Trimmer & Ringtone Maker in the track menu

### Reports, Lyrics & Tag Fixer
- Custom date-range Listening Reports with in-app 9:16 Story Card PNG preview before sharing
- Lyric Card 9:16 quote sharing and a line-by-line LRC timestamp editor
- Batch Tag & Cover Fixer combining fast algorithmic cleanup/matching with optional AI refinement

### Downloader, Library & Customization
- MusicMan auto-crawling for uncached tracks, playlist/album batch downloads, quality & folder picker, and faster search/downloads
- Multiple saved queues, Private Session mode, Folder Whitelist mode, custom app fonts (Vazirmatn, Sans, Serif, Mono), and a Power Features Guide in Home & Settings
- Support / Donate links (Daramet & GRAM/TON) in Settings → About

## 1.8.0 - 2026-10-29

### Player
- The next cover always waits on the right: swipe left to advance and right to go back, in English and Persian alike, and the mini player uses the same rule. The direction setting is gone
- Shuffle keeps the songs that already played in place and reshuffles only what is still upcoming

### Queue
- Played songs are read-only: no drag handle and no swipe-to-delete, because their order is history
- The drag handle is a clear grip icon instead of something that looked like a delete button
- The drop line now appears exactly where the row will land when dragging down

### Widgets
- The 4×2 widget fits again instead of clipping its buttons, and shows shuffle and repeat next to previous, play and next

### Updates
- In-app update check against GitHub releases, with an ABI-matched APK download, progress and the system installer
- The automatic check runs at startup and is on by default; **Settings → About** can switch it off
- Anonymous first-launch install record (random install id, app version, device model and Android version), so installs from Telegram count too. It is sent once per install; app updates never count again
- Settings → About shows the installed version, the latest release and its download count

## 1.7.0 - 2026-10-29

### Widgets
- The single widget is replaced by five: Small (2×2), Strip (4×1), Medium (4×2), Large (4×4 with an up-next queue) and Player (4×4 with full-bleed artwork)
- Widget state now comes from the live player, so buttons and playing state stay in sync

### Download
- New source: MusicMan, with a source filter (All, MusicMan, YouTube, SoundCloud) that is remembered
- Download a whole album from a search result, with duplicate detection and one summary notification
- Better metadata merging from Deezer, iTunes and MusicBrainz

### Playback and queue
- Shuffle can be toggled without interrupting the current song, and its state survives restarts
- Queue edits stay in sync with the original order, so songs added while shuffled are not lost
- Swipe the cover to preview the next or previous track before changing it
- Swipe a song row to play it next
- The speed dialog shows one consistent value

### Library and tags
- Albums with the same name are separated by album artist
- New "Fix names" tool: review before/after corrections and apply them per track
- Find cover art online from the tag editor
- Auto-fill tags from online metadata
- More robust M4A tag editing (64-bit boxes, QuickTime metadata, `moov` at the end, fragmented files)
- Sort order is remembered per tab, even if the app is killed right after changing it

### Lyrics
- Genius works again (it no longer accepts the old page size)
- If Genius is blocked, lyrics come from lyrics.ovh and are labeled as a fallback

### AI
- Magic playlist banner on the Home screen
- Regenerate a daily mix: current songs stay and the AI adds new ones
- Smaller requests: a limited sample of song metadata is sent, and album names and file paths never are