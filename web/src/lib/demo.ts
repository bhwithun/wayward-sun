import { utcDay, zonedTimeToUtc, detroitParts, addDays, ymdKey } from "./time";

const PAST_DAYS = 2;
const FUTURE_DAYS = 2;

export type SolcastRow = {
  pv_estimate: number;
  period_end: string;
  period: string;
};

export type SnapshotBody = {
  resourceId: string;
  fetchedAt: string | null;
  source: string;
  forecasts: { forecasts: SolcastRow[] };
  actuals: { estimated_actuals: SolcastRow[] };
  requestsDay: string;
  requestsUsed: number;
  /** Forecast HTTP calls already made on requestsDay. */
  autoFetchesUsed: number;
  /** Estimated-actuals HTTP calls already made on requestsDay. */
  actualsFetchesUsed: number;
  /** Detroit slot id of the last successful forecast, `YYYY-MM-DDTHH:mm`. */
  forecastSlot: string | null;
  /** Detroit slot id of the last successful estimated-actuals pull. */
  actualsSlot: string | null;
  actualsFetchedAt: string | null;
  message: string | null;
  weatherFetchedAt: string | null;
};

const DEMO_SPAN = (PAST_DAYS + FUTURE_DAYS + 1) * 48;
const DEMO_EPS = 0.0001;

/** Sample-curve kW at `index` half-hours after a Detroit midnight. */
export function demoKilowatts(periodEnd: Date, index: number): number {
  const local = detroitParts(periodEnd);
  const hour = local.hour + local.minute / 60;
  const sun = Math.max(0, Math.sin(((hour - 6) / 12) * Math.PI));
  const clouds = 0.85 + 0.15 * Math.cos(index / 3);
  return Math.round(6.4 * sun * sun * clouds * 1000) / 1000;
}

/**
 * True when `kw` is the sample curve for this half-hour.
 * Night zeros match the curve too, so only positive kW counts.
 */
export function matchesDemoActual(periodEnd: string, kw: number): boolean {
  if (!(kw > 0)) return false;
  const t = Date.parse(periodEnd);
  if (!Number.isFinite(t)) return false;
  const period = new Date(t);
  const local = detroitParts(period);
  for (let back = 0; back <= FUTURE_DAYS + PAST_DAYS; back++) {
    const startYmd = addDays(local, -back);
    const start = zonedTimeToUtc(startYmd.year, startYmd.month, startYmd.day);
    const index = (t - start.getTime()) / (30 * 60 * 1000);
    const rounded = Math.round(index);
    if (rounded < 0 || rounded >= DEMO_SPAN) continue;
    if (Math.abs(index - rounded) > 1e-6) continue;
    if (Math.abs(demoKilowatts(period, rounded) - kw) < DEMO_EPS) return true;
  }
  return false;
}

export type ActualSample = { periodEnd: string; actualKw: number | null };

/**
 * Periods whose stored actual is still the sample curve.
 * A Detroit day is included only when every positive actual that day matches,
 * so a real Solcast day is left alone. Zeros on that day are included.
 */
export function demoActualPeriods(points: ActualSample[]): string[] {
  const byDay = new Map<string, ActualSample[]>();
  for (const point of points) {
    if (point.actualKw == null || !Number.isFinite(point.actualKw)) continue;
    const t = Date.parse(point.periodEnd);
    if (!Number.isFinite(t)) continue;
    const key = ymdKey(detroitParts(new Date(t)));
    const list = byDay.get(key) ?? [];
    list.push(point);
    byDay.set(key, list);
  }
  const out: string[] = [];
  for (const rows of byDay.values()) {
    const positives = rows.filter((row) => (row.actualKw as number) > 0);
    if (positives.length < 4) continue;
    if (!positives.every((row) => matchesDemoActual(row.periodEnd, row.actualKw as number))) continue;
    for (const row of rows) out.push(row.periodEnd);
  }
  return out;
}

export function demoSnapshot(resourceId: string, now = new Date()): SnapshotBody {
  const forecasts: SolcastRow[] = [];
  const actuals: SolcastRow[] = [];
  const today = detroitParts(now);
  const startYmd = addDays(today, -PAST_DAYS);
  const start = zonedTimeToUtc(startYmd.year, startYmd.month, startYmd.day);
  const intervals = (PAST_DAYS + FUTURE_DAYS + 1) * 48;

  for (let i = 0; i < intervals; i++) {
    const periodEnd = new Date(start.getTime() + i * 30 * 60 * 1000);
    const kw = demoKilowatts(periodEnd, i);
    const row: SolcastRow = {
      pv_estimate: kw,
      period_end: periodEnd.toISOString().replace(".000", ""),
      period: "PT30M",
    };
    if (periodEnd.getTime() > now.getTime()) forecasts.push(row);
    else actuals.push(row);
  }

  return {
    resourceId,
    fetchedAt: now.toISOString(),
    source: "demo",
    forecasts: { forecasts },
    actuals: { estimated_actuals: actuals },
    requestsDay: utcDay(now),
    requestsUsed: 0,
    autoFetchesUsed: 0,
    actualsFetchesUsed: 0,
    forecastSlot: null,
    actualsSlot: null,
    actualsFetchedAt: null,
    message: "Demo curve — no Solcast key on this deployment.",
    weatherFetchedAt: null,
  };
}

export type ChartPoint = { t: number; kw: number; kind: "live" | "forecast" };

export function combinedPoints(snapshot: {
  actuals?: { estimated_actuals?: SolcastRow[] };
  forecasts?: { forecasts?: SolcastRow[] };
} | null): ChartPoint[] {
  const live = asPoints(snapshot?.actuals?.estimated_actuals, "live");
  const forecast = asPoints(snapshot?.forecasts?.forecasts, "forecast");
  const latestLive = live.reduce((max, p) => (p.t > max ? p.t : max), 0);
  const future = latestLive ? forecast.filter((p) => p.t > latestLive) : forecast;
  return [...live, ...future].sort((a, b) => a.t - b.t);
}

function asPoints(rows: SolcastRow[] | undefined, kind: "live" | "forecast"): ChartPoint[] {
  if (!Array.isArray(rows)) return [];
  const out: ChartPoint[] = [];
  for (const row of rows) {
    const t = Date.parse(row.period_end);
    const kw = Number(row.pv_estimate);
    if (!Number.isFinite(t) || !Number.isFinite(kw)) continue;
    out.push({ t, kw, kind });
  }
  return out;
}
