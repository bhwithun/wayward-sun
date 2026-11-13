import { addDays, detroitParts, parseYmd, ymdKey, type Ymd } from "./time";

/** Average kW over a 30-minute bucket: Wh / 0.5 h / 1000 = Wh / 500. */
export const HOUSE_WH_PER_KW = 500;
export const HOUSE_BUCKET_MS = 30 * 60 * 1000;

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

/** Step polylines. Each run ends at its last real period end. A gap starts a new run. */
export function houseStepRuns(samples: HouseSample[]): HouseVertex[][] {
  const ordered = [...samples].sort((a, b) => a.t - b.t);
  const groups: HouseSample[][] = [];
  let current: HouseSample[] = [];
  for (const sample of ordered) {
    const previous = current[current.length - 1];
    if (previous != null && sample.t - previous.t !== HOUSE_BUCKET_MS) {
      groups.push(current);
      current = [];
    }
    current.push(sample);
  }
  if (current.length > 0) groups.push(current);
  return groups.map(stepVertices);
}

function stepVertices(samples: HouseSample[]): HouseVertex[] {
  const vertices: HouseVertex[] = [];
  samples.forEach((sample, index) => {
    if (index === 0) vertices.push({ t: sample.t - HOUSE_BUCKET_MS, kw: sample.kw });
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
  const samplesInRange = samples.filter((sample) => sample.t >= fromMs && sample.t < toMs);
  if (inRangeDays.length === 0 && samplesInRange.length === 0) return null;

  const coveredDates = new Set(inRangeDays.map((day) => day.day));
  const sampleKwh = samplesInRange.reduce((sum, sample) => {
    const date = ymdKey(detroitParts(new Date(sample.t)));
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
