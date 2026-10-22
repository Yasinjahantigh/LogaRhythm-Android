#!/usr/bin/env python3
import html
import json
import os
import subprocess
import urllib.error
import urllib.request

TAG = os.environ["TAG_NAME"].strip()
GEMINI_KEY = os.environ.get("GEMINI_API_KEY", "").strip()
BOT_TOKEN = os.environ["TELEGRAM_BOT_TOKEN"].strip()
CHANNEL_ID = os.environ["TELEGRAM_CHANNEL_ID"].strip()

# The two ARM builds cover every phone; x86 APKs are only for emulators.
APK_ABIS = [
    ("arm64-v8a", "نسخهٔ ۶۴ بیتی — مناسب بیشتر گوشی‌های جدید"),
    ("armeabi-v7a", "نسخهٔ ۳۲ بیتی — مناسب گوشی‌های قدیمی‌تر"),
]
# Telegram bots may upload documents up to 50 MB; leave room for multipart overhead.
MAX_APK_BYTES = 49 * 1024 * 1024

FALLBACK_CHANGELOG = (
    "• بخش دانلود موسیقی با جست‌وجو در یوتیوب و ساندکلود\n"
    "• ذخیرهٔ صدا با کیفیت اصلی، کاور و تگ کامل\n"
    "• تاریخچهٔ دانلود با پخش، باز کردن و اشتراک‌گذاری\n"
    "• پنهان‌شدن پیش‌فرض فایل‌های سیستمی و ضبط‌ها\n"
    "• نمایش فرمت و کیفیت صدا در پخش‌کننده"
)

if not CHANNEL_ID:
    raise SystemExit("TELEGRAM_CHANNEL_ID secret is empty or missing.")
if not BOT_TOKEN:
    raise SystemExit("TELEGRAM_BOT_TOKEN secret is empty or missing.")


def apk_path(abi):
    return f"LogaRhythm-{TAG}-{abi}.apk"


def get_commits():
    try:
        tags = subprocess.check_output(
            ["git", "tag", "-l", "v*.*.*"], text=True
        ).strip().split("\n")
        tags = sorted([t for t in tags if t], key=lambda t: [int(x) for x in t.lstrip("v").split(".")])
        if len(tags) < 2:
            return ""
        prev = tags[-2]
        log = subprocess.check_output(
            ["git", "log", f"{prev}..HEAD", "--pretty=format:--- %s%n%b", "--", "app/src"],
            text=True,
        )
        skip = ("docs:", "ci:", "chore:", "build:", "style:", "test:")
        lines = [l for l in log.splitlines() if not any(l.startswith(f"--- {p}") for p in skip)]
        return "\n".join(lines[:80])
    except Exception:
        return ""


def ask_gemma(commits):
    if not GEMINI_KEY or not commits:
        return None
    prompt = (
        "کامیت‌های زیر تغییرات نسخه جدید اپ اندروید LogaRhythm (پخش‌کننده موسیقی) هستند. "
        "آن‌ها را به فارسی روان و کاربرپسند برای کاربر نهایی بازنویسی کن. "
        "هر تغییر یک خط با بولت • باشد، حداکثر ۵ خط. "
        "تغییرات مربوط به زیرساخت، CI، تلگرام، مستندات و کارهای فنی داخلی را نادیده بگیر. "
        "فقط فهرست تغییرات را بنویس، بدون مقدمه و توضیح اضافه:\n\n" + commits
    )
    body = json.dumps({"contents": [{"parts": [{"text": prompt}]}]}).encode()
    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        "gemma-4-26b-a4b-it:generateContent"
    )
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json", "x-goog-api-key": GEMINI_KEY},
    )
    for attempt in range(2):
        try:
            with urllib.request.urlopen(req, timeout=240) as r:
                data = json.load(r)
            parts = data["candidates"][0]["content"]["parts"]
            text = "\n".join(
                p["text"] for p in parts if p.get("text") and not p.get("thought")
            ).strip()
            if text:
                return text
            print(f"Gemma returned empty text: {json.dumps(data)[:500]}")
        except urllib.error.HTTPError as e:
            print(f"Gemma attempt {attempt + 1} failed: {e.code} {e.read().decode(errors='replace')[:500]}")
        except Exception as e:
            print(f"Gemma attempt {attempt + 1} failed: {e}")
    return None


def normalize_bullets(text):
    lines = []
    for line in text.splitlines():
        line = line.strip().lstrip("-*•").strip()
        if line:
            lines.append("• " + line)
    return "\n".join(lines[:5])


def build_message(changelog):
    body = normalize_bullets(changelog) if changelog else FALLBACK_CHANGELOG
    return (
        f"<b>🎵 LogaRhythm {html.escape(TAG)}</b>\n\n"
        f"<b>تغییرات این نسخه:</b>\n{html.escape(body)}\n\n"
        "📥 <b>دانلود از گیت‌هاب:</b>\n"
        "https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest\n\n"
        "💬 اگر باگ، ایراد یا پیشنهادی داشتید دایرکت بفرستین:\n"
        "https://t.me/LogaRhythmApp?direct\n\n"
        "📢 @LogaRhythmApp"
    )


def send_request(url, data, content_type, timeout):
    req = urllib.request.Request(
        url, data=data, headers={"Content-Type": content_type}
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            result = json.load(r)
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Telegram HTTP {e.code}: {e.read().decode(errors='replace')}")
    if not result.get("ok"):
        raise SystemExit(f"Telegram error: {result}")
    return result


def send_message(text):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    body = json.dumps(
        {"chat_id": CHANNEL_ID, "text": text, "parse_mode": "HTML"}
    ).encode()
    send_request(url, body, "application/json", timeout=60)
    print("Telegram changelog sent.")


def send_apk(path, caption):
    if not os.path.isfile(path):
        raise SystemExit(f"Missing APK: {path}")

    size = os.path.getsize(path)
    if size > MAX_APK_BYTES:
        print(
            f"{path} is {size / (1024 * 1024):.1f} MB, over Telegram's 50 MB "
            "upload limit; the changelog already links to GitHub."
        )
        return

    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendDocument"
    boundary = "----FormBoundary"
    with open(path, "rb") as apk:
        apk_data = apk.read()

    def field(name, value):
        return (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
            f"{value}\r\n"
        ).encode()

    body = (
        field("chat_id", CHANNEL_ID)
        + field("caption", caption)
        + field("parse_mode", "HTML")
        + (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="document"; filename="{os.path.basename(path)}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode()
        + apk_data
        + f"\r\n--{boundary}--\r\n".encode()
    )

    send_request(url, body, f"multipart/form-data; boundary={boundary}", timeout=180)
    print(f"Telegram APK sent: {os.path.basename(path)}")


commits = get_commits()
changelog = ask_gemma(commits)
message = build_message(changelog)
send_message(message)

for abi, note in APK_ABIS:
    caption = (
        f"<b>🎵 LogaRhythm {html.escape(TAG)} · {abi}</b>\n"
        f"{html.escape(note)}"
    )
    send_apk(apk_path(abi), caption)
