/**
 * LogaRhythm install & active-user counter (Cloudflare Worker + KV).
 *
 * Works across all distribution channels (GitHub, Telegram, direct APK shares).
 * Stores device/version metadata inside KV key metadata on `install:<installId>`
 * so /stats avoids both lost-update KV race conditions and N+1 KV subrequest limits.
 *
 * Routes:
 *   POST /hit     Register or heartbeat an installId
 *   GET  /stats   JSON stats: { installs, active24h, active7d, active30d, versions, devices }
 *   GET  /badge   shields.io JSON badge
 *   GET  /        Human-readable live HTML dashboard
 */

const MAX_BODY = 2048;
const MAX_FIELD = 48;
const DAY_MS = 24 * 60 * 60 * 1000;

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "content-type"
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (url.pathname === "/hit" && request.method === "POST") return hit(request, env);
    if (url.pathname === "/stats" && request.method === "GET") return stats(env);
    if (url.pathname === "/badge" && request.method === "GET") return badge(env);
    if (url.pathname === "/releases" && request.method === "GET") return releases(url);
    if (url.pathname === "/" && request.method === "GET") return dashboard(env);
    return json({ error: "not found" }, 404);
  }
};

async function hit(request, env) {
  const text = await request.text();
  if (text.length > MAX_BODY) return json({ error: "too large" }, 413);

  let data;
  try {
    data = JSON.parse(text);
  } catch {
    return json({ error: "bad json" }, 400);
  }

  const installId = field(data.installId, 64);
  if (!/^[0-9a-f-]{16,64}$/i.test(installId)) return json({ error: "bad id" }, 400);

  const key = `install:${installId}`;
  const existing = await env.INSTALLS.getWithMetadata(key, { type: "json" });
  const nowIso = new Date().toISOString();
  const prevMeta = existing.metadata || (existing.value && typeof existing.value === "object" ? existing.value : null);

  const model = [field(data.manufacturer), field(data.model)].filter(Boolean).join(" ") || "unknown";
  const record = {
    model,
    version: field(data.version, 24),
    android: field(data.android, 24),
    abi: field(data.abi, 24),
    firstSeen: prevMeta?.firstSeen || prevMeta?.at || nowIso,
    lastSeen: nowIso
  };

  // Store record in metadata so list({ prefix: "install:" }) reads everything in 1 call.
  await env.INSTALLS.put(key, "1", { metadata: record });
  return json({ ok: true, existing: Boolean(prevMeta) });
}

