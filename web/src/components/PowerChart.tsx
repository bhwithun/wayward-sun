"use client";

import { useEffect, useRef } from "react";
import { bands } from "@/lib/dte";
import { isNight, type SeriesPoint, type SunSpan } from "@/lib/series";

const LIVE = "#2EE6A6";
const FORECAST = "#F5C542";
const NOW = "#FF8A4C";
const GRID = "rgba(74, 98, 136, 0.45)";
const INK = "#E8EEF7";
const PRECIP = "rgba(92, 168, 255, 0.40)";
const TOU_MID = "rgba(201, 162, 39, 0.20)";
const TOU_PEAK = "rgba(224, 112, 112, 0.30)";
const TEMP = "#FFFFFF";
const FREEZE = "#64B5F6";
const HOT = "#FF8A4C";

const SOLAR_MAX = 8;
const TEMP_MIN = -20;
const TEMP_MAX = 100;
const PRECIP_MAX = 100;

export type ChartLayers = {
  solar: boolean;
  temp: boolean;
  precip: boolean;
  rates: boolean;
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
  demo: boolean;
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

  return <canvas ref={ref} className="chart" aria-label="Solar, weather, and rate chart" />;
}

function paint(canvas: HTMLCanvasElement, props: Props) {
  const parent = canvas.parentElement;
  const width = parent?.clientWidth ?? 640;
  const height = 320;
  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);

  const units = axisUnits(props.layers);
  const left = units === "none" ? 12 : 52;
  const right = width - 12;
  const top = props.layers.temp ? 28 : 16;
  const bottom = height - (props.layers.solar ? 36 : 28);
  const plotW = Math.max(1, right - left);
  const plotH = Math.max(1, bottom - top);
  const span = Math.max(1, props.to - props.from);

  const xOf = (t: number) => left + ((t - props.from) / span) * plotW;
  const ySolar = (kw: number) => bottom - (clamp(kw / SOLAR_MAX, 0, 1) * plotH);
  const yTemp = (temp: number) =>
    bottom - (clamp((temp - TEMP_MIN) / (TEMP_MAX - TEMP_MIN), 0, 1) * plotH);
  const yPop = (pop: number) => bottom - (clamp(pop / PRECIP_MAX, 0, 1) * plotH);

  if (props.layers.rates) {
    for (const band of bands(new Date(props.from), new Date(props.to))) {
      if (band.period === "OFF_PEAK") continue;
      const x1 = clamp(xOf(band.start.getTime()), left, right);
      const x2 = clamp(xOf(band.end.getTime()), left, right);
      if (x2 <= x1) continue;
      ctx.fillStyle = band.period === "PEAK" ? TOU_PEAK : TOU_MID;
      ctx.fillRect(x1, top, x2 - x1, plotH);
    }
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
  for (const step of [0, 1, 2]) {
    const y = ySolar((SOLAR_MAX * step) / 2);
    ctx.beginPath();
    ctx.moveTo(left, y);
    ctx.lineTo(right, y);
    ctx.stroke();
  }
  if (units === "solar") {
    ctx.fillText("8 kW", 6, top + 12);
    ctx.fillText("0 kW", 6, bottom);
  } else if (units === "temp") {
    ctx.fillText("100°F", 4, top + 12);
    ctx.fillText("-20°F", 4, bottom);
  } else if (units === "precip") {
    ctx.fillText("100%", 6, top + 12);
    ctx.fillText("0%", 6, bottom);
  }

  if (props.layers.solar) {
    strokeSeries(ctx, props.points.filter((p) => p.kind === "live"), xOf, ySolar, LIVE, props.demo);
    strokeSeries(ctx, props.points.filter((p) => p.kind === "forecast"), xOf, ySolar, FORECAST, props.demo);
  }

  if (props.layers.temp && props.weather.length >= 2) {
    strokeTemp(ctx, props.weather, xOf, yTemp, props.sun);
  }

  const nowX = clamp(xOf(props.now), left, right);
  ctx.strokeStyle = NOW;
  ctx.lineWidth = 2;
  ctx.setLineDash([]);
  ctx.beginPath();
  ctx.moveTo(nowX, top);
  ctx.lineTo(nowX, bottom);
  ctx.stroke();
}

function strokeSeries(
  ctx: CanvasRenderingContext2D,
  points: SeriesPoint[],
  xOf: (t: number) => number,
  yOf: (kw: number) => number,
  color: string,
  demo: boolean
) {
  if (points.length < 2) return;
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = 2;
  ctx.lineJoin = "round";
  ctx.lineCap = "round";
  ctx.setLineDash(demo ? [5, 4] : []);
  ctx.beginPath();
  points.forEach((point, index) => {
    const x = xOf(point.t);
    const y = yOf(point.kw);
    if (index === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
  ctx.restore();
}

function strokeTemp(
  ctx: CanvasRenderingContext2D,
  weather: WeatherMark[],
  xOf: (t: number) => number,
  yOf: (temp: number) => number,
  sun: SunSpan[]
) {
  type Seg = { pts: WeatherMark[]; night: boolean; glow: string | null };
  const segments: Seg[] = [];
  for (const mark of weather) {
    const night = isNight(mark.t, sun);
    const glow = mark.tempF < 32 ? FREEZE : mark.tempF > 90 ? HOT : null;
    const last = segments[segments.length - 1];
    if (!last || last.night !== night || last.glow !== glow) {
      const seed = last ? [last.pts[last.pts.length - 1], mark] : [mark];
      segments.push({ pts: seed, night, glow });
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
    if (segment.glow) {
      ctx.strokeStyle = segment.glow;
      ctx.lineWidth = 7;
      ctx.shadowColor = segment.glow;
      ctx.shadowBlur = 8;
      trace(ctx, segment.pts, xOf, yOf);
      ctx.shadowBlur = 0;
    }
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

function axisUnits(layers: ChartLayers): "none" | "solar" | "temp" | "precip" {
  const labeled: Array<"solar" | "temp" | "precip"> = [];
  if (layers.solar) labeled.push("solar");
  if (layers.temp) labeled.push("temp");
  if (layers.precip) labeled.push("precip");
  return labeled.length === 1 ? labeled[0] : "none";
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
