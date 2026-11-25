# LogaRhythm Cloudflare Worker (`install-counter`)

A lightweight Cloudflare Worker + Workers KV backend (`INSTALLS`) that handles:
- **One-time anonymous install counting** (`POST /hit`, `GET /stats`, `GET /badge`, `GET /`)
- **Release Radar lookup relay** (`GET /releases?artist=...`, aggregating iTunes Artist Lookup + Deezer Artist Albums sorted descending by verified release date)
- **Crash, shake bug report, Suggestions, and weekly stats alerts** (`POST /crash`, `POST /report`, `POST /feedback`, `POST /listening-weekly`) stored in KV (30-day TTL for reports, 90-day TTL for weekly aggregates) with instant Telegram Bot notifications
- **Protected admin inspection** (`GET /admin/summary`, `/admin/crashes`, `/admin/reports`, `/admin/feedback`, `/admin/weekly`) authenticated via `ADMIN_TOKEN`

## Data Minimality & Privacy

- `POST /hit` receives only `installId` (random UUID), `version`, `code`, and `channel` (`github` or `myket`). No hardware/device model IDs, no accounts, and no 24-hour heartbeat pings.
- `POST /crash` and `POST /report` store sanitized stack traces / user-submitted descriptions and redacted logs (`30d` TTL) and forward a summary to your Telegram bot.
- `POST /feedback` stores in-app **Settings → Suggestions** submissions (`30d` TTL) and notifies your Telegram bot.
- `POST /listening-weekly` updates an anonymous per-ISO-week aggregate (`weekly:<YYYY-Www>`) only if the user enabled weekly stats in the consent sheet / Privacy settings and is not in Private Session.

## Setup & Secrets

1. Create KV namespace (or verify `id` in `wrangler.toml`):
   ```bash
   cd server/install-counter
   npx wrangler login
   npx wrangler kv namespace create INSTALLS
   ```
2. Configure the 3 Cloudflare Worker secrets:
   ```bash
   npx wrangler secret put TELEGRAM_BOT_TOKEN
   npx wrangler secret put TELEGRAM_CHAT_ID
   npx wrangler secret put ADMIN_TOKEN
   ```
3. Deploy:
   ```bash
   npx wrangler deploy
   ```
4. Smoke test:
   ```bash
   curl -X POST https://logarhythm-installs.<subdomain>.workers.dev/hit \
     -H "content-type: application/json" \
     -d '{"installId":"11111111-2222-3333-4444-555555555555","version":"2.2.0","code":20200,"channel":"github"}'

   curl https://logarhythm-installs.<subdomain>.workers.dev/stats
   ```
5. Optionally set repository Actions variable `INSTALL_ENDPOINT` to your deployed `https://.../hit` URL (otherwise the app defaults to the production worker URL in `app/build.gradle.kts`).
