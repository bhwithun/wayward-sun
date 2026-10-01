import { combinedPoints, type SnapshotBody } from "./demo";
import { draftsFromSnapshot, type WeatherHour } from "./drafts";
import { sql } from "./db";
import type { IntervalDraft } from "./merge";
import type { SunDay } from "./weather";

const DAILY_LIMIT = 10;

type StateRow = {
  resource_id: string;
  fetched_at: string | null;
  source: string | null;
  requests_day: string | null;
  requests_used: number;
  auto_fetches_used: number;
  message: string | null;
  forecasts: SnapshotBody["forecasts"] | null;
  actuals: SnapshotBody["actuals"] | null;
  weather_fetched_at: string | null;
};

/** Postgres `date` values may arrive as a Date. `String(date).slice(0, 10)` is "Thu Oct 01". */
export function isoDate(value: unknown): string {
  if (value == null || value === "") return "";
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? "" : value.toISOString().slice(0, 10);
  }
  const text = String(value);
  const prefix = /^(\d{4}-\d{2}-\d{2})/.exec(text);
  if (prefix) return prefix[1];
  const parsed = new Date(text);
  return Number.isNaN(parsed.getTime()) ? "" : parsed.toISOString().slice(0, 10);
}

export async function loadState(): Promise<SnapshotBody | null> {
  const rows = await sql()`
    SELECT resource_id, fetched_at, source, requests_day, requests_used,
           auto_fetches_used, message, forecasts, actuals, weather_fetched_at
    FROM fetch_state
    WHERE id = 1
  `;
  const row = rows[0] as StateRow | undefined;
  if (!row) return null;
  return {
    resourceId: row.resource_id,
    fetchedAt: row.fetched_at ? new Date(row.fetched_at).toISOString() : null,
    source: row.source || "empty",
    forecasts: asJson<SnapshotBody["forecasts"]>(row.forecasts) ?? { forecasts: [] },
    actuals: asJson<SnapshotBody["actuals"]>(row.actuals) ?? { estimated_actuals: [] },
    requestsDay: isoDate(row.requests_day),
    requestsUsed: row.requests_used ?? 0,
    autoFetchesUsed: row.auto_fetches_used ?? 0,
    message: row.message,
    weatherFetchedAt: row.weather_fetched_at
      ? new Date(row.weather_fetched_at).toISOString()
      : null,
  };
}

export async function persistState(
  state: SnapshotBody,
  weather: { hours: WeatherHour[]; sun: SunDay[] } | null,
  now: Date
): Promise<void> {
  const drafts = draftsFromSnapshot(
    state.actuals.estimated_actuals,
    state.forecasts.forecasts,
    weather?.hours ?? null
  );
  const db = sql();
  await db`
    INSERT INTO fetch_state (
      id, resource_id, fetched_at, source, requests_day, requests_used,
      auto_fetches_used, message, forecasts, actuals, weather_fetched_at
    ) VALUES (
      1,
      ${state.resourceId},
      ${state.fetchedAt},
      ${state.source},
      ${state.requestsDay || null},
      ${state.requestsUsed},
      ${state.autoFetchesUsed},
      ${state.message},
      ${JSON.stringify(state.forecasts)}::jsonb,
      ${JSON.stringify(state.actuals)}::jsonb,
      ${state.weatherFetchedAt}
    )
    ON CONFLICT (id) DO UPDATE SET
      resource_id = EXCLUDED.resource_id,
      fetched_at = EXCLUDED.fetched_at,
      source = EXCLUDED.source,
      requests_day = EXCLUDED.requests_day,
      requests_used = EXCLUDED.requests_used,
      auto_fetches_used = EXCLUDED.auto_fetches_used,
      message = EXCLUDED.message,
      forecasts = EXCLUDED.forecasts,
      actuals = EXCLUDED.actuals,
      weather_fetched_at = EXCLUDED.weather_fetched_at
  `;
  if (drafts.length > 0) await upsertIntervals(drafts, now);
  if (weather && weather.sun.length > 0) await upsertSun(weather.sun);
}

