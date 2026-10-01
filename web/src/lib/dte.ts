/**
 * Port of app DteTou.kt. Keep the cent constants in sync with that file.
 * Hours are year-round in America/Detroit. Do not scrape DTE.
 */
import { addDays, detroitParts, ymdKey, zonedTimeToUtc, type Ymd } from "./time";

export const PLAN_CODE = "D1.8";
export const PLAN_NAME = "Residential Dynamic Peak Pricing";
export const RIDER = "R18 Cat1";
export const RATES_AS_OF = "2026-07-06";

export const BASE_OFF_PEAK_CENTS = 14.31;
export const BASE_MID_PEAK_CENTS = 18.61;
export const BASE_PEAK_CENTS = 26.92;

export const OFF_PEAK_CENTS = 17.53;
export const MID_PEAK_CENTS = 21.83;
export const PEAK_CENTS = 30.15;

export const PSCR_CENTS = 1.877;
export const OUTFLOW_OFF_PEAK_CENTS = 4.583;
export const OUTFLOW_MID_PEAK_CENTS = 8.884;
export const OUTFLOW_PEAK_CENTS = 17.198;

export type RateBand = "OFF_PEAK" | "MID_PEAK" | "PEAK";

export type RateStamp = {
  rateBand: RateBand;
  importCents: number;
  outflowCents: number;
};

const WEEKEND = new Set(["Sat", "Sun"]);

export function periodAt(instant: Date): RateBand {
  const local = detroitParts(instant);
  if (isOffPeakDay(local, local.weekday)) return "OFF_PEAK";
  const minutes = local.hour * 60 + local.minute;
  if (minutes < 7 * 60) return "OFF_PEAK";
  if (minutes < 15 * 60) return "MID_PEAK";
  if (minutes < 19 * 60) return "PEAK";
  if (minutes < 23 * 60) return "MID_PEAK";
  return "OFF_PEAK";
}

export function stampAt(instant: Date): RateStamp {
  const rateBand = periodAt(instant);
  if (rateBand === "MID_PEAK") {
    return {
      rateBand,
      importCents: MID_PEAK_CENTS,
      outflowCents: OUTFLOW_MID_PEAK_CENTS + PSCR_CENTS,
    };
  }
  if (rateBand === "PEAK") {
    return {
      rateBand,
      importCents: PEAK_CENTS,
      outflowCents: OUTFLOW_PEAK_CENTS + PSCR_CENTS,
    };
  }
  return {
    rateBand,
    importCents: OFF_PEAK_CENTS,
    outflowCents: OUTFLOW_OFF_PEAK_CENTS + PSCR_CENTS,
  };
}

export type RateSample = {
  periodEnd: string;
  periodHours: number | null;
  importCents: number | null;
  outflowCents: number | null;
};

export type RateVertex = { t: number; cents: number };

/**
 * Buy and sell step outlines across [from, to]. The chart fills each down to 0¢.
 * A stored half-hour overrides the schedule for the cents it has.
 * Each segment is two vertices at the same cents, so the top edge stays horizontal
 * and the next segment's first vertex makes the vertical join.
 */
export function rateSteps(
  from: Date,
  to: Date,
  samples: RateSample[]
): { buy: RateVertex[]; sell: RateVertex[] } {
  const fromMs = from.getTime();
  const toMs = to.getTime();
  if (fromMs >= toMs) return { buy: [], sell: [] };

  const cuts = new Set<number>([fromMs, toMs]);
  for (const band of bands(from, to)) {
    cuts.add(band.start.getTime());
    cuts.add(band.end.getTime());
  }
  for (const sample of samples) {
    const end = Date.parse(sample.periodEnd);
    if (!Number.isFinite(end)) continue;
    const start = end - (sample.periodHours ?? 0.5) * 3_600_000;
    if (end <= fromMs || start >= toMs) continue;
    cuts.add(Math.max(start, fromMs));
    cuts.add(Math.min(end, toMs));
  }

  const times = [...cuts].filter((t) => t >= fromMs && t <= toMs).sort((a, b) => a - b);
  const slices: Array<{ start: number; end: number; buy: number; sell: number }> = [];
  for (let i = 0; i < times.length - 1; i++) {
    const start = times[i];
    const end = times[i + 1];
    if (end <= start) continue;
    const mid = (start + end) / 2;
    const sample = sampleAt(samples, mid);
    const stamp = stampAt(new Date(mid));
    const buy = sample?.importCents ?? stamp.importCents;
    const sell = sample?.outflowCents ?? stamp.outflowCents;
    const last = slices[slices.length - 1];
    if (last && last.end === start && last.buy === buy && last.sell === sell) last.end = end;
    else slices.push({ start, end, buy, sell });
  }

  return {
    buy: slices.flatMap((slice) => [
      { t: slice.start, cents: slice.buy },
      { t: slice.end, cents: slice.buy },
    ]),
    sell: slices.flatMap((slice) => [
      { t: slice.start, cents: slice.sell },
      { t: slice.end, cents: slice.sell },
    ]),
  };
}

