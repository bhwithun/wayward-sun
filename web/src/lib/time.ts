export const ZONE = "America/Detroit";

export type Ymd = { year: number; month: number; day: number };

export type ZonedParts = Ymd & {
  hour: number;
  minute: number;
  second: number;
  /** Short weekday from Intl, e.g. "Wed". */
  weekday: string;
};

export function detroitParts(instant: Date): ZonedParts {
  const dtf = new Intl.DateTimeFormat("en-US", {
    timeZone: ZONE,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    weekday: "short",
  });
  const bag: Record<string, string> = {};
  for (const part of dtf.formatToParts(instant)) {
    if (part.type !== "literal") bag[part.type] = part.value;
  }
  let hour = Number(bag.hour);
  if (hour === 24) hour = 0;
  return {
    year: Number(bag.year),
    month: Number(bag.month),
    day: Number(bag.day),
    hour,
    minute: Number(bag.minute),
    second: Number(bag.second),
    weekday: bag.weekday,
  };
}

/** Minutes east of UTC. Detroit is negative. */
export function offsetMinutes(instant: Date): number {
  const p = detroitParts(instant);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return Math.round((asUtc - instant.getTime()) / 60000);
}

/** Wall-clock time in America/Detroit as a UTC instant. */
export function zonedTimeToUtc(
  year: number,
  month: number,
  day: number,
  hour = 0,
  minute = 0,
  second = 0
): Date {
  const utcGuess = Date.UTC(year, month - 1, day, hour, minute, second);
  let offset = offsetMinutes(new Date(utcGuess));
  let instant = new Date(utcGuess - offset * 60000);
  offset = offsetMinutes(instant);
  instant = new Date(utcGuess - offset * 60000);
  return instant;
}

export function addDays(ymd: Ymd, days: number): Ymd {
  const t = new Date(Date.UTC(ymd.year, ymd.month - 1, ymd.day + days));
  return {
    year: t.getUTCFullYear(),
    month: t.getUTCMonth() + 1,
    day: t.getUTCDate(),
  };
}

export function ymdKey(ymd: Ymd): string {
  const m = String(ymd.month).padStart(2, "0");
  const d = String(ymd.day).padStart(2, "0");
  return `${ymd.year}-${m}-${d}`;
}

export function parseYmd(value: string): Ymd | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const check = new Date(Date.UTC(year, month - 1, day));
  if (
    check.getUTCFullYear() !== year ||
    check.getUTCMonth() + 1 !== month ||
    check.getUTCDate() !== day
  ) {
    return null;
  }
  return { year, month, day };
}

/** Sunday on or before this calendar date. Weeks are Sunday–Saturday. */
export function weekStart(ymd: Ymd): Ymd {
  const dow = new Date(Date.UTC(ymd.year, ymd.month - 1, ymd.day)).getUTCDay();
  return addDays(ymd, -dow);
}

export type CalendarDay = Ymd & { inMonth: boolean };

export type CalendarWeek = {
  /** Sunday, `YYYY-MM-DD`. */
  id: string;
  days: CalendarDay[];
};

/** One month, plus the spill days that complete the first and last Sunday weeks. */
export function monthWeeks(year: number, month: number): CalendarWeek[] {
  const first = weekStart({ year, month, day: 1 });
  const next = month === 12 ? { year: year + 1, month: 1, day: 1 } : { year, month: month + 1, day: 1 };
  const lastSunday = weekStart(addDays(next, -1));
  const weeks: CalendarWeek[] = [];
  let cursor = first;
  while (ymdKey(cursor) <= ymdKey(lastSunday)) {
    const days: CalendarDay[] = [];
    for (let i = 0; i < 7; i++) {
      const day = addDays(cursor, i);
      days.push({ ...day, inMonth: day.year === year && day.month === month });
    }
    weeks.push({ id: ymdKey(cursor), days });
    cursor = addDays(cursor, 7);
  }
  return weeks;
}

/** Start of 4 local days before `today` through the start of the day after 2 days ahead. */
export function liveWindow(today: Ymd): { from: Ymd; to: Ymd } {
  return { from: addDays(today, -4), to: addDays(today, 3) };
}

export function shiftMonth(year: number, month: number, delta: number): Ymd {
  const shifted = new Date(Date.UTC(year, month - 1 + delta, 1));
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: 1,
  };
}

/** Two local days before today through the start of the day after two days ahead. */
export function displayRange(now: Date): { from: Date; to: Date } {
  const today = detroitParts(now);
  const from = addDays(today, -2);
  const to = addDays(today, 3);
  return {
    from: zonedTimeToUtc(from.year, from.month, from.day),
    to: zonedTimeToUtc(to.year, to.month, to.day),
  };
}

/** Hour key for the Open-Meteo hour that contains a Solcast period ending at `periodEnd`. */
export function weatherHourKey(periodEnd: Date): string {
  const p = detroitParts(new Date(periodEnd.getTime() - 1));
  const hour = String(p.hour).padStart(2, "0");
  return `${ymdKey(p)}T${hour}`;
}

export function utcDay(now: Date): string {
  return now.toISOString().slice(0, 10);
}

const DAY_LABEL = new Intl.DateTimeFormat("en-US", {
  timeZone: ZONE,
  weekday: "short",
  day: "numeric",
});

export function dayLabel(instant: Date): string {
  return DAY_LABEL.format(instant);
}
