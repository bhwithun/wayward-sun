import { addDays, detroitParts, parseYmd, ymdKey, zonedTimeToUtc, type Ymd } from "./time";

/** Average kW over a 30-minute bucket: Wh / 0.5 h / 1000 = Wh / 500. */
export const HOUSE_WH_PER_KW = 500;
export const HOUSE_BUCKET_MS = 30 * 60 * 1000;
export const HOUSE_HOUR_MS = 60 * 60 * 1000;
/** Fixed solar axis. A higher bucket is drawn on this edge and marked as clipped. */
export const HOUSE_SCALE_MAX_KW = 8;

export type HouseSample = { t: number; kw: number };

export type HouseVertex = { t: number; kw: number };

export type HouseDay = {
  day: string;
  homeKwh: number;
  fromSolarKwh: number;
  hasHalfHour: boolean;
};

export type HouseSummary = {
  homeKwh: number;
  /** Null when solar is hidden or any started day in the range lacks half-hour data. */
  coveredPercent: number | null;
  hourlyDetailUnavailable: boolean;
};

export function homeKwFromWh(homeWh: number): number {
  return homeWh / HOUSE_WH_PER_KW;
}

export function kwhFromWh(wh: number): number {
  return wh / 1000;
}

export function detroitDayBounds(fromIso: string, toIso: string): { fromDay: string; toDay: string } {
  const from = detroitParts(new Date(fromIso));
  const inclusiveEnd = new Date(new Date(toIso).getTime() - 1);
  const to = detroitParts(inclusiveEnd);
  return { fromDay: ymdKey(from), toDay: ymdKey(to) };
}

/** Half-hour steps on a single day. Longer windows use hourly averages. */
export function houseDetailIsHalfHour(fromMs: number, toMs: number): boolean {
  const span = toMs - fromMs;
  return span > 0 && span <= 26 * 60 * 60 * 1000;
}

/** Step polylines. Each run ends at its last real period end. A gap starts a new run. */
export function houseStepRuns(samples: HouseSample[], bucketMs = HOUSE_BUCKET_MS): HouseVertex[][] {
  const ordered = [...samples].sort((a, b) => a.t - b.t);
  const groups: HouseSample[][] = [];
  let current: HouseSample[] = [];
  for (const sample of ordered) {
    const previous = current[current.length - 1];
    if (previous != null && sample.t - previous.t !== bucketMs) {
      groups.push(current);
      current = [];
    }
    current.push(sample);
  }
  if (current.length > 0) groups.push(current);
  return groups.map((group) => stepVertices(group, bucketMs));
}

/**
 * One point per Detroit clock hour: the mean kW of the half-hours whose midpoint falls in that hour.
 * The point's time is the end of the hour. Hours with no samples are left out.
 */
export function houseHourlySamples(samples: HouseSample[]): HouseSample[] {
  const groups = new Map<string, { end: number; rows: HouseSample[] }>();
  for (const sample of samples) {
    const mid = detroitParts(new Date(sample.t - 15 * 60 * 1000));
    const key = `${ymdKey(mid)}T${String(mid.hour).padStart(2, "0")}`;
    let endHour = mid.hour + 1;
    let endDay: Ymd = mid;
    if (endHour === 24) {
      endHour = 0;
      endDay = addDays(mid, 1);
    }
    const end = zonedTimeToUtc(endDay.year, endDay.month, endDay.day, endHour).getTime();
    const current = groups.get(key);
    if (current) current.rows.push(sample);
    else groups.set(key, { end, rows: [sample] });
  }
  return [...groups.values()]
    .sort((a, b) => a.end - b.end)
    .map((group) => ({
      t: group.end,
      kw: group.rows.reduce((sum, row) => sum + row.kw, 0) / group.rows.length,
    }));
}

/** `period_end` is the bucket end, so a day owns `(midnight, next midnight]`. */
export function housePeriodInRange(t: number, fromMs: number, toMs: number): boolean {
  return t > fromMs && t <= toMs;
}

/** The half-hour ending at midnight belongs to the day that just finished. */
export function houseReadingDay(t: number): string {
  return ymdKey(detroitParts(new Date(t - 1)));
}

export function housePlotSamples(samples: HouseSample[], fromMs: number, toMs: number): HouseSample[] {
  const visible = samples.filter((sample) => housePeriodInRange(sample.t, fromMs, toMs));
  return houseDetailIsHalfHour(fromMs, toMs) ? visible : houseHourlySamples(visible);
}

/** Max half-hour kW in `(from, to]`, before any hourly average. */
export function houseHalfHourPeak(samples: HouseSample[], fromMs: number, toMs: number): number | null {
  return housePeakKw(samples.filter((sample) => housePeriodInRange(sample.t, fromMs, toMs)));
}

