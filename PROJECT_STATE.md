# LogaRhythm Product State - 2026-10-11

## Done in this pass
- **Tag engine** (`data/tagger/engine`): MP3 ID3v2.2/2.3/2.4, FLAC, M4A/MP4 (chunk-offset shifting), Ogg Vorbis/Opus. Verified against real ffmpeg-generated files with mutagen as an independent reader and decoded-audio hash comparison (audio identical, artwork kept). Unit tests: `TagEngineTest` (run and passing on a desktop JVM).
- **AudioFileTagWriter** rebuilt on the engine: staged write, re-parse verification, byte-for-byte readback, rollback, MediaStore rescan. Unchanged fields and "Unknown ..." placeholders are no longer written into files.
- **Lyrics**: embedded lyrics are read from all supported formats and can be truly removed from the file; delete flow asks for consent and degrades to "hidden in app" if declined.
- **Permissions/consent**: unified `rememberMediaStoreConsent` (API 30+ MediaStore sheet with grant reuse; API 26-29 runtime WRITE_EXTERNAL_STORAGE). Manifest handles configuration changes so a rotation cannot drop a pending system dialog.
- **Library sync** (`core/library/LibrarySync`): indexes on foreground and on MediaStore changes; fixes empty Home after first-run permission grant. Home empty state is permission-aware (grant / open settings / rescan).
- **Performance**: playback position no longer recomposes the app shell or the 900-line full player; mini player progress is read in the draw phase; custom `PlaybackTimeline` scrubber (own drag state, LTR in every language, accessible).

- **Lighter image pipeline**: album art is decoded at the size the screen needs (list rows no longer decode 512px thumbnails; oversized embedded pictures are down-sampled), the image memory cache is capped at 15% of app memory, and the scrubber gives a light haptic on grab/release.

## Not verified here
- The Android/Compose layer could not be compiled in this environment (no Android SDK / Maven access). Pure-Kotlin logic was compiled and tested; the writer was type-checked against android.jar; Compose files were syntax-checked only. Run `./gradlew assembleDebug :app:testDebugUnitTest` first and report any compile error.
- No on-device testing of consent flows on Android 10 or older.

## Known limits
- No tag writing for WAV/WMA/ADTS, SAF providers, or multi-file bulk consent.
- Companion `.lrc` read/delete needs a filesystem path from MediaStore (unavailable under scoped storage on many devices).
- Multiplexed/chained Ogg and fragmented MP4 are refused.
