# PROJECT STATE

## File Tree (Paths Only)
- /.env.example
- /.gitignore
- /PROJECT_STATE.md
- /README.md
- /app/.gitignore
- /app/build.gradle.kts
- /app/proguard-rules.pro
- /app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt
- /app/src/main/AndroidManifest.xml
- /app/src/main/java/com/example/musicplayer/MainActivity.kt
- /app/src/main/java/com/example/musicplayer/MusicApplication.kt
- /app/src/main/java/com/example/musicplayer/core/di/AppModule.kt
- /app/src/main/java/com/example/musicplayer/core/di/PlaybackModule.kt
- /app/src/main/java/com/example/musicplayer/core/di/RepositoryModule.kt
- /app/src/main/java/com/example/musicplayer/core/utils/ArtworkColorExtractor.kt
- /app/src/main/java/com/example/musicplayer/core/utils/AudioArtworkFetcher.kt
- /app/src/main/java/com/example/musicplayer/core/utils/PermissionUtils.kt
- /app/src/main/java/com/example/musicplayer/core/utils/Resource.kt
- /app/src/main/java/com/example/musicplayer/core/utils/SearchUtils.kt
- /app/src/main/java/com/example/musicplayer/core/utils/TimeUtils.kt
- /app/src/main/java/com/example/musicplayer/data/datastore/PreferencesManager.kt
- /app/src/main/java/com/example/musicplayer/data/repository/LyricsRepositoryImpl.kt
- /app/src/main/java/com/example/musicplayer/data/repository/PlaylistRepositoryImpl.kt
- /app/src/main/java/com/example/musicplayer/data/repository/StatsRepositoryImpl.kt
- /app/src/main/java/com/example/musicplayer/data/repository/TrackRepositoryImpl.kt
- /app/src/main/java/com/example/musicplayer/data/room/AlbumQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/room/AlbumStatQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/room/ArtistQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/room/ArtistStatQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/room/FolderQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/room/GenreQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/room/LyricsDao.kt
- /app/src/main/java/com/example/musicplayer/data/room/LyricsEntity.kt
- /app/src/main/java/com/example/musicplayer/data/room/MusicDao.kt
- /app/src/main/java/com/example/musicplayer/data/room/MusicDatabase.kt
- /app/src/main/java/com/example/musicplayer/data/room/PlayEventEntity.kt
- /app/src/main/java/com/example/musicplayer/data/room/PlaylistDao.kt
- /app/src/main/java/com/example/musicplayer/data/room/PlaylistEntity.kt
- /app/src/main/java/com/example/musicplayer/data/room/PlaylistQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/room/PlaylistTrackEntity.kt
- /app/src/main/java/com/example/musicplayer/data/room/TrackEntity.kt
- /app/src/main/java/com/example/musicplayer/data/room/TrackStatQueryResult.kt
- /app/src/main/java/com/example/musicplayer/data/sources/LocalFileLyricsSource.kt
- /app/src/main/java/com/example/musicplayer/data/sources/LocalMusicSource.kt
- /app/src/main/java/com/example/musicplayer/data/sources/LrclibLyricsSource.kt
- /app/src/main/java/com/example/musicplayer/data/sources/LyricsOvhLyricsSource.kt
- /app/src/main/java/com/example/musicplayer/data/sources/NeteaseLyricsSource.kt
- /app/src/main/java/com/example/musicplayer/domain/ai/MusicAiEngine.kt
- /app/src/main/java/com/example/musicplayer/domain/lyrics/LrcParser.kt
- /app/src/main/java/com/example/musicplayer/domain/mix/DailyMixGenerator.kt
- /app/src/main/java/com/example/musicplayer/domain/model/Album.kt
- /app/src/main/java/com/example/musicplayer/domain/model/AlbumStat.kt
- /app/src/main/java/com/example/musicplayer/domain/model/Artist.kt
- /app/src/main/java/com/example/musicplayer/domain/model/ArtistStat.kt
- /app/src/main/java/com/example/musicplayer/domain/model/DailyMix.kt
- /app/src/main/java/com/example/musicplayer/domain/model/Folder.kt
- /app/src/main/java/com/example/musicplayer/domain/model/Genre.kt
- /app/src/main/java/com/example/musicplayer/domain/model/LyricLine.kt
- /app/src/main/java/com/example/musicplayer/domain/model/Lyrics.kt
- /app/src/main/java/com/example/musicplayer/domain/model/PlayEvent.kt
- /app/src/main/java/com/example/musicplayer/domain/model/Playlist.kt
- /app/src/main/java/com/example/musicplayer/domain/model/SourceType.kt
- /app/src/main/java/com/example/musicplayer/domain/model/TimeRange.kt
- /app/src/main/java/com/example/musicplayer/domain/model/Track.kt
- /app/src/main/java/com/example/musicplayer/domain/model/TrackStat.kt
- /app/src/main/java/com/example/musicplayer/domain/repository/LyricsRepository.kt
- /app/src/main/java/com/example/musicplayer/domain/repository/PlaylistRepository.kt
- /app/src/main/java/com/example/musicplayer/domain/repository/StatsRepository.kt
- /app/src/main/java/com/example/musicplayer/domain/repository/TrackRepository.kt
- /app/src/main/java/com/example/musicplayer/domain/source/LyricsSource.kt
- /app/src/main/java/com/example/musicplayer/domain/source/MusicSource.kt
- /app/src/main/java/com/example/musicplayer/feature/album/AlbumDetailScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/album/AlbumDetailViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/artist/ArtistDetailScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/artist/ArtistDetailViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/equalizer/EqualizerScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/equalizer/EqualizerViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/folder/FolderDetailScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/folder/FolderDetailViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/genre/GenreDetailScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/genre/GenreDetailViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/home/DailyMixCard.kt
- /app/src/main/java/com/example/musicplayer/feature/home/HomeComponents.kt
- /app/src/main/java/com/example/musicplayer/feature/home/HomeScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/home/HomeViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/library/AlbumsTabContent.kt
- /app/src/main/java/com/example/musicplayer/feature/library/ArtistsTabContent.kt
- /app/src/main/java/com/example/musicplayer/feature/library/FastScroller.kt
- /app/src/main/java/com/example/musicplayer/feature/library/FoldersTabContent.kt
- /app/src/main/java/com/example/musicplayer/feature/library/GenresTabContent.kt
- /app/src/main/java/com/example/musicplayer/feature/library/LibraryEmptyState.kt
- /app/src/main/java/com/example/musicplayer/feature/library/LibraryPermissionState.kt
- /app/src/main/java/com/example/musicplayer/feature/library/LibraryScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/library/LibraryTab.kt
- /app/src/main/java/com/example/musicplayer/feature/library/LibraryUiState.kt
- /app/src/main/java/com/example/musicplayer/feature/library/LibraryViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/library/SongsTabContent.kt
- /app/src/main/java/com/example/musicplayer/feature/library/SortOption.kt
- /app/src/main/java/com/example/musicplayer/feature/library/TrackListItem.kt
- /app/src/main/java/com/example/musicplayer/feature/lyrics/LyricsUiState.kt
- /app/src/main/java/com/example/musicplayer/feature/lyrics/LyricsView.kt
- /app/src/main/java/com/example/musicplayer/feature/lyrics/LyricsViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/onboarding/OnboardingScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/player/FullPlayerControls.kt
- /app/src/main/java/com/example/musicplayer/feature/player/FullPlayerHeader.kt
- /app/src/main/java/com/example/musicplayer/feature/player/FullPlayerScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/player/MiniPlayer.kt
- /app/src/main/java/com/example/musicplayer/feature/player/PlaybackSpeedDialog.kt
- /app/src/main/java/com/example/musicplayer/feature/player/PlayerViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/player/SleepTimerDialog.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/AddToPlaylistDialog.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/PlaylistCard.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/PlaylistDetailScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/PlaylistDetailViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/PlaylistDialogs.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/PlaylistViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/PlaylistsTabContent.kt
- /app/src/main/java/com/example/musicplayer/feature/playlist/SongMenuBottomSheet.kt
- /app/src/main/java/com/example/musicplayer/feature/queue/QueueItem.kt
- /app/src/main/java/com/example/musicplayer/feature/queue/QueueScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/search/RecentSearchesView.kt
- /app/src/main/java/com/example/musicplayer/feature/search/SearchResultsView.kt
- /app/src/main/java/com/example/musicplayer/feature/search/SearchScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/search/SearchUiState.kt
- /app/src/main/java/com/example/musicplayer/feature/search/SearchViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/settings/SettingsComponents.kt
- /app/src/main/java/com/example/musicplayer/feature/settings/SettingsDialogs.kt
- /app/src/main/java/com/example/musicplayer/feature/settings/SettingsScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/settings/SettingsViewModel.kt
- /app/src/main/java/com/example/musicplayer/feature/stats/StatsAlbumsTab.kt
- /app/src/main/java/com/example/musicplayer/feature/stats/StatsArtistsTab.kt
- /app/src/main/java/com/example/musicplayer/feature/stats/StatsComponents.kt
- /app/src/main/java/com/example/musicplayer/feature/stats/StatsScreen.kt
- /app/src/main/java/com/example/musicplayer/feature/stats/StatsTracksTab.kt
- /app/src/main/java/com/example/musicplayer/feature/stats/StatsViewModel.kt
- /app/src/main/java/com/example/musicplayer/playback/EqualizerManager.kt
- /app/src/main/java/com/example/musicplayer/playback/EqualizerState.kt
- /app/src/main/java/com/example/musicplayer/playback/MediaItemMapper.kt
- /app/src/main/java/com/example/musicplayer/playback/PlayScrobbler.kt
- /app/src/main/java/com/example/musicplayer/playback/PlaybackController.kt
- /app/src/main/java/com/example/musicplayer/playback/PlaybackService.kt
- /app/src/main/java/com/example/musicplayer/playback/PlayerController.kt
- /app/src/main/java/com/example/musicplayer/playback/PlayerControllerImpl.kt
- /app/src/main/java/com/example/musicplayer/playback/QueuePersistenceManager.kt
- /app/src/main/java/com/example/musicplayer/playback/SleepTimerManager.kt
- /app/src/main/java/com/example/musicplayer/ui/MainScreen.kt
- /app/src/main/java/com/example/musicplayer/ui/designsystem/AppDesignSystem.kt
- /app/src/main/java/com/example/musicplayer/ui/navigation/MusicBottomBar.kt
- /app/src/main/java/com/example/musicplayer/ui/navigation/MusicNavGraph.kt
- /app/src/main/java/com/example/musicplayer/ui/navigation/NavDestination.kt
- /app/src/main/java/com/example/musicplayer/ui/theme/Color.kt
- /app/src/main/java/com/example/musicplayer/ui/theme/Shape.kt
- /app/src/main/java/com/example/musicplayer/ui/theme/Theme.kt
- /app/src/main/java/com/example/musicplayer/ui/theme/Typography.kt
- /app/src/main/java/com/example/musicplayer/widget/MusicWidget.kt
- /app/src/main/java/com/example/musicplayer/widget/MusicWidgetActions.kt
- /app/src/main/java/com/example/musicplayer/widget/MusicWidgetReceiver.kt
- /app/src/main/java/com/example/musicplayer/widget/MusicWidgetUpdater.kt
- /app/src/main/java/com/example/musicplayer/widget/WidgetArtworkUtils.kt
- /app/src/main/res/drawable/ic_launcher_background.xml
- /app/src/main/res/drawable/ic_launcher_foreground.xml
- /app/src/main/res/drawable/ic_music_placeholder.xml
- /app/src/main/res/drawable/ic_widget_next.xml
- /app/src/main/res/drawable/ic_widget_pause.xml
- /app/src/main/res/drawable/ic_widget_play.xml
- /app/src/main/res/drawable/ic_widget_previous.xml
- /app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- /app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
- /app/src/main/res/mipmap-hdpi/ic_launcher.webp
- /app/src/main/res/mipmap-hdpi/ic_launcher_round.webp
- /app/src/main/res/mipmap-mdpi/ic_launcher.webp
- /app/src/main/res/mipmap-mdpi/ic_launcher_round.webp
- /app/src/main/res/mipmap-xhdpi/ic_launcher.webp
- /app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp
- /app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
- /app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp
- /app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp
- /app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
- /app/src/main/res/values-fa/strings.xml
- /app/src/main/res/values/colors.xml
- /app/src/main/res/values/strings.xml
- /app/src/main/res/values/themes.xml
- /app/src/main/res/xml/backup_rules.xml
- /app/src/main/res/xml/data_extraction_rules.xml
- /app/src/main/res/xml/music_widget_info.xml
- /app/src/test/java/com/example/ExampleRobolectricTest.kt
- /app/src/test/java/com/example/ExampleUnitTest.kt
- /app/src/test/java/com/example/musicplayer/core/utils/TimeUtilsTest.kt
- /app/src/test/java/com/example/musicplayer/data/room/MusicDaoTest.kt
- /app/src/test/java/com/example/musicplayer/data/room/PlaylistDaoTest.kt
- /app/src/test/java/com/example/musicplayer/data/room/StatsAggregationTest.kt
- /app/src/test/java/com/example/musicplayer/domain/lyrics/LrcParserTest.kt
- /app/src/test/java/com/example/musicplayer/domain/mix/DailyMixGeneratorTest.kt
- /app/src/test/java/com/example/musicplayer/playback/SleepTimerManagerTest.kt
- /build.gradle.kts
- /debug.keystore
- /debug.keystore.base64
- /gradle.properties
- /gradle/libs.versions.toml
- /gradle/wrapper/gradle-wrapper.properties
- /metadata.json
- /settings.gradle.kts

