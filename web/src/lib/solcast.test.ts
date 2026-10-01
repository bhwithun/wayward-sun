import { describe, expect, it } from "vitest";
import type { SnapshotBody } from "./demo";
import { planSolcastCalls, type PullPlan } from "./solcast";
import { zonedTimeToUtc } from "./time";

const emptyForecasts = { forecasts: [] };
const emptyActuals = { estimated_actuals: [] };

function snapshot(overrides: Partial<SnapshotBody> = {}): SnapshotBody {
  return {
    resourceId: "84d7-8b52-33f3-bd7b",
    fetchedAt: "2026-07-15T10:00:00.000Z",
    source: "solcast",
    forecasts: emptyForecasts,
    actuals: emptyActuals,
    requestsDay: "2026-07-15",
    requestsUsed: 0,
    autoFetchesUsed: 0,
    actualsFetchesUsed: 0,
    forecastSlot: null,
    actualsSlot: null,
    actualsFetchedAt: null,
    message: null,
    weatherFetchedAt: null,
    ...overrides,
  };
}

function at(year: number, month: number, day: number, hour: number, minute: number): Date {
  return zonedTimeToUtc(year, month, day, hour, minute);
}

function calls(plan: PullPlan): string {
  return `${plan.forecast ? "forecast" : ""}${plan.actuals ? " actuals" : ""}`.trim() || "none";
}

describe("planSolcastCalls", () => {
  it("pulls a forecast on each awake-hour slot in daylight time", () => {
    const slots = [
      [6, 0, "forecast actuals"],
      [8, 30, "forecast"],
      [11, 0, "forecast"],
      [13, 30, "forecast"],
      [16, 0, "forecast"],
      [18, 30, "forecast actuals"],
      [21, 0, "forecast"],
      [23, 30, "forecast"],
    ] as const;
    for (const [hour, minute, expected] of slots) {
      const plan = planSolcastCalls(snapshot(), at(2026, 7, 15, hour, minute), "present");
      expect(calls(plan), `${hour}:${minute}`).toBe(expected);
    }
  });

  it("uses the same wall-clock slots in standard time", () => {
    const morning = planSolcastCalls(snapshot({ requestsDay: "2026-01-15" }), at(2026, 1, 15, 6, 0), "present");
    const evening = planSolcastCalls(snapshot({ requestsDay: "2026-01-15" }), at(2026, 1, 15, 18, 30), "present");
    const between = planSolcastCalls(snapshot({ requestsDay: "2026-01-15" }), at(2026, 1, 15, 7, 30), "present");
    expect(calls(morning)).toBe("forecast actuals");
    expect(calls(evening)).toBe("forecast actuals");
    expect(calls(between)).toBe("none");
  });

  it("does not pull between slots", () => {
    const plan = planSolcastCalls(snapshot(), at(2026, 7, 15, 7, 0), "present");
    expect(plan.forecast).toBe(false);
    expect(plan.actuals).toBe(false);
    expect(plan.message).toBeNull();
  });

  it("still accepts a slot a few minutes late", () => {
    const plan = planSolcastCalls(snapshot(), at(2026, 7, 15, 6, 10), "present");
    expect(calls(plan)).toBe("forecast actuals");
  });

  it("does not repeat a slot that already succeeded", () => {
    const done = snapshot({
      forecastSlot: "2026-07-15T06:00",
      actualsSlot: "2026-07-15T06:00",
      requestsUsed: 2,
      autoFetchesUsed: 1,
      actualsFetchesUsed: 1,
    });
    const plan = planSolcastCalls(done, at(2026, 7, 15, 6, 5), "present");
    expect(calls(plan)).toBe("none");
  });

  it("retries actuals when the forecast for that slot is already stored", () => {
    const partial = snapshot({
      forecastSlot: "2026-07-15T06:00",
      requestsUsed: 1,
      autoFetchesUsed: 1,
    });
    const plan = planSolcastCalls(partial, at(2026, 7, 15, 6, 2), "present");
    expect(plan.forecast).toBe(false);
    expect(plan.actuals).toBe(true);
  });

  it("stops at 8 forecasts, 2 actuals, and 10 HTTP requests", () => {
    const cappedForecasts = snapshot({ requestsUsed: 8, autoFetchesUsed: 8 });
    expect(planSolcastCalls(cappedForecasts, at(2026, 7, 15, 8, 30), "present").forecast).toBe(false);

    const cappedActuals = snapshot({
      requestsUsed: 8,
      autoFetchesUsed: 6,
      actualsFetchesUsed: 2,
      forecastSlot: "2026-07-15T06:00",
    });
    const evening = planSolcastCalls(cappedActuals, at(2026, 7, 15, 18, 30), "present");
    expect(evening.forecast).toBe(true);
    expect(evening.actuals).toBe(false);

    const lastRequest = snapshot({ requestsUsed: 9, autoFetchesUsed: 7, actualsFetchesUsed: 1 });
    const both = planSolcastCalls(lastRequest, at(2026, 7, 15, 18, 30), "present");
    expect(both.forecast).toBe(true);
    expect(both.actuals).toBe(false);

    const full = snapshot({ requestsUsed: 10, autoFetchesUsed: 8, actualsFetchesUsed: 2 });
    const blocked = planSolcastCalls(full, at(2026, 7, 15, 16, 0), "present");
    expect(blocked.forecast).toBe(false);
    expect(blocked.actuals).toBe(false);
    expect(blocked.message).toMatch(/quota/i);
  });

  it("replaces a demo snapshot on the next slot when the key is set", () => {
    const demo = snapshot({ source: "demo", fetchedAt: "2026-07-15T13:00:00.000Z" });
    expect(planSolcastCalls(demo, at(2026, 7, 15, 11, 0), "present").forecast).toBe(true);
    expect(planSolcastCalls(demo, at(2026, 7, 15, 11, 0), undefined).forecast).toBe(false);
  });
});