export function houseClippedSamples(samples: HouseSample[], limit = HOUSE_SCALE_MAX_KW): HouseSample[] {
  return samples.filter((sample) => sample.kw > limit);
}

export function housePeakKw(samples: HouseSample[]): number | null {
  if (samples.length === 0) return null;
  return Math.max(...samples.map((sample) => sample.kw));
}

export function houseLegendLabel(peakKw: number | null): string {
  if (peakKw == null || !Number.isFinite(peakKw)) return "House use";
  return `House use · Peak ${peakKw.toFixed(1)} kW (half-hour)`;
}

/** Each value covers `[t - bucket, t)`. The vertex at `t` is the right edge. */
function stepVertices(samples: HouseSample[], bucketMs: number): HouseVertex[] {
  const vertices: HouseVertex[] = [];
  samples.forEach((sample, index) => {
    if (index === 0) vertices.push({ t: sample.t - bucketMs, kw: sample.kw });
    else vertices.push({ t: samples[index - 1].t, kw: sample.kw });
    vertices.push({ t: sample.t, kw: sample.kw });
  });
  return vertices;
}

export function detroitDatesInRange(fromMs: number, toMs: number): string[] {
  if (!(toMs > fromMs)) return [];
  const first = ymdKey(detroitParts(new Date(fromMs)));
  const last = ymdKey(detroitParts(new Date(toMs - 1)));
  const dates: string[] = [];
  let cursor: Ymd | null = parseYmd(first);
  while (cursor != null && ymdKey(cursor) <= last && dates.length < 400) {
    dates.push(ymdKey(cursor));
    cursor = addDays(cursor, 1);
  }
  return dates;
}

export function houseSummary(
  samples: HouseSample[],
  days: HouseDay[],
  fromMs: number,
  toMs: number,
  nowMs: number,
  solarVisible: boolean
): HouseSummary | null {
  const dates = detroitDatesInRange(fromMs, toMs);
  const byDay = new Map(days.map((day) => [day.day, day]));
  const inRangeDays = dates.flatMap((date) => {
    const day = byDay.get(date);
    return day ? [day] : [];
  });
  const samplesInRange = samples.filter((sample) => housePeriodInRange(sample.t, fromMs, toMs));
  if (inRangeDays.length === 0 && samplesInRange.length === 0) return null;

  const coveredDates = new Set(inRangeDays.map((day) => day.day));
  const sampleKwh = samplesInRange.reduce((sum, sample) => {
    const date = houseReadingDay(sample.t);
    return coveredDates.has(date) ? sum : sum + sample.kw * 0.5;
  }, 0);
  const homeKwh = inRangeDays.reduce((sum, day) => sum + day.homeKwh, 0) + sampleKwh;

  const today = ymdKey(detroitParts(new Date(nowMs)));
  const started = dates.filter((date) => date <= today);
  let coveredPercent: number | null = null;
  if (solarVisible && started.length > 0 && started.every((date) => byDay.get(date)?.hasHalfHour === true)) {
    const home = started.reduce((sum, date) => sum + (byDay.get(date)?.homeKwh ?? 0), 0);
    const solar = started.reduce((sum, date) => sum + (byDay.get(date)?.fromSolarKwh ?? 0), 0);
    if (home > 0 && Number.isFinite(home) && Number.isFinite(solar)) {
      coveredPercent = Math.round((100 * solar) / home);
    }
  }

  return {
    homeKwh,
    coveredPercent,
    hourlyDetailUnavailable: inRangeDays.some((day) => !day.hasHalfHour),
  };
}

export function houseSummaryLabel(summary: HouseSummary): string {
  const total = `House ${summary.homeKwh.toFixed(1)} kWh`;
  return summary.coveredPercent == null ? total : `${total} · Solar covered ${summary.coveredPercent}%`;
}

export function houseChipVisible(
  samples: HouseSample[],
  days: HouseDay[],
  fromMs: number,
  toMs: number
): boolean {
  return houseSummary(samples, days, fromMs, toMs, toMs, false) != null;
}

/** Days whose Detroit date overlaps [fromMs, toMs). */
export function houseDaysOverlapping(days: HouseDay[], fromMs: number, toMs: number): HouseDay[] {
  const dates = new Set(detroitDatesInRange(fromMs, toMs));
  return days.filter((day) => dates.has(day.day));
}

/** True when `house_energy` or `house_energy_day` has not been created yet. */
export function isMissingHouseTable(error: unknown): boolean {
  if (!error || typeof error !== "object") return false;
  const code = "code" in error ? String((error as { code?: unknown }).code) : "";
  if (code === "42P01") return true;
  const message = error instanceof Error ? error.message : String(error);
  return /house_energy/i.test(message) && /does not exist/i.test(message);
}
