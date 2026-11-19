# Releasing

Maintainer guide for pushing updates to GitHub and publishing signed release APKs (with automatic GitHub Release + Telegram channel publishing).

> Never commit the release keystore or passwords to the repository.

## 1. Required GitHub Secrets (Settings → Secrets and variables → Actions)

| Secret | Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64-encoded release keystore (`base64 -w 0 logarhythm-release.keystore`) |
| `KEYSTORE_PASSWORD` | Release keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Key password |
| `TELEGRAM_BOT_TOKEN` | *(Optional)* Telegram Bot token for posting releases to `@LogaRhythmApp` |
| `TELEGRAM_CHANNEL_ID` | *(Optional)* Target Telegram channel ID (e.g. `@LogaRhythmApp`) |
| `GEMINI_API_KEY` | *(Optional)* Used only as a fallback if `docs/telegram/<tag>.md` is not present |

## 2. Prepare Screenshots & Release Notes

Before tagging `v2.1.0`:
1. Capture the updated screenshots on your test device and place them in `docs/screenshots/`:
   - `home.png`, `player.png`, `vinyl.png`, `lyrics.png`
   - `library.png`, `radar.png`, `share-studio.png`, `tag-fixer.png`
2. Review or tweak `docs/telegram/v2.1.0.md` — this single file is automatically used as **both** the GitHub Release body and the Telegram channel announcement.

## 3. Publish a New Release

Push your changes to `main`, then create and push a version tag:

```bash
git add .
git commit -m "release: LogaRhythm v2.1.0 UX overhaul & new features"
git push origin main

git tag -a v2.1.0 -m "Release v2.1.0"
git push origin v2.1.0
```

Alternatively, open **Actions → Build and release APK → Run workflow** on GitHub and enter `2.1.0` (or leave empty to auto-bump the patch version).

### What the Release Workflow Does Automatically
1. Runs unit tests (`:app:testDebugUnitTest`).
2. Decodes your signing keystore and builds signed per-ABI APKs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, and `universal`) with version code/name derived automatically from the git tag.
3. Verifies every APK signature with `apksigner`.
4. Publishes a GitHub Release using `docs/telegram/<tag>.md` (or auto-generated GitHub release notes if that file does not exist).
5. Posts the release announcement and ARM APKs to Telegram (automatically skipped if Telegram secrets are not configured).

## 4. Local Testing Before Push

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

If you want to verify a signed release APK locally:

```bash
apksigner verify --verbose --print-certs LogaRhythm-v2.1.0-arm64-v8a.apk
```

Keep the keystore and passwords safely backed up outside the repository. If the signing key is lost, existing installs cannot update in place.