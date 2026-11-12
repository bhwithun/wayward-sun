"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { PowerChart, type ChartLayers } from "@/components/PowerChart";
import { hvacPresence, hvacSummary, hvacSummaryLabel, type EcobeeSample } from "@/lib/ecobee";
import {
  BASE_MID_PEAK_CENTS,
  BASE_OFF_PEAK_CENTS,
  BASE_PEAK_CENTS,
  MID_PEAK_CENTS,
  OFF_PEAK_CENTS,
  OUTFLOW_MID_PEAK_CENTS,
  OUTFLOW_OFF_PEAK_CENTS,
  OUTFLOW_PEAK_CENTS,
  PEAK_CENTS,
  PLAN_CODE,
  PLAN_NAME,
  PSCR_CENTS,
  RATES_AS_OF,
  RIDER,
} from "@/lib/dte";
import { dayTotals, seriesFromHistory, type SeriesPoint, type SunSpan } from "@/lib/series";
import {
  addDays,
  detroitParts,
  livePeriod,
  liveWindow,
  monthWeeks,
  parseYmd,
  periodRange,
  usWeekNumber,
  shiftMonth,
  ymdKey,
  zonedTimeToUtc,
  type Ymd,
} from "@/lib/time";

const LAYER_KEY = "wayward-sun-layers";
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const DEFAULT_LAYERS: ChartLayers = {
  solcast: true,
  enphase: true,
  temp: true,
  precip: true,
  buy: true,
  sell: true,
  ecobee: true,
  hvac: false,
};

function readLayers(): ChartLayers {
  try {
    const raw = localStorage.getItem(LAYER_KEY);
    if (!raw) return DEFAULT_LAYERS;
    const parsed = JSON.parse(raw) as Partial<ChartLayers> & { solar?: boolean };
    const legacy = parsed.solar !== false;
    return {
      solcast: parsed.solcast ?? legacy,
      enphase: parsed.enphase ?? legacy,
      temp: parsed.temp !== false,
      precip: parsed.precip !== false,
      buy: parsed.buy !== false,
      sell: parsed.sell !== false,
      ecobee: parsed.ecobee !== false,
      hvac: parsed.hvac === true,
    };
  } catch {
    return DEFAULT_LAYERS;
  }
}

type CacheBody = {
  resourceId?: string;
  fetchedAt?: string | null;
  source?: string;
  requestsDay?: string;
  requestsUsed?: number;
  autoFetchesUsed?: number;
  actualsFetchesUsed?: number;
  message?: string | null;
  error?: string;
};

type HistoryPoint = {
  periodEnd: string;
  actualKw: number | null;
  forecastKw: number | null;
  periodHours: number | null;
  tempF: number | null;
  precipPct: number | null;
  importCents: number | null;
  outflowCents: number | null;
};

type EcobeeBody = {
  t: string;
  indoorF: number;
  coolF: number | null;
  heatF: number | null;
  heatPumpSec?: number | null;
  auxHeatSec?: number | null;
  coolSec?: number | null;
  fanSec?: number | null;
  outdoorF?: number | null;
};

type HistoryBody = {
  points?: HistoryPoint[];
  sun?: Array<{ date: string; sunrise: string; sunset: string }>;
  ecobee?: EcobeeBody[];
  error?: string;
};

type Tab = "live" | "history";
type HistoryFocus = "week" | "day";

const RATES: Array<[string, string, string, string]> = [
  ["Import base", cents(BASE_OFF_PEAK_CENTS), cents(BASE_MID_PEAK_CENTS), cents(BASE_PEAK_CENTS)],
  ["Import effective", cents(OFF_PEAK_CENTS), cents(MID_PEAK_CENTS), cents(PEAK_CENTS)],
  ["Export (R18)", cents(OUTFLOW_OFF_PEAK_CENTS), cents(OUTFLOW_MID_PEAK_CENTS), cents(OUTFLOW_PEAK_CENTS)],
  [
    "Export + PSCR",
    cents(OUTFLOW_OFF_PEAK_CENTS + PSCR_CENTS),
    cents(OUTFLOW_MID_PEAK_CENTS + PSCR_CENTS),
    cents(OUTFLOW_PEAK_CENTS + PSCR_CENTS),
  ],
];

const MONTH_LABEL = new Intl.DateTimeFormat("en-US", {
  month: "long",
  year: "numeric",
  timeZone: "UTC",
});