## Key Decisions
1. **Architecture & Dependency Flow**: Strict clean layering (`ui -> domain <- data`). UI talks exclusively to domain interfaces and the player controller wrapper; UI never touches Room or ExoPlayer directly.
2. **Music Source Abstraction**: Every track identifier is prefixed with source origin (`local:123`, `web:abc`), decoupling playback orchestration from storage medium.
3. **Dedicated MediaItem Conversion**: `MediaItemMapper` is the exclusive single conversion boundary between domain `Track` and Media3 `MediaItem`.
4. **MediaSessionService & System Integration**: Background playback is powered by `PlaybackService` (`MediaSessionService`) with ExoPlayer handling audio focus (`setAudioAttributes` with `handleAudioFocus=true`) and pause on headphone disconnection (`setHandleAudioBecomingNoisy=true`).
5. **MediaController Wrapper**: `PlayerControllerImpl` encapsulates `MediaController` connection lifecycle with queuing for commands before session binding, exposing pure Kotlin `StateFlow` primitives to ViewModels.
6. **Room Migration & Zero-Loss Data Protection**: Explicit manual migrations (`MIGRATION_1_2`) with `addMigrations` instead of destructive wipes. User-generated content (custom playlists, playlist tracks, play events, user-edited lyrics) is strictly protected. Cached MediaStore track metadata can be safely refreshed without affecting user tables.
7. **Audiofx Equalizer with Hardware Graceful Fallback**: `EqualizerManager` handles devices with missing or restricted audio effects gracefully (`hasControl()`, null-safety checks, catch-all `IllegalArgumentException` / `UnsupportedOperationException`). Hardware sliders auto-disable with clean UI message if BassBoost or Virtualizer are unsupported.
8. **Asynchronous Background Artwork & Color Extraction**: Album artwork extraction and palette computation run completely on `Dispatchers.IO` / `Dispatchers.Default` off the main thread, caching palettes to prevent UI jank.
9. **Multi-Source Hierarchical Synced Lyrics Engine**: 
   - Step 1: Check embedded ID3/MP4 metadata inside the audio file itself (`LocalFileLyricsSource`).
   - Step 2: Check local companion `.lrc` file beside audio file or user custom imported file.
   - Step 3: Check cached synced lyrics in Room `lyrics` table.
   - Step 4: If enabled in settings, query online API (e.g. LRCLIB synced lyrics).
   - Fallback: Allow manual entry in UI with instant embedding into local database and export to companion `.lrc`.
