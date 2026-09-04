/**
 * Same idea as ForecastRepository.demoPoints(): a sine-shaped rooftop curve
 * so the Worker can be opened with no Solcast key.
 */

const PAST_DAYS = 2;
const FUTURE_DAYS = 2;

export function utcDay(now = new Date()) {
  return now.toISOString().slice(0, 10);
}

export function demoSnapshot(resourceId, now = new Date()) {
  const forecasts = [];
  const actuals = [];
  const start = localDayStart(now, -PAST_DAYS);
  const intervals = (PAST_DAYS + FUTURE_DAYS + 1) * 48;

  for (let i = 0; i < intervals; i++) {
    const periodEnd = new Date(start.getTime() + i * 30 * 60 * 1000);
    const hour = periodEnd.getHours() + periodEnd.getMinutes() / 60;
    const sun = Math.max(0, Math.sin(((hour - 6) / 12) * Math.PI));
    const clouds = 0.85 + 0.15 * Math.cos(i / 3);
    const kw = Math.round(6.4 * sun * sun * clouds * 1000) / 1000;
    const row = {
      pv_estimate: kw,
      period_end: periodEnd.toISOString().replace(".000", ""),
      period: "PT30M",
    };
    if (periodEnd.getTime() > now.getTime()) {
      forecasts.push(row);
    } else {
      actuals.push(row);
    }
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
    message: "Demo curve — no Solcast key in this local Worker.",
  };
}

export function combinedPoints(snapshot) {
  const live = asPoints(snapshot?.actuals?.estimated_actuals, "live");
  const forecast = asPoints(snapshot?.forecasts?.forecasts, "forecast");
  const latestLive = live.reduce((max, p) => (p.t > max ? p.t : max), 0);
  const future = latestLive
    ? forecast.filter((p) => p.t > latestLive)
    : forecast;
  return [...live, ...future].sort((a, b) => a.t - b.t);
}

function asPoints(rows, kind) {
  if (!Array.isArray(rows)) return [];
  const out = [];
  for (const row of rows) {
    const t = Date.parse(row.period_end);
    const kw = Number(row.pv_estimate);
    if (!Number.isFinite(t) || !Number.isFinite(kw)) continue;
    out.push({ t, kw, kind });
  }
  return out;
}

function localDayStart(now, offsetDays) {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + offsetDays);
  return d;
}
