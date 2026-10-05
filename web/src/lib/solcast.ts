import { demoSnapshot, type SnapshotBody } from "./demo";
import { loadState, persistState, scrubDemoActuals } from "./store";
import { detroitParts, utcDay, ymdKey } from "./time";
import { fetchSiteWeather } from "./weather";

export const DAILY_LIMIT = 10;
export const DAILY_FORECAST_LIMIT = 8;
export const DAILY_ACTUALS_LIMIT = 2;
export const FORECAST_HOURS = 336;
export const ACTUALS_HOURS = 168;

/** A cron that lands this many minutes after a slot still counts as that slot. */
export const SLOT_GRACE_MINUTES = 10;

const SOLCAST = "https://api.solcast.com.au";
const WEATHER_AGE_MS = 60 * 60 * 1000;

export type ForecastSlot = {
  hour: number;
  minute: number;
  actuals: boolean;
};

/** Awake-hour forecast times in America/Detroit. Actuals ride on the first and the 6:30pm run. */
export const FORECAST_SLOTS: ForecastSlot[] = [
  { hour: 6, minute: 0, actuals: true },
  { hour: 8, minute: 30, actuals: false },
  { hour: 11, minute: 0, actuals: false },
  { hour: 13, minute: 30, actuals: false },
  { hour: 16, minute: 0, actuals: false },
  { hour: 18, minute: 30, actuals: true },
  { hour: 21, minute: 0, actuals: false },
  { hour: 23, minute: 30, actuals: false },
];

export type PullPlan = {
  slotId: string | null;
  forecast: boolean;
  actuals: boolean;
  message: string | null;
};

export function resourceId(): string {
  return process.env.RESOURCE_ID || "84d7-8b52-33f3-bd7b";
}

export function detroitSlot(now: Date): { id: string; actuals: boolean } | null {
  const parts = detroitParts(now);
  const nowMinutes = parts.hour * 60 + parts.minute;
  for (const slot of FORECAST_SLOTS) {
    const delta = nowMinutes - (slot.hour * 60 + slot.minute);
    if (delta >= 0 && delta <= SLOT_GRACE_MINUTES) {
      const hh = String(slot.hour).padStart(2, "0");
      const mm = String(slot.minute).padStart(2, "0");
      return { id: `${ymdKey(parts)}T${hh}:${mm}`, actuals: slot.actuals };
    }
  }
  return null;
}

function dayCounters(existing: SnapshotBody | null, now: Date) {
  const day = utcDay(now);
  const same = existing?.requestsDay === day;
  return {
    day,
    requestsUsed: same ? existing?.requestsUsed ?? 0 : 0,
    forecastsUsed: same ? existing?.autoFetchesUsed ?? 0 : 0,
    actualsUsed: same ? existing?.actualsFetchesUsed ?? 0 : 0,
  };
}

/**
 * Which Solcast calls a cron hit should make. Cache reads do not use this.
 * Forecasts only on an awake-hour slot that has not succeeded yet today.
 * Estimated actuals only on the 6:00am and 6:30pm slots.
 */
export function planSolcastCalls(
  existing: SnapshotBody | null,
  now: Date,
  apiKey: string | undefined = process.env.SOLCAST_API_KEY
): PullPlan {
  const slot = detroitSlot(now);
  if (!slot) return { slotId: null, forecast: false, actuals: false, message: null };
  if (!apiKey) {
    return {
      slotId: slot.id,
      forecast: false,
      actuals: false,
      message: "No SOLCAST_API_KEY — serving stored data.",
    };
  }

  const counters = dayCounters(existing, now);
  let requests = counters.requestsUsed;
  if (requests >= DAILY_LIMIT) {
    return {
      slotId: slot.id,
      forecast: false,
      actuals: false,
      message: `Daily Solcast quota reached (${requests}/${DAILY_LIMIT}).`,
    };
  }

  const forecast =
    existing?.forecastSlot !== slot.id &&
    counters.forecastsUsed < DAILY_FORECAST_LIMIT &&
    requests < DAILY_LIMIT;
  if (forecast) requests += 1;

  const actuals =
    slot.actuals &&
    existing?.actualsSlot !== slot.id &&
    counters.actualsUsed < DAILY_ACTUALS_LIMIT &&
    requests < DAILY_LIMIT;

  let message: string | null = null;
  if (!forecast && existing?.forecastSlot !== slot.id && counters.forecastsUsed >= DAILY_FORECAST_LIMIT) {
    message = `Daily forecast limit reached (${DAILY_FORECAST_LIMIT}).`;
  }
  return { slotId: slot.id, forecast, actuals, message };
}

