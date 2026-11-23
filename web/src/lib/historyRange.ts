import { addDays, detroitParts, parseYmd, zonedTimeToUtc } from "./time";

const HOUR_MS = 60 * 60 * 1000;

export type HistoryRange = { from: Date; to: Date } | { error: string };

export function parseRange(params: URLSearchParams, now: Date): HistoryRange {
  const dayRaw = params.get("day");
  if (dayRaw) {
    const ymd = parseYmd(dayRaw);
    if (!ymd) return { error: "day must be YYYY-MM-DD" };
    const next = addDays(ymd, 1);
    return {
      from: zonedTimeToUtc(ymd.year, ymd.month, ymd.day),
      to: zonedTimeToUtc(next.year, next.month, next.day),
    };
  }
  const sinceRaw = params.get("since");
  if (sinceRaw) {
    const since = new Date(sinceRaw);
    if (Number.isNaN(since.getTime())) return { error: "since must be an ISO instant" };
    const toRaw = params.get("to");
    let to: Date | null;
    if (toRaw) {
      to = parseBound(toRaw);
      if (!to) return { error: "to must be an ISO instant or YYYY-MM-DD" };
    } else {
      const today = detroitParts(now);
      const end = addDays(today, 2);
      to = zonedTimeToUtc(end.year, end.month, end.day);
    }
    return { from: new Date(since.getTime() + 1), to };
  }
  const fromRaw = params.get("from");
  const toRaw = params.get("to");
  if (!fromRaw || !toRaw) return { error: "from and to are required" };
  const from = parseBound(fromRaw);
  const to = parseBound(toRaw);
  if (!from || !to) return { error: "from and to must be ISO instants or YYYY-MM-DD" };
  return { from, to };
}

export type HistoryRes = "raw" | "15m" | "1h" | "auto" | "invalid";

export function parseRes(value: string | null): HistoryRes {
  if (value == null || value === "" || value === "raw") return "raw";
  if (value === "15m" || value === "1h" || value === "auto") return value;
  return "invalid";
}

export function bucketMinutes(res: HistoryRes, spanMs: number): number | null {
  if (res === "15m") return 15;
  if (res === "1h") return 60;
  if (res !== "auto") return null;
  if (spanMs <= 2 * 24 * HOUR_MS + 2 * HOUR_MS) return null;
  if (spanMs <= 8 * 24 * HOUR_MS + 2 * HOUR_MS) return 15;
  return 60;
}

/**
 * CDN seconds for a successful response. A range that ended more than two days
 * ago changes only on a backfill, so it keeps an hour. Ranges that touch the
 * last two days keep 5 minutes for a day or less and 15 minutes when longer, so
 * a client that repeats the same month request every 30 seconds reaches Neon at
 * most four times an hour.
 */
export function historyCacheSeconds(from: Date, to: Date, now: Date): number {
  if (to.getTime() < now.getTime() - 2 * 24 * HOUR_MS) return 3600;
  if (to.getTime() - from.getTime() <= 26 * HOUR_MS) return 300;
  return 900;
}

/** A date-only value is midnight in America/Detroit. Instant values are used as given. */
function parseBound(value: string): Date | null {
  const ymd = parseYmd(value);
  if (ymd) return zonedTimeToUtc(ymd.year, ymd.month, ymd.day);
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return parsed;
}
