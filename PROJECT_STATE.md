# LogaRhythm Product State - 2026-10-13

## Build & Release Pipeline Status (2026-10-13)
- **Debug Build**: Green (`gradle :app:assembleDebug` completed successfully).
- **Unit Tests**: Green (104 / 104 unit tests passed, including `MusicDatabaseMigrationTest`, `SearchUtilsTest`, `ArtistSplitterTest`, `TimeUtilsTest`, `LrcParserTest`, `ReplayGainTest`, etc.).
- **Release Build**: Green with R8 minification and resource shrinking enabled (`gradle :app:assembleRelease` built successfully).
- **GitHub Actions Release Pipeline**:
  - `.github/workflows/ci.yml`: runs `assembleDebug`, `testDebugUnitTest`, `lintDebug` on pushes/PRs.
  - `.github/workflows/release.yml`: triggers on tag `v*` or `workflow_dispatch`. Decodes `KEYSTORE_BASE64` secret into `$RUNNER_TEMP/release.jks`, executes unit tests, builds signed release APK with `REQUIRE_RELEASE_SIGNING: "true"`, verifies APK signature with `apksigner`, renames to `LogaRhythm-<version>.apk`, uploads artifact, and creates GitHub Release with auto-generated release notes.
- **Keystore & Versioning**:
  - `app/build.gradle.kts`: reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from environment/gradle properties. Fails in CI if `REQUIRE_RELEASE_SIGNING` is set without secrets; falls back to debug signing only for local builds.
  - `versionName` and `versionCode` derived deterministically from git tag (e.g., `v1.3.0` -> `versionName "1.3.0"`, `versionCode 10300`).
- **Documentation**:
  - `docs/RELEASING.md`: comprehensive Persian step-by-step release and signing guide.

## Part A - Fixed & Verified Requirements
- **Build Fixes**: Resolved all conflicting overloads (`AlbumsTabContent`, `ArtistsTabContent`, `FoldersTabContent`, `GenresTabContent` consolidated into `LibraryCollections.kt`), missing `withStyle` import in `LyricsView.kt`, duplicate `Locale` import in `FullPlayerScreen.kt`, `compileSdk = 36`, and duplicate permission in `AndroidManifest.xml`.
- **Room Database (v8)**:
  - Enabled `exportSchema = true` in `MusicDatabase.kt`.
  - Added complete migrations `MIGRATION_1_2` through `MIGRATION_7_8` and exported `ALL_MIGRATIONS` array so no old user is left without a migration path.
  - Verified with `MusicDatabaseMigrationTest`.
- **Playback & Audio Enhancements**:
  - `PlaybackEnhancer`: ReplayGain parsing fails safe (`null` -> 1f unity volume). Volume fading and skip silence operate safely without leaving volume stuck at 0. Headset reconnect resume only triggers if paused by `AUDIO_BECOMING_NOISY`.
- **Waveform Computation**:
  - `WaveformStore`: serialised, cancellable, low-priority background decoding for current track (160 bins) with fallback to fingerprint.
- **Backup & Restore**:
  - `BackupManager`: JSON v1 versioned format. Restores inside Room `withTransaction`. Validates format token (`logarhythm-backup`), ensuring corrupt or malformed backup files never wipe existing database state.

## Remaining Manual Steps for Maintainer
1. Generate release keystore with `keytool`:
   `keytool -genkeypair -v -keystore logarhythm-release.keystore -alias logarhythm-key -keyalg RSA -keysize 2048 -validity 10000 -storetype JKS`
2. Encode keystore to base64:
   `base64 -w 0 logarhythm-release.keystore > keystore_base64.txt` (or OS equivalent)
3. Add the 4 GitHub repository secrets in **Settings > Secrets and variables > Actions**:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
4. Create and push first release tag:
   `git tag v1.0.0 && git push origin v1.0.0`