10. **On-Device Music Curation (`MusicAiEngine`)**: Offline metadata-based energy/mood estimates, date-seeded mixes, and track recommendations scored by metadata-derived distance. This is heuristic curation, not audio-signal fingerprinting or a learned model.
11. **100% Bilingual & RTL Support**: Complete parity across English (`strings.xml`) and Persian (`values-fa/strings.xml`) with native RTL directionality.

## What Is Done
- Phase 0: Project skeleton, dependencies, theming, navigation, and DI setup.
- Phase 1: Local Library & MediaStore integration with runtime permissions and background differential rescan.
- Phase 2: Media3 ExoPlayer & MediaSessionService playback core, MediaController wrapper, MiniPlayer and FullPlayer UI.
- Phase 3: Browsing (Songs, Albums, Artists, Folders) and search with Persian normalization and recent search history.
- Phase 4: Queue, playlists & built-in favorites in Room, drag-and-drop reordering, swipe-to-remove, and persistent queue state.
- Phase 5: Statistics & Insights: pure SQLite aggregations on `play_events`, time-range filtering, and dynamic Home dashboard.
- Phase 6: Synced Lyrics (.lrc parser, embedded ID3 reader, local companion LRC loader, LRCLIB API client, manual entry dialog, auto-scrolling lyrics view).
- Phase 7: Extras: background sleep timer, audio session-bound equalizer with frequency band sliders, hardware-safe BassBoost & Virtualizer, native ExoPlayer speed control.
- Phase 8: Offline date-seeded Daily Mixes, personalization settings (theme, dynamic album art gradient, corner roundness, track length filter, excluded folders).
- Phase 9: Glance home screen widget, first-run onboarding screen, string auditing and polish across English & Persian locales.
- Overhaul Phase:
  - MiniPlayer full controls (Previous, Play/Pause, Next), horizontal swipe gesture, marquee text.
  - FullPlayer 10s rewind/forward, album art swipe gesture, remaining time toggle, format badge.
  - Complete Genres layer (MediaStore scanner, Room schema, repository, UI tab, and GenreDetailScreen).
  - FastScroller with alphabetical floating bubble index.
  - Multi-criteria library sorting bottom sheet (Title, Artist, Album, Date Added, Duration - Asc/Desc).
  - Active track visual indicator and equalizer icon in track lists.
  - Real album art dynamic theming generating full Material 3 ColorScheme from playing track.
  - Graphic interactive Canvas frequency curve in Equalizer plus Bass Boost and Virtualizer 3D audio controls.
  - Room migration protection (`MIGRATION_1_2`) preserving user playlists, favorites, and history.
  - Safe Audiofx hardware capability detection for older / budget devices.
  - Off-main-thread image decoding and palette extraction.
  - Multi-source Synced Lyrics with embedded tag reading and interactive user manual entry.
  - On-device metadata-based mood playlists and similar-track recommendations, with Persian keyword normalization and no recommendation-service network calls.
  - **Batch 1 (Design System, Layering & Insets Fix, Unified Rows, Rebalanced MiniPlayer, Clean Library Defaults)**:
    - Centralized design system tokens (`AppSpacing`, `AppShapes`, `AppDimensions`, `AppMotion`, `LocalPlaybackBottomPadding`).
    - Fixed bottom window insets/padding across all screens (`MainScreen`, `LibraryScreen`, `HomeScreen`, `SearchScreen`, `PlaylistDetailScreen`, `AlbumDetailScreen`, `ArtistDetailScreen`, `FolderDetailScreen`, `GenreDetailScreen`, `StatsScreen`, `QueueScreen`). Content now scrolls smoothly underneath floating mini player and bottom bar without hard clips or empty gaps.
    - FullPlayerScreen moved to full-screen top overlay layer with 100% opaque surface background, eliminating Settings screen bleed-through and duplicate mini player.
    - Created unified `TrackListItem` with fixed 52dp artwork, fixed text offset, localized metadata fallbacks (`R.string.unknown_title`, `R.string.unknown_artist`), and duration preservation (`Modifier.weight(1f, fill = false)` for artist so duration is never truncated).
    - Rebalanced `MiniPlayer` with 40dp play button, 36dp prev/next buttons, 44dp artwork, marquee title, and subtle progress indicator.
    - Updated `LocalMusicSource` & `PreferencesManager` defaults: 60s minimum track length, default excluded folders (`WhatsApp Audio`, `Telegram Audio`, `Recordings`, `Ringtones`, `Notifications`, `Alarms`), and MediaStore `<unknown>` sanitization.
    - Fixed Material 3 selected vs unselected tab colors in `LibraryScreen` and `StatsScreen`.
  - **Batch 2 (Complete Menu Audit, Bottom Player Space Optimization, and Multi-Source Lyrics Engine)**:
    - **Bottom Player Space Optimization**:
      - Hides `MusicBottomBar` completely on all sub-screens/detail pages (`AlbumDetailScreen`, `ArtistDetailScreen`, `FolderDetailScreen`, `GenreDetailScreen`, `PlaylistDetailScreen`, `EqualizerScreen`, etc.), freeing 80dp of vertical space.
      - MiniPlayer reduced to 56dp height (48dp in compact mode), 40dp artwork, compact controls.
      - Added direct dismiss/close button (`X`) on the MiniPlayer to hide it completely on demand without stopping background audio.
      - Added user preference toggle in Settings for Compact MiniPlayer.
    - **Comprehensive Menu Audit**:
      - `SongMenuBottomSheet` updated with full, consistent actions: Play Next, Add to Queue, Favorite Toggle, Add to Playlist, Remove from Playlist (contextual), View/Edit Lyrics, Search Lyrics on Google, Go to Album, Go to Artist, Song Information / Details Dialog, Share Song via Android Share Sheet, and Delete Song from Device with confirmation.
      - Detail screens (`AlbumDetailScreen`, `ArtistDetailScreen`, `FolderDetailScreen`, `GenreDetailScreen`, `PlaylistDetailScreen`, `SearchResultsView`, `SongsTabContent`, and `HomeScreen`) all wired to full song menu.
      - Added top app bar "More Options" menu to `FullPlayerHeader` providing quick access to Equalizer, Add to Playlist, Google Lyrics Search, Song Details, and Share Song.
    - **Enhanced Multi-Source Lyrics Engine**:
      - Fixed online lyrics lookup with dual-phase query: exact sanitized match followed by broad query search across LRCLIB.
      - Added multi-source fallback to Netease Cloud Music and Lyrics.ovh.
      - Added 1-tap "Search Lyrics on Google" button directly formatting a clean web search query.
      - Added "Paste from Clipboard & Save" button in `LyricsView` and `ManualLyricsDialog`.
      - Added full ability to remove registered lyrics with confirmation dialog. When deleted, a tombstone is recorded in Room so deleted lyrics do not unexpectedly re-download automatically, and companion `.lrc` file on disk is deleted.
      - Added file embedding: when lyrics are imported, saved manually, pasted, or downloaded, they are written to a companion `.lrc` file directly in the song's directory on disk.
      - Added "Export to .lrc file" via Android file picker.

