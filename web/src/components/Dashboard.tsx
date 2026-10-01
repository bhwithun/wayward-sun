"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { PowerChart, type ChartLayers } from "@/components/PowerChart";
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
  liveWindow,
  monthWeeks,
  parseYmd,
  shiftMonth,
  ymdKey,
  zonedTimeToUtc,
  type CalendarWeek,
  type Ymd,
} from "@/lib/time";

const LAYER_KEY = "wayward-sun-layers";
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

const DEFAULT_LAYERS: ChartLayers = {
  solar: true,
  temp: true,
  precip: true,
  buy: true,
  sell: true,
};

function readLayers(): ChartLayers {
  try {
    const raw = localStorage.getItem(LAYER_KEY);
    if (!raw) return DEFAULT_LAYERS;
    const parsed = JSON.parse(raw) as Partial<ChartLayers>;
    return {
      solar: parsed.solar !== false,
      temp: parsed.temp !== false,
      precip: parsed.precip !== false,
      buy: parsed.buy !== false,
      sell: parsed.sell !== false,
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

type HistoryBody = {
  points?: HistoryPoint[];
  sun?: Array<{ date: string; sunrise: string; sunset: string }>;
  error?: string;
};

type Tab = "live" | "history";

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
    else if (cacheBody.message) setStatus(cacheBody.message);
  }, [range]);

  useEffect(() => {
    void load();
  }, [load]);

  const chart = chartModel(history, clock, tab === "history" ? selectedWeek : null, todayParts);
  const monthName = MONTH_LABEL.format(new Date(Date.UTC(month.year, month.month - 1, 1)));

  return (
    <main>
      <h1>Wayward Sun</h1>
      <p className="muted">
        Shared Solcast cache on Vercel. Phones download <code>GET /cache</code>. Solcast itself runs only on the Detroit schedule.
      </p>

      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "live"} className={tab === "live" ? "on" : ""} onClick={() => setTab("live")}>
          Live
        </button>
        <button type="button" role="tab" aria-selected={tab === "history"} className={tab === "history" ? "on" : ""} onClick={() => setTab("history")}>
          History
        </button>
      </div>

      <div className="toolbar">
        <Chip label="Solar" on={layers.solar} onClick={() => setLayers({ ...layers, solar: !layers.solar })} />
        <Chip label="Temp" on={layers.temp} onClick={() => setLayers({ ...layers, temp: !layers.temp })} />
        <Chip label="Precip" on={layers.precip} onClick={() => setLayers({ ...layers, precip: !layers.precip })} />
        <Chip label="Buy" on={layers.buy} onClick={() => setLayers({ ...layers, buy: !layers.buy })} />
        <Chip label="Sell" on={layers.sell} onClick={() => setLayers({ ...layers, sell: !layers.sell })} />
      </div>

      {status && <p className="banner">{status}</p>}

      {tab === "history" && (
        <section className="panel calendar">
          <div className="cal-nav">
            <button type="button" aria-label="Previous month" onClick={() => setMonth(shiftMonth(month.year, month.month, -1))}>
              ‹
            </button>
            <h2>{monthName}</h2>
            <button type="button" aria-label="Next month" onClick={() => setMonth(shiftMonth(month.year, month.month, 1))}>
              ›
            </button>
          </div>
          <div className="cal-dow">
            {WEEKDAYS.map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
          {weeks.map((week) => (
            <button
              key={week.id}
              type="button"
              className={week.id === selectedWeek?.id ? "week selected" : "week"}
              aria-pressed={week.id === selectedWeek?.id}
              onClick={() => setWeekId(week.id)}
            >
              {week.days.map((day) => {
                const key = ymdKey(day);
                const className = [day.inMonth ? "" : "out", key === todayKey ? "today" : ""].filter(Boolean).join(" ");
                return (
                  <span key={key} className={className}>
                    {day.day}
                  </span>
                );
              })}
            </button>
          ))}
        </section>
      )}

      <section className="panel chart-wrap">
        <div className="legend">
          <span style={{ color: "#2EE6A6" }}>● Live</span>
          <span style={{ color: "#F5C542" }}>● Forecast</span>
          <span style={{ color: "#FF8A4C" }}>● Now</span>
          {layers.buy && <span style={{ color: "#C9898C" }}>▮ Buy</span>}
          {layers.sell && <span style={{ color: "#8AA4C4" }}>▮ Sell</span>}
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
        />
        {chart.empty && <p className="muted">No stored intervals in this range yet.</p>}
      </section>

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
  week: CalendarWeek | null,
  today: Ymd
) {
  const bounds = week
    ? span(parseYmd(week.id) ?? { year: today.year, month: today.month, day: today.day }, addDays(parseYmd(week.id) ?? today, 7))
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
  const sun: SunSpan[] = (history?.sun ?? [])
    .map((day) => ({ sunrise: Date.parse(day.sunrise), sunset: Date.parse(day.sunset) }))
    .filter((day) => day.sunset >= fromMs && day.sunrise < toMs);
  const start = week ? parseYmd(week.id) : liveWindow(today).from;
  return {
    points,
    weather,
    sun,
    fromMs,
    toMs,
    rates: rows,
    totals: filledTotals(start ?? today, 7, points),
    empty: history != null && rows.length === 0 && !history.error,
  };
}

function filledTotals(start: Ymd, count: number, points: SeriesPoint[]) {
  const byLabel = new Map(dayTotals(points).map((row) => [row.label, row]));
  return Array.from({ length: count }, (_, index) => {
    const label = ymdKey(addDays(start, index)).slice(5);
    return byLabel.get(label) ?? { label, kwh: 0, liveKwh: 0, forecastKwh: 0 };
  });
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

function cents(value: number): string {
  return `${value.toFixed(2)}¢`;
}
