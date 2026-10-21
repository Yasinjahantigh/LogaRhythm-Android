<div align="center">

<img src="docs/icon.svg" width="112" height="112" alt="LogaRhythm" />

<h1>LogaRhythm</h1>

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

There's a version of music listening that doesn't require an account, a subscription, or trusting a company with your taste. LogaRhythm is that version — a local music player for Android that treats your files as the source of truth and keeps everything else on the device.

Your library, your play history, your playlists, your edited tags, your lyrics: none of it leaves the phone.

### What's actually in it

**Playback** that handles the small things properly — cross-fade between songs (speed-aware), skip silence, headphones reconnect and resume within 10 minutes, per-song playback speed, sleep timer by minutes or by track count.

**ReplayGain** across MP3, FLAC, M4A and Ogg. Track and album modes, user preamp, 5-band EQ, bass boost. Volume levels between albums finally behave.

**Lyrics** from wherever they exist — embedded in the file, a `.lrc` beside it, or fetched from LRCLIB, NetEase, Kugou and others. Synced scrolling, per-song timing offset, manual editing, import and export.

**Tag editing** that doesn't guess the format from the extension. The first 12 bytes of the file decide — ID3/MP3, FLAC, Ogg, or MP4. Writes to a staged copy, verifies it, and only then replaces the original. Rolls back on failure.

**Resume position** for anything over 20 minutes. Position saved every 10 seconds, restored on re-open.

**Discovery on the device** — daily mixes, mood queues (late-night calm, deep focus, energy, rainy day, acoustic morning), artist radio, and listening stats by week, month or all time. No server involved.

**Library tools** — duplicate finder, missing tags view, hidden songs, excluded folders, artist splitting for "A feat. B" tracks, minimum track length filter so voice notes don't clutter the library.

**Full backup and restore** — playlists, favorites, history, lyrics, all in one file.

**Home screen widget** — artwork, play/pause, previous, next.

**Persian and English** — complete localization in both directions, RTL layouts, switchable in-app.

### Design

The default palette is called **Ink & Ember**: a warm near-black surface (`#0E1013`), amber accent (`#F4B266`), slate and sage for secondary roles. Something like a stage lit from one side. You can also let the playing track's artwork set the colors, or hand it to Material You and your wallpaper.

### Build

```bash
git clone https://github.com/Yasinjahantigh/LogaRhythm-Android.git
cd LogaRhythm-Android
./gradlew :app:assembleDebug
```

Requires JDK 21 and Android SDK 36. Gradle wrapper included. On Windows: `gradlew.bat`.

> If Google Maven is unreachable on your network, add `logarhythm.mirrors=true` to `local.properties`.

### License

Apache 2.0 — see [LICENSE](LICENSE).

---

<div dir="rtl">

## فارسی

یه جور گوش‌دادن به موسیقی هست که نه حساب کاربری می‌خواهد، نه اشتراک، نه اینکه به یک شرکت اعتماد کنید. LogaRhythm همان است — یک پخش‌کنندهٔ موسیقی محلی برای اندروید که فایل‌هایتان را مرجع اصلی می‌داند و همه چیز را روی گوشی نگه می‌دارد.

کتابخانه، تاریخچهٔ پخش، پلی‌لیست‌ها، تگ‌های ویرایش‌شده، متن ترانه‌ها — هیچ‌کدام از گوشی بیرون نمی‌روند.

---

### چه چیزهایی واقعاً داخلش است

**پخش** که کارهای کوچک را درست انجام می‌دهد — محوشدن بین آهنگ‌ها (با توجه به سرعت پخش)، پرش از سکوت، از سرگیری پخش بعد از اتصال دوبارهٔ هدفون (تا ۱۰ دقیقه)، تنظیم سرعت پخش، تایمر خواب با دقیقه یا تعداد آهنگ.

**ReplayGain** برای MP3، FLAC، M4A و Ogg. حالت آهنگ و آلبوم، پیش‌تقویت دستی، اکولایزر پنج‌باندی و bass boost. اختلاف صدا بین آلبوم‌ها بالاخره درست می‌شود.

**متن ترانه** از هر جایی که وجود داشته باشد — داخل فایل، یک `.lrc` کنارش، یا دریافت از LRCLIB، NetEase، Kugou و بقیه. اسکرول همگام، تنظیم زمان به‌ازای هر آهنگ، ویرایش دستی، ورود و خروج.

**ویرایش تگ** که فرمت را از پسوند فایل حدس نمی‌زند. ۱۲ بایت اول فایل تصمیم می‌گیرد — ID3/MP3، FLAC، Ogg یا MP4. روی یک نسخهٔ موقت می‌نویسد، بررسی می‌کند، و فقط بعد از آن فایل اصلی را جایگزین می‌کند. در صورت خطا برمی‌گردد.

**موقعیت پخش** برای هر چیزی بیشتر از ۲۰ دقیقه. هر ۱۰ ثانیه ذخیره می‌شود و دفعهٔ بعد از همانجا ادامه می‌دهد.

**کشف موسیقی روی دستگاه** — میکس‌های روزانه، صف‌های حال‌وهوایی (آرامش نیمه‌شب، تمرکز عمیق، انرژی، روز بارانی، صبح آکوستیک)، رادیوی هنرمند و آمار شنیدن. بدون سرور.

**ابزار کتابخانه** — یافتن تکراری‌ها، نمایش تگ‌های ناقص، مخفی‌کردن آهنگ، استثنا کردن پوشه، جداکردن «هنرمند الف feat. ب»، فیلتر حداقل مدت آهنگ تا پیام‌های صوتی کتابخانه را پر نکنند.

**پشتیبان‌گیری و بازیابی کامل** — پلی‌لیست‌ها، علاقه‌مندی‌ها، تاریخچه، متن ترانه‌ها، همه در یک فایل.

**ویجت صفحهٔ اصلی** — کاور، پخش/مکث، قبلی، بعدی.

**فارسی و انگلیسی** — هر دو زبان کامل، چیدمان راست‌به‌چپ، قابل‌تغییر از داخل اپ.

---

### طراحی

پالت پیش‌فرض **Ink & Ember** نام دارد: یک نزدیک‌به‌مشکی گرم (`#0E1013`)، لهجهٔ کهربایی (`#F4B266`)، سربی و مریم‌گلی برای نقش‌های فرعی. چیزی شبیه یک صحنه که از یک طرف نور می‌خورد. می‌توانید رنگ کاور آهنگ در حال پخش را جای آن بگذارید، یا آن را به Material You و والپیپرتان بسپارید.

---

### ساخت

```bash
git clone https://github.com/Yasinjahantigh/LogaRhythm-Android.git
cd LogaRhythm-Android
./gradlew :app:assembleDebug
```

نیاز به JDK 21 و Android SDK 36. در ویندوز از `gradlew.bat` استفاده کنید.

> اگر Google Maven در شبکه‌تان قابل‌دسترس نیست، `logarhythm.mirrors=true` را به `local.properties` اضافه کنید.

---

Apache 2.0 — فایل [LICENSE](LICENSE) را ببینید.

</div>