## Recent Product Quality Pass — 2026-10-09
- Fixed smart mood-playback lifecycle: a mood tap now reads one current library snapshot instead of leaving a database Flow collector alive that could restart playback after later library updates.
- Localized all five mood mix labels and added a localized snackbar message for an empty result.
- Replaced generic scaffold branding with LogaRhythm in the app label, onboarding welcome, and widget labels.
- Replaced the stock Android Studio launcher graphic with a custom waveform mark on a midnight gradient background.
- Removed dependence on an ignored project-local debug keystore; debug builds now use the Android Gradle Plugin default signing key.
- Clarified that the on-device recommendation engine uses metadata heuristics, not true signal-level audio fingerprinting. Added Persian keyword normalization and mood cues, plus guards for non-positive playlist/recommendation limits.
- Added focused JVM tests for empty-library handling, requested playlist limits, and duplicate-free results.
- Rewrote README with product scope, privacy model, architecture, and accurate setup instructions.

## Known Issues
- Online website streaming source is planned for a future online phase.
- The source archive omits `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar`; generate them with Gradle 9.3.1 before using the wrapper commands.

## Exact Signatures

### 1. MusicSource
```kotlin
package com.example.musicplayer.domain.source

import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicSource {
    val sourceType: SourceType
    fun getTracks(): Flow<List<Track>>
    suspend fun getTrackById(id: String): Track?
    suspend fun searchTracks(query: String): List<Track>
    suspend fun getPlaybackUri(track: Track): String
}
```

