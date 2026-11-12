"use client";

import { useEffect, useRef } from "react";
import { rateSteps, type RateSample, type RateVertex } from "@/lib/dte";
import {
  ecobeeFillSegments,
  hvacRuns,
  type HvacRun,
  prepareEcobee,
  sharedFahrenheitRange,
  temperatureGrid,
  type EcobeeSample,
} from "@/lib/ecobee";
import { FORECAST_GAP_MS, forecastStrokes, isNight, type SeriesPoint, type SunSpan } from "@/lib/series";
import { detroitParts } from "@/lib/time";

const LIVE = "#2EE6A6";
const FORECAST = "#F5C542";
const NOW = "rgba(255, 255, 255, 0.60)";
const GRID = "rgba(74, 98, 136, 0.45)";
const INK = "#E8EEF7";
const PRECIP = "rgba(77, 208, 200, 0.40)";
const BUY = { r: 229, g: 127, b: 216 };
const SELL = { r: 123, g: 216, b: 143 };
const TEMP = "#E6E1F0";
const SETPOINT = "#FFF176";
const TOO_WARM = "#4DA3FF";
const TOO_COOL = "#FF9800";
const HEAT_PUMP = "#00897B";
const AUX_HEAT = "#EF5350";
const COOL_RUN = "#90CAF9";

const SOLAR_MAX = 8;
const PRECIP_MAX = 100;
const RATE_MIN = 0;
const RATE_MAX = 40;

export type ChartLayers = {
  solcast: boolean;
  enphase: boolean;
  temp: boolean;
  precip: boolean;
  buy: boolean;
  sell: boolean;
  ecobee: boolean;
  hvac: boolean;
};

export type WeatherMark = { t: number; tempF: number; precipPct: number | null };

type Props = {
  points: SeriesPoint[];
  weather: WeatherMark[];
  from: number;
  to: number;
  now: number;
  layers: ChartLayers;
  sun: SunSpan[];
  rates: RateSample[];
  demo: boolean;
  ecobee?: EcobeeSample[];
};

export function PowerChart(props: Props) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const draw = () => paint(canvas, props);
    draw();
    const observer = new ResizeObserver(draw);
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [props]);

  return <canvas ref={ref} className="chart" aria-label="Solcast, Enphase, weather, rates, and indoor temperature chart" />;
}

