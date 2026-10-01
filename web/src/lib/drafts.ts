import { stampAt } from "./dte";
import type { SolcastRow } from "./demo";
import { periodHours, type IntervalDraft } from "./merge";
import { addDays, parseYmd, zonedTimeToUtc } from "./time";

export type WeatherHour = {
  /** America/Detroit hour start, `YYYY-MM-DDTHH`. */
  key: string;
  tempF: number;
  precipPct: number | null;
};

export function draftsFromSnapshot(
  actuals: SolcastRow[] | undefined,
  forecasts: SolcastRow[] | undefined,
  weather: WeatherHour[] | null
): IntervalDraft[] {
  const byEnd = new Map<string, IntervalDraft>();

  for (const row of actuals ?? []) {
    const draft = baseDraft(row);
    if (!draft) continue;
    const current = byEnd.get(draft.periodEnd);
    byEnd.set(draft.periodEnd, {
      ...(current ?? draft),
      actualKw: draft.actualKw,
      periodHours: draft.periodHours,
    });
  }

  for (const row of forecasts ?? []) {
    const draft = baseDraft(row);
    if (!draft) continue;
    const current = byEnd.get(draft.periodEnd);
    if (current) {
      current.forecastKw = draft.actualKw;
      current.periodHours = current.periodHours ?? draft.periodHours;
    } else {
      byEnd.set(draft.periodEnd, {
        ...draft,
        actualKw: null,
        forecastKw: draft.actualKw,
      });
    }
  }

  for (const hour of weather ?? []) {
    for (const periodEnd of halfHoursEndingIn(hour.key)) {
      const current = byEnd.get(periodEnd);
      if (current) {
        current.tempF = hour.tempF;
        current.precipPct = hour.precipPct;
        continue;
      }
      const stamp = stampAt(new Date(periodEnd));
      byEnd.set(periodEnd, {
        periodEnd,
        actualKw: null,
        forecastKw: null,
        periodHours: 0.5,
        tempF: hour.tempF,
        precipPct: hour.precipPct,
        rateBand: stamp.rateBand,
        importCents: stamp.importCents,
        outflowCents: stamp.outflowCents,
      });
    }
  }

  return [...byEnd.values()].sort((a, b) => a.periodEnd.localeCompare(b.periodEnd));
}

/** Both Solcast period ends inside an Open-Meteo hour key `YYYY-MM-DDTHH`. */
export function halfHoursEndingIn(hourKey: string): string[] {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2})$/.exec(hourKey);
  if (!match) return [];
  const ymd = parseYmd(match[1]);
  const hour = Number(match[2]);
  if (!ymd || hour < 0 || hour > 23) return [];
  const half = zonedTimeToUtc(ymd.year, ymd.month, ymd.day, hour, 30).toISOString();
  const nextHour = hour === 23 ? 0 : hour + 1;
  const nextDay = hour === 23 ? addDays(ymd, 1) : ymd;
  const end = zonedTimeToUtc(nextDay.year, nextDay.month, nextDay.day, nextHour, 0).toISOString();
  return [half, end];
}

function baseDraft(row: SolcastRow): IntervalDraft | null {
  const periodMs = Date.parse(row.period_end);
  const kw = Number(row.pv_estimate);
  if (!Number.isFinite(periodMs) || !Number.isFinite(kw)) return null;
  const stamp = stampAt(new Date(periodMs));
  return {
    periodEnd: new Date(periodMs).toISOString(),
    actualKw: kw,
    forecastKw: null,
    periodHours: periodHours(row.period),
    tempF: null,
    precipPct: null,
    rateBand: stamp.rateBand,
    importCents: stamp.importCents,
    outflowCents: stamp.outflowCents,
  };
}