### 2. LyricsSource
```kotlin
package com.example.musicplayer.domain.source

import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track

interface LyricsSource {
    val name: String
    suspend fun getLyrics(track: Track): Lyrics?
}
```

### 3. TrackRepository
```kotlin
package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Folder
import com.example.musicplayer.domain.model.Genre
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getAllTracks(): Flow<List<Track>>
    fun getTrackById(id: String): Flow<Track?>
    fun searchTracks(query: String): Flow<List<Track>>
    fun searchAlbums(query: String): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>
    fun searchGenres(query: String): Flow<List<Genre>>
    fun getAlbums(): Flow<List<Album>>
    fun getArtists(): Flow<List<Artist>>
    fun getFolders(): Flow<List<Folder>>
    fun getGenres(): Flow<List<Genre>>
    fun getTracksByAlbum(album: String): Flow<List<Track>>
    fun getTracksByArtist(artist: String): Flow<List<Track>>
    fun getTracksByFolder(folder: String): Flow<List<Track>>
    fun getTracksByGenre(genre: String): Flow<List<Track>>
    fun getAlbumByName(album: String): Flow<Album?>
    fun getArtistByName(artist: String): Flow<Artist?>
    fun getGenreByName(genre: String): Flow<Genre?>
    suspend fun refreshTracks()
}
```