function paint(canvas: HTMLCanvasElement, props: Props) {
  const parent = canvas.parentElement;
  canvas.style.width = "100%";
  canvas.style.maxWidth = "100%";
  const width = Math.max(1, parent?.clientWidth ?? 640);
  const plotHeight = 320;
  const height = plotHeight;
  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);
  canvas.style.height = `${height}px`;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);

  const units = axisUnits(props.layers);
  const showTempLabels = props.layers.temp || props.layers.ecobee;
  const left = showTempLabels || units !== "none" ? 52 : 12;
  const right = width - 18;
  const showPower = props.layers.solcast || props.layers.enphase;
  const ecobee = (props.ecobee ?? []).filter((sample) => sample.t <= props.now);
  const showDegrees = props.layers.temp || props.layers.ecobee;
  const oneDay = props.to - props.from > 0 && props.to - props.from <= 26 * 60 * 60 * 1000;
  const weekColumns = !oneDay && props.to - props.from <= 10 * 24 * 60 * 60 * 1000;
  const top = (showDegrees ? 28 : 16) + (weekColumns ? 20 : 0);
  const bottom = plotHeight - (oneDay || showPower || weekColumns ? 36 : 28);
  const plotW = Math.max(1, right - left);
  const plotH = Math.max(1, bottom - top);
  const span = Math.max(1, props.to - props.from);

  const xOf = (t: number) => left + ((t - props.from) / span) * plotW;
  const ySolar = (kw: number) => bottom - (clamp(kw / SOLAR_MAX, 0, 1) * plotH);
  const outdoor = props.layers.temp
    ? props.weather.filter((mark) => mark.t >= props.from && mark.t < props.to).map((mark) => mark.tempF)
    : [];
  const rawTemps = sharedFahrenheitRange(
    outdoor,
    props.layers.ecobee ? ecobee : [],
    props.from,
    props.to,
    props.layers.ecobee,
    !props.layers.ecobee
  );
  const tempGrid = temperatureGrid(rawTemps.min, rawTemps.max, props.layers.ecobee);
  const tempRange = { min: tempGrid.min, max: tempGrid.max };
  const tempSpan = Math.max(0.001, tempRange.max - tempRange.min);
  const yTemp = (temp: number) => bottom - (clamp((temp - tempRange.min) / tempSpan, 0, 1) * plotH);
  const yPop = (pop: number) => bottom - (clamp(pop / PRECIP_MAX, 0, 1) * plotH);
  const yRate = (cents: number) =>
    bottom - (clamp((cents - RATE_MIN) / (RATE_MAX - RATE_MIN), 0, 1) * plotH);

  if (props.layers.buy || props.layers.sell) {
    const steps = rateSteps(new Date(props.from), new Date(props.to), props.rates);
    const baseline = yRate(RATE_MIN);
    const ceiling = yRate(RATE_MAX);
    if (props.layers.buy) fillSteps(ctx, steps.buy, xOf, yRate, baseline, ceiling, BUY);
    if (props.layers.sell) fillSteps(ctx, steps.sell, xOf, yRate, baseline, ceiling, SELL);
  }

  if (props.layers.precip && props.weather.length >= 2) {
    const bar = (plotW / props.weather.length) * 0.65;
    ctx.fillStyle = PRECIP;
    for (const mark of props.weather) {
      if (mark.precipPct == null || mark.precipPct <= 0) continue;
      const x = xOf(mark.t);
      if (x < left || x > right) continue;
      const y = yPop(mark.precipPct);
      ctx.fillRect(x - bar / 2, y, bar, bottom - y);
    }
  }

  ctx.strokeStyle = GRID;
  ctx.lineWidth = 1;
  ctx.fillStyle = INK;
  ctx.font = "12px ui-sans-serif, system-ui, sans-serif";
  if (showTempLabels) {
    for (const tick of tempGrid.ticks) {
      const y = yTemp(tick);
      ctx.beginPath();
      ctx.moveTo(left, y);
      ctx.lineTo(right, y);
      ctx.stroke();
      const labelY = Math.abs(tick - tempRange.max) < 0.01 ? top + 12 : Math.abs(tick - tempRange.min) < 0.01 ? bottom : y + 4;
      ctx.fillText(`${Math.round(tick)}°F`, 4, labelY);
    }
  } else {
    for (const step of [0, 1, 2]) {
      const y = ySolar((SOLAR_MAX * step) / 2);
      ctx.beginPath();
      ctx.moveTo(left, y);
      ctx.lineTo(right, y);
      ctx.stroke();
    }
  }
  if (units === "solar") {
    ctx.fillText("8 kW", 6, top + 12);
    ctx.fillText("4 kW", 6, ySolar(4) + 4);
    ctx.fillText("0 kW", 6, bottom);
  } else if (units === "temp") {
    // Labeled with the round grid above.
  } else if (units === "precip") {
    ctx.fillText("100%", 6, top + 12);
    ctx.fillText("50%", 6, yPop(50) + 4);
    ctx.fillText("0%", 6, bottom);
  } else if (units === "rates") {
    for (let cents = 0; cents <= RATE_MAX; cents += 10) {
      const y = cents === RATE_MAX ? top + 12 : cents === RATE_MIN ? bottom : yRate(cents);
      ctx.fillText(`${cents}¢`, 6, y);
    }
  }

  if (showDegrees) drawFreezeReference(ctx, yTemp, left, right, tempRange.min, tempRange.max);

  const ecobeeEnd = Math.min(props.to, props.now);
  if (props.layers.ecobee) drawEcobee(ctx, ecobee, props.from, ecobeeEnd, plotW, xOf, yTemp, "fill", props.now);

  if (props.layers.solcast) {
    const gold = forecastStrokes(props.points, props.now);
    strokeSeries(ctx, gold.past, xOf, ySolar, FORECAST, props.demo, 0.5, FORECAST_GAP_MS);
    strokeSeries(ctx, gold.future, xOf, ySolar, FORECAST, props.demo, 1, FORECAST_GAP_MS);
  }
  if (props.layers.enphase) {
    strokeSeries(ctx, props.points.filter((p) => p.kind === "live"), xOf, ySolar, LIVE, props.demo, 1);
  }

  if (props.layers.temp && props.weather.length >= 2) {
    strokeTemp(ctx, props.weather, xOf, yTemp, props.sun);
  }
  if (props.layers.ecobee) drawEcobee(ctx, ecobee, props.from, ecobeeEnd, plotW, xOf, yTemp, "line", props.now, dpr);

  const laneH = props.layers.hvac ? 10 : 0;
  const laneGap = props.layers.hvac ? 4 : 0;
  const laneTop = top - laneGap - laneH;
  if (props.layers.hvac) {
    const trackRight = props.now < props.from ? left : props.now >= props.to ? right : xOf(props.now);
    drawRuntimeLane(
      ctx,
      hvacRuns(ecobee, props.from, Math.min(props.to, props.now), props.now),
      xOf,
      left,
      trackRight,
      right,
      laneTop,
      laneH
    );
  }

  if (oneDay) {
    drawHourAxis(ctx, props.from, props.to, xOf, left, right, top, bottom, height);
  } else if (weekColumns) {
    drawDayColumns(ctx, props.from, props.to, xOf, left, right, laneTop, props.points, props.now, height);
  }

  if (props.now >= props.from && props.now < props.to) {
    const nowX = xOf(props.now);
    ctx.strokeStyle = NOW;
    ctx.lineWidth = 2;
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(nowX, props.layers.hvac ? laneTop : top);
    ctx.lineTo(nowX, bottom);
    ctx.stroke();
  }
}

