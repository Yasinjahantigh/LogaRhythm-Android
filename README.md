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

Your library, your play history, your playlists, your edited tags, your lyrics: none of it leaves the phone. The one exception is the optional AI playlist feature: it stays off until you add your own API key, and only library metadata is sent, only when you tap Generate.

### New in 1.6.0

- **Lyrics that hold up** — sources race in parallel, weak matches are rejected by score, and a candidate picker lets you fix a bad query. Per-source switches, a test screen, a custom HTTPS endpoint, and Genius without a VPN.
- **Artists done right** — collaborations split on commas, `&`, `feat.` and Persian `،` without breaking names like *Tyler, The Creator*; every artist gets a page with top songs, albums and "Appears on".
- **A smarter library** — sort by year, play count, last played or random, filter by favorites/downloaded/recently added, and save the per-tab choice.
- **Smart mixes** — seven self-updating collections, plus "Save as playlist" for any mix and for the playing queue.
- **Tag & cover studio** — edit every common field and the cover on MP3, FLAC, Ogg and M4A; writes are verified and MediaStore, notification, lock screen and widget refresh immediately.
- **Download hardening** — real file validation, honest cancellation, stale-row cleanup and a clear "saved without tags" note when only WebM is available.
- **Optional AI playlist** — bring your own OpenAI-compatible or Gemini key and base URL; generate a playlist from your own library and preview it before saving.
- **Player polish** — swipe right for the next track and left for the previous one, with haptics and a direction toggle in Settings.

### What's actually in it

**Playback** that handles the small things properly — cross-fade between songs (speed-aware), skip silence, headphones reconnect and resume within 10 minutes, per-song playback speed, sleep timer by minutes or by track count. Swipe right for the next song, left for the previous one (both directions are switchable).

**ReplayGain** across MP3, FLAC, M4A and Ogg. Track and album modes, user preamp, 5-band EQ, bass boost. Volume levels between albums finally behave.

**Lyrics** from wherever they exist — embedded in the file, a `.lrc` beside it, or fetched from LRCLIB, NetEase, Kugou, lyrics.ovh and Genius. Sources run in parallel inside a hard time budget, blocked providers cool down instead of stalling every song, and results are scored against title, artist, album and duration before they are accepted. If the best match is not convincing, a candidate picker across all sources lets you search with your own title/artist (Latin or Persian), preview the first line, and lock the result as manual. Genius is fetched directly, without a VPN; if a network challenges it, the app says so and uses the other sources. A custom HTTPS endpoint is available for your own lyrics server.

**Multi-select** — long-press a song to select it, then pick more and act once: play next, queue, add to a playlist, favorite, hide, or remove from a playlist.

**Tag & cover studio** that doesn't guess the format from the extension. The first 12 bytes of the file decide — ID3/MP3, FLAC, Ogg, or MP4. Edit title, artist, album, album artist, genre, year, track/disc number and composer; pick a new cover (with square crop and zoom), replace it or remove it. Writes go to a staged copy, are read back and verified (including a SHA-256 check of the cover), mirrored into MediaStore, and only then replace the original. Notification, lock screen and widget refresh immediately.

**Resume position** for anything over 20 minutes. Position saved every 10 seconds, restored on re-open.

**Discovery on the device** — daily mixes, mood queues (late-night calm, deep focus, energy, rainy day, acoustic morning), artist radio, and listening stats by week, month or all time. No server involved.

**Library tools** — duplicate finder, missing tags view, hidden songs, excluded folders, artist splitting for "A feat. B" tracks, minimum track length filter so voice notes don't clutter the library. Collaborations split on commas, ampersands, `feat.` and Persian `،`, while band names like *Tyler, The Creator* or *Earth, Wind & Fire* stay whole; you can add your own never-split names. Artists get a real page with top songs, albums and "Appears on" credits.

**Sorting and filters** — title, artist, album, year, date added/modified, duration, play count, last played and random, remembered per tab. Songs can be narrowed to favorites, downloaded or recently added.

**Smart mixes** — Recently added, Recently played, Most played, Forgotten favorites, Songs without lyrics, Long songs and Downloaded. They update themselves from the library and can be saved as a normal playlist. The playing queue can be saved as a playlist too.

**Optional AI playlist (bring your own key)** — describe a mood and let your own OpenAI-compatible or Gemini endpoint pick songs from your catalog. Off by default; the key is encrypted with the Android Keystore, and only titles, artists, albums, genres and years are sent, only when you press Generate. Preview the result before saving it.