### 4. StatsRepository
```kotlin
package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.AlbumStat
import com.example.musicplayer.domain.model.ArtistStat
import com.example.musicplayer.domain.model.PlayEvent
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackStat
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    suspend fun recordPlayEvent(event: PlayEvent)
    fun getPlayEventsForTrack(trackId: String): Flow<List<PlayEvent>>
    fun getAllPlayEvents(): Flow<List<PlayEvent>>
    fun getTotalListenTimeMs(sinceTimestamp: Long = 0L): Flow<Long>
    fun getTopTracks(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<TrackStat>>
    fun getTopArtists(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<ArtistStat>>
    fun getTopAlbums(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<AlbumStat>>
    fun getRecentlyPlayedTracks(limit: Int = 20): Flow<List<Track>>
    fun getMostPlayedTracks(limit: Int = 20): Flow<List<Track>>
}
```

### 5. PlaylistRepository
```kotlin
package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(playlistId: Long): Flow<Playlist?>
    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)
    suspend fun reorderPlaylistTracks(playlistId: Long, fromIndex: Int, toIndex: Int)
    fun isTrackFavorite(trackId: String): Flow<Boolean>
    suspend fun toggleFavorite(trackId: String)
}
```

### 6. LyricsRepository
```kotlin
package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    fun getLyrics(track: Track): Flow<Lyrics?>
    suspend fun loadLyrics(track: Track): Lyrics?
    suspend fun saveLyrics(track: Track, content: String, source: String): Boolean
    suspend fun deleteLyrics(trackId: String)
    suspend fun deleteLyrics(track: Track)
    suspend fun fetchOnlineLyrics(track: Track): Lyrics?
}
```