function sampleAt(samples: RateSample[], mid: number): RateSample | undefined {
  let best: RateSample | undefined;
  let bestEnd = -Infinity;
  for (const sample of samples) {
    if (sample.importCents == null && sample.outflowCents == null) continue;
    const end = Date.parse(sample.periodEnd);
    if (!Number.isFinite(end)) continue;
    const start = end - (sample.periodHours ?? 0.5) * 3_600_000;
    if (mid < start || mid >= end || end < bestEnd) continue;
    best = sample;
    bestEnd = end;
  }
  return best;
}

export type Band = { start: Date; end: Date; period: RateBand };

export function bands(from: Date, to: Date): Band[] {
  if (from.getTime() >= to.getTime()) return [];
  const result: Band[] = [];
  let cursor = from;
  while (cursor.getTime() < to.getTime()) {
    const period = periodAt(cursor);
    const nextMs = Math.min(nextBoundary(cursor).getTime(), to.getTime());
    const next = new Date(nextMs);
    const last = result[result.length - 1];
    if (last && last.period === period) last.end = next;
    else result.push({ start: cursor, end: next, period });
    cursor = next;
  }
  return result;
}

function nextBoundary(instant: Date): Date {
  const local = detroitParts(instant);
  const today = { year: local.year, month: local.month, day: local.day };
  const tomorrow = addDays(today, 1);
  const candidates = [
    zonedTimeToUtc(today.year, today.month, today.day, 7, 0),
    zonedTimeToUtc(today.year, today.month, today.day, 15, 0),
    zonedTimeToUtc(today.year, today.month, today.day, 19, 0),
    zonedTimeToUtc(today.year, today.month, today.day, 23, 0),
    zonedTimeToUtc(tomorrow.year, tomorrow.month, tomorrow.day, 0, 0),
  ];
  return candidates.find((c) => c.getTime() > instant.getTime())
    ?? zonedTimeToUtc(tomorrow.year, tomorrow.month, tomorrow.day, 0, 0);
}

export function isOffPeakDay(date: Ymd, weekday?: string): boolean {
  const label = weekday ?? detroitParts(zonedTimeToUtc(date.year, date.month, date.day, 12, 0)).weekday;
  return WEEKEND.has(label) || isDesignatedHoliday(date);
}

export function isDesignatedHoliday(date: Ymd): boolean {
  const key = ymdKey(date);
  const year = date.year;
  return (
    key === `${year}-01-01` ||
    key === ymdKey(goodFriday(year)) ||
    key === ymdKey(memorialDay(year)) ||
    key === `${year}-07-04` ||
    key === ymdKey(laborDay(year)) ||
    key === ymdKey(thanksgiving(year)) ||
    key === `${year}-12-25`
  );
}

function memorialDay(year: number): Ymd {
  return lastWeekdayOfMonth(year, 5, 1);
}

function laborDay(year: number): Ymd {
  return nthWeekdayOfMonth(year, 9, 1, 1);
}

function thanksgiving(year: number): Ymd {
  return nthWeekdayOfMonth(year, 11, 4, 4);
}

function goodFriday(year: number): Ymd {
  return addDays(easterSunday(year), -2);
}

/** Anonymous Gregorian computus. Same arithmetic as DteTou.kt. */
function easterSunday(year: number): Ymd {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return { year, month, day };
}

function weekdayIndex(year: number, month: number, day: number): number {
  return new Date(Date.UTC(year, month - 1, day)).getUTCDay();
}

function nthWeekdayOfMonth(year: number, month: number, weekday: number, n: number): Ymd {
  const first = weekdayIndex(year, month, 1);
  const delta = (weekday - first + 7) % 7;
  return { year, month, day: 1 + delta + (n - 1) * 7 };
}

function lastWeekdayOfMonth(year: number, month: number, weekday: number): Ymd {
  const lastDate = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const lastDow = weekdayIndex(year, month, lastDate);
  const delta = (lastDow - weekday + 7) % 7;
  return { year, month, day: lastDate - delta };
}
