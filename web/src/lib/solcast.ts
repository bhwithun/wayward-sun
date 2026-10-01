import { demoSnapshot, type SnapshotBody } from "./demo";
import { loadState, persistState } from "./store";
import { utcDay } from "./time";
import { fetchSiteWeather } from "./weather";

export const DAILY_LIMIT = 10;
export const DAILY_AUTO_LIMIT = 5;
const REQUESTS_PER_REFRESH = 2;
const MIN_AUTO_AGE_MS = 4 * 60 * 60 * 1000;
const WEATHER_AGE_MS = 60 * 60 * 1000;
const SOLCAST = "https://api.solcast.com.au";

export function resourceId(): string {
  return process.env.RESOURCE_ID || "84d7-8b52-33f3-bd7b";
}

export function snapshotAgeMs(snapshot: SnapshotBody | null, now: Date): number {
  const fetchedAt = snapshot?.fetchedAt ? Date.parse(snapshot.fetchedAt) : 0;
  return fetchedAt ? now.getTime() - fetchedAt : Number.POSITIVE_INFINITY;
}

export function isFresh(snapshot: SnapshotBody | null, now: Date): boolean {
  return Boolean(snapshot?.fetchedAt) && snapshotAgeMs(snapshot, now) < MIN_AUTO_AGE_MS;
}

function weatherDue(snapshot: SnapshotBody | null, now: Date): boolean {
  const fetched = snapshot?.weatherFetchedAt ? Date.parse(snapshot.weatherFetchedAt) : 0;
  if (!fetched) return true;
  return now.getTime() - fetched >= WEATHER_AGE_MS;
}

/**
 * Shared by GET /cache, POST /refresh, and the daily cron.
 * Solcast runs only when the snapshot is older than 4 hours, at most 5 times per UTC day.
 */
export async function serveCache(): Promise<SnapshotBody> {
  const now = new Date();
  let state = await loadState();
  if (!isFresh(state, now)) {
    state = await refreshSolcast(state, now);
  }
  let weather = null;
  let notice: string | null = null;
  if (!state) {
    throw new Error("Solcast refresh returned no snapshot");
  }
  if (weatherDue(state, now)) {
    try {
      weather = await fetchSiteWeather();
      if (weather) {
        state = { ...state, weatherFetchedAt: now.toISOString() };
      } else {
        notice = "SITE_LAT and SITE_LNG are not set, so weather history was skipped.";
      }
    } catch (error) {
      notice = error instanceof Error ? error.message : String(error);
    }
  }
  await persistState(state, weather, now);
  if (notice) {
    state = { ...state, message: state.message ? `${state.message} ${notice}` : notice };
  }
  return state;
}

async function refreshSolcast(existing: SnapshotBody | null, now: Date): Promise<SnapshotBody> {
  const day = utcDay(now);
  const used = existing && existing.requestsDay === day ? existing.requestsUsed : 0;
  const autoUsed = existing && existing.requestsDay === day ? existing.autoFetchesUsed : 0;
  const weatherFetchedAt = existing?.weatherFetchedAt ?? null;

  if (existing && snapshotAgeMs(existing, now) < MIN_AUTO_AGE_MS) {
    return {
      ...existing,
      message: "Cache younger than 4 hours; skipped Solcast.",
    };
  }

  if (autoUsed >= DAILY_AUTO_LIMIT) {
    const base = existing ?? demoSnapshot(resourceId(), now);
    return {
      ...base,
      requestsDay: existing?.requestsDay ?? day,
      requestsUsed: existing ? existing.requestsUsed : 0,
      autoFetchesUsed: autoUsed,
      weatherFetchedAt,
      message: `Daily automatic pull limit reached (${DAILY_AUTO_LIMIT}).`,
    };
  }

  if (!process.env.SOLCAST_API_KEY) {
    return {
      ...demoSnapshot(resourceId(), now),
      requestsDay: day,
      requestsUsed: used,
      autoFetchesUsed: autoUsed + 1,
      weatherFetchedAt,
      message: "No SOLCAST_API_KEY — serving demo data.",
    };
  }

  if (used + REQUESTS_PER_REFRESH > DAILY_LIMIT) {
    const base = existing ?? demoSnapshot(resourceId(), now);
    return {
      ...base,
      weatherFetchedAt,
      message: `Daily Solcast quota reached (${used}/${DAILY_LIMIT}).`,
    };
  }

  const id = encodeURIComponent(resourceId());
  const forecasts = (await solcastGet(
    `${SOLCAST}/rooftop_sites/${id}/forecasts?format=json`,
    process.env.SOLCAST_API_KEY
  )) as SnapshotBody["forecasts"];
  const actuals = (await solcastGet(
    `${SOLCAST}/rooftop_sites/${id}/estimated_actuals?format=json`,
    process.env.SOLCAST_API_KEY
  )) as SnapshotBody["actuals"];

  return {
    resourceId: resourceId(),
    fetchedAt: now.toISOString(),
    source: "solcast",
    forecasts,
    actuals,
    requestsDay: day,
    requestsUsed: used + REQUESTS_PER_REFRESH,
    autoFetchesUsed: autoUsed + 1,
    message: "On-demand Solcast pull.",
    weatherFetchedAt,
  };
}

async function solcastGet(url: string, apiKey: string): Promise<unknown> {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${apiKey}`,
      Accept: "application/json",
      "User-Agent": "SolWidget-Web/1.0",
    },
  });
  const body = await response.text();
  if (!response.ok) {
    throw new Error(`Solcast HTTP ${response.status}: ${body.slice(0, 240)}`);
  }
  return JSON.parse(body) as SnapshotBody["forecasts"];
}