### 7. Playback Controller Wrapper (PlayerController & PlaybackController)
```kotlin
package com.example.musicplayer.playback

import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    val currentTrack: StateFlow<Track?>
    val isPlaying: StateFlow<Boolean>
    val position: StateFlow<Long>
    val duration: StateFlow<Long>
    val shuffle: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>
    val queue: StateFlow<List<Track>>

    // Phase 7 & Overhaul: Extras
    val playbackSpeed: StateFlow<Float>
    val sleepTimerRemainingMs: StateFlow<Long?>
    val isSleepTimerEndOfTrack: StateFlow<Boolean>
    val equalizerState: StateFlow<EqualizerState>

    fun play(list: List<Track>, startIndex: Int = 0)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()

    fun playNext(track: Track)
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int): Track?
    fun insertIntoQueue(index: Int, track: Track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun playQueueItem(index: Int)
    fun clearQueue()

    // Extras
    fun setPlaybackSpeed(speed: Float)
    fun setSleepTimer(minutes: Int)
    fun setSleepTimerEndOfTrack()
    fun cancelSleepTimer()
    fun setEqualizerEnabled(enabled: Boolean)
    fun setEqualizerPreset(presetIndex: Short)
    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short)
    fun setBassBoostStrength(strength: Short)
    fun setVirtualizerStrength(strength: Short)
    fun resetEqualizer()
}

interface PlaybackController : PlayerController {
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    fun playTrack(track: Track)
    fun playQueue(tracks: List<Track>, startIndex: Int = 0)
    fun skipToNext()
    fun skipToPrevious()
    fun stop()
}
```

## Product Quality Pass 2 — 2026-10-09
- Added a one-tap “Shuffle all” Home action. It uses the shared library state, and enables Media3 shuffle mode rather than merely randomizing the displayed list.
- Added a true Recently Added section based on `MediaStore.Audio.Media.DATE_ADDED`, not the file modification timestamp.
- Bumped Room schema to v6 with a 5→6 migration that adds `dateAdded` and backfills older rows from `dateModified`; registered the migration so v5 user data is retained.
- Hardened MediaStore scanning: a null cursor, permission loss, or query failure now fails the scan instead of returning an empty list. Because repository deletion happens only after a complete scan, failed scans no longer erase the saved library index.
- Added a buffered refresh-error event and localized snackbar feedback; cancellation still propagates normally.
- Added pure JVM tests for Recently Added sorting, tie-breaking, result limits, and non-positive limits.
- Added `gradle/wrapper/gradle-wrapper.properties`; the wrapper launchers and JAR remain absent from the original archive.

## Verification Notes — Pass 2
- Environment inspection found Java 21 but no Gradle executable, Gradle wrapper launcher/JAR, Android SDK, SDK manager, or ADB.
- DNS resolution for both Gradle distributions and Google's Android command-line tools failed inside the build environment, so downloading the missing toolchain was not possible here.
- A debug APK and Android/Room integration tests are therefore not verified yet. Run `./gradlew assembleDebug` and `./gradlew :app:testDebugUnitTest` after generating the wrapper and installing the Android SDK platform/build tools specified by the Gradle configuration.

## Final Verification Update — 2026-10-09
- Fixed a fast-tap edge case in mood playback: it now requests the current library emission from the repository instead of possibly reading the StateFlow's initial empty placeholder.
- Re-ran the standalone Kotlin smoke suite after the change: Recently Added ordering/ties/limits, Persian mood scoring, and the recommendation limit guard passed.
- Re-ran resource XML checks and executed the Room 5→6 SQL against a sample v5 database; XML/key consistency and row-preserving timestamp backfill passed.
- Attempted `./gradlew assembleDebug` and `gradle :app:assembleDebug`; both are blocked because the launcher and Gradle executable are absent. `sdkmanager`/`adb` are absent too, and DNS lookup for `services.gradle.org` and `dl.google.com` fails. No APK, full Android compilation, or runtime playback has been verified in this environment.


