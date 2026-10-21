<div align="center">

<img src="docs/icon.svg" width="112" height="112" alt="LogaRhythm" />

<h1>LogaRhythm</h1>

<p>A music player that stays out of your way.</p>

<p>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="License: Apache 2.0" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" />
  <a href="https://github.com/Yasinjahantigh/LogaRhythm-Android/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/Yasinjahantigh/LogaRhythm-Android/actions/workflows/ci.yml/badge.svg" /></a>
</p>

<p><a href="#english">English</a> · <a href="#فارسی">فارسی</a></p>

</div>

---

## English

LogaRhythm plays the music you already have. No account, no streaming, no ads — just your files, organized the way you'd expect.

Everything lives on your device: your library, playlists, play history, and lyrics. Nothing leaves unless you ask it to.

### What it does

- Plays local audio (MP3, FLAC, M4A, Ogg) with a queue you can actually drag around
- Synced lyrics from the file itself, a `.lrc` sidecar, or an optional online lookup
- Tag editor that writes safely — staged copy, verified, rolled back on failure
- ReplayGain, 5-band EQ, bass boost, cross-fade, skip silence, sleep timer
- Daily mixes, mood queues, and listening stats — all computed on the device
- Home screen widget, full backup/restore, M3U playlist import/export
- English and Persian UI with proper RTL support
- Material 3 color theming from album art or your wallpaper

### Build

```bash
git clone https://github.com/Yasinjahantigh/LogaRhythm-Android.git
cd LogaRhythm-Android
./gradlew :app:assembleDebug   # Windows: gradlew.bat
```

Requires JDK 21 and Android SDK 36. Gradle wrapper is included.

> If Google Maven is blocked on your network, add `logarhythm.mirrors=true` to `local.properties`.

### Contributing

Bug reports and focused pull requests are welcome. For anything bigger, open an issue first.

### License

Apache 2.0 — see [LICENSE](LICENSE).

---

<div dir="rtl">

## فارسی

LogaRhythm فایل‌های موسیقی‌ای که دارید را پخش می‌کند. بدون حساب کاربری، بدون استریمینگ، بدون تبلیغات — فقط فایل‌هایتان، همان‌طور که انتظار دارید.

همه چیز روی گوشی می‌ماند: کتابخانه، پلی‌لیست‌ها، تاریخچهٔ گوش‌دادن و متن ترانه‌ها. هیچ‌چیزی بیرون نمی‌رود مگر اینکه خودتان بخواهید.

### چه کاری می‌کند

- پخش محلی MP3، FLAC، M4A و Ogg با صف قابل‌جابه‌جایی
- متن همگام از داخل فایل، فایل `.lrc` کنار آهنگ، یا جستجوی اختیاری آنلاین
- ویرایشگر تگ با نوشتن ایمن — کپی موقت، بررسی بایت‌به‌بایت، بازگردانی در صورت خطا
- ReplayGain، اکولایزر پنج‌باندی، bass boost، محوشدن بین آهنگ‌ها، پرش از سکوت، تایمر خواب
- میکس‌های روزانه، صف‌های حال‌وهوایی و آمار شنیدن — همه روی دستگاه
- ویجت صفحهٔ اصلی، پشتیبان‌گیری و بازیابی کامل، ورود و خروج پلی‌لیست M3U
- رابط فارسی و انگلیسی با پشتیبانی درست از راست‌به‌چپ
- تم رنگی Material 3 از کاور آلبوم یا والپیپر

### ساخت

```bash
git clone https://github.com/Yasinjahantigh/LogaRhythm-Android.git
cd LogaRhythm-Android
./gradlew :app:assembleDebug   # ویندوز: gradlew.bat
```

نیاز به JDK 21 و Android SDK 36 دارد. Gradle wrapper همراه پروژه است.

> اگر Google Maven در شبکه‌تان قابل‌دسترس نیست، `logarhythm.mirrors=true` را به `local.properties` اضافه کنید.

### مجوز

Apache 2.0 — فایل [LICENSE](LICENSE) را ببینید.

</div>
