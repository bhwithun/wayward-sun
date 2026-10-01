import { stampAt } from "./dte";
import type { SolcastRow } from "./demo";
import { periodHours, type IntervalDraft } from "./merge";
import { weatherHourKey } from "./time";

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
  const weatherByHour = new Map((weather ?? []).map((hour) => [hour.key, hour]));

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

  for (const draft of byEnd.values()) {
    const hour = weatherByHour.get(weatherHourKey(new Date(draft.periodEnd)));
    if (!hour) continue;
    draft.tempF = hour.tempF;
    draft.precipPct = hour.precipPct;
  }

  return [...byEnd.values()].sort((a, b) => a.periodEnd.localeCompare(b.periodEnd));
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