## Product Quality Pass 3 — 2026-10-09
- Added an explicit `playRadio(seedTrack, similarTracks)` playback-controller operation. The player’s More menu now offers “Start radio from this song,” computes up to 12 recommendations from the current local library, and reports whether a radio could be started. The user-facing message clearly says the prior queue is replaced.
- Radio starts with the seed song, preserves its current seek position when possible, clears shuffle/repeat to make sequence order predictable, updates queue persistence, and handles duplicate track IDs.
- Fixed a playback-position regression: queue rebuild/restore can emit a media transition for the same track; the controller no longer resets position unless the track ID really changes.
- Made play-scrobbling track-aware. Rebuilding a queue around the same playing song no longer resets its listen threshold, while an explicit play action starts a fresh listen session.
- Improved metadata-based recommendations: mood scoring now includes acoustic character and estimated danceability, recognizes more English/Persian terms, normalizes Arabic/Persian variants and diacritics, and sequences artist/album-diverse results while keeping mood relevance primary. Recommendation APIs deduplicate tracks and support excluded IDs.
- Fixed search result staleness: a query change now invalidates the previous results immediately, presents a loading state, and debounces database queries rather than letting results from the old query appear under the new one. Empty searches include a clear-search recovery action and a localized hint.
- Localized album/artist metadata in search results and hardened Home discovery cards against blank titles, blank artwork URI, and artwork loading failures.
- Added JVM tests for acoustic metadata, acoustic-mood ranking, artist diversity, excluded radio candidates, and Arabic/Persian search normalization.

## Verification Notes — Pass 3
- Compiled the actual `MusicAiEngine.kt` and `SearchUtils.kt` with `kotlinc`.
- Ran a standalone Kotlin smoke harness against those production sources: acoustic-vs-electronic scoring, Acoustic Morning selection, radio exclusions and limits, unique recommendations, and Arabic/Persian normalization passed. The observed sample radio sequence avoided repeating the seed artist when comparable alternatives were available.
- Still not verified: full Gradle/JUnit suite, Compose/Media3 integration compilation, Android runtime playback, and actual queue/seek restoration on a device. No Android build-toolchain investigation was performed in this pass, per task scope.


## Product Quality Pass 4 — 2026-10-09
- Replaced the oversized “Shuffle all” Home hero with a time-aware listening card: start a queue sized for 15, 30, or 60 minutes, or still shuffle the full library from the same entry point.
- Added a pure-Kotlin `ListeningSessionPlanner`. It filters out unplayable/invalid-duration tracks, prevents duplicates, stays at or below the target duration, gently favors tracks from the current top daily mix, and penalizes artist repetition.
- Added explicit `setShuffleEnabled` and `setRepeatMode` operations to `PlayerController`; timed sessions disable shuffle and repeat so the finite queue and intended order are preserved, while Shuffle All enables shuffle explicitly.
- Reworked the three offline daily mixes into distinct intents: familiar rotation, rediscovery, and lower-played discovery. Their track selection is stable for the supplied day seed, prioritizes non-overlapping tracks between mixes when the library is large enough, and interleaves artists to reduce repetition.
- Redesigned daily-mix cards to use a 2×2 collage of actual library cover art (with fallbacks), localized mix titles and fallback descriptions, and clearer play affordance.
- Reordered Home around quick listening, moods, daily mixes, and recent activity; moved listening statistics below the main discovery content.
- Added unit tests for session time bounds, duplicates, invalid tracks/targets, mix preference, mix intent names, and non-overlap across a sufficiently large library.

## Verification Notes — Pass 4
- Compiled and executed the actual pure-Kotlin production sources for `ListeningSessionPlanner` and `DailyMixGenerator` with a standalone smoke harness. Time-bound session generation, current-mix preference, same-seed stability, daily variation, distinct mix intents, non-overlap for a 60-track library, and prior generator regression expectations passed.
- Kotlin parser check on the touched Compose/ViewModel/controller files detected no syntax errors. This is not a substitute for Android/Compose type-checking.
- Parsed all 16 resource XML files; all 300 English/Persian string keys match, all formatted placeholders are compatible, and the Home feature's string references are present.
- No Gradle, Android SDK, APK generation, or device-runtime checks were attempted in this pass, per request. Full Compose compilation and real playback integration remain unverified.
