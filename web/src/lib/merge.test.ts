import { describe, expect, it } from "vitest";
import { draftsFromSnapshot } from "./drafts";
import { mergeInterval, type IntervalDraft } from "./merge";
import { weatherHourKey } from "./time";

const now = Date.parse("2026-06-17T16:00:00Z");

function row(periodEnd: string, kw: number): IntervalDraft {
  return {
    periodEnd,
    actualKw: kw,
    forecastKw: null,
    periodHours: 0.5,
    tempF: null,
    precipPct: null,
    rateBand: "PEAK",
    importCents: 30.15,
    outflowCents: 19.075,
  };
}

describe("interval merge", () => {
  it("keeps a stored forecast when an actual arrives", () => {
    const existing = { ...row("2026-06-17T15:00:00Z", 1), actualKw: null, forecastKw: 3.5 };
    const incoming = row("2026-06-17T15:00:00Z", 3.1);
    const merged = mergeInterval(existing, incoming, now);
    expect(merged.actualKw).toBe(3.1);
    expect(merged.forecastKw).toBe(3.5);
  });

  it("replaces the forecast while the period is still in the future", () => {
    const existing = { ...row("2026-06-17T18:00:00Z", 1), actualKw: null, forecastKw: 2 };
    const incoming = { ...row("2026-06-17T18:00:00Z", 4), actualKw: null, forecastKw: 4 };
    expect(mergeInterval(existing, incoming, now).forecastKw).toBe(4);
  });

  it("does not replace a past forecast", () => {
    const existing = { ...row("2026-06-17T15:00:00Z", 1), actualKw: 1, forecastKw: 2 };
    const incoming = { ...row("2026-06-17T15:00:00Z", 1), actualKw: null, forecastKw: 9 };
    expect(mergeInterval(existing, incoming, now).forecastKw).toBe(2);
  });

  it("keeps the first rate cents", () => {
    const existing = row("2026-06-17T15:00:00Z", 1);
    const incoming = { ...row("2026-06-17T15:00:00Z", 2), importCents: 1, outflowCents: 1, rateBand: "OFF_PEAK" };
    const merged = mergeInterval(existing, incoming, now);
    expect(merged.importCents).toBe(30.15);
    expect(merged.outflowCents).toBe(19.075);
    expect(merged.rateBand).toBe("PEAK");
  });
});

describe("drafts", () => {
  it("copies an Open-Meteo hour onto the half-hours inside it", () => {
    const end = "2026-06-21T18:30:00.000Z";
    const hour = weatherHourKey(new Date(end));
    const drafts = draftsFromSnapshot(
      [{ pv_estimate: 2, period_end: end, period: "PT30M" }],
      [{ pv_estimate: 3, period_end: "2026-06-21T20:00:00.000Z", period: "PT30M" }],
      [{ key: hour, tempF: 70, precipPct: 20 }]
    );
    const past = drafts.find((d) => d.periodEnd.startsWith("2026-06-21T18:30"));
    expect(past?.actualKw).toBe(2);
    expect(past?.tempF).toBe(70);
    expect(past?.precipPct).toBe(20);
    const future = drafts.find((d) => d.forecastKw === 3);
    expect(future?.actualKw).toBeNull();
  });

  it("keeps precipitation for an hour that has no solar period", () => {
    const drafts = draftsFromSnapshot([], [], [
      { key: "2026-06-21T14", tempF: 72, precipPct: 40 },
    ]);
    expect(drafts).toHaveLength(2);
    expect(drafts.every((d) => d.actualKw === null && d.forecastKw === null)).toBe(true);
    expect(drafts.every((d) => d.precipPct === 40 && d.tempF === 72)).toBe(true);
    expect(drafts.map((d) => d.periodEnd)).toEqual([
      "2026-06-21T18:30:00.000Z",
      "2026-06-21T19:00:00.000Z",
    ]);
  });
});
