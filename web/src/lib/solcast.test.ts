import { describe, expect, it } from "vitest";
import type { SnapshotBody } from "./demo";
import { needsSolcastPull } from "./solcast";

const now = new Date("2026-10-01T04:50:00Z");

function snapshot(source: string, fetchedAt: string): SnapshotBody {
  return {
    resourceId: "84d7-8b52-33f3-bd7b",
    fetchedAt,
    source,
    forecasts: { forecasts: [] },
    actuals: { estimated_actuals: [] },
    requestsDay: "2026-10-01",
    requestsUsed: 0,
    autoFetchesUsed: 1,
    message: null,
    weatherFetchedAt: null,
  };
}

describe("needsSolcastPull", () => {
  it("replaces a recent demo snapshot when the key is set", () => {
    const demo = snapshot("demo", "2026-10-01T02:16:12.379Z");
    expect(needsSolcastPull(demo, now, "present")).toBe(true);
    expect(needsSolcastPull(demo, now, undefined)).toBe(false);
  });

  it("keeps a recent Solcast snapshot", () => {
    const live = snapshot("solcast", "2026-10-01T04:00:00Z");
    expect(needsSolcastPull(live, now, "present")).toBe(false);
  });

  it("pulls when the Solcast snapshot is at least 4 hours old", () => {
    const live = snapshot("solcast", "2026-10-01T00:00:00Z");
    expect(needsSolcastPull(live, now, "present")).toBe(true);
  });
});