/** Stored snapshot for phones and the dashboard. Does not call Solcast. */
export async function serveCache(): Promise<SnapshotBody> {
  const now = new Date();
  const loaded = await loadState();
  if (!loaded) return demoSnapshot(resourceId(), now);
  const state = (await scrubDemoActuals(loaded)) ?? loaded;
  return attachWeather(state, now);
}

/** Scheduled entry. Solcast runs only when `planSolcastCalls` says so. */
export async function serveCron(): Promise<SnapshotBody> {
  const now = new Date();
  let state = await scrubDemoActuals(await loadState());
  const plan = planSolcastCalls(state, now);
  if (plan.forecast || plan.actuals) {
    state = await pullSolcast(state, plan, now);
  } else if (plan.message && state) {
    state = { ...state, message: plan.message };
    await persistState(state, null, now);
  }
  if (!state) return demoSnapshot(resourceId(), now);
  return attachWeather(state, now);
}

async function pullSolcast(
  existing: SnapshotBody | null,
  plan: PullPlan,
  now: Date
): Promise<SnapshotBody> {
  const apiKey = process.env.SOLCAST_API_KEY;
  if (!apiKey || !plan.slotId) throw new Error("Solcast pull missing key or slot");
  const counters = dayCounters(existing, now);
  const base = existing ?? emptySnapshot(now);
  let state: SnapshotBody = {
    ...base,
    requestsDay: counters.day,
    requestsUsed: counters.requestsUsed,
    autoFetchesUsed: counters.forecastsUsed,
    actualsFetchesUsed: counters.actualsUsed,
  };
  const id = encodeURIComponent(resourceId());

  if (plan.forecast) {
    const forecasts = (await solcastGet(
      `${SOLCAST}/rooftop_sites/${id}/forecasts?format=json&hours=${FORECAST_HOURS}`,
      apiKey
    )) as SnapshotBody["forecasts"];
    state = {
      ...state,
      resourceId: resourceId(),
      fetchedAt: now.toISOString(),
      source: "solcast",
      forecasts,
      requestsUsed: state.requestsUsed + 1,
      autoFetchesUsed: state.autoFetchesUsed + 1,
      forecastSlot: plan.slotId,
      message: null,
    };
    await persistState(state, null, now);
  }

  if (plan.actuals) {
    try {
      const actuals = (await solcastGet(
        `${SOLCAST}/rooftop_sites/${id}/estimated_actuals?format=json&hours=${ACTUALS_HOURS}`,
        apiKey
      )) as SnapshotBody["actuals"];
      state = {
        ...state,
        resourceId: resourceId(),
        source: "solcast",
        actuals,
        requestsUsed: state.requestsUsed + 1,
        actualsFetchesUsed: state.actualsFetchesUsed + 1,
        actualsSlot: plan.slotId,
        actualsFetchedAt: now.toISOString(),
        message: null,
      };
      await persistState(state, null, now);
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      state = {
        ...state,
        message: `Actuals failed: ${reason}`,
      };
      await persistState(state, null, now);
      throw error;
    }
  }

  return state;
}

function emptySnapshot(now: Date): SnapshotBody {
  return {
    ...demoSnapshot(resourceId(), now),
    source: "empty",
    fetchedAt: null,
    forecasts: { forecasts: [] },
    actuals: { estimated_actuals: [] },
    message: null,
  };
}

async function attachWeather(state: SnapshotBody, now: Date): Promise<SnapshotBody> {
  let weather: Awaited<ReturnType<typeof fetchSiteWeather>> = null;
  let notice: string | null = null;
  if (weatherDue(state, now)) {
    try {
      weather = await fetchSiteWeather();
      if (weather) {
        state = { ...state, weatherFetchedAt: now.toISOString() };
      } else {
        notice = "SITE_LAT and SITE_LNG are not set, so weather history was skipped.";
      }
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      notice = `Weather history skipped: ${reason}`;
    }
  }
  if (weather) await persistState(state, weather, now);
  if (notice) state = { ...state, message: notice };
  return state;
}

function weatherDue(snapshot: SnapshotBody | null, now: Date): boolean {
  const fetched = snapshot?.weatherFetchedAt ? Date.parse(snapshot.weatherFetchedAt) : 0;
  if (!fetched) return true;
  return now.getTime() - fetched >= WEATHER_AGE_MS;
}

async function solcastGet(url: string, apiKey: string): Promise<unknown> {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${apiKey}`,
      Accept: "application/json",
      "User-Agent": "WaywardSun/2.0",
    },
  });
  const body = await response.text();
  if (!response.ok) {
    throw new Error(`Solcast HTTP ${response.status}: ${body.slice(0, 240)}`);
  }
  return JSON.parse(body) as unknown;
}
