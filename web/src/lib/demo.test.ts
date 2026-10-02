import { describe, expect, it } from "vitest";
import { demoActualPeriods, demoSnapshot, matchesDemoActual } from "./demo";
import { withoutDemoActuals } from "./store";
import { detroitParts, ymdKey } from "./time";

describe("sample curve", () => {
  it("matches the actuals that were stored from the October 1 sample snapshot", () => {
    expect(matchesDemoActual("2026-09-29T14:30:00.000Z", 5.261)).toBe(true);
    expect(matchesDemoActual("2026-10-01T18:00:00.000Z", 3.446)).toBe(true);
    expect(matchesDemoActual("2026-09-28T16:00:00.000Z", 5.3)).toBe(true);
    expect(matchesDemoActual("2026-10-01T16:00:00.000Z", 0.8364)).toBe(false);
    expect(matchesDemoActual("2026-10-01T08:00:00.000Z", 0)).toBe(false);
  });

  it("drops a day only when every positive actual is the sample curve", () => {
    const demo = demoSnapshot("resource", new Date("2026-10-01T18:00:00.000Z"));
    const points = demo.actuals.estimated_actuals.map((row) => ({
      periodEnd: row.period_end,
      actualKw: row.pv_estimate,
    }));
    expect(demoActualPeriods(points)).toHaveLength(points.length);

    const noon = points.find((point) => point.periodEnd.startsWith("2026-10-01T16:00"));
    expect(noon).toBeTruthy();
    const polluted = points.map((point) =>
      point === noon ? { ...point, actualKw: 0.8364 } : point
    );
    const kept = new Set(demoActualPeriods(polluted));
    const noonDay = ymdKey(detroitParts(new Date("2026-10-01T16:00:00.000Z")));
    const noonDayKept = polluted.filter(
      (point) => ymdKey(detroitParts(new Date(point.periodEnd))) === noonDay && kept.has(point.periodEnd)
    );
    expect(noonDayKept).toHaveLength(0);
    expect(kept.size).toBeGreaterThan(0);

    const cleaned = withoutDemoActuals({ ...demo, source: "solcast" });
    expect(cleaned.actuals.estimated_actuals).toHaveLength(0);
    expect(withoutDemoActuals(cleaned)).toBe(cleaned);
  });
});
