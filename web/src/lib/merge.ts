/**
 * Row merge used by the intervals upsert.
 * The SQL in ingest.ts must follow the same rules:
 * - actual_kw: incoming wins when present, never clears a stored actual
 * - forecast_kw: incoming replaces only while period_end is still in the future;
 *   a past incoming forecast fills the column only when it was empty
 * - temp and precip: incoming wins when present
 * - rate band and cents: first write sticks
 */
export type IntervalDraft = {
  periodEnd: string;
  actualKw: number | null;
  forecastKw: number | null;
  periodHours: number | null;
  tempF: number | null;
  precipPct: number | null;
  rateBand: string | null;
  importCents: number | null;
  outflowCents: number | null;
};

export function mergeInterval(
  existing: IntervalDraft | null,
  incoming: IntervalDraft,
  nowMs: number
): IntervalDraft {
  if (!existing) return { ...incoming };
  const periodMs = Date.parse(incoming.periodEnd);
  const future = Number.isFinite(periodMs) && periodMs > nowMs;
  let forecastKw = existing.forecastKw;
  if (incoming.forecastKw != null) {
    if (future || existing.forecastKw == null) forecastKw = incoming.forecastKw;
  }
  return {
    periodEnd: incoming.periodEnd,
    actualKw: incoming.actualKw ?? existing.actualKw,
    forecastKw,
    periodHours: incoming.periodHours ?? existing.periodHours,
    tempF: incoming.tempF ?? existing.tempF,
    precipPct: incoming.precipPct ?? existing.precipPct,
    rateBand: existing.rateBand ?? incoming.rateBand,
    importCents: existing.importCents ?? incoming.importCents,
    outflowCents: existing.outflowCents ?? incoming.outflowCents,
  };
}

export function periodHours(period: unknown): number {
  if (typeof period !== "string") return 0.5;
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/.exec(period);
  if (!match) return 0.5;
  const hours = Number(match[1] || 0) + Number(match[2] || 0) / 60 + Number(match[3] || 0) / 3600;
  return hours > 0 ? hours : 0.5;
}
