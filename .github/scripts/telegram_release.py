#!/usr/bin/env python3
import json
import os
import subprocess
import urllib.request
import urllib.parse

TAG = os.environ["TAG_NAME"]
GEMINI_KEY = os.environ.get("GEMINI_API_KEY", "")
BOT_TOKEN = os.environ["TELEGRAM_BOT_TOKEN"]
CHANNEL_ID = os.environ["TELEGRAM_CHANNEL_ID"]
APK_PATH = f"LogaRhythm-{TAG}.apk"


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
            ["git", "log", f"{prev}..HEAD", "--pretty=format:- %s"], text=True
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
        "کامیت‌های زیر تغییرات نسخه جدید نرم‌افزار LogaRhythm هستند "
        "(یک پخش‌کننده موسیقی اندرویدی). آن‌ها را به فارسی روان و کاربرپسند "
        "تبدیل کن. هر تغییر یک خط با بولت • باشد. حداکثر ۵ مورد. "
        "فقط تغییرات را بنویس بدون هیچ توضیح اضافه:\n\n" + commits
    )
    body = json.dumps({"contents": [{"parts": [{"text": prompt}]}]}).encode()
    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        f"gemma-3-12b-it:generateContent?key={GEMINI_KEY}"
    )
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.load(r)
        return data["candidates"][0]["content"]["parts"][0]["text"].strip()
    except Exception as e:
        print(f"Gemma error: {e}")
        return None


def build_message(changelog):
    body = changelog if changelog else "بهبودهای کلی و رفع مشکلات"
    return (
        f"LogaRhythm {TAG}\n\n"
        f"تغییرات این نسخه:\n{body}\n\n"
        f"دانلود:\n"
        f"https://github.com/Yasinjahantigh/LogaRhythm-Android/releases/tag/{TAG}\n\n"
        "باگ یا پیشنهاد؟ همین پایین کامنت بذارید."
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
    with urllib.request.urlopen(req, timeout=120) as r:
        result = json.load(r)
    if not result.get("ok"):
        raise RuntimeError(f"Telegram error: {result}")
    print("Telegram message sent.")


commits = get_commits()
changelog = ask_gemma(commits)
message = build_message(changelog)
send_apk(message)
