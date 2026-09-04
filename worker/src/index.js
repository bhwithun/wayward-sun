/**
 * Shared Solcast cache. This Worker is the only Solcast client.
 *
 * GET  /cache     → latest forecasts + estimated actuals (public, for phones)
 * POST /refresh   → try an automatic Solcast pull (~5h / 5 per UTC day)
 * Cron 00/05/10/15/20 UTC → same automatic pull
 *
 * SOLCAST_API_KEY is a Wrangler secret. Phones never see it and never call Solcast.
 */

import { combinedPoints, demoSnapshot, utcDay } from "./demo.js";
import { renderPage } from "./page.js";

const DAILY_LIMIT = 10;
const DAILY_AUTO_LIMIT = 5;
const REQUESTS_PER_REFRESH = 2;
const MIN_AUTO_AGE_HOURS = 4;
const MIN_AUTO_AGE_MS = MIN_AUTO_AGE_HOURS * 60 * 60 * 1000;
const SNAPSHOT_KEY = "snapshot";
const SOLCAST = "https://api.solcast.com.au";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }));
    }

    try {
      const local = isLocalHost(url);

      if (url.pathname === "/" && request.method === "GET") {
        const snapshot = (await loadSnapshot(env)) ?? demoSnapshot(resourceId(env));
        return html(renderPage(snapshot, env, { local }));
      }

      if (url.pathname === "/health" && request.method === "GET") {
        return json({
          ok: true,
          runtime: local ? "local-wrangler" : "cloudflare",
          deployed: !local,
        });
      }

      if (url.pathname === "/cache" && request.method === "GET") {
        let snapshot = await loadSnapshot(env);
        if (!snapshot) {
          snapshot = await refresh(env, { auto: true });
        }
        return json(publicCache(snapshot));
      }

      if (url.pathname === "/refresh" && request.method === "POST") {
        const snapshot = await refresh(env, { auto: true });
        return json(publicCache(snapshot));
      }

      return json({ error: "Not found" }, 404);
    } catch (error) {
      return json({ error: error.message || String(error) }, 500);
    }
  },

  async scheduled(_event, env) {
    await refresh(env, { auto: true });
  },
};

function resourceId(env) {
  return env.RESOURCE_ID || "84d7-8b52-33f3-bd7b";
}

function isLocalHost(url) {
  return url.hostname === "127.0.0.1" || url.hostname === "localhost";
}

async function loadSnapshot(env) {
  const raw = await env.CACHE.get(SNAPSHOT_KEY);
  if (!raw) return null;
  return JSON.parse(raw);
}

async function saveSnapshot(env, snapshot) {
  await env.CACHE.put(SNAPSHOT_KEY, JSON.stringify(snapshot));
}

function publicCache(snapshot) {
  return {
    resourceId: snapshot.resourceId,
    fetchedAt: snapshot.fetchedAt,
    source: snapshot.source,
    forecasts: snapshot.forecasts,
    actuals: snapshot.actuals,
    requestsDay: snapshot.requestsDay,
    requestsUsed: snapshot.requestsUsed,
    autoFetchesUsed: snapshot.autoFetchesUsed,
    remainingRequests: Math.max(0, DAILY_LIMIT - (snapshot.requestsUsed || 0)),
    pointCount: combinedPoints(snapshot).length,
    message: snapshot.message || null,
  };
}

async function refresh(env, { auto }) {
  const now = new Date();
  const day = utcDay(now);
  const existing = await loadSnapshot(env);
  const used = existing && existing.requestsDay === day ? existing.requestsUsed : 0;
  const autoUsed = existing && existing.requestsDay === day ? existing.autoFetchesUsed : 0;
  const fetchedAt = existing?.fetchedAt ? Date.parse(existing.fetchedAt) : 0;
  const ageMs = fetchedAt ? now.getTime() - fetchedAt : Number.POSITIVE_INFINITY;

  if (existing && ageMs < MIN_AUTO_AGE_MS) {
    return {
      ...existing,
      message: `Cache younger than ${MIN_AUTO_AGE_HOURS} hours; skipped Solcast.`,
    };
  }

  if (auto && autoUsed >= DAILY_AUTO_LIMIT) {
    return {
      ...(existing ?? demoSnapshot(resourceId(env), now)),
      message: `Daily automatic pull limit reached (${DAILY_AUTO_LIMIT}).`,
    };
  }

  if (!env.SOLCAST_API_KEY) {
    const snapshot = {
      ...demoSnapshot(resourceId(env), now),
      requestsDay: day,
      requestsUsed: used,
      autoFetchesUsed: autoUsed + (auto ? 1 : 0),
      message: "No SOLCAST_API_KEY secret on this Worker — serving demo data.",
    };
    await saveSnapshot(env, snapshot);
    return snapshot;
  }

  if (used + REQUESTS_PER_REFRESH > DAILY_LIMIT) {
    return {
      ...(existing ?? demoSnapshot(resourceId(env), now)),
      message: `Daily Solcast quota reached (${used}/${DAILY_LIMIT}).`,
    };
  }

  const id = encodeURIComponent(resourceId(env));
  const forecasts = await solcastGet(
    `${SOLCAST}/rooftop_sites/${id}/forecasts?format=json`,
    env.SOLCAST_API_KEY
  );
  const actuals = await solcastGet(
    `${SOLCAST}/rooftop_sites/${id}/estimated_actuals?format=json`,
    env.SOLCAST_API_KEY
  );

  const snapshot = {
    resourceId: resourceId(env),
    fetchedAt: now.toISOString(),
    source: "solcast",
    forecasts,
    actuals,
    requestsDay: day,
    requestsUsed: used + REQUESTS_PER_REFRESH,
    autoFetchesUsed: autoUsed + (auto ? 1 : 0),
    message: "Automatic Solcast pull.",
  };
  await saveSnapshot(env, snapshot);
  return snapshot;
}

async function solcastGet(url, apiKey) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${apiKey}`,
      Accept: "application/json",
      "User-Agent": "SolWidget-CacheWorker/1.0",
    },
  });
  const body = await response.text();
  if (!response.ok) {
    throw new Error(`Solcast HTTP ${response.status}: ${body.slice(0, 240)}`);
  }
  return JSON.parse(body);
}

function json(data, status = 200) {
  return withCors(
    new Response(JSON.stringify(data, null, 2), {
      status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    })
  );
}

function html(body) {
  return withCors(
    new Response(body, {
      headers: { "Content-Type": "text/html; charset=utf-8" },
    })
  );
}

function withCors(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Headers", "Content-Type");
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  return new Response(response.body, { status: response.status, headers });
}