async function upsertIntervals(drafts: IntervalDraft[], now: Date): Promise<void> {
  const periodEnd = drafts.map((d) => d.periodEnd);
  const actualKw = drafts.map((d) => d.actualKw);
  const forecastKw = drafts.map((d) => d.forecastKw);
  const periodHours = drafts.map((d) => d.periodHours);
  const tempF = drafts.map((d) => d.tempF);
  const precipPct = drafts.map((d) => d.precipPct);
  const rateBand = drafts.map((d) => d.rateBand);
  const importCents = drafts.map((d) => d.importCents);
  const outflowCents = drafts.map((d) => d.outflowCents);
  const nowIso = now.toISOString();
  await sql()`
    INSERT INTO intervals (
      period_end, actual_kw, forecast_kw, period_hours, temp_f, precip_pct,
      rate_band, import_cents, outflow_cents, updated_at
    )
    SELECT
      t.period_end, t.actual_kw, t.forecast_kw, t.period_hours, t.temp_f, t.precip_pct,
      t.rate_band, t.import_cents, t.outflow_cents, ${nowIso}::timestamptz
    FROM unnest(
      ${periodEnd}::timestamptz[],
      ${actualKw}::float8[],
      ${forecastKw}::float8[],
      ${periodHours}::float8[],
      ${tempF}::float8[],
      ${precipPct}::float8[],
      ${rateBand}::text[],
      ${importCents}::float8[],
      ${outflowCents}::float8[]
    ) AS t(
      period_end, actual_kw, forecast_kw, period_hours, temp_f, precip_pct,
      rate_band, import_cents, outflow_cents
    )
    ON CONFLICT (period_end) DO UPDATE SET
      actual_kw = COALESCE(EXCLUDED.actual_kw, intervals.actual_kw),
      forecast_kw = CASE
        WHEN EXCLUDED.forecast_kw IS NULL THEN intervals.forecast_kw
        WHEN intervals.period_end > ${nowIso}::timestamptz THEN EXCLUDED.forecast_kw
        ELSE COALESCE(intervals.forecast_kw, EXCLUDED.forecast_kw)
      END,
      period_hours = COALESCE(EXCLUDED.period_hours, intervals.period_hours),
      temp_f = COALESCE(EXCLUDED.temp_f, intervals.temp_f),
      precip_pct = COALESCE(EXCLUDED.precip_pct, intervals.precip_pct),
      rate_band = COALESCE(intervals.rate_band, EXCLUDED.rate_band),
      import_cents = COALESCE(intervals.import_cents, EXCLUDED.import_cents),
      outflow_cents = COALESCE(intervals.outflow_cents, EXCLUDED.outflow_cents),
      updated_at = now()
  `;
}

async function upsertSun(days: SunDay[]): Promise<void> {
  const day = days.map((d) => d.day);
  const sunrise = days.map((d) => d.sunrise);
  const sunset = days.map((d) => d.sunset);
  await sql()`
    INSERT INTO sun_days (day, sunrise, sunset)
    SELECT * FROM unnest(
      ${day}::date[],
      ${sunrise}::timestamptz[],
      ${sunset}::timestamptz[]
    ) AS t(day, sunrise, sunset)
    ON CONFLICT (day) DO UPDATE SET
      sunrise = EXCLUDED.sunrise,
      sunset = EXCLUDED.sunset
  `;
}

export function publicCache(snapshot: SnapshotBody) {
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
    message: snapshot.message,
  };
}

export type HistoryPoint = {
  periodEnd: string;
  actualKw: number | null;
  forecastKw: number | null;
  periodHours: number | null;
  tempF: number | null;
  precipPct: number | null;
  rateBand: string | null;
  importCents: number | null;
  outflowCents: number | null;
};

export async function loadHistory(fromIso: string, toIso: string) {
  const points = await sql()`
    SELECT period_end, actual_kw, forecast_kw, period_hours, temp_f, precip_pct,
           rate_band, import_cents, outflow_cents
    FROM intervals
    WHERE period_end >= ${fromIso}::timestamptz
      AND period_end < ${toIso}::timestamptz
    ORDER BY period_end
  `;
  const sun = await sql()`
    SELECT day, sunrise, sunset
    FROM sun_days
    WHERE sunrise < ${toIso}::timestamptz
      AND sunset >= ${fromIso}::timestamptz
    ORDER BY day
  `;
  return {
    points: (points as Array<Record<string, unknown>>).map(mapPoint),
    sun: (sun as Array<Record<string, unknown>>).map((row) => ({
      date: isoDate(row.day),
      sunrise: new Date(String(row.sunrise)).toISOString(),
      sunset: new Date(String(row.sunset)).toISOString(),
    })),
  };
}

function mapPoint(row: Record<string, unknown>): HistoryPoint {
  return {
    periodEnd: new Date(String(row.period_end)).toISOString(),
    actualKw: num(row.actual_kw),
    forecastKw: num(row.forecast_kw),
    periodHours: num(row.period_hours),
    tempF: num(row.temp_f),
    precipPct: num(row.precip_pct),
    rateBand: row.rate_band == null ? null : String(row.rate_band),
    importCents: num(row.import_cents),
    outflowCents: num(row.outflow_cents),
  };
}

function asJson<T>(value: unknown): T | null {
  if (value == null) return null;
  if (typeof value === "string") return JSON.parse(value) as T;
  return value as T;
}

function num(value: unknown): number | null {
  if (value == null) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}