**Full backup and restore** — playlists, favorites, history, lyrics, all in one file.

**Music download** — search YouTube and SoundCloud, save the original-quality audio (M4A or MP3 when the source provides it) with artwork and tags into Music/LogaRhythm, then play, open or share from the download history. Metadata and covers come from Deezer, MusicBrainz and iTunes; an optional cookies.txt can unlock age-restricted videos. Files are validated after download, partial leftovers are rejected, cancelling is honest, and a source that only offers WebM is saved with a clear "without tags" note instead of pretending. The player shows the file's container, codec, bitrate and sample rate, while ringtones and voice recordings stay out of the library by default (Settings → Library).

**Home screen widget** — artwork, play/pause, previous, next.

**Persian and English** — complete localization in both directions, RTL layouts, switchable in-app.

### Design

The default palette is called **Ink & Ember**: a warm near-black surface (`#0E1013`), amber accent (`#F4B266`), slate and sage for secondary roles. Something like a stage lit from one side. The Theme Studio adds presets for **true-black OLED**, artwork-driven colors and the wallpaper's Material You palette, plus a custom theme where you pick the accent hue, saturation and base (dark, light or OLED).

### Optional AI setup

Magic Playlist is off by default and contacts no provider until you press Generate.

1. Open **Settings → AI (optional)**.
2. Pick **OpenAI-compatible** or **Google Gemini**, then keep or change the Base URL and model.
3. Paste your API key, tap **Test connection**, and save.
4. Open **Library → Playlists → Magic playlist**, describe the mood, pick the song count and generate. Preview, rename and save.

The key is encrypted with an Android Keystore key on this device and can be removed at any time. A request contains only track metadata (id, title, artist, album, year, genre, duration) for at most about 400 songs, and only when you tap Generate. No API key ships with the app.

### Upgrading

The library database migrates to v10 on first launch; playlists, favorites, history and lyrics are preserved. A backup from **Settings → Backup** is still a good habit before any major update.

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

کتابخانه، تاریخچهٔ پخش، پلی‌لیست‌ها، تگ‌های ویرایش‌شده، متن ترانه‌ها — هیچ‌کدام از گوشی بیرون نمی‌روند. تنها استثنا قابلیت اختیاری «لیست پخش هوش مصنوعی» است: تا وقتی کلید API خودتان را وارد نکنید خاموش می‌ماند و فقط وقتی «بساز» را بزنید، متادیتای کتابخانه فرستاده می‌شود.

---

### تازه در ۱.۶.۰

- **متن ترانهٔ قابل‌اعتماد** — منابع موازی، رد تطابق ضعیف با امتیازدهی، و انتخابگر کاندید برای اصلاح جست‌وجو. کلید جداگانه برای هر منبع، صفحهٔ آزمایش، Endpoint سفارشی HTTPS و Genius بدون VPN.
- **هنرمندان درست** — جداکردن همکاری‌ها با کاما، `&`، `feat.` و ویرگول فارسی «،» بدون شکستن نام‌هایی مثل *Tyler, The Creator*؛ هر هنرمند صفحهٔ کامل با آهنگ‌های برتر، آلبوم‌ها و «حضور در» دارد.
- **کتابخانهٔ هوشمندتر** — مرتب‌سازی با سال، تعداد پخش، آخرین پخش یا تصادفی، فیلتر علاقه‌مندی/دانلودشده/تازه‌ها، و ذخیرهٔ انتخاب هر تب.
- **میکس‌های هوشمند** — هفت مجموعهٔ خودبه‌روز، به‌علاوهٔ «ذخیره به‌عنوان لیست پخش» برای هر میکس و برای صف پخش.
- **استودیوی تگ و کاور** — ویرایش همهٔ فیلدهای رایج و کاور برای MP3، FLAC، Ogg و M4A؛ نوشتن تأییدشده و تازه‌شدن فوری MediaStore، اعلان، صفحهٔ قفل و ویجت.
- **دانلود مقاوم‌تر** — اعتبارسنجی واقعی فایل، لغو صادقانه، پاک‌سازی ردیف‌های گیرکرده و یادداشت شفاف «بدون تگ ذخیره شد» وقتی فقط WebM هست.
- **لیست پخش هوش مصنوعی اختیاری** — کلید و Base URL خودتان (OpenAI-compatible یا Gemini)؛ لیست از کتابخانهٔ خودتان بسازید و قبل از ذخیره ببینید.
- **پلیر** — سوایپ راست برای آهنگ بعدی و چپ برای قبلی، با هپتیک و تنظیم جهت در تنظیمات.

