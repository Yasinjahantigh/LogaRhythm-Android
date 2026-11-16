# Changelog

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