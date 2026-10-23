import { detroitParts, ymdKey } from "./time";

export type HistoryLike = {
  periodEnd: string;
  actualKw: number | null;
  forecastKw: number | null;
  periodHours: number | null;
};

export type SeriesPoint = {
  t: number;
  kw: number;
  kind: "live" | "forecast";
  hours: number;
};

export function seriesFromHistory(points: HistoryLike[], nowMs: number): SeriesPoint[] {
  const out: SeriesPoint[] = [];
  for (const point of points) {
    const t = Date.parse(point.periodEnd);
    if (!Number.isFinite(t)) continue;
    const hours = point.periodHours ?? 0.5;
    if (t <= nowMs) {
      // Gold is every stored Solcast forecast, including half-hours Enphase
      // has not measured yet. Those still stand in on the green trace.
      if (point.actualKw != null) {
        out.push({ t, kw: point.actualKw, kind: "live", hours });
      } else if (point.forecastKw != null) {
        out.push({ t, kw: point.forecastKw, kind: "live", hours });
      }
      if (point.forecastKw != null) {
        out.push({ t, kw: point.forecastKw, kind: "forecast", hours });
      }
    } else if (point.forecastKw != null) {
      out.push({ t, kw: point.forecastKw, kind: "forecast", hours });
    } else if (point.actualKw != null) {
      out.push({ t, kw: point.actualKw, kind: "live", hours });
    }
  }
  return out;
}

/** Half-hours further apart than this are a break in the line, not a chord. */
export const FORECAST_GAP_MS = 45 * 60 * 1000;

/** Past gold stays at half opacity. The segment that crosses now stays solid. */
export function forecastStrokes(points: SeriesPoint[], nowMs: number): { past: SeriesPoint[]; future: SeriesPoint[] } {
  const forecast = points.filter((point) => point.kind === "forecast").sort((a, b) => a.t - b.t);
  const past = forecast.filter((point) => point.t <= nowMs);
  const ahead = forecast.filter((point) => point.t > nowMs);
  const seam = past.at(-1);
  const next = ahead[0];
  const future =
    seam != null && next != null && next.t - seam.t <= FORECAST_GAP_MS ? [seam, ...ahead] : ahead;
  return { past, future };
}

export type DayTotal = {
  label: string;
  kwh: number;
  liveKwh: number;
  forecastKwh: number;
};

export function dayTotals(points: SeriesPoint[], nowMs: number): DayTotal[] {
  const groups = new Map<string, DayTotal>();
  for (const point of points) {
    const ymd = detroitParts(new Date(point.t));
    const key = ymdKey(ymd);
    const energy = point.kw * point.hours;
    const current = groups.get(key) ?? {
      label: key.slice(5),
      kwh: 0,
      liveKwh: 0,
      forecastKwh: 0,
    };
    if (point.kind === "live") {
      current.liveKwh += energy;
      current.kwh += energy;
    } else {
      current.forecastKwh += energy;
      if (point.t > nowMs) current.kwh += energy;
    }
    groups.set(key, current);
  }
  return [...groups.values()];
}

export type SunSpan = { sunrise: number; sunset: number };

export function isNight(t: number, sun: SunSpan[]): boolean {
  if (sun.length === 0) return false;
  const ordered = [...sun].sort((a, b) => a.sunrise - b.sunrise);
  if (t < ordered[0].sunrise) return true;
  for (let i = 0; i < ordered.length; i++) {
    const day = ordered[i];
    if (t >= day.sunrise && t < day.sunset) return false;
    const next = ordered[i + 1]?.sunrise;
    if (t >= day.sunset && (next == null || t < next)) return true;
  }
  return true;
}