export function Dashboard() {
  const [layers, setLayers] = useState<ChartLayers>(DEFAULT_LAYERS);
  const [layersReady, setLayersReady] = useState(false);
  const [tab, setTab] = useState<Tab>("live");
  const [legendOpen, setLegendOpen] = useState(false);
  const legendRow = useRef<HTMLDivElement>(null);
  const [focus, setFocus] = useState<HistoryFocus>("week");
  const [dayId, setDayId] = useState<string | null>(null);
  const [cache, setCache] = useState<CacheBody | null>(null);
  const [history, setHistory] = useState<HistoryBody | null>(null);
  const [status, setStatus] = useState("");
  const [clock, setClock] = useState(() => Date.now());
  const todayParts = detroitParts(new Date(clock));
  const todayKey = ymdKey(todayParts);
  const [month, setMonth] = useState(() => ({ year: todayParts.year, month: todayParts.month }));
  const [weekId, setWeekId] = useState<string | null>(null);

  useEffect(() => {
    setLayers(readLayers());
    setLayersReady(true);
    const id = setInterval(() => setClock(Date.now()), 60_000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (!layersReady) return;
    localStorage.setItem(LAYER_KEY, JSON.stringify(layers));
  }, [layers, layersReady]);

  const weeks = useMemo(() => monthWeeks(month.year, month.month), [month]);
  const selectedWeek = useMemo(
    () =>
      weeks.find((week) => week.id === weekId) ??
      weeks.find((week) => week.days.some((day) => ymdKey(day) === todayKey)) ??
      weeks[0],
    [weeks, weekId, todayKey]
  );

  const selectedDay = useMemo(() => {
    const picked = dayId ? parseYmd(dayId) : null;
    if (picked) return picked;
    if (todayParts.year === month.year && todayParts.month === month.month) {
      return { year: todayParts.year, month: todayParts.month, day: todayParts.day };
    }
    return { year: month.year, month: month.month, day: 1 };
  }, [dayId, month, todayParts]);

  const range = useMemo(() => {
    const today = parseYmd(todayKey);
    if (!today) return null;
    if (tab === "live") {
      const window = liveWindow(today);
      return span(window.from, window.to);
    }
    if (weeks.length === 0) return null;
    const start = parseYmd(weeks[0].id);
    const endSunday = parseYmd(weeks[weeks.length - 1].id);
    if (!start || !endSunday) return null;
    return span(start, addDays(endSunday, 7));
  }, [tab, todayKey, weeks]);

  const load = useCallback(async () => {
    if (!range) {
      setHistory(null);
      return;
    }
    setStatus("");
    const [cacheRes, historyRes] = await Promise.all([
      fetch("/cache"),
      fetch(`/history?from=${encodeURIComponent(range.from)}&to=${encodeURIComponent(range.to)}`),
    ]);
    const cacheBody = (await cacheRes.json()) as CacheBody;
    const historyBody = (await historyRes.json()) as HistoryBody;
    setCache(cacheBody);
    setHistory(historyBody);
    if (!cacheRes.ok) setStatus(cacheBody.error || `Cache HTTP ${cacheRes.status}`);
    else if (!historyRes.ok) setStatus(historyBody.error || `History HTTP ${historyRes.status}`);
    else if (cacheBody.message && !isRoutinePullNote(cacheBody.message)) setStatus(cacheBody.message);
  }, [range]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!legendOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setLegendOpen(false);
    };
    const onPointer = (event: MouseEvent) => {
      if (!legendRow.current?.contains(event.target as Node)) setLegendOpen(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onPointer);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onPointer);
    };
  }, [legendOpen]);

  const chartFocus =
    tab === "history" && focus === "week" && selectedWeek
      ? { from: parseYmd(selectedWeek.id) ?? selectedDay, days: 7 }
      : tab === "history"
        ? { from: selectedDay, days: 1 }
        : null;
  const chart = chartModel(history, clock, chartFocus, todayParts);
  const runtime = hvacPresence(chart.ecobee, chart.fromMs, chart.toMs, clock);
  const monthName = MONTH_LABEL.format(new Date(Date.UTC(month.year, month.month - 1, 1)));

  return (
    <main>
      <h1>Wayward Sun</h1>
      <div className="tabs" role="tablist" data-tab={tab}>
        <button type="button" role="tab" aria-selected={tab === "live"} onClick={() => setTab("live")}>
          Live
        </button>
        <button type="button" role="tab" aria-selected={tab === "history"} onClick={() => setTab("history")}>
          History
        </button>
        <span className="tab-indicator" aria-hidden="true">
          <i />
        </span>
      </div>
      <p className="muted">
        Shared Solcast cache on Vercel. Phones download <code>GET /cache</code>. Solcast itself runs only on the Detroit schedule.
      </p>

      <div className="toolbar">
        <Chip label="Solcast" on={layers.solcast} onClick={() => setLayers({ ...layers, solcast: !layers.solcast })} />
        <Chip label="Enphase" on={layers.enphase} onClick={() => setLayers({ ...layers, enphase: !layers.enphase })} />
        <Chip label="Outdoor" on={layers.temp} onClick={() => setLayers({ ...layers, temp: !layers.temp })} />
        <Chip label="Precip" on={layers.precip} onClick={() => setLayers({ ...layers, precip: !layers.precip })} />
        <Chip label="Buy" on={layers.buy} onClick={() => setLayers({ ...layers, buy: !layers.buy })} />
        <Chip label="Sell" on={layers.sell} onClick={() => setLayers({ ...layers, sell: !layers.sell })} />
        <Chip label="Ecobee" on={layers.ecobee} onClick={() => setLayers({ ...layers, ecobee: !layers.ecobee })} />
        <Chip label="HVAC" on={layers.hvac} onClick={() => setLayers({ ...layers, hvac: !layers.hvac })} />
      </div>

      {status && <p className="banner">{status}</p>}

      <section className="panel chart-wrap">
        <div className="period-row" ref={legendRow}>
          <p className="period">
            {tab === "history" && selectedWeek
              ? historyPeriod(focus, selectedWeek.id, selectedDay)
              : livePeriod(todayParts)}
          </p>
          <button
            type="button"
            className="legend-button"
            aria-expanded={legendOpen}
            onClick={() => setLegendOpen((open) => !open)}
          >
            <i className="legend-mark" aria-hidden="true" />
            Legend
          </button>
          {legendOpen && (
            <div className="legend-popover" role="dialog" aria-label="Legend">
              <div className="legend">
          <div className="legend-row">
            {layers.solcast && <span style={{ color: "#F5C542" }}>● Solcast</span>}
            {layers.enphase && <span style={{ color: "#2EE6A6" }}>● Enphase</span>}
            {layers.temp && <span style={{ color: "#E6E1F0" }}>● Outdoor</span>}
            <span style={{ color: "rgba(255,255,255,0.60)" }}>● Now</span>
          </div>
          {layers.ecobee && (
            <div className="legend-row">
              <span>
                <i className="swatch hairline" style={{ background: "#FFF176" }} /> Setpoint
              </span>
              {chart.tooWarm && (
                <span>
                  <i className="swatch" style={{ background: "rgba(77,163,255,0.40)" }} /> Too warm
                </span>
              )}
              {chart.tooCool && (
                <span>
                  <i className="swatch" style={{ background: "rgba(255,152,0,0.50)" }} /> Too cool
                </span>
              )}
            </div>
          )}
          {layers.hvac && (runtime.heatPump || runtime.aux || runtime.cool) && (
            <div className="legend-row">
              {runtime.heatPump && (
                <span>
                  <i className="swatch" style={{ background: "#00897B" }} /> Heat pump
                </span>
              )}
              {runtime.aux && (
                <span>
                  <i className="swatch" style={{ background: "#EF5350" }} /> Aux heat
                </span>
              )}
              {runtime.cool && (
                <span>
                  <i className="swatch" style={{ background: "#90CAF9" }} /> Cool
                </span>
              )}
            </div>
          )}
          {(layers.precip || layers.buy || layers.sell) && (
            <div className="legend-row">
              {layers.precip && <span style={{ color: "#4DD0C8" }}>▮ Precip</span>}
              {layers.buy && <span style={{ color: "#E57FD8" }}>▮ Buy</span>}
              {layers.sell && <span style={{ color: "#7BD88F" }}>▮ Sell</span>}
            </div>
          )}
              </div>
            </div>
          )}
        </div>
        <PowerChart
          points={chart.points}
          weather={chart.weather}
          from={chart.fromMs}
          to={chart.toMs}
          now={clock}
          layers={layers}
          sun={chart.sun}
          rates={chart.rates}
          demo={cache?.source === "demo"}
          ecobee={chart.ecobee}
        />
        {layers.hvac && (
          <p className="muted hvac-summary" title="Aux heat is about $0.60 per runtime hour">
            {hvacSummaryLabel(hvacSummary(chart.ecobee, chart.fromMs, chart.toMs, clock))}
          </p>
        )}
        {chart.empty && <p className="muted">No stored intervals in this range yet.</p>}
      </section>

      {tab === "history" && (
        <section className="panel calendar">
          <div className="cal-nav">
            <button type="button" aria-label="Previous month" onClick={() => setMonth(shiftMonth(month.year, month.month, -1))}>
              ‹
            </button>
            <h2>
              <button
                type="button"
                className="month-jump"
                onClick={() => {
                  setMonth({ year: todayParts.year, month: todayParts.month });
                  setFocus("week");
                  setWeekId(null);
                  setDayId(null);
                }}
              >
                {monthName}
              </button>
            </h2>
            <button type="button" aria-label="Next month" onClick={() => setMonth(shiftMonth(month.year, month.month, 1))}>
              ›
            </button>
          </div>
          <div className="cal-grid">
          <div className="cal-dow">
            <span aria-hidden="true" />
            {WEEKDAYS.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
          {weeks.map((week) => {
            const weekOn = focus === "week" && week.id === selectedWeek?.id;
            const weekNumber = usWeekNumber(parseYmd(week.id) ?? { year: month.year, month: month.month, day: 1 });
            return (
              <div key={week.id} className={weekOn ? "cal-week selected" : "cal-week"}>
                <button
                  type="button"
                  className="wk"
                  aria-pressed={weekOn}
                  aria-label={`Week ${weekNumber}`}
                  onClick={() => {
                    setWeekId(week.id);
                    setFocus("week");
                  }}
                >
                  <span className="wk-chip">W{weekNumber}</span>
                </button>
                {week.days.map((day) => {
                  const key = ymdKey(day);
                  const dayOn = focus === "day" && key === ymdKey(selectedDay);
                  const className = ["day", day.inMonth ? "" : "out", key === todayKey ? "today" : "", dayOn ? "selected" : ""]
                    .filter(Boolean)
                    .join(" ");
                  return (
                    <button
                      key={key}
                      type="button"
                      className={className}
                      aria-pressed={dayOn}
                      onClick={() => {
                        setDayId(key);
                        setFocus("day");
                        if (day.year !== month.year || day.month !== month.month) {
                          setMonth({ year: day.year, month: day.month });
                        }
                      }}
                    >
                      {day.day}
                      {key === todayKey && <i className="today-dot" />}
                    </button>
                  );
                })}
              </div>
            );
          })}
          </div>
        </section>
      )}

      {chart.totals.length > 0 && (
        <div className="totals">
          {chart.totals.map((day) => (
            <div key={day.label} className="panel total">
              <div className="muted">{day.label}</div>
              <div className="stat">{day.kwh.toFixed(1)} kWh</div>
              <div className="muted">
                {day.liveKwh.toFixed(1)} live · {day.forecastKwh.toFixed(1)} forecast
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === "live" && (
        <>
          <div className="grid">
            <section className="panel">
              <h2>Snapshot</h2>
              <div className="stat">{cache?.source || "—"}</div>
              <p className="muted">as of {cache?.fetchedAt ? new Date(cache.fetchedAt).toLocaleString() : "never"}</p>
              <p className="muted">resource {cache?.resourceId || "—"}</p>
            </section>
            <section className="panel">
              <h2>Quota (UTC day {cache?.requestsDay || "—"})</h2>
              <div className="stat">{cache?.requestsUsed ?? 0} / 10 requests</div>
              <p className="muted">{cache?.autoFetchesUsed ?? 0} / 8 forecasts · {cache?.actualsFetchesUsed ?? 0} / 2 actuals</p>
              <p className="muted">Forecasts at 6:00am, 8:30am, 11:00am, 1:30pm, 4:00pm, 6:30pm, 9:00pm, and 11:30pm Detroit. Actuals at 6:00am and 6:30pm.</p>
            </section>
          </div>

          <section className="panel rates">
            <h2>Rates</h2>
            <p>{PLAN_CODE} · {PLAN_NAME} · {RIDER}</p>
            <table>
              <thead>
                <tr>
                  <th></th>
                  <th>Off-peak</th>
                  <th>Mid-peak</th>
                  <th>Peak</th>
                </tr>
              </thead>
              <tbody>
                {RATES.map(([label, off, mid, peak]) => (
                  <tr key={label}>
                    <td>{label}</td>
                    <td>{off}</td>
                    <td>{mid}</td>
                    <td>{peak}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="muted">Rates as of {RATES_AS_OF}. Import effective includes PSCR and volumetric surcharges. Excludes the $8.50 service charge and sales tax.</p>
            <p className="muted">
              Export is Rider 18 outflow (power supply only, before then plus PSCR {cents(PSCR_CENTS)}). Not 1:1 retail net metering. DTE rate book Sheet D-115, Case U-21860.
            </p>
          </section>
        </>
      )}
    </main>
  );
}

function chartModel(
  history: HistoryBody | null,
  clock: number,
  focus: { from: Ymd; days: number } | null,
  today: Ymd
) {
  const bounds = focus
    ? span(focus.from, addDays(focus.from, focus.days))
    : span(liveWindow(today).from, liveWindow(today).to);
  const fromMs = Date.parse(bounds.from);
  const toMs = Date.parse(bounds.to);
  const rows = (history?.points ?? []).filter((point) => {
    const t = Date.parse(point.periodEnd);
    return t >= fromMs && t < toMs;
  });
  const points = seriesFromHistory(rows, clock);
  const weather = rows
    .filter((point) => point.tempF != null)
    .map((point) => ({
      t: Date.parse(point.periodEnd),
      tempF: point.tempF as number,
      precipPct: point.precipPct,
    }));
  const ecobee: EcobeeSample[] = (history?.ecobee ?? [])
    .map((sample) => ({
      t: Date.parse(sample.t),
      indoorF: sample.indoorF,
      coolF: sample.coolF,
      heatF: sample.heatF,
      heatPumpSec: sample.heatPumpSec ?? null,
      auxHeatSec: sample.auxHeatSec ?? null,
      coolSec: sample.coolSec ?? null,
      fanSec: sample.fanSec ?? null,
      outdoorF: sample.outdoorF ?? null,
    }))
    .filter((sample) => Number.isFinite(sample.t) && sample.t >= fromMs && sample.t < toMs);
  const sun: SunSpan[] = (history?.sun ?? [])
    .map((day) => ({ sunrise: Date.parse(day.sunrise), sunset: Date.parse(day.sunset) }))
    .filter((day) => day.sunset >= fromMs && day.sunrise < toMs);
  const start = focus ? focus.from : liveWindow(today).from;
  const totalDays = focus ? focus.days : 7;
  let tooWarm = false;
  let tooCool = false;
  for (const sample of ecobee) {
    if (sample.t > clock) continue;
    if (sample.coolF != null && sample.indoorF > sample.coolF) tooWarm = true;
    else if (sample.heatF != null && sample.indoorF < sample.heatF) tooCool = true;
  }
  return {
    points,
    weather,
    sun,
    tooWarm,
    tooCool,
    fromMs,
    toMs,
    rates: rows,
    ecobee,
    totals: filledTotals(start ?? today, totalDays, points, clock),
    empty: history != null && rows.length === 0 && !history.error,
  };
}

function filledTotals(start: Ymd, count: number, points: SeriesPoint[], nowMs: number) {
  const byLabel = new Map(dayTotals(points, nowMs).map((row) => [row.label, row]));
  return Array.from({ length: count }, (_, index) => {
    const label = ymdKey(addDays(start, index)).slice(5);
    return byLabel.get(label) ?? { label, kwh: 0, liveKwh: 0, forecastKwh: 0 };
  });
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function historyPeriod(focus: HistoryFocus, weekId: string, day: Ymd): string {
  if (focus === "day") {
    const noon = zonedTimeToUtc(day.year, day.month, day.day, 12);
    const parts = detroitParts(noon);
    return `${parts.weekday}, ${MONTHS[day.month - 1]} ${day.day}`;
  }
  const start = parseYmd(weekId) ?? day;
  return periodRange(start, addDays(start, 6));
}

function span(from: Ymd, to: Ymd): { from: string; to: string } {
  return {
    from: zonedTimeToUtc(from.year, from.month, from.day).toISOString(),
    to: zonedTimeToUtc(to.year, to.month, to.day).toISOString(),
  };
}

function Chip({ label, on, onClick }: { label: string; on: boolean; onClick: () => void }) {
  return (
    <button type="button" className={on ? "chip on" : "chip"} onClick={onClick}>
      {label}
    </button>
  );
}

/** A normal Solcast slot stores this note. It is not a problem to show on the chart. */
function isRoutinePullNote(message: string): boolean {
  return /^Scheduled (forecast and actuals|forecast|actuals) pull\.$/.test(message);
}

function cents(value: number): string {
  return `${value.toFixed(2)}¢`;
}