function drawDayColumns(
  ctx: CanvasRenderingContext2D,
  from: number,
  to: number,
  xOf: (t: number) => number,
  left: number,
  right: number,
  laneTop: number,
  points: SeriesPoint[],
  now: number,
  height: number
) {
  ctx.save();
  ctx.fillStyle = INK;
  ctx.font = "12px ui-sans-serif, system-ui, sans-serif";
  ctx.textAlign = "center";
  const labelY = Math.max(14, laneTop - 10);
  for (let midnight = from; midnight < to; midnight += 24 * 60 * 60 * 1000) {
    const noon = midnight + 12 * 60 * 60 * 1000;
    if (noon < from || noon >= to) continue;
    const x = xOf(noon);
    if (x <= left + 8 || x >= right - 8) continue;
    const parts = detroitParts(new Date(noon));
    ctx.fillText(`${parts.weekday} ${parts.day}`, x, labelY);
    const next = midnight + 24 * 60 * 60 * 1000;
    let kwh = 0;
    let saw = false;
    for (const point of points) {
      if (point.kind !== "live" && point.kind !== "forecast") continue;
      if (point.t <= midnight || point.t > next) continue;
      if (point.kind === "forecast" && point.t <= now) continue;
      if (point.kind === "live" && point.t > now) continue;
      kwh += point.kw * point.hours;
      saw = true;
    }
    if (saw) ctx.fillText(`${Math.round(kwh)} kWh`, x, height - 8);
  }
  ctx.restore();
}

function hourClock(hour: number): string {
  if (hour === 0) return "12a";
  if (hour === 12) return "12p";
  if (hour < 12) return `${hour}a`;
  return `${hour - 12}p`;
}

function drawHourAxis(
  ctx: CanvasRenderingContext2D,
  from: number,
  to: number,
  xOf: (t: number) => number,
  left: number,
  right: number,
  top: number,
  bottom: number,
  height: number
) {
  ctx.save();
  ctx.strokeStyle = GRID;
  ctx.fillStyle = INK;
  ctx.font = "11px ui-sans-serif, system-ui, sans-serif";
  ctx.textAlign = "center";
  ctx.lineWidth = 1;
  ctx.setLineDash([]);
  for (let t = from; t < to; t += 3 * 60 * 60 * 1000) {
    const hour = detroitParts(new Date(t)).hour;
    if (hour % 3 !== 0) continue;
    const x = xOf(t);
    if (x < left || x > right) continue;
    ctx.beginPath();
    ctx.moveTo(x, top);
    ctx.lineTo(x, bottom);
    ctx.stroke();
    if (x > left + 22 && x < right - 22) ctx.fillText(hourClock(hour), x, height - 8);
  }
  ctx.restore();
}

