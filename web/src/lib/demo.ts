import { utcDay, zonedTimeToUtc, detroitParts, addDays } from "./time";

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
  autoFetchesUsed: number;
  message: string | null;
  weatherFetchedAt: string | null;
};

export function demoSnapshot(resourceId: string, now = new Date()): SnapshotBody {
  const forecasts: SolcastRow[] = [];
  const actuals: SolcastRow[] = [];
  const today = detroitParts(now);
  const startYmd = addDays(today, -PAST_DAYS);
  const start = zonedTimeToUtc(startYmd.year, startYmd.month, startYmd.day);
  const intervals = (PAST_DAYS + FUTURE_DAYS + 1) * 48;

  for (let i = 0; i < intervals; i++) {
    const periodEnd = new Date(start.getTime() + i * 30 * 60 * 1000);
    const local = detroitParts(periodEnd);
    const hour = local.hour + local.minute / 60;
    const sun = Math.max(0, Math.sin(((hour - 6) / 12) * Math.PI));
    const clouds = 0.85 + 0.15 * Math.cos(i / 3);
    const kw = Math.round(6.4 * sun * sun * clouds * 1000) / 1000;
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
