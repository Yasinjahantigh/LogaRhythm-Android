#!/usr/bin/env python3
import json
import os
import subprocess
import urllib.error
import urllib.request

TAG = os.environ["TAG_NAME"].strip()
GEMINI_KEY = os.environ.get("GEMINI_API_KEY", "").strip()
BOT_TOKEN = os.environ["TELEGRAM_BOT_TOKEN"].strip()
CHANNEL_ID = os.environ["TELEGRAM_CHANNEL_ID"].strip()
APK_PATH = f"LogaRhythm-{TAG}.apk"

if not CHANNEL_ID:
    raise SystemExit("TELEGRAM_CHANNEL_ID secret is empty or missing.")
if not BOT_TOKEN:
    raise SystemExit("TELEGRAM_BOT_TOKEN secret is empty or missing.")


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
            ["git", "log", f"{prev}..HEAD", "--pretty=format:- %s", "--", "app/src"],
            text=True,
        )
        skip = ("docs:", "ci:", "chore:", "build:", "style:", "test:")
        lines = [l for l in log.splitlines() if not any(l.startswith(f"- {p}") for p in skip)]
        return "\n".join(lines[:15])
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


def build_message(changelog):
    body = changelog if changelog else "• بهبودهای کلی و رفع مشکلات"
    return (
        f"LogaRhythm {TAG} 🚀\n\n"
        f"تغییرات این نسخه:\n{body}\n\n"
        "دانلود از گیت‌هاب:\n"
        "https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/latest\n\n"
        "باگ یا پیشنهاد؟ زیر همین پست کامنت بذارید."
    )


def send_apk(caption):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendDocument"
    boundary = "----FormBoundary"
    with open(APK_PATH, "rb") as apk:
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
        + (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="document"; filename="{os.path.basename(APK_PATH)}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode()
        + apk_data
        + f"\r\n--{boundary}--\r\n".encode()
    )

    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            result = json.load(r)
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Telegram HTTP {e.code}: {e.read().decode(errors='replace')}")
    if not result.get("ok"):
        raise SystemExit(f"Telegram error: {result}")
    print("Telegram message sent.")


commits = get_commits()
changelog = ask_gemma(commits)
message = build_message(changelog)
send_apk(message)