function strokeSeries(
  ctx: CanvasRenderingContext2D,
  points: SeriesPoint[],
  xOf: (t: number) => number,
  yOf: (kw: number) => number,
  color: string,
  demo: boolean,
  alpha: number,
  maxGapMs?: number
) {
  if (points.length < 2) return;
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.strokeStyle = color;
  ctx.lineWidth = 2;
  ctx.lineJoin = "round";
  ctx.lineCap = "round";
  ctx.setLineDash(demo ? [5, 4] : []);
  ctx.beginPath();
  points.forEach((point, index) => {
    const x = xOf(point.t);
    const y = yOf(point.kw);
    const gap = index > 0 && maxGapMs != null && point.t - points[index - 1].t > maxGapMs;
    if (index === 0 || gap) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
  ctx.restore();
}

function fillSteps(
  ctx: CanvasRenderingContext2D,
  vertices: RateVertex[],
  xOf: (t: number) => number,
  yOf: (cents: number) => number,
  baseline: number,
  ceiling: number,
  color: { r: number; g: number; b: number }
) {
  if (vertices.length < 2) return;
  const wash = ctx.createLinearGradient(0, ceiling, 0, baseline);
  wash.addColorStop(0, `rgba(${color.r}, ${color.g}, ${color.b}, 0.20)`);
  wash.addColorStop(1, `rgba(${color.r}, ${color.g}, ${color.b}, 0)`);
  ctx.save();
  ctx.fillStyle = wash;
  ctx.beginPath();
  ctx.moveTo(xOf(vertices[0].t), baseline);
  vertices.forEach((vertex) => ctx.lineTo(xOf(vertex.t), yOf(vertex.cents)));
  ctx.lineTo(xOf(vertices[vertices.length - 1].t), baseline);
  ctx.closePath();
  ctx.fill();
  ctx.beginPath();
  vertices.forEach((vertex, index) => {
    const x = xOf(vertex.t);
    const y = yOf(vertex.cents);
    if (index === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.strokeStyle = `rgba(${color.r}, ${color.g}, ${color.b}, 0.55)`;
  ctx.lineWidth = 1;
  ctx.lineJoin = "miter";
  ctx.lineCap = "butt";
  ctx.setLineDash([]);
  ctx.stroke();
  ctx.restore();
}

function drawFreezeReference(
  ctx: CanvasRenderingContext2D,
  yOf: (temp: number) => number,
  left: number,
  right: number,
  tempMin: number,
  tempMax: number
) {
  if (!(tempMin < 32 && tempMax > 32)) return;
  const y = yOf(32);
  ctx.save();
  ctx.strokeStyle = "rgba(255, 255, 255, 0.25)";
  ctx.lineWidth = 1;
  ctx.setLineDash([2, 4]);
  ctx.beginPath();
  ctx.moveTo(left, y);
  ctx.lineTo(right, y);
  ctx.stroke();
  ctx.setLineDash([]);
  const text = "32°";
  ctx.font = "11px ui-sans-serif, system-ui, sans-serif";
  ctx.textAlign = "left";
  const textWidth = ctx.measureText(text).width;
  const boxLeft = left + 4;
  ctx.fillStyle = "rgba(11, 18, 32, 0.88)";
  ctx.beginPath();
  ctx.roundRect(boxLeft, y - 13, textWidth + 8, 14, 4);
  ctx.fill();
  ctx.fillStyle = "rgba(255, 255, 255, 0.92)";
  ctx.fillText(text, boxLeft + 4, y - 3);
  ctx.restore();
}

function strokeTemp(
  ctx: CanvasRenderingContext2D,
  weather: WeatherMark[],
  xOf: (t: number) => number,
  yOf: (temp: number) => number,
  sun: SunSpan[]
) {
  type Seg = { pts: WeatherMark[]; night: boolean };
  const segments: Seg[] = [];
  for (const mark of weather) {
    const night = isNight(mark.t, sun);
    const last = segments[segments.length - 1];
    if (!last || last.night !== night) {
      const seed = last ? [last.pts[last.pts.length - 1], mark] : [mark];
      segments.push({ pts: seed, night });
    } else {
      last.pts.push(mark);
    }
  }
  for (const segment of segments) {
    if (segment.pts.length < 2) continue;
    ctx.save();
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.setLineDash(segment.night ? [9, 7] : []);
    ctx.globalAlpha = segment.night ? 0.3 : 1;
    ctx.strokeStyle = TEMP;
    ctx.lineWidth = 1.6;
    trace(ctx, segment.pts, xOf, yOf);
    ctx.restore();
  }
}

function trace(
  ctx: CanvasRenderingContext2D,
  points: WeatherMark[],
  xOf: (t: number) => number,
  yOf: (temp: number) => number
) {
  ctx.beginPath();
  points.forEach((point, index) => {
    const x = xOf(point.t);
    const y = yOf(point.tempF);
    if (index === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
}

function drawEcobee(
  ctx: CanvasRenderingContext2D,
  samples: EcobeeSample[],
  from: number,
  to: number,
  plotW: number,
  xOf: (t: number) => number,
  yOf: (temp: number) => number,
  phase: "fill" | "line",
  now?: number,
  dpr = 1
) {
  const runs = prepareEcobee(samples, from, to, Math.max(1, Math.round(plotW)), undefined, now);
  for (const run of runs) {
    if (phase === "fill") {
      for (const segment of ecobeeFillSegments(run)) {
        ctx.beginPath();
        segment.forEach((mark, index) => {
          const x = xOf(mark.t);
          const y = yOf(mark.indoorF);
          if (index === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        });
        for (let index = segment.length - 1; index >= 0; index -= 1) {
          const mark = segment[index];
          ctx.lineTo(xOf(mark.t), yOf(mark.setF ?? mark.indoorF));
        }
        ctx.closePath();
        ctx.fillStyle = segment[0].band === "cooling" ? "rgba(77, 163, 255, 0.40)" : "rgba(255, 152, 0, 0.50)";
        ctx.fill();
      }
      continue;
    }
    ctx.save();
    ctx.setLineDash([]);
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.lineWidth = 1;
    for (const segment of ecobeeFillSegments(run)) {
      ctx.beginPath();
      ctx.strokeStyle = segment[0].band === "cooling" ? TOO_WARM : TOO_COOL;
      segment.forEach((mark, index) => {
        const x = xOf(mark.t);
        const y = yOf(mark.indoorF);
        if (index === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
    }
    ctx.strokeStyle = SETPOINT;
    ctx.lineWidth = 1.5;
    ctx.lineCap = "butt";
    ctx.lineJoin = "miter";
    ctx.beginPath();
    let drawing = false;
    for (const mark of run) {
      if (mark.setF == null) {
        drawing = false;
        continue;
      }
      const x = xOf(mark.t);
      const y = yOf(mark.setF);
      if (!drawing) {
        ctx.moveTo(x, y);
        drawing = true;
      } else {
        ctx.lineTo(x, y);
      }
    }
    ctx.stroke();
    ctx.restore();
  }
}

function drawRuntimeLane(
  ctx: CanvasRenderingContext2D,
  runs: HvacRun[],
  xOf: (t: number) => number,
  left: number,
  trackRight: number,
  right: number,
  laneTop: number,
  laneHeight: number
) {
  if (laneHeight < 1 || right <= left) return;
  ctx.save();
  const colorFor = (source: HvacRun["source"]) =>
    source === "aux" ? AUX_HEAT : source === "cool" ? COOL_RUN : HEAT_PUMP;
  const trackEnd = Math.min(right, Math.max(left, trackRight));
  if (trackEnd - left >= 1) {
    ctx.fillStyle = "rgba(255, 255, 255, 0.08)";
    ctx.beginPath();
    ctx.roundRect(left, laneTop, trackEnd - left, laneHeight, laneHeight / 2);
    ctx.fill();
  }
  for (const run of runs) {
    const x1 = Math.min(right, Math.max(left, xOf(run.start)));
    const x2 = Math.min(right, Math.max(left, xOf(run.end)));
    const span = x2 - x1;
    if (span < 1) continue;
    ctx.fillStyle = colorFor(run.source);
    ctx.beginPath();
    ctx.roundRect(x1, laneTop, span, laneHeight, Math.min(laneHeight / 2, span / 2));
    ctx.fill();
  }
  ctx.restore();
}

function axisUnits(layers: ChartLayers): "none" | "solar" | "temp" | "precip" | "rates" {
  const labeled: Array<"solar" | "temp" | "precip" | "rates"> = [];
  if (layers.solcast || layers.enphase) labeled.push("solar");
  if (layers.temp || layers.ecobee) labeled.push("temp");
  if (layers.precip) labeled.push("precip");
  if (layers.buy || layers.sell) labeled.push("rates");
  return labeled.length === 1 ? labeled[0] : "none";
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