### چه چیزهایی واقعاً داخلش است

**پخش** که کارهای کوچک را درست انجام می‌دهد — محوشدن بین آهنگ‌ها (با توجه به سرعت پخش)، پرش از سکوت، از سرگیری پخش بعد از اتصال دوبارهٔ هدفون (تا ۱۰ دقیقه)، تنظیم سرعت پخش، تایمر خواب با دقیقه یا تعداد آهنگ. سوایپ راست = آهنگ بعدی، سوایپ چپ = قبلی (جهت هر دو قابل تغییر است).

**ReplayGain** برای MP3، FLAC، M4A و Ogg. حالت آهنگ و آلبوم، پیش‌تقویت دستی، اکولایزر پنج‌باندی و bass boost. اختلاف صدا بین آلبوم‌ها بالاخره درست می‌شود.

**متن ترانه** از هر جایی که وجود داشته باشد — داخل فایل، یک `.lrc` کنارش، یا دریافت از LRCLIB، NetEase، Kugou، lyrics.ovh و Genius. منابع موازی و در یک محدودهٔ زمانی سخت اجرا می‌شوند، منبع بلاک‌شده به‌جای معطل‌کردن هر آهنگ مدتی کنار می‌رود، و نتیجه پیش از پذیرش با عنوان، هنرمند، آلبوم و مدت آهنگ امتیازدهی می‌شود. اگر بهترین تطابق قانع‌کننده نباشد، انتخابگر کاندید از همهٔ منابع باز می‌شود تا با عنوان/هنرمند خودتان (فارسی یا لاتین) جست‌وجو کنید، خط اول را ببینید و نتیجه را دستی قفل کنید. Genius مستقیم و بدون VPN خوانده می‌شود؛ اگر شبکه‌ای چالش بدهد، اپ می‌گوید و از منابع دیگر استفاده می‌کند. یک Endpoint سفارشی HTTPS هم برای سرور متن خودتان هست. اسکرول همگام، تنظیم زمان هر آهنگ، ویرایش دستی، ورود و خروج.

**انتخاب چندتایی** — با نگه‌داشتن یک آهنگ انتخاب را شروع کنید، چند آهنگ دیگر را اضافه کنید و یک‌جا عملیات را انجام دهید: پخش بعدی، صف، افزودن به پلی‌لیست، علاقه‌مندی، مخفی‌کردن یا حذف از پلی‌لیست.

**استودیوی تگ و کاور** که فرمت را از پسوند فایل حدس نمی‌زند. ۱۲ بایت اول فایل تصمیم می‌گیرد — ID3/MP3، FLAC، Ogg یا MP4. عنوان، هنرمند، آلبوم، هنرمند آلبوم، سبک، سال، شمارهٔ آهنگ/دیسک و آهنگساز را ویرایش کنید؛ کاور جدید انتخاب کنید (با برش مربعی و زوم)، جایگزینش کنید یا حذفش کنید. نوشتن روی نسخهٔ موقت انجام می‌شود، بازخوانی و بررسی می‌شود (شامل هش SHA-256 کاور)، با MediaStore همگام می‌شود و بعد جایگزین فایل اصلی می‌شود. اعلان، صفحهٔ قفل و ویجت فوراً تازه می‌شوند.

**موقعیت پخش** برای هر چیزی بیشتر از ۲۰ دقیقه. هر ۱۰ ثانیه ذخیره می‌شود و دفعهٔ بعد از همانجا ادامه می‌دهد.

**کشف موسیقی روی دستگاه** — میکس‌های روزانه، صف‌های حال‌وهوایی (آرامش نیمه‌شب، تمرکز عمیق، انرژی، روز بارانی، صبح آکوستیک)، رادیوی هنرمند و آمار شنیدن. بدون سرور.

**ابزار کتابخانه** — یافتن تکراری‌ها، نمایش تگ‌های ناقص، مخفی‌کردن آهنگ، استثنا کردن پوشه، فیلتر حداقل مدت آهنگ تا پیام‌های صوتی کتابخانه را پر نکنند. همکاری‌ها با کاما، &، feat. و ویرگول فارسی «،» جدا می‌شوند، اما نام گروه‌هایی مثل *Tyler, The Creator* یا *Earth, Wind & Fire* یک‌پارچه می‌مانند؛ می‌توانید نام‌های «هرگز جدا نکن» خودتان را هم اضافه کنید. هر هنرمند صفحهٔ واقعی با آهنگ‌های برتر، آلبوم‌ها و بخش «حضور در» دارد.

