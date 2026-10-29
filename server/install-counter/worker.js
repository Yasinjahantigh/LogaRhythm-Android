/**
 * LogaRhythm install counter.
 *
 * POST /hit     one record per install (deduplicated by installId)
 * GET  /stats   { installs, devices: [{ model, count }] }
 * GET  /badge   shields.io endpoint JSON
 *
 * Bind a KV namespace as INSTALLS. Counters are plain keys:
 *   install:<id>  the stored record (dedupe)
 *   total         install count
 *   device:<make model>
 *   day:<yyyy-mm-dd>
 */

const MAX_BODY = 2048;
const MAX_FIELD = 48;

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
    if (url.pathname === "/stats" && request.method === "GET") return stats(env, url);
    if (url.pathname === "/badge" && request.method === "GET") return badge(env);
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
  if (await env.INSTALLS.get(key)) return json({ ok: true, duplicate: true });

  const model = [field(data.manufacturer), field(data.model)].filter(Boolean).join(" ") || "unknown";
  const record = {
    model,
    version: field(data.version, 24),
    android: field(data.android, 24),
    abi: field(data.abi, 24),
    at: new Date().toISOString()
  };

  await env.INSTALLS.put(key, JSON.stringify(record));
  await bump(env, "total");
  await bump(env, `device:${model}`);
  await bump(env, `day:${record.at.slice(0, 10)}`);
  return json({ ok: true });
}

async function stats(env, url) {
  const total = await counter(env, "total");
  const list = await env.INSTALLS.list({ prefix: "device:", limit: 1000 });
  const devices = [];
  for (const key of list.keys) {
    devices.push({ model: key.name.slice("device:".length), count: await counter(env, key.name) });
  }
  devices.sort((a, b) => b.count - a.count);

  const days = [];
  if (url.searchParams.get("days") === "1") {
    const dayList = await env.INSTALLS.list({ prefix: "day:", limit: 365 });
    for (const key of dayList.keys) {
      days.push({ day: key.name.slice("day:".length), count: await counter(env, key.name) });
    }
    days.sort((a, b) => a.day.localeCompare(b.day));
  }

  return json({ installs: total, devices: devices.slice(0, 50), days });
}

async function badge(env) {
  return json({
    schemaVersion: 1,
    label: "installs",
    message: String(await counter(env, "total")),
    color: "2ea44f"
  });
}

async function bump(env, key) {
  const value = (await counter(env, key)) + 1;
  await env.INSTALLS.put(key, String(value));
}

async function counter(env, key) {
  return parseInt((await env.INSTALLS.get(key)) ?? "0", 10) || 0;
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
