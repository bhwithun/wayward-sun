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
      // Estimated actuals replace the forecast. Until they arrive, the frozen
      // forecast is the historical trace for this half-hour.
      const kw = point.actualKw ?? point.forecastKw;
      if (kw != null) out.push({ t, kw, kind: "live", hours });
    } else if (point.forecastKw != null) {
      out.push({ t, kw: point.forecastKw, kind: "forecast", hours });
    } else if (point.actualKw != null) {
      out.push({ t, kw: point.actualKw, kind: "live", hours });
    }
  }
  return out;
}

export type DayTotal = {
  label: string;
  kwh: number;
  liveKwh: number;
  forecastKwh: number;
};

export function dayTotals(points: SeriesPoint[]): DayTotal[] {
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
    current.kwh += energy;
    if (point.kind === "live") current.liveKwh += energy;
    else current.forecastKwh += energy;
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
