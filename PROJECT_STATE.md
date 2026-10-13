# LogaRhythm Product State - 2026-10-12

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

## UI/UX pass (design-only, no logic changes)
- **Type**: zero letter-spacing everywhere (tracking breaks Arabic-script joins); Persian/Arabic UIs use the sans family for headlines (a serif fallback there is Naskh and clashes); roomier line heights.
- **Home**: large greeting, tonal icon badges + display-type section headers, borderless cover-shelf tiles, pill mood chips, tertiary stats banner.
- **Navigation**: tonal bar/rail with pill indicator, selected label emphasised.
- **Mini player**: floating rounded card, artwork-tinted shadow, circular progress ring around play/pause (draw-phase only), transport always LTR.
- **Now Playing**: aurora backdrop (artwork colour + theme tertiary), hero artwork that "breathes" with playback, morphing play button (squircle <-> circle), wide skip keys, active tonal state for shuffle/repeat, +/-10 s as quiet pills, speed and sleep timer moved into the overflow menu (still tagged `player_speed_button` / `player_sleep_timer_button`), one shared artwork/meta implementation for portrait and landscape. Transport is always LTR in Persian too.
- **Lyrics**: poster-style start-aligned lines (Persian right, English left via content text direction), distance-based dimming (sung lines recede most), no bounce/scale, faded top/bottom edges, slim header with an overflow menu (edit/import/export/google/delete keep their test tags).
- **Library / Search**: pill tabs; pill search field. Track rows: rounded, highlighted current row.
- Not compiled here (no Android SDK). All edited Kotlin files were parsed with tree-sitter (0 syntax errors) and imports were cross-checked by hand; run `./gradlew assembleDebug` and report any compile error.

## UI/UX redesign v2 (structure + visuals, 2026-10-11)
Not compiled here (no Android SDK / Gradle). A custom static checker (brackets, imports, string resources) passes on every Kotlin file; run `./gradlew assembleDebug` first and report any compile error.

- **Navigation**: 4 tabs -> 3 (Home, Library, Settings). Search is no longer a tab: a search pill at the top of Home and Library opens a full-screen search (back arrow, keyboard opens immediately). Mini player and navigation are ONE floating dock (`MusicDock`); the selected destination expands into a labelled pill. Swipe the mini player sideways = next/previous, swipe down = dismiss. Landscape/tablet keeps a rail.
- **Library**: category rail (icon + live count per category) replaces chip tabs; Songs opens with Play/Shuffle bar; albums = big covers with count badge, artists = round monogram tiles, genres = colour tiles, folders = list. Anything without artwork gets a deterministic gradient + monogram (`ArtworkImage`).
- **Track row**: new unified `TrackListItem` (artist · album, duration next to menu, animated equaliser on the playing row).
- **Now Playing**: blurred-artwork backdrop (Android 12+, colour wash below), big swipeable cover, per-song waveform scrubber with +/-10 s beside the clock, transport row, and a labelled quick-action bar (Lyrics / Queue / Timer / Speed; active state shows live value). Header menu holds the rest (radio, equalizer, vinyl mode, edit, details, share). Lyrics mode shows a compact song row above the lyrics.
- **Home**: greeting + search pill, hero card, quick start (shuffle + 15/30/60), ONE "Jump back in" shelf (recent / new / on repeat) instead of three sections, daily mixes, moods, stats. "Most played" list removed (merged into On repeat).
- **Detail pages** (album, artist, genre, folder, playlist): one shared `DetailHero` (no app bar).
- Not redesigned: Settings, Queue, Stats, Equalizer, Lyrics internals, Onboarding.

## Round 2: performance, fonts, lyrics, settings, queue
Still not compiled in the authoring sandbox (no SDK/Gradle); static checks pass. Run `./gradlew assembleDebug` first.

- **Why it felt heavy, and what changed**
  1. Library: five Room flows each triggered a full sort of every list on the MAIN thread (five sorts per change, with a lowercase string allocated per comparison). Now one `combine(...)` pipeline sorts once on `Dispatchers.Default` with `String.CASE_INSENSITIVE_ORDER`.
  2. `TrackRepositoryImpl.getAllTracks/searchTracks` mapped every entity on the collector's thread -> `flowOn(Default)`. Home's daily mixes were generated on Main -> `flowOn(Default)`.
  3. Release builds had `isMinifyEnabled = false`. R8 + resource shrinking are now on (`proguard-rules.pro` has conservative keeps). TEST THE RELEASE BUILD. Debug builds of Compose apps are always much slower than release.
  4. Removed the full-screen blur behind the player (GPU heavy), lighter shadows, image requests are remembered, list rows no longer own animation objects, Coil uses RGB565 + a bigger memory cache.
- **Fonts**: Poppins (bundled, Latin, Circular-like geometric sans) for the whole UI. Persian: Vazirmatn, fetched by the `downloadPersianFont` Gradle task into `app/src/main/assets/fonts/` on first build (best effort, falls back to system Arabic-script font). Drop the five `Vazirmatn-*.ttf` files there manually if the build machine is offline.
- **Lyrics**: delete is now a visible red button in the lyrics header. The dialog has two clear choices: "Remove wrong lyrics" (instant, app only, no system prompt) and "Also remove from the audio file" (needs write consent).
- **Settings**: grouped cards (Appearance / Library / Lyrics), Equalizer + Stats as big shortcut tiles, whole-row switches. **Queue**: Played / Now playing / Up next sections, opens on the current song, same rows as the rest of the app.


## Round 3: engineering pass (2026-10-12, uncompiled)
- DB v8 (MIGRATION_7_8): indices on tracks/play_events, tracks.albumArtist + filePath, tables lyrics_offsets and resume_positions. Destructive fallback now only for schemas 1-4.
- LibrarySync skips a scan when MediaStore version/generation (+ filters) is unchanged; user rescans pass force = true.
- playback/PlaybackEnhancer: fade between songs (single player, no overlap), ReplayGain via stream metadata, skip silence, sleep-timer fade, long-audio resume (>= 20 min), headset auto-resume. Service uses a small LoadControl and WAKE_MODE_LOCAL.
- data/waveform/WaveformStore: MediaCodec-decoded 160-bin waveform cached in cacheDir/waveforms; PlayerTimeline falls back to the seeded fingerprint.
- Lyrics: offset (lyrics_offsets), enhanced-LRC words (LyricLine.words), LyricsMismatch banner, hidden list + restore.
- Library: ArtistSplitter (prefs: split on/off, separators), DuplicateFinder, LibraryToolsScreen, M3uCodec + PlaylistFileService, BackupManager (JSON v1).
- Removed: Firebase/google-services and secrets plugins, .env.example, metadata.json, unused catalog entries.
- Not done: batch tag edit, per-device EQ, smart playlists, yearly recap, crossfade with overlap, Macrobenchmark-generated profile, sidecar .lrc via SAF.
- First debug targets: Room schema validation, baseline-prof.txt wildcard syntax, PlaybackEnhancer on device, Settings/Lyrics imports.
