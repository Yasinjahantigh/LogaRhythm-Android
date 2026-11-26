/**
 * LogaRhythm backend worker (Cloudflare Worker + KV + Telegram Bot notifications).
 *
 * Routes:
 *   POST /hit               1-time anonymous install counter
 *   GET  /stats             Aggregated public install stats JSON
 *   GET  /badge             shields.io JSON badge
 *   GET  /releases          Release Radar (iTunes Artist Lookup + Deezer Artist Albums, sorted desc by releaseDate)
 *   POST /crash             Sanitized crash report -> KV (30d TTL) + Telegram alert
 *   POST /report            Shake-to-report bug -> KV (30d TTL) + Telegram alert
 *   POST /feedback          In-app Suggestions -> KV (30d TTL) + Telegram alert
 *   POST /listening-weekly  Anonymous weekly play summary -> KV aggregate + Telegram alert
 *   GET  /admin/*           ADMIN_TOKEN-protected endpoints (/admin/summary, /admin/crashes, /admin/reports, /admin/feedback, /admin/weekly)
 *   GET  /                  Minimal public install counter dashboard
 */

const MAX_BODY_SMALL = 4096;
const MAX_BODY_REPORT = 24576;
const MAX_FIELD = 64;
const DAY_MS = 24 * 60 * 60 * 1000;
const TTL_30_DAYS = 30 * 24 * 60 * 60;
const TTL_90_DAYS = 90 * 24 * 60 * 60;

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "content-type, authorization, x-admin-token"
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

    if (url.pathname === "/hit" && request.method === "POST") return hit(request, env);
    if (url.pathname === "/stats" && request.method === "GET") return stats(env);
    if (url.pathname === "/badge" && request.method === "GET") return badge(env);
    if (url.pathname === "/releases" && request.method === "GET") return releases(url);
    if (url.pathname === "/crash" && request.method === "POST") return submitCrash(request, env);
    if (url.pathname === "/report" && request.method === "POST") return submitReport(request, env);
    if (url.pathname === "/feedback" && request.method === "POST") return submitFeedback(request, env);
    if (url.pathname === "/listening-weekly" && request.method === "POST") return submitWeeklyListening(request, env);

    if ((url.pathname === "/admin" || url.pathname === "/admin/") && request.method === "GET") {
      return adminDashboard();
    }
    if (url.pathname.startsWith("/admin/") && request.method === "GET") {
      return handleAdmin(url, request, env);
    }

    if (url.pathname === "/" && request.method === "GET") return dashboard(env);
    return json({ error: "not found" }, 404);
  }
};

async function parseBody(request, maxBytes) {
  const text = await request.text();
  if (text.length > maxBytes) {
    return { error: json({ error: "too large" }, 413) };
  }
  try {
    return { data: JSON.parse(text) };
  } catch {
    return { error: json({ error: "bad json" }, 400) };
  }
}

async function hit(request, env) {
  const { data, error } = await parseBody(request, MAX_BODY_SMALL);
  if (error) return error;

  const installId = field(data.installId, 64);
  if (!/^[0-9a-f-]{16,64}$/i.test(installId)) return json({ error: "bad id" }, 400);

  const key = `install:${installId}`;
  const existing = await env.INSTALLS.getWithMetadata(key, { type: "json" });
  const nowIso = new Date().toISOString();
  const prevMeta = existing.metadata || (existing.value && typeof existing.value === "object" ? existing.value : null);

  const channel = field(data.channel, 24).toLowerCase() || "github";
  const record = {
    version: field(data.version, 24),
    code: Number.isFinite(Number(data.code)) ? Number(data.code) : 0,
    channel,
    firstSeen: prevMeta?.firstSeen || prevMeta?.at || nowIso,
    lastSeen: nowIso
  };

  await env.INSTALLS.put(key, "1", { metadata: record });
  return json({ ok: true, existing: Boolean(prevMeta) });
}

