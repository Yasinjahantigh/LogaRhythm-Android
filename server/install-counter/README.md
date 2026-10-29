# Install counter

A tiny Cloudflare Worker that counts installs no matter where the APK came from: GitHub, Telegram,
or a friend's file manager. The app sends one anonymous record on first launch; the worker keeps
the totals and a per-device-model breakdown in Workers KV.

- `POST /hit` — one record per install, deduplicated by the random install id. Body:
  `{"installId":"…","version":"1.8.0","code":10800,"manufacturer":"Xiaomi","model":"M2007J3SG","android":"15","sdk":35,"abi":"arm64-v8a"}`
- `GET /stats` — `{"installs": 123, "devices": [{"model":"Xiaomi M2007J3SG","count": 40}], "days": []}`.
  Add `?days=1` to include the per-day series.
- `GET /badge` — shields.io endpoint JSON for the README.

Only a random install id, the app version, the device make/model, the Android version and the CPU
ABI are received. No account, file names, library data or advertising id. The record is sent once
per install: app updates keep the same install id and never send it again, so the count only grows
when someone actually installs the app. Nothing is sent when the build has no endpoint.

## One-time setup

1. `cd server/install-counter`
2. `npx wrangler login`
3. `npx wrangler kv namespace create INSTALLS`
   Copy the printed `id` into `wrangler.toml` (replace `PUT_THE_KV_NAMESPACE_ID_HERE`).
4. `npx wrangler deploy`
   It prints the worker URL, for example `https://logarhythm-installs.<subdomain>.workers.dev`.
5. Smoke test:

   ```bash
   curl -X POST https://logarhythm-installs.<subdomain>.workers.dev/hit \
     -H "content-type: application/json" \
     -d '{"installId":"11111111-2222-3333-4444-555555555555","version":"test","manufacturer":"Xiaomi","model":"M2007J3SG","android":"15","abi":"arm64-v8a"}'

   curl https://logarhythm-installs.<subdomain>.workers.dev/stats
   ```

6. In the repository: **Settings → Secrets and variables → Actions → Variables → New variable**
   - Name: `INSTALL_ENDPOINT`
   - Value: the full `…/hit` URL from step 4

   The release workflow passes it to Gradle, so every tagged release after that counts installs.
   For a local build use `INSTALL_ENDPOINT=https://…/hit ./gradlew :app:assembleRelease`, or add
   `INSTALL_ENDPOINT=…` to `local.properties` and pass it as an environment variable.

7. Optional README badge:

   ```markdown
   ![Installs](https://img.shields.io/endpoint?url=https://logarhythm-installs.<subdomain>.workers.dev/badge)
   ```

## Updating the worker later

`npx wrangler deploy` from this folder, or run the **Deploy install counter** workflow
(workflow_dispatch) after adding the `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` secrets.

## Notes

- `workers.dev` is reachable for most users, but some networks (including inside Iran) may block
  it. The app retries on later launches, so a user who opens the app again through a working
  connection is still counted once.
- KV counters are read-modify-write, so simultaneous first launches can very slightly undercount.
  That is acceptable for a rough install number and keeps the worker free of a database.
- `/stats` is public on purpose: it only exposes totals and device models, and it makes the numbers
  easy to show in the repository.
