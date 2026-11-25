# Releasing

Maintainer checklist for publishing signed LogaRhythm releases to GitHub Releases, Myket, and Telegram.

> Never commit the release keystore (`*.keystore`, `*.jks`) or signing passwords to the repository.

## 1. Required GitHub Secrets (`Settings → Secrets and variables → Actions`)

| Secret | Purpose |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64-encoded release keystore (`base64 -w 0 logarhythm-release.keystore`) |
| `KEYSTORE_PASSWORD` | Release keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Key password |
| `TELEGRAM_BOT_TOKEN` | *(Optional)* Telegram Bot token for `@LogaRhythmApp` announcements |
| `TELEGRAM_CHANNEL_ID` | *(Optional)* Target Telegram channel (`@LogaRhythmApp`) |
| `GEMINI_API_KEY` | *(Optional)* Fallback summarizer when `docs/telegram/<tag>.md` is absent |

## 2. Prepare Release Notes & Channel Announcement

Release notes and Telegram channel posts are maintained separately:

1. **GitHub Release & In-App Updater** (`docs/releases/<tag>.md`):
   - Include a `## What's New` English section first and a `## فارسی` Persian section below it using plain Markdown bullets.
   - GitHub displays the English section at the top; inside the app (`UpdateDialog`), English and Persian users automatically see only the section matching their app language.
2. **Telegram Channel Announcement** (`docs/telegram/<tag>.md`):
   - Dedicated Persian announcement for `@LogaRhythmApp` posted alongside the ARM APKs by `.github/scripts/telegram_release.py`.

## 3. Publish a New Release

Push your changes to `main`, then tag and push the release:

```bash
git push origin main
git tag -a v2.2.0 -m "Release v2.2.0"
git push origin v2.2.0
```

Alternatively, trigger **Actions → Build and release APK → Run workflow** on GitHub and enter `2.2.0`.

### Automated Workflow Steps
1. Runs `:app:testDebugUnitTest` and `:app:lintDebug`.
2. Builds signed GitHub split APKs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, `universal`) with `versionName` / `versionCode` derived from the tag.
3. Builds the signed Myket universal APK (`-PdistributionChannel=myket`) and asserts `REQUEST_INSTALL_PACKAGES` is absent from `stripInstallPermissionRelease/AndroidManifest.xml`.
4. Verifies every APK signature with `apksigner`.
5. Publishes the GitHub Release using `docs/releases/<tag>.md`.
6. Posts the Persian announcement from `docs/telegram/<tag>.md` and ARM APKs to Telegram.

## 4. Local Verification & Manual Builds

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease -PdistributionChannel=myket
```

Verify any signed APK locally:

```bash
apksigner verify --verbose --print-certs LogaRhythm-v2.2.0-myket-universal.apk
```

Keep the release keystore backed up securely outside the repo. Losing it prevents in-place updates for existing installs.
