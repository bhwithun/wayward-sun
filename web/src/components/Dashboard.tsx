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
import { dayTotals, seriesFromHistory, type SunSpan } from "@/lib/series";
import { addDays, detroitParts, displayRange, ymdKey, zonedTimeToUtc } from "@/lib/time";

type CacheBody = {
  resourceId?: string;
  fetchedAt?: string | null;
  source?: string;
  requestsDay?: string;
  requestsUsed?: number;
  autoFetchesUsed?: number;
  message?: string | null;
  error?: string;
};

type HistoryBody = {
  points?: Array<{
    periodEnd: string;
    actualKw: number | null;
    forecastKw: number | null;
    periodHours: number | null;
    tempF: number | null;
    precipPct: number | null;
  }>;
  sun?: Array<{ date: string; sunrise: string; sunset: string }>;
  error?: string;
};

type RangeMode = "window" | "7" | "30" | "90" | "custom";

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

export function Dashboard() {
  const [layers, setLayers] = useState<ChartLayers>({
    solar: true,
    temp: true,
    precip: true,
    rates: true,
  });
  const [mode, setMode] = useState<RangeMode>("window");
  const [customFrom, setCustomFrom] = useState("");
  const [customTo, setCustomTo] = useState("");
  const [cache, setCache] = useState<CacheBody | null>(null);
  const [history, setHistory] = useState<HistoryBody | null>(null);
  const [status, setStatus] = useState("");
  const [clock, setClock] = useState(() => Date.now());
  const today = ymdKey(detroitParts(new Date(clock)));

  useEffect(() => {
    const id = setInterval(() => setClock(Date.now()), 60_000);
    return () => clearInterval(id);
  }, []);

  const range = useMemo(
    () => resolveRange(mode, today, customFrom, customTo),
    [mode, today, customFrom, customTo]
  );

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

  async function askUpdate() {
    setStatus("Working…");
    const res = await fetch("/refresh", { method: "POST" });
    const body = (await res.json()) as CacheBody;
    if (!res.ok) {
      setStatus(body.error || `HTTP ${res.status}`);
      return;
    }
    setCache(body);
    setStatus(body.message || "Updated.");
    await load();
  }

  const points = seriesFromHistory(history?.points ?? [], clock);
  const weather = (history?.points ?? [])
    .filter((point) => point.tempF != null)
    .map((point) => ({
      t: Date.parse(point.periodEnd),
      tempF: point.tempF as number,
      precipPct: point.precipPct,
    }));
  const sun: SunSpan[] = (history?.sun ?? []).map((day) => ({
    sunrise: Date.parse(day.sunrise),
    sunset: Date.parse(day.sunset),
  }));
  const totals = mode === "window" ? dayTotals(points) : [];
  const fromMs = range ? Date.parse(range.from) : clock;
  const toMs = range ? Date.parse(range.to) : clock;

  return (
    <main>
      <h1>Wayward Sun</h1>
      <p className="muted">
        Shared Solcast cache on Vercel. Phones download <code>GET /cache</code>. This site is the only Solcast client.
      </p>

      <div className="toolbar">
        <RangeButton active={mode === "window"} onClick={() => setMode("window")}>5-day</RangeButton>
        <RangeButton active={mode === "7"} onClick={() => setMode("7")}>7 days</RangeButton>
        <RangeButton active={mode === "30"} onClick={() => setMode("30")}>30 days</RangeButton>
        <RangeButton active={mode === "90"} onClick={() => setMode("90")}>90 days</RangeButton>
        <RangeButton active={mode === "custom"} onClick={() => setMode("custom")}>Custom</RangeButton>
      </div>
      {mode === "custom" && (
        <div className="toolbar">
          <label>
            From <input type="date" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} />
          </label>
          <label>
            To <input type="date" value={customTo} onChange={(e) => setCustomTo(e.target.value)} />
          </label>
        </div>
      )}

      <div className="toolbar">
        <Chip label="Solar" on={layers.solar} onClick={() => setLayers({ ...layers, solar: !layers.solar })} />
        <Chip label="Temp" on={layers.temp} onClick={() => setLayers({ ...layers, temp: !layers.temp })} />
        <Chip label="Precip" on={layers.precip} onClick={() => setLayers({ ...layers, precip: !layers.precip })} />
        <Chip label="Rates" on={layers.rates} onClick={() => setLayers({ ...layers, rates: !layers.rates })} />
      </div>

      {status && <p className="banner">{status}</p>}

      <section className="panel chart-wrap">
        <div className="legend">
          <span style={{ color: "#2EE6A6" }}>● Live</span>
          <span style={{ color: "#F5C542" }}>● Forecast</span>
          <span style={{ color: "#FF8A4C" }}>● Now</span>
        </div>
        <PowerChart
          points={points}
          weather={weather}
          from={fromMs}
          to={toMs}
          now={clock}
          layers={layers}
          sun={sun}
          demo={cache?.source === "demo"}
        />
        {history?.points?.length === 0 && !history.error && (
          <p className="muted">No stored intervals in this range yet. Ask for an update after the database is migrated.</p>
        )}
      </section>

      {totals.length > 0 && (
        <div className="totals">
          {totals.map((day) => (
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
          <p className="muted">{cache?.autoFetchesUsed ?? 0} / 5 automatic pulls</p>
          <p className="muted">Solcast runs only when this snapshot is older than 4 hours.</p>
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

      <div className="toolbar">
        <button className="gold" type="button" onClick={() => void askUpdate()}>Ask for update</button>
      </div>
    </main>
  );
}

function RangeButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: string;
}) {
  return (
    <button type="button" className={active ? "on" : ""} onClick={onClick}>
      {children}
    </button>
  );
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

function resolveRange(
  mode: RangeMode,
  todayKey: string,
  customFrom: string,
  customTo: string
): { from: string; to: string } | null {
  const [year, month, day] = todayKey.split("-").map(Number);
  const today = { year, month, day };
  if (mode === "custom") {
    if (!customFrom || !customTo) return null;
    const [fy, fm, fd] = customFrom.split("-").map(Number);
    const [ty, tm, td] = customTo.split("-").map(Number);
    if (!fy || !ty) return null;
    const end = addDays({ year: ty, month: tm, day: td }, 1);
    return {
      from: zonedTimeToUtc(fy, fm, fd).toISOString(),
      to: zonedTimeToUtc(end.year, end.month, end.day).toISOString(),
    };
  }
  if (mode === "window") {
    const window = displayRange(zonedTimeToUtc(year, month, day, 12, 0));
    return { from: window.from.toISOString(), to: window.to.toISOString() };
  }
  const days = Number(mode);
  const start = addDays(today, -(days - 1));
  const end = addDays(today, 3);
  return {
    from: zonedTimeToUtc(start.year, start.month, start.day).toISOString(),
    to: zonedTimeToUtc(end.year, end.month, end.day).toISOString(),
  };
}