**مرتب‌سازی و فیلتر** — عنوان، هنرمند، آلبوم، سال، تاریخ افزودن/تغییر، مدت، تعداد پخش، آخرین پخش و تصادفی، ذخیره‌شده برای هر تب. آهنگ‌ها را می‌توان به علاقه‌مندی‌ها، دانلودشده‌ها یا تازه‌ها محدود کرد.

**میکس‌های هوشمند** — تازه اضافه‌شده، اخیراً پخش‌شده، پربازترین‌ها، علاقه‌مندی‌های فراموش‌شده، آهنگ‌های بدون متن، آهنگ‌های طولانی و دانلودشده. خودشان از کتابخانه به‌روز می‌شوند و می‌توانید به‌صورت یک لیست پخش معمولی ذخیره‌شان کنید. صف پخش هم قابل ذخیره به‌عنوان لیست پخش است.

**لیست پخش هوش مصنوعی اختیاری (با کلید خودتان)** — حال‌وهوا را توصیف کنید و بگذارید Endpoint سازگار با OpenAI یا Gemini خودتان از کاتالوگ شما آهنگ انتخاب کند. پیش‌فرض خاموش؛ کلید با Android Keystore رمزنگاری می‌شود و فقط عنوان، هنرمند، آلبوم، سبک و سال آهنگ‌ها و فقط هنگام زدن «بساز» فرستاده می‌شود. نتیجه را قبل از ذخیره ببینید.

**پشتیبان‌گیری و بازیابی کامل** — پلی‌لیست‌ها، علاقه‌مندی‌ها، تاریخچه، متن ترانه‌ها، همه در یک فایل.

**دانلود موسیقی** — جست‌وجو در یوتیوب و ساندکلود، ذخیرهٔ صدای اصلی (M4A یا MP3 وقتی منبع بدهد) با کاور و تگ در پوشهٔ Music/LogaRhythm و تاریخچهٔ دانلود با پخش، باز کردن و اشتراک‌گذاری. متادیتا و کاور از Deezer، MusicBrainz و iTunes می‌آید؛ یک فایل cookies.txt اختیاری هم می‌تواند ویدیوهای محدودشده را باز کند. فایل‌ها بعد از دانلود اعتبارسنجی می‌شوند، باقی‌ماندهٔ ناقص رد می‌شود، لغو کردن صادقانه است و اگر منبع فقط WebM بدهد، با یادداشت واضح «بدون تگ ذخیره شد» نگه داشته می‌شود. پخش‌کننده فرمت، کدک، بیت‌ریت و نرخ نمونه‌برداری فایل را نشان می‌دهد و زنگ‌ها و ضبط‌های صوتی به‌صورت پیش‌فرض از کتابخانه کنار گذاشته می‌شوند (تنظیمات ← کتابخانه).

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

### راه‌اندازی AI اختیاری

«لیست پخش جادویی» به‌صورت پیش‌فرض خاموش است و تا وقتی «بساز» را نزنید با هیچ سرویسی تماس نمی‌گیرد.

۱. **تنظیمات ← هوش مصنوعی (اختیاری)** را باز کنید.
۲. **سازگار با OpenAI** یا **Google Gemini** را انتخاب کنید و آدرس پایه و مدل را نگه دارید یا تغییر دهید.
۳. کلید API را جای‌گذاری کنید، **آزمایش اتصال** را بزنید و ذخیره کنید.
۴. از **کتابخانه ← لیست‌های پخش ← لیست پخش جادویی** حال‌وهوا را توصیف کنید، تعداد آهنگ را انتخاب کنید و بسازید. پیش‌نمایش را ببینید، نام بگذارید و ذخیره کنید.

کلید با یک کلید Android Keystore روی همین دستگاه رمزنگاری می‌شود و هر زمان قابل حذف است. درخواست فقط متادیتای آهنگ‌ها (شناسه، عنوان، هنرمند، آلبوم، سال، سبک، مدت) برای حداکثر حدود ۴۰۰ آهنگ را دارد و فقط هنگام زدن «بساز» فرستاده می‌شود. هیچ کلید API همراه اپ نیست.

### ارتقا

دیتابیس کتابخانه در اولین اجرا به نسخهٔ ۱۰ مهاجرت می‌کند؛ لیست‌های پخش، علاقه‌مندی‌ها، تاریخچه و متن ترانه‌ها حفظ می‌شوند. با این حال، قبل از هر آپدیت بزرگ یک پشتیبان از **تنظیمات ← پشتیبان‌گیری** بگیرید.

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