async function computeStats(env) {
  let cursor = undefined;
  let totalKeys = 0;
  let active24h = 0;
  let active7d = 0;
  let active30d = 0;
  const versionCounts = new Map();
  const channelCounts = new Map();
  const now = Date.now();

  do {
    const page = await env.INSTALLS.list({ prefix: "install:", cursor, limit: 1000 });
    totalKeys += page.keys.length;
    for (const entry of page.keys) {
      const m = entry.metadata;
      if (!m) continue;
      const last = Date.parse(m.lastSeen || m.at || "");
      if (Number.isFinite(last)) {
        const age = now - last;
        if (age <= DAY_MS) active24h++;
        if (age <= 7 * DAY_MS) active7d++;
        if (age <= 30 * DAY_MS) active30d++;
      }
      if (m.version) versionCounts.set(m.version, (versionCounts.get(m.version) || 0) + 1);
      const ch = m.channel || "github";
      channelCounts.set(ch, (channelCounts.get(ch) || 0) + 1);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);

  const legacyTotal = parseInt((await env.INSTALLS.get("total")) ?? "0", 10) || 0;
  const installs = Math.max(totalKeys, legacyTotal);

  const versions = [...versionCounts.entries()]
    .map(([version, count]) => ({ version, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 25);

  const channels = [...channelCounts.entries()]
    .map(([channel, count]) => ({ channel, count }))
    .sort((a, b) => b.count - a.count);

  return { installs, active24h, active7d, active30d, versions, channels };
}

async function stats(env) {
  return json(await computeStats(env));
}

async function badge(env) {
  const { installs } = await computeStats(env);
  return json({
    schemaVersion: 1,
    label: "installs",
    message: String(installs),
    color: "2ea44f"
  });
}

async function dashboard(env) {
  const s = await computeStats(env);
  const esc = (v) => String(v).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c]));
  const versionRows = s.versions.map((v) => `<tr><td>v${esc(v.version)}</td><td>${v.count}</td></tr>`).join("");
  const channelRows = s.channels.map((c) => `<tr><td>${esc(c.channel)}</td><td>${c.count}</td></tr>`).join("");
  const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LogaRhythm Installs</title>
<style>body{font-family:system-ui,sans-serif;background:#0e1013;color:#f3f4f6;margin:0;padding:24px;max-width:760px;margin:auto}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin:16px 0}
.card{background:#181b20;border-radius:14px;padding:16px}.num{font-size:28px;font-weight:700;color:#f4b266}
table{width:100%;border-collapse:collapse;background:#181b20;border-radius:14px;overflow:hidden;margin-top:12px}
td,th{padding:10px 14px;text-align:left;border-bottom:1px solid #262930}th{color:#9ca3af;font-size:12px;text-transform:uppercase}
</style></head><body><h1>LogaRhythm Install Stats</h1>
<div class="grid">
  <div class="card"><div>Total Unique</div><div class="num">${s.installs}</div></div>
  <div class="card"><div>Active 7d</div><div class="num">${s.active7d}</div></div>
  <div class="card"><div>Active 30d</div><div class="num">${s.active30d}</div></div>
</div>
<h2>Versions</h2><table><tr><th>Version</th><th>Users</th></tr>${versionRows || "<tr><td colspan=2>No data yet</td></tr>"}</table>
<h2>Channels</h2><table><tr><th>Channel</th><th>Users</th></tr>${channelRows || "<tr><td colspan=2>No data yet</td></tr>"}</table>
</body></html>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
}

/**
 * Release Radar endpoint:
 * 1) Resolves canonical artist ID on iTunes (`entity=musicArtist`), then queries `/lookup?entity=song&sort=recent`
 *    and `/lookup?entity=album&sort=recent` so tracks/singles carry accurate ISO-8601 `releaseDate`.
 * 2) Resolves canonical artist ID on Deezer and queries `/artist/{id}/albums` for accurate `release_date`.
 * 3) Deduplicates and sorts strictly descending by releaseDate timestamp.
 */
async function releases(url) {
  const artist = field(url.searchParams.get("artist") || "", 80);
  if (!artist) return json({ error: "missing artist" }, 400);

  const q = normalizeArtist(artist);
  const headers = {
    accept: "application/json",
    "user-agent": "LogaRhythm/2.1 (ReleaseRadar)"
  };

  const collected = [];

  // 1. iTunes canonical artist lookup
  try {
    const artistSearchUrl = `https://itunes.apple.com/search?term=${encodeURIComponent(artist)}&entity=musicArtist&limit=5`;
    const artistResp = await fetch(artistSearchUrl, {
      headers,
      cf: { cacheTtl: 1800, cacheEverything: true }
    });
    if (artistResp.ok) {
      const artistJson = await artistResp.json();
      const artistHits = Array.isArray(artistJson?.results) ? artistJson.results : [];
      const matchedArtist = artistHits.find((a) => artistMatches(a?.artistName, q));
      if (matchedArtist?.artistId) {
        const artistId = matchedArtist.artistId;
        const [songsResp, albumsResp] = await Promise.all([
          fetch(`https://itunes.apple.com/lookup?id=${artistId}&entity=song&limit=25&sort=recent`, {
            headers,
            cf: { cacheTtl: 1800, cacheEverything: true }
          }),
          fetch(`https://itunes.apple.com/lookup?id=${artistId}&entity=album&limit=15&sort=recent`, {
            headers,
            cf: { cacheTtl: 1800, cacheEverything: true }
          })
        ]);

        if (songsResp.ok) {
          const body = await songsResp.json();
          const raw = Array.isArray(body?.results) ? body.results : [];
          for (const item of raw) {
            if (!item || item.wrapperType !== "track" || !item.trackId || !item.trackName) continue;
            if (!artistMatches(item.artistName, q)) continue;
            const releaseDate = String(item.releaseDate || "").trim();
            if (!releaseDate) continue;
            collected.push({
              id: `it_${item.trackId}`,
              title: String(item.trackName),
              artist: String(item.artistName || matchedArtist.artistName || artist),
              album: String(item.collectionName || ""),
              artworkUrl: String(item.artworkUrl100 || "").replace("100x100bb", "600x600bb"),
              durationMs: Number(item.trackTimeMillis || 0),
              releaseDate,
              trackViewUrl: String(item.trackViewUrl || item.collectionViewUrl || "")
            });
          }
        }

        if (albumsResp.ok) {
          const body = await albumsResp.json();
          const raw = Array.isArray(body?.results) ? body.results : [];
          for (const item of raw) {
            if (!item || item.wrapperType !== "collection" || !item.collectionId || !item.collectionName) continue;
            if (!artistMatches(item.artistName, q)) continue;
            const releaseDate = String(item.releaseDate || "").trim();
            if (!releaseDate) continue;
            collected.push({
              id: `it_alb_${item.collectionId}`,
              title: String(item.collectionName),
              artist: String(item.artistName || matchedArtist.artistName || artist),
              album: String(item.collectionName),
              artworkUrl: String(item.artworkUrl100 || "").replace("100x100bb", "600x600bb"),
              durationMs: 0,
              releaseDate,
              collectionViewUrl: String(item.collectionViewUrl || "")
            });
          }
        }
      }
    }
  } catch {
    // Fall through cleanly to Deezer
  }

  // 2. Deezer canonical artist albums (also gives accurate release_date)
  try {
    const artistSearchUrl = `https://api.deezer.com/search/artist?q=${encodeURIComponent(artist)}&limit=5`;
    const artistResp = await fetch(artistSearchUrl, {
      headers,
      cf: { cacheTtl: 1800, cacheEverything: true }
    });
    if (artistResp.ok) {
      const artistBody = await artistResp.json();
      const artistHits = Array.isArray(artistBody?.data) ? artistBody.data : [];
      const matched = artistHits.find((a) => artistMatches(a?.name, q));
      if (matched?.id) {
        const albumsUrl = `https://api.deezer.com/artist/${matched.id}/albums?limit=25`;
        const albumsResp = await fetch(albumsUrl, {
          headers,
          cf: { cacheTtl: 1800, cacheEverything: true }
        });
        if (albumsResp.ok) {
          const albumsBody = await albumsResp.json();
          const albumsRaw = Array.isArray(albumsBody?.data) ? albumsBody.data : [];
          for (const item of albumsRaw) {
            if (!item || !item.id || !item.title) continue;
            const releaseDate = String(item.release_date || "").trim();
            if (!releaseDate) continue;
            collected.push({
              id: `dz_alb_${item.id}`,
              title: String(item.title),
              artist: String(matched.name || artist),
              album: String(item.title),
              artworkUrl: String(item.cover_xl || item.cover_big || item.cover_medium || ""),
              durationMs: 0,
              releaseDate,
              collectionViewUrl: String(item.link || "")
            });
          }
        }
      }
    }
  } catch {
    // Keep collected iTunes items if Deezer fails
  }

  // Deduplicate by normalized artist + title, keeping earliest-parsed most-precise timestamp
  const byKey = new Map();
  for (const item of collected) {
    const key = `${normalizeArtist(item.artist)}::${normalizeArtist(item.title)}`;
    const ts = Date.parse(item.releaseDate);
    if (!Number.isFinite(ts)) continue;
    const prev = byKey.get(key);
    if (!prev || ts > prev._ts || (ts === prev._ts && item.durationMs > prev.durationMs)) {
      byKey.set(key, { ...item, _ts: ts });
    }
  }

  const sorted = [...byKey.values()]
    .sort((a, b) => b._ts - a._ts)
    .slice(0, 30)
    .map(({ _ts, ...rest }) => rest);

  return new Response(JSON.stringify({ tracks: sorted }), {
    status: 200,
    headers: { ...CORS, "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=1800" }
  });
}

async function submitCrash(request, env) {
  const { data, error } = await parseBody(request, MAX_BODY_REPORT);
  if (error) return error;

  const entry = {
    type: "crash",
    at: new Date().toISOString(),
    installId: field(data.installId, 64),
    version: field(data.version, 24),
    code: Number(data.code || 0),
    channel: field(data.channel, 24) || "github",
    exceptionType: field(data.exceptionType, 120),
    message: sanitizeMultiline(data.message, 600),
    stackTrace: sanitizeMultiline(data.stackTrace, 6000),
    recentLogs: sanitizeMultiline(data.recentLogs, 6000),
    androidVersion: field(data.androidVersion, 24),
    abi: field(data.abi, 24)
  };
  if (!entry.exceptionType && !entry.stackTrace) return json({ error: "missing crash data" }, 400);

  const key = `crash:${Date.now()}:${crypto.randomUUID().slice(0, 8)}`;
  await env.INSTALLS.put(key, JSON.stringify(entry), {
    expirationTtl: TTL_30_DAYS,
    metadata: {
      at: entry.at,
      version: entry.version,
      channel: entry.channel,
      exceptionType: entry.exceptionType,
      message: entry.message.slice(0, 140)
    }
  });

  const tgText = [
    `💥 LogaRhythm Crash (${entry.channel} v${entry.version})`,
    `Exception: ${entry.exceptionType}`,
    entry.message ? `Message: ${entry.message.slice(0, 280)}` : "",
    `Android: ${entry.androidVersion || "?"} (${entry.abi || "?"})`,
    entry.stackTrace ? `\nStack:\n${entry.stackTrace.slice(0, 1200)}` : ""
  ].filter(Boolean).join("\n");
  await sendTelegram(env, tgText);

  return json({ ok: true, id: key });
}

async function submitReport(request, env) {
  const { data, error } = await parseBody(request, MAX_BODY_REPORT);
  if (error) return error;

  const description = sanitizeMultiline(data.description, 2500);
  if (!description) return json({ error: "missing description" }, 400);

  const entry = {
    type: "report",
    at: new Date().toISOString(),
    installId: field(data.installId, 64),
    version: field(data.version, 24),
    code: Number(data.code || 0),
    channel: field(data.channel, 24) || "github",
    device: field(data.device, 64),
    androidVersion: field(data.androidVersion, 24),
    category: field(data.category, 48) || "bug",
    description,
    contact: field(data.contact, 120),
    deviceSnapshot: sanitizeMultiline(data.deviceSnapshot, 4000),
    recentLogs: sanitizeMultiline(data.recentLogs, 8000)
  };

  const key = `report:${Date.now()}:${crypto.randomUUID().slice(0, 8)}`;
  await env.INSTALLS.put(key, JSON.stringify(entry), {
    expirationTtl: TTL_30_DAYS,
    metadata: {
      at: entry.at,
      version: entry.version,
      channel: entry.channel,
      category: entry.category,
      summary: description.slice(0, 140)
    }
  });

  const tgText = [
    `🫨 Shake Bug Report (${entry.channel} v${entry.version})`,
    `Category: ${entry.category}`,
    entry.device || entry.androidVersion ? `Device: ${entry.device || "?"} | Android ${entry.androidVersion || "?"}` : "",
    entry.contact ? `Contact: ${entry.contact}` : "",
    `\n${description.slice(0, 1200)}`,
    entry.deviceSnapshot ? `\nState:\n${entry.deviceSnapshot.slice(0, 1500)}` : "",
    entry.recentLogs ? `\nRecent logs (tail):\n${entry.recentLogs.slice(-800)}` : ""
  ].filter(Boolean).join("\n");
  await sendTelegram(env, tgText);

  return json({ ok: true, id: key });
}

async function submitFeedback(request, env) {
  const { data, error } = await parseBody(request, 8192);
  if (error) return error;

  const text = sanitizeMultiline(data.text, 2500);
  if (!text) return json({ error: "missing feedback text" }, 400);

  const entry = {
    type: "feedback",
    at: new Date().toISOString(),
    installId: field(data.installId, 64),
    version: field(data.version, 24),
    code: Number(data.code || 0),
    channel: field(data.channel, 24) || "github",
    category: field(data.category, 48) || "suggestion",
    text,
    contact: field(data.contact, 120)
  };

  const key = `feedback:${Date.now()}:${crypto.randomUUID().slice(0, 8)}`;
  await env.INSTALLS.put(key, JSON.stringify(entry), {
    expirationTtl: TTL_30_DAYS,
    metadata: {
      at: entry.at,
      version: entry.version,
      channel: entry.channel,
      category: entry.category,
      summary: text.slice(0, 140)
    }
  });

  const tgText = [
    `💡 User Suggestion (${entry.channel} v${entry.version})`,
    `Topic: ${entry.category}`,
    entry.contact ? `Contact: ${entry.contact}` : "",
    `\n${text.slice(0, 1800)}`
  ].filter(Boolean).join("\n");
  await sendTelegram(env, tgText);

  return json({ ok: true, id: key });
}

async function submitWeeklyListening(request, env) {
  const { data, error } = await parseBody(request, MAX_BODY_SMALL);
  if (error) return error;

  const week = field(data.week, 16);
  const installId = field(data.installId, 64);
  if (!/^\d{4}-W\d{2}$/.test(week) || !/^[0-9a-f-]{16,64}$/i.test(installId)) {
    return json({ error: "bad weekly payload" }, 400);
  }

  const seenKey = `weekly_seen:${week}:${installId}`;
  const already = await env.INSTALLS.get(seenKey);
  if (already) return json({ ok: true, deduplicated: true });

  const totalPlays = Math.max(0, Math.min(50000, Number(data.totalPlays || 0)));
  const totalMinutes = Math.max(0, Math.min(200000, Number(data.totalMinutes || 0)));
  const topTracksRaw = Array.isArray(data.topTracks) ? data.topTracks.slice(0, 5) : [];

  const weeklyKey = `weekly:${week}`;
  const existing = (await env.INSTALLS.get(weeklyKey, { type: "json" })) || {
    week,
    reporters: 0,
    totalPlays: 0,
    totalMinutes: 0,
    tracks: {}
  };

  existing.reporters = (existing.reporters || 0) + 1;
  existing.totalPlays = (existing.totalPlays || 0) + totalPlays;
  existing.totalMinutes = (existing.totalMinutes || 0) + totalMinutes;
  existing.tracks = existing.tracks || {};

  const trackSummaryLines = [];
  for (const t of topTracksRaw) {
    const title = field(t?.title, 60);
    const artist = field(t?.artist, 60);
    const plays = Math.max(1, Math.min(5000, Number(t?.plays || 1)));
    if (!title) continue;
    const label = artist ? `${artist} — ${title}` : title;
    existing.tracks[label] = (existing.tracks[label] || 0) + plays;
    trackSummaryLines.push(`• ${label} (${plays}x)`);
  }

  await env.INSTALLS.put(weeklyKey, JSON.stringify(existing), { expirationTtl: TTL_90_DAYS });
  await env.INSTALLS.put(seenKey, "1", { expirationTtl: TTL_90_DAYS });

  const tgText = [
    `📊 Weekly Listening Pulse (${week})`,
    `Plays: ${totalPlays} · Minutes: ${totalMinutes} · Reporters this week: ${existing.reporters}`,
    trackSummaryLines.length ? `\nTop tracks:\n${trackSummaryLines.join("\n")}` : ""
  ].filter(Boolean).join("\n");
  await sendTelegram(env, tgText);

  return json({ ok: true });
}

async function handleAdmin(url, request, env) {
  if (!env.ADMIN_TOKEN) return json({ error: "admin disabled" }, 503);
  const auth = request.headers.get("authorization") || "";
  const bearer = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  const headerToken = request.headers.get("x-admin-token")?.trim() || "";
  const token = bearer || headerToken;
  if (!token || !timingSafeEqual(token, env.ADMIN_TOKEN)) {
    return json({ error: "unauthorized" }, 401);
  }

  const limit = Math.max(1, Math.min(100, Number(url.searchParams.get("limit") || 25)));
  if (url.pathname === "/admin/summary") {
    const s = await computeStats(env);
    const [crashes, reports, feedback, weekly] = await Promise.all([
      listPrefix(env, "crash:", 10),
      listPrefix(env, "report:", 10),
      listPrefix(env, "feedback:", 10),
      listPrefix(env, "weekly:", 8)
    ]);
    return json({ stats: s, recentCrashes: crashes, recentReports: reports, recentFeedback: feedback, weekly });
  }
  if (url.pathname === "/admin/crashes") return json({ items: await listPrefix(env, "crash:", limit) });
  if (url.pathname === "/admin/reports") return json({ items: await listPrefix(env, "report:", limit) });
  if (url.pathname === "/admin/feedback") return json({ items: await listPrefix(env, "feedback:", limit) });
  if (url.pathname === "/admin/weekly") return json({ items: await listPrefix(env, "weekly:", limit) });

  return json({ error: "not found" }, 404);
}

async function listPrefix(env, prefix, limit) {
  const page = await env.INSTALLS.list({ prefix, limit });
  const keys = [...page.keys].sort((a, b) => (a.name < b.name ? 1 : -1)).slice(0, limit);
  const items = await Promise.all(
    keys.map(async (k) => {
      const value = await env.INSTALLS.get(k.name, { type: "json" });
      return { id: k.name, metadata: k.metadata || null, value };
    })
  );
  return items;
}

async function sendTelegram(env, text) {
  if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID) return;
  const endpoint = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`;
  try {
    await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        chat_id: env.TELEGRAM_CHAT_ID,
        text: String(text).slice(0, 3900),
        disable_web_page_preview: true
      })
    });
  } catch {
    // Non-fatal: never fail client submission if Telegram is temporarily unreachable.
  }
}

function timingSafeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const enc = new TextEncoder();
  const ab = enc.encode(a);
  const bb = enc.encode(b);
  if (ab.length !== bb.length) return false;
  let diff = 0;
  for (let i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
  return diff === 0;
}

function normalizeArtist(name) {
  return String(name || "")
    .normalize("NFKD")
    .replace(/[\u064A\u0649]/g, "\u06CC")
    .replace(/\u0643/g, "\u06A9")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function artistMatches(candidate, normalizedTarget) {
  const c = normalizeArtist(candidate);
  if (!c || !normalizedTarget) return false;
  return c === normalizedTarget || c.includes(normalizedTarget) || normalizedTarget.includes(c);
}

function field(value, max = MAX_FIELD) {
  return typeof value === "string"
    ? value.replace(/[\r\n\t]/g, " ").trim().slice(0, max)
    : "";
}

function sanitizeMultiline(value, max) {
  return typeof value === "string"
    ? value.replace(/\r\n/g, "\n").trim().slice(0, max)
    : "";
}

function adminDashboard() {
  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>LogaRhythm Admin</title>
<style>
  :root { color-scheme: dark; --bg:#0d1117; --card:#161b22; --border:#30363d; --text:#e6edf3; --muted:#8b949e; --accent:#34d399; --danger:#f87171; --warn:#fbbf24; }
  * { box-sizing:border-box; }
  body { margin:0; font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif; background:var(--bg); color:var(--text); line-height:1.5; }
  .wrap { max-width:1040px; margin:0 auto; padding:20px 16px 48px; }
  header { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:12px; margin-bottom:20px; }
  h1 { font-size:20px; margin:0; display:flex; align-items:center; gap:8px; }
  .badge { font-size:12px; padding:2px 8px; border-radius:999px; background:#1f2937; color:var(--accent); border:1px solid var(--border); }
  .card { background:var(--card); border:1px solid var(--border); border-radius:12px; padding:16px; margin-bottom:14px; }
  .login { max-width:420px; margin:64px auto; }
  .row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
  input[type=password], input[type=text] { flex:1; min-width:180px; background:#0b0f14; color:var(--text); border:1px solid var(--border); border-radius:8px; padding:10px 12px; font-size:14px; }
  button { background:var(--accent); color:#052e16; border:0; border-radius:8px; padding:9px 14px; font-weight:600; cursor:pointer; font-size:13px; }
  button.sec { background:#21262d; color:var(--text); border:1px solid var(--border); }
  button:disabled { opacity:.6; cursor:default; }
  .stats { display:grid; grid-template-columns:repeat(auto-fit,minmax(140px,1fr)); gap:10px; margin-bottom:16px; }
  .stat { background:var(--card); border:1px solid var(--border); border-radius:10px; padding:12px; }
  .stat .k { font-size:12px; color:var(--muted); }
  .stat .v { font-size:22px; font-weight:700; margin-top:2px; }
  .tabs { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:14px; }
  .tab { background:#161b22; color:var(--muted); border:1px solid var(--border); border-radius:999px; padding:7px 14px; font-size:13px; cursor:pointer; }
  .tab.active { background:var(--accent); color:#052e16; border-color:var(--accent); font-weight:600; }
  .meta { display:flex; flex-wrap:wrap; gap:8px; align-items:center; font-size:12px; color:var(--muted); margin-bottom:8px; }
  .pill { padding:2px 8px; border-radius:6px; background:#21262d; color:var(--text); border:1px solid var(--border); }
  .pill.warn { color:var(--warn); }
  .pill.danger { color:var(--danger); }
  pre { background:#0b0f14; border:1px solid var(--border); border-radius:8px; padding:12px; overflow:auto; max-height:360px; font-size:12px; white-space:pre-wrap; word-break:break-word; margin:8px 0 0; }
  details { margin-top:8px; }
  summary { cursor:pointer; color:var(--accent); font-size:13px; font-weight:600; }
  .err { color:var(--danger); font-size:13px; margin-top:8px; }
  .empty { color:var(--muted); text-align:center; padding:32px 12px; }
</style>
</head>
<body>
<div class="wrap">
  <div id="loginView" class="card login">
    <h1 style="margin-bottom:8px">LogaRhythm Admin</h1>
    <p style="color:var(--muted);font-size:13px;margin:0 0 14px">Enter your ADMIN_TOKEN to view crash reports, shake bug logs, suggestions, and weekly stats.</p>
    <form id="loginForm" class="row">
      <input id="tokenInput" type="password" placeholder="ADMIN_TOKEN" autocomplete="current-password" required>
      <button type="submit">Sign in</button>
    </form>
    <div id="loginErr" class="err"></div>
  </div>

  <div id="appView" style="display:none">
    <header>
      <h1>LogaRhythm Admin <span class="badge" id="statusBadge">Connected</span></h1>
      <div class="row">
        <button class="sec" id="refreshBtn">Refresh</button>
        <button class="sec" id="logoutBtn">Log out</button>
      </div>
    </header>
    <div class="stats" id="statsGrid"></div>
    <div class="tabs" id="tabs">
      <button class="tab active" data-tab="reports">Bug Reports</button>
      <button class="tab" data-tab="crashes">Crashes</button>
      <button class="tab" data-tab="feedback">Suggestions</button>
      <button class="tab" data-tab="weekly">Weekly Stats</button>
    </div>
    <div id="list"></div>
  </div>
</div>
<script>
const KEY = "lr_admin_token";
let activeTab = "reports";
const el = id => document.getElementById(id);
const esc = s => String(s ?? "").replace(/[&<>"]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;" }[c]));

function getToken() { return sessionStorage.getItem(KEY) || ""; }

async function api(path) {
  const res = await fetch(path, { headers: { "x-admin-token": getToken() } });
  if (res.status === 401) throw new Error("unauthorized");
  if (!res.ok) throw new Error("HTTP " + res.status);
  return res.json();
}

function fmtDate(iso) {
  if (!iso) return "";
  try { return new Date(iso).toLocaleString(); } catch { return iso; }
}

function renderStats(s) {
  const versions = (s.versions || []).slice(0, 4).map(v => v.version + ": " + v.count).join(" · ") || "—";
  const channels = (s.channels || []).map(c => c.channel + ": " + c.count).join(" · ") || "—";
  el("statsGrid").innerHTML = [
    ["Installs", s.installs ?? 0],
    ["Active 24h", s.active24h ?? 0],
    ["Active 7d", s.active7d ?? 0],
    ["Active 30d", s.active30d ?? 0],
    ["Versions", versions],
    ["Channels", channels]
  ].map(([k, v]) => '<div class="stat"><div class="k">' + esc(k) + '</div><div class="v" style="' + (typeof v === "string" ? "font-size:13px;font-weight:600" : "") + '">' + esc(v) + '</div></div>').join("");
}

function renderItems(items) {
  if (!items || !items.length) {
    el("list").innerHTML = '<div class="card empty">No entries yet.</div>';
    return;
  }
  el("list").innerHTML = items.map((item, idx) => {
    const v = item.value || {};
    if (activeTab === "weekly") {
      const tracks = (v.topTracks || []).map(t => "• " + t.title + " — " + t.artist + " (" + t.plays + ")").join("\\n");
      return '<div class="card">' +
        '<div class="meta"><span class="pill">' + esc(v.week || item.id) + '</span><span>Reporters: ' + esc(v.reporters) + '</span><span>Plays: ' + esc(v.totalPlays) + '</span><span>Minutes: ' + esc(v.totalMinutes) + '</span></div>' +
        (tracks ? '<pre>' + esc(tracks) + '</pre>' : '') +
      '</div>';
    }
    const body = v.description || v.text || v.message || "";
    const stateSnap = v.deviceSnapshot || "";
    const logs = v.recentLogs || v.stack || "";
    const combinedLogs = stateSnap && !logs.includes("=== DEVICE & SYSTEM ===")
      ? stateSnap + (logs ? "\n\n" + logs : "")
      : logs;
    const pillClass = activeTab === "crashes" ? "danger" : (activeTab === "reports" ? "warn" : "");
    return '<div class="card">' +
      '<div class="meta">' +
        '<span class="pill ' + pillClass + '">' + esc(v.category || v.exception || activeTab) + '</span>' +
        '<span class="pill">' + esc((v.channel || "github") + " v" + (v.version || "?")) + '</span>' +
        (v.device ? '<span>' + esc(v.device) + '</span>' : '') +
        (v.contact ? '<span>Contact: ' + esc(v.contact) + '</span>' : '') +
        '<span>' + esc(fmtDate(v.at)) + '</span>' +
      '</div>' +
      (body ? '<div style="white-space:pre-wrap;font-size:14px">' + esc(body) + '</div>' : '') +
      (combinedLogs ? '<details><summary>View State Snapshot & Logs</summary><div style="margin-top:8px"><button class="sec" onclick="copyPre(' + idx + ')">Copy</button></div><pre id="pre_' + idx + '">' + esc(combinedLogs) + '</pre></details>' : '') +
    '</div>';
  }).join("");
}

window.copyPre = async function(idx) {
  const node = document.getElementById("pre_" + idx);
  if (!node) return;
  await navigator.clipboard.writeText(node.textContent);
};

async function loadAll() {
  el("statusBadge").textContent = "Loading...";
  try {
    const [summary, listRes] = await Promise.all([
      api("/admin/summary?limit=10"),
      api("/admin/" + activeTab + "?limit=50")
    ]);
    renderStats(summary.stats || {});
    renderItems(listRes.items || []);
    el("loginView").style.display = "none";
    el("appView").style.display = "block";
    el("statusBadge").textContent = "Connected";
  } catch (e) {
    if (e.message === "unauthorized") {
      sessionStorage.removeItem(KEY);
      el("appView").style.display = "none";
      el("loginView").style.display = "block";
      el("loginErr").textContent = "Invalid ADMIN_TOKEN.";
    } else {
      el("statusBadge").textContent = "Error: " + e.message;
    }
  }
}

el("loginForm").addEventListener("submit", async ev => {
  ev.preventDefault();
  el("loginErr").textContent = "";
  sessionStorage.setItem(KEY, el("tokenInput").value.trim());
  await loadAll();
});
el("logoutBtn").addEventListener("click", () => {
  sessionStorage.removeItem(KEY);
  el("tokenInput").value = "";
  el("appView").style.display = "none";
  el("loginView").style.display = "block";
});
el("refreshBtn").addEventListener("click", loadAll);
el("tabs").addEventListener("click", async ev => {
  const btn = ev.target.closest(".tab");
  if (!btn) return;
  document.querySelectorAll(".tab").forEach(b => b.classList.remove("active"));
  btn.classList.add("active");
  activeTab = btn.dataset.tab;
  const listRes = await api("/admin/" + activeTab + "?limit=50");
  renderItems(listRes.items || []);
});

if (getToken()) loadAll();
</script>
</body>
</html>`;
  return new Response(html, {
    status: 200,
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" }
  });
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}
