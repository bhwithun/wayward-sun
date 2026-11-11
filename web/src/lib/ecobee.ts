export type EcobeeSample = {
  t: number;
  indoorF: number;
  coolF: number | null;
  heatF: number | null;
};

export type EcobeeBand = "deadband" | "cooling" | "heating";

export type EcobeeMark = {
  t: number;
  indoorF: number;
  setF: number | null;
  band: EcobeeBand;
};

/** A hole longer than this breaks the line and the fill. */
export const ECOBEE_GAP_MS = 15 * 60 * 1000;

const TEMP_MIN = -20;
const TEMP_MAX = 100;

export function classifyEcobee(sample: EcobeeSample): EcobeeMark {
  const cool = finite(sample.coolF);
  const heat = finite(sample.heatF);
  if (cool != null && sample.indoorF > cool) {
    return { t: sample.t, indoorF: sample.indoorF, setF: cool, band: "cooling" };
  }
  if (heat != null && sample.indoorF < heat) {
    return { t: sample.t, indoorF: sample.indoorF, setF: heat, band: "heating" };
  }
  return { t: sample.t, indoorF: sample.indoorF, setF: null, band: "deadband" };
}

/** Blank and "off" are not a temperature. */
export function parseEcobeeSetpoint(raw: string | null | undefined): number | null {
  if (raw == null) return null;
  const text = raw.trim().toLowerCase();
  if (text === "" || text === "off" || text === "null" || text === "nan") return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
}

export function ecobeeFillSegments(run: EcobeeMark[]): EcobeeMark[][] {
  const segments: EcobeeMark[][] = [];
  let index = 0;
  while (index < run.length) {
    const band = run[index].band;
    let end = index + 1;
    while (end < run.length && run[end].band === band) end += 1;
    if (band !== "deadband" && end - index >= 2) segments.push(run.slice(index, end));
    index = end;
  }
  return segments;
}

/**
 * Keep samples in [from, to), split raw gaps, then one mark per pixel column.
 * Gaps are split before bucketing so downsampling cannot draw across a hole.
 */
export function prepareEcobee(
  samples: EcobeeSample[],
  from: number,
  to: number,
  plotWidthPx: number,
  gapMs = ECOBEE_GAP_MS,
  now?: number
): EcobeeMark[][] {
  const end = now != null && now < to ? now : to;
  if (plotWidthPx < 1 || end <= from) return [];
  const window = samples
    .filter(
      (sample) =>
        Number.isFinite(sample.indoorF) &&
        sample.t >= from &&
        sample.t < to &&
        (now == null || sample.t <= now)
    )
    .sort((a, b) => a.t - b.t);
  if (window.length === 0) return [];
  const span = end - from;
  const runs: EcobeeSample[][] = [];
  let current: EcobeeSample[] = [];
  for (const sample of window) {
    const previous = current[current.length - 1];
    if (previous && sample.t - previous.t > gapMs) {
      runs.push(current);
      current = [];
    }
    current.push(sample);
  }
  if (current.length > 0) runs.push(current);
  return runs.map((run) => downsampleEcobee(run, from, span, plotWidthPx));
}

export function downsampleEcobee(
  run: EcobeeSample[],
  from: number,
  spanMs: number,
  plotWidthPx: number
): EcobeeMark[] {
  if (run.length === 0 || plotWidthPx < 1 || spanMs <= 0) return [];
  const buckets = new Map<number, EcobeeMark>();
  for (const sample of run) {
    const mark = classifyEcobee(sample);
    const px = Math.min(plotWidthPx - 1, Math.max(0, Math.floor(((mark.t - from) / spanMs) * plotWidthPx)));
    const existing = buckets.get(px);
    if (!existing || gapScore(mark) >= gapScore(existing)) buckets.set(px, mark);
  }
  return [...buckets.entries()].sort((a, b) => a[0] - b[0]).map((entry) => entry[1]);
}

export function sharedFahrenheitRange(
  outdoorF: number[],
  indoor: EcobeeSample[],
  from: number,
  to: number,
  scaleToData: boolean
): { min: number; max: number } {
  if (!scaleToData) return { min: TEMP_MIN, max: TEMP_MAX };
  const values: number[] = [];
  for (const temp of outdoorF) if (Number.isFinite(temp)) values.push(temp);
  for (const sample of indoor) {
    if (!Number.isFinite(sample.indoorF) || sample.t < from || sample.t >= to) continue;
    values.push(sample.indoorF);
    if (sample.coolF != null && Number.isFinite(sample.coolF)) values.push(sample.coolF);
    if (sample.heatF != null && Number.isFinite(sample.heatF)) values.push(sample.heatF);
  }
  if (values.length === 0) return { min: TEMP_MIN, max: TEMP_MAX };
  let lo = Math.min(...values);
  let hi = Math.max(...values);
  if (hi - lo < 6) {
    const mid = (lo + hi) / 2;
    lo = mid - 3;
    hi = mid + 3;
  }
  const pad = Math.max((hi - lo) * 0.08, 0.5);
  return { min: lo - pad, max: hi + pad };
}

export type TemperatureGrid = {
  min: number;
  max: number;
  step: number;
  ticks: number[];
};

/** °F grid on a 5/10/20/50 step. Ticks within 3° of 32 are omitted. */
export function temperatureGrid(dataMin: number, dataMax: number, snapEnds: boolean): TemperatureGrid {
  let lo = dataMin;
  let hi = dataMax;
  if (hi - lo < 1) {
    lo -= 5;
    hi += 5;
  }
  const step = chooseTempStep(hi - lo);
  if (snapEnds) {
    lo = Math.floor(lo / step) * step;
    hi = Math.ceil(hi / step) * step;
    if (hi <= lo) hi = lo + step;
  }
  const ticks: number[] = [];
  const add = (value: number) => {
    if (value < lo - 1e-6 || value > hi + 1e-6) return;
    if (Math.abs(value - 32) <= 3) return;
    if (ticks.some((tick) => Math.abs(tick - value) < 0.01)) return;
    ticks.push(value);
  };
  add(lo);
  add(hi);
  for (let tick = Math.ceil(lo / step - 1e-9) * step; tick <= hi + 1e-6; tick += step) add(tick);
  ticks.sort((a, b) => a - b);
  return { min: lo, max: hi, step, ticks };
}

export function chooseTempStep(span: number): number {
  const options = [5, 10, 20, 50, 100];
  return options.reduce((best, step) => (stepScore(span, step) < stepScore(span, best) ? step : best));
}

function stepScore(span: number, step: number): number {
  const intervals = span / step;
  if (intervals < 2) return 10 + (2 - intervals);
  if (intervals > 6) return 10 + (intervals - 6);
  return Math.abs(intervals - 3);
}

function gapScore(mark: EcobeeMark): number {
  if (mark.band === "cooling") return mark.indoorF - (mark.setF ?? mark.indoorF);
  if (mark.band === "heating") return (mark.setF ?? mark.indoorF) - mark.indoorF;
  return 0;
}

function finite(value: number | null): number | null {
  return value != null && Number.isFinite(value) ? value : null;
}
