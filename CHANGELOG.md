# Changelog

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