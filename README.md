<div align="center">

<img src="docs/icon.svg" width="112" height="112" alt="LogaRhythm" />

<h1>LogaRhythm</h1>

<p>
  <a href="https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-2ea44f?logo=android&logoColor=white" /></a>
  <a href="https://t.me/LogaRhythmApp"><img alt="Telegram Channel" src="https://img.shields.io/badge/Telegram-@LogaRhythmApp-2CA5E0?logo=telegram&logoColor=white" /></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="License: Apache 2.0" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" />
  <a href="https://github.com/Yasinjahantigh/LogaRhythm-Android/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/Yasinjahantigh/LogaRhythm-Android/actions/workflows/ci.yml/badge.svg" /></a>
</p>

<p>
  <a href="https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest"><b>Download Latest APK</b></a> · 
  <a href="https://t.me/LogaRhythmApp">Telegram Channel</a> · 
  <a href="#english">English</a> · 
  <a href="#فارسی">فارسی</a>
</p>

</div>

---

## English

There's a version of music listening that doesn't require an account, a subscription, or trusting a company with your taste. LogaRhythm is that version — a local music player for Android that treats your files as the source of truth and keeps everything else on the device.

Your library, your play history, your playlists, your edited tags, your lyrics: none of it leaves the phone.

### What's actually in it

**Playback** that handles the small things properly — cross-fade between songs (speed-aware), skip silence, headphones reconnect and resume within 10 minutes, per-song playback speed, sleep timer by minutes or by track count.

**ReplayGain** across MP3, FLAC, M4A and Ogg. Track and album modes, user preamp, 5-band EQ, bass boost. Volume levels between albums finally behave.

**Lyrics** from wherever they exist — embedded in the file, a `.lrc` beside it, or fetched from LRCLIB, NetEase, Kugou and others. When no timed source has the song, Genius supplies plain lyrics as a last fallback. Synced scrolling, per-song timing offset, manual editing, import and export.

**Multi-select** — long-press a song to select it, then pick more and act once: play next, queue, add to a playlist, favorite, hide, or remove from a playlist.

**Tag editing** that doesn't guess the format from the extension. The first 12 bytes of the file decide — ID3/MP3, FLAC, Ogg, or MP4. Writes to a staged copy, verifies it, and only then replaces the original. Rolls back on failure.

**Resume position** for anything over 20 minutes. Position saved every 10 seconds, restored on re-open.

**Discovery on the device** — daily mixes, mood queues (late-night calm, deep focus, energy, rainy day, acoustic morning), artist radio, and listening stats by week, month or all time. No server involved.

**Library tools** — duplicate finder, missing tags view, hidden songs, excluded folders, artist splitting for "A feat. B" tracks, minimum track length filter so voice notes don't clutter the library.

**Full backup and restore** — playlists, favorites, history, lyrics, all in one file.

**Music download** — search YouTube and SoundCloud, save the original-quality audio (M4A or MP3) with artwork and tags into Music/LogaRhythm, then play, open or share from the download history. Metadata and covers come from Deezer, MusicBrainz and iTunes; an optional cookies.txt can unlock age-restricted videos. The player shows the file's container, codec, bitrate and sample rate, while ringtones and voice recordings stay out of the library by default (Settings → Library).

**Home screen widget** — artwork, play/pause, previous, next.

**Persian and English** — complete localization in both directions, RTL layouts, switchable in-app.

### Design

The default palette is called **Ink & Ember**: a warm near-black surface (`#0E1013`), amber accent (`#F4B266`), slate and sage for secondary roles. Something like a stage lit from one side. The Theme Studio adds presets for **true-black OLED**, artwork-driven colors and the wallpaper's Material You palette, plus a custom theme where you pick the accent hue, saturation and base (dark, light or OLED).

### Build

```bash
git clone https://github.com/Yasinjahantigh/LogaRhythm-Android.git
cd LogaRhythm-Android
./gradlew :app:assembleDebug
```

Requires JDK 21 and Android SDK 36. Gradle wrapper included. On Windows: `gradlew.bat`.

> If Google Maven is unreachable on your network, add `logarhythm.mirrors=true` to `local.properties`.

### Download & Community

- **Download APK**: Grab the latest build from [GitHub Releases](https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest).
- **Updates & Feedback**: Join the Telegram channel [@LogaRhythmApp](https://t.me/LogaRhythmApp) for announcements, changelogs, and direct feedback.

### License

Apache 2.0 — see [LICENSE](LICENSE).

Bundled [Vazirmatn](https://github.com/rastikerdar/vazirmatn) font © 2015 The Vazirmatn Project Authors, licensed under SIL OFL 1.1 — see [OFL.txt](app/src/main/assets/OFL.txt).

The Music Download section uses [youtubedl-android](https://github.com/junkfood02/youtubedl-android) (the maintained fork of yausername's project) and [yt-dlp](https://github.com/yt-dlp/yt-dlp), which are GPL-3.0. LogaRhythm's own code stays Apache-2.0; released APKs bundle those components and [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) lists them in full.

---

<div dir="rtl">

## فارسی

یه جور گوش‌دادن به موسیقی هست که نه حساب کاربری می‌خواهد، نه اشتراک، نه اینکه به یک شرکت اعتماد کنید. LogaRhythm همان است — یک پخش‌کنندهٔ موسیقی محلی برای اندروید که فایل‌هایتان را مرجع اصلی می‌داند و همه چیز را روی گوشی نگه می‌دارد.

کتابخانه، تاریخچهٔ پخش، پلی‌لیست‌ها، تگ‌های ویرایش‌شده، متن ترانه‌ها — هیچ‌کدام از گوشی بیرون نمی‌روند.

---

### چه چیزهایی واقعاً داخلش است

**پخش** که کارهای کوچک را درست انجام می‌دهد — محوشدن بین آهنگ‌ها (با توجه به سرعت پخش)، پرش از سکوت، از سرگیری پخش بعد از اتصال دوبارهٔ هدفون (تا ۱۰ دقیقه)، تنظیم سرعت پخش، تایمر خواب با دقیقه یا تعداد آهنگ.

**ReplayGain** برای MP3، FLAC، M4A و Ogg. حالت آهنگ و آلبوم، پیش‌تقویت دستی، اکولایزر پنج‌باندی و bass boost. اختلاف صدا بین آلبوم‌ها بالاخره درست می‌شود.

**متن ترانه** از هر جایی که وجود داشته باشد — داخل فایل، یک `.lrc` کنارش، یا دریافت از LRCLIB، NetEase، Kugou و بقیه. وقتی هیچ منبع همگامی آهنگ را نداشته باشد، Genius متن ساده را به‌عنوان آخرین گزینه می‌دهد. اسکرول همگام، تنظیم زمان به‌ازای هر آهنگ، ویرایش دستی، ورود و خروج.

**انتخاب چندتایی** — با نگه‌داشتن یک آهنگ انتخاب را شروع کنید، چند آهنگ دیگر را اضافه کنید و یک‌جا عملیات را انجام دهید: پخش بعدی، صف، افزودن به پلی‌لیست، علاقه‌مندی، مخفی‌کردن یا حذف از پلی‌لیست.

**ویرایش تگ** که فرمت را از پسوند فایل حدس نمی‌زند. ۱۲ بایت اول فایل تصمیم می‌گیرد — ID3/MP3، FLAC، Ogg یا MP4. روی یک نسخهٔ موقت می‌نویسد، بررسی می‌کند، و فقط بعد از آن فایل اصلی را جایگزین می‌کند. در صورت خطا برمی‌گردد.

**موقعیت پخش** برای هر چیزی بیشتر از ۲۰ دقیقه. هر ۱۰ ثانیه ذخیره می‌شود و دفعهٔ بعد از همانجا ادامه می‌دهد.

**کشف موسیقی روی دستگاه** — میکس‌های روزانه، صف‌های حال‌وهوایی (آرامش نیمه‌شب، تمرکز عمیق، انرژی، روز بارانی، صبح آکوستیک)، رادیوی هنرمند و آمار شنیدن. بدون سرور.

**ابزار کتابخانه** — یافتن تکراری‌ها، نمایش تگ‌های ناقص، مخفی‌کردن آهنگ، استثنا کردن پوشه، جداکردن «هنرمند الف feat. ب»، فیلتر حداقل مدت آهنگ تا پیام‌های صوتی کتابخانه را پر نکنند.

**پشتیبان‌گیری و بازیابی کامل** — پلی‌لیست‌ها، علاقه‌مندی‌ها، تاریخچه، متن ترانه‌ها، همه در یک فایل.

**دانلود موسیقی** — جست‌وجو در یوتیوب و ساندکلود، ذخیرهٔ صدای اصلی (M4A یا MP3) با کاور و تگ در پوشهٔ Music/LogaRhythm و تاریخچهٔ دانلود با پخش، باز کردن و اشتراک‌گذاری. متادیتا و کاور از Deezer، MusicBrainz و iTunes می‌آید؛ یک فایل cookies.txt اختیاری هم می‌تواند ویدیوهای محدودشده را باز کند. پخش‌کننده فرمت، کدک، بیت‌ریت و نرخ نمونه‌برداری فایل را نشان می‌دهد و زنگ‌ها و ضبط‌های صوتی به‌صورت پیش‌فرض از کتابخانه کنار گذاشته می‌شوند (تنظیمات ← کتابخانه).

**ویجت صفحهٔ اصلی** — کاور، پخش/مکث، قبلی، بعدی.

**فارسی و انگلیسی** — هر دو زبان کامل، چیدمان راست‌به‌چپ، قابل‌تغییر از داخل اپ.

---

### طراحی

پالت پیش‌فرض **Ink & Ember** نام دارد: یک نزدیک‌به‌مشکی گرم (`#0E1013`)، لهجهٔ کهربایی (`#F4B266`)، سربی و مریم‌گلی برای نقش‌های فرعی. چیزی شبیه یک صحنه که از یک طرف نور می‌خورد. استودیوی پوسته پوسته‌های **مشکی خالص OLED**، رنگ‌های برگرفته از کاور و پالت Material You والپیپر را دارد، به‌علاوهٔ یک پوستهٔ سفارشی که در آن خودتان رنگ تأکید، اشباع رنگ و پایه (تاریک، روشن یا OLED) را انتخاب می‌کنید.

---

### دریافت و ارتباط

- **دانلود نسخهٔ نهایی**: فایل APK را از [صفحهٔ انتشار گیت‌هاب](https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest) دریافت کنید.
- **کانال تلگرام و گزارش باگ**: برای دریافت آپدیت‌ها، تغییرات و ارسال باگ/پیشنهاد به [@LogaRhythmApp](https://t.me/LogaRhythmApp) بپیوندید.

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

### اجزای شخص ثالث

بخش «دانلود موسیقی» از کتابخانهٔ [youtubedl-android](https://github.com/junkfood02/youtubedl-android) (نسخهٔ fork شدهٔ yausername) و [yt-dlp](https://github.com/yt-dlp/yt-dlp) استفاده می‌کند که تحت GPL-3.0 منتشر شده‌اند. کد خود LogaRhythm همچنان Apache-2.0 است؛ APKهای منتشرشده این اجزا را همراه دارند و فهرست کامل در [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) آمده است.

---

Apache 2.0 — فایل [LICENSE](LICENSE) را ببینید.

</div>