async function computeStats(env) {
  let cursor = undefined;
  let totalKeys = 0;
  let active24h = 0;
  let active7d = 0;
  let active30d = 0;
  const deviceCounts = new Map();
  const versionCounts = new Map();
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
      if (m.model) deviceCounts.set(m.model, (deviceCounts.get(m.model) || 0) + 1);
      if (m.version) versionCounts.set(m.version, (versionCounts.get(m.version) || 0) + 1);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);

  const legacyTotal = parseInt((await env.INSTALLS.get("total")) ?? "0", 10) || 0;
  const installs = Math.max(totalKeys, legacyTotal);

  const devices = [...deviceCounts.entries()]
    .map(([model, count]) => ({ model, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 50);

  const versions = [...versionCounts.entries()]
    .map(([version, count]) => ({ version, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 25);

  return { installs, active24h, active7d, active30d, versions, devices };
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
  const deviceRows = s.devices.map((d) => `<tr><td>${esc(d.model)}</td><td>${d.count}</td></tr>`).join("");
  const versionRows = s.versions.map((v) => `<tr><td>v${esc(v.version)}</td><td>${v.count}</td></tr>`).join("");
  const html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LogaRhythm Installs</title>
<style>body{font-family:system-ui,sans-serif;background:#0e1013;color:#f3f4f6;margin:0;padding:24px;max-width:760px;margin:auto}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px;margin:16px 0}
.card{background:#181b20;border-radius:14px;padding:16px}.num{font-size:28px;font-weight:700;color:#f4b266}
table{width:100%;border-collapse:collapse;background:#181b20;border-radius:14px;overflow:hidden;margin-top:12px}
td,th{padding:10px 14px;text-align:left;border-bottom:1px solid #262930}th{color:#9ca3af;font-size:12px;text-transform:uppercase}
</style></head><body><h1>LogaRhythm Install Stats</h1>
<div class="grid">
  <div class="card"><div>Total Unique</div><div class="num">${s.installs}</div></div>
  <div class="card"><div>Active 24h</div><div class="num">${s.active24h}</div></div>
  <div class="card"><div>Active 7d</div><div class="num">${s.active7d}</div></div>
  <div class="card"><div>Active 30d</div><div class="num">${s.active30d}</div></div>
</div>
<h2>Versions</h2><table><tr><th>Version</th><th>Users</th></tr>${versionRows || "<tr><td colspan=2>No data yet</td></tr>"}</table>
<h2>Top Devices</h2><table><tr><th>Model</th><th>Users</th></tr>${deviceRows || "<tr><td colspan=2>No data yet</td></tr>"}</table>
</body></html>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
}

async function releases(url) {
  const artist = field(url.searchParams.get("artist") || "", 80);
  if (!artist) return json({ error: "missing artist" }, 400);

  const q = artist.toLowerCase();
  const headers = {
    accept: "application/json",
    "user-agent": "LogaRhythm/2.0 (ReleaseRadar)"
  };

  // 1. Primary: iTunes Search API (30m edge cache)
  try {
    const itunesUrl = `https://itunes.apple.com/search?term=${encodeURIComponent(artist)}&entity=song&limit=15&sort=recent`;
    const upstream = await fetch(itunesUrl, {
      headers,
      cf: { cacheTtl: 1800, cacheEverything: true }
    });
    if (upstream.ok) {
      const body = await upstream.json();
      const raw = Array.isArray(body?.results) ? body.results : [];
      const normalized = raw
        .filter((item) => item && item.trackId && item.trackName)
        .filter((item) => {
          const a = String(item.artistName || "").toLowerCase();
          return a.includes(q) || q.includes(a);
        })
        .map((item) => ({
          id: String(item.trackId),
          title: String(item.trackName || ""),
          artist: String(item.artistName || artist),
          album: String(item.collectionName || ""),
          artworkUrl: String(item.artworkUrl100 || "").replace("100x100bb", "600x600bb"),
          durationMs: Number(item.trackTimeMillis || 0),
          releaseDate: String(item.releaseDate || "")
        }));
      if (normalized.length > 0) {
        return new Response(JSON.stringify({ tracks: normalized }), {
          status: 200,
          headers: { ...CORS, "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=1800" }
        });
      }
    }
  } catch {
    // Fall through to Deezer fallback.
  }

  // 2. Fallback: Deezer artist albums (with release_date), then general track search
  try {
    const artistSearchUrl = `https://api.deezer.com/search/artist?q=${encodeURIComponent(artist)}&limit=5`;
    const artistResp = await fetch(artistSearchUrl, {
      headers,
      cf: { cacheTtl: 1800, cacheEverything: true }
    });
    if (artistResp.ok) {
      const artistBody = await artistResp.json();
      const artistHits = Array.isArray(artistBody?.data) ? artistBody.data : [];
      const matched = artistHits.find((a) => {
        const name = String(a?.name || "").toLowerCase();
        return name && (name.includes(q) || q.includes(name));
      });
      if (matched?.id) {
        const albumsUrl = `https://api.deezer.com/artist/${matched.id}/albums?limit=15`;
        const albumsResp = await fetch(albumsUrl, {
          headers,
          cf: { cacheTtl: 1800, cacheEverything: true }
        });
        if (albumsResp.ok) {
          const albumsBody = await albumsResp.json();
          const albumsRaw = Array.isArray(albumsBody?.data) ? albumsBody.data : [];
          const normalizedAlbums = albumsRaw
            .filter((item) => item && item.id && item.title)
            .map((item) => ({
              id: `dz_alb_${item.id}`,
              title: String(item.title || ""),
              artist: String(matched.name || artist),
              album: String(item.title || ""),
              artworkUrl: String(item.cover_xl || item.cover_big || item.cover_medium || ""),
              durationMs: 0,
              releaseDate: String(item.release_date || "")
            }));
          if (normalizedAlbums.length > 0) {
            return new Response(JSON.stringify({ tracks: normalizedAlbums }), {
              status: 200,
              headers: { ...CORS, "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=1800" }
            });
          }
        }
      }
    }

    const deezerUrl = `https://api.deezer.com/search?q=${encodeURIComponent(artist)}&limit=15`;
    const dzResp = await fetch(deezerUrl, {
      headers,
      cf: { cacheTtl: 1800, cacheEverything: true }
    });
    if (!dzResp.ok) return json({ error: "upstream", status: dzResp.status }, 502);
    const dzBody = await dzResp.json();
    const raw = Array.isArray(dzBody?.data) ? dzBody.data : [];
    const normalized = raw
      .filter((item) => item && item.id && item.title)
      .filter((item) => {
        const a = String(item.artist?.name || "").toLowerCase();
        return a.includes(q) || q.includes(a);
      })
      .map((item) => ({
        id: `dz_${item.id}`,
        title: String(item.title || ""),
        artist: String(item.artist?.name || artist),
        album: String(item.album?.title || ""),
        artworkUrl: String(item.album?.cover_xl || item.album?.cover_big || item.album?.cover_medium || ""),
        durationMs: Number(item.duration || 0) * 1000,
        releaseDate: ""
      }));
    return new Response(JSON.stringify({ tracks: normalized }), {
      status: 200,
      headers: { ...CORS, "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=1800" }
    });
  } catch {
    return json({ error: "unreachable" }, 502);
  }
}

function field(value, max = MAX_FIELD) {
  return typeof value === "string"
    ? value.replace(/[\r\n\t]/g, " ").trim().slice(0, max)
    : "";
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}
