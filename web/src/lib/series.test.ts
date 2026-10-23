import { describe, expect, it } from "vitest";
import { dayTotals, forecastStrokes, seriesFromHistory } from "./series";

const now = Date.parse("2026-10-02T20:00:00.000Z");

describe("seriesFromHistory", () => {
  it("keeps a past forecast when no estimated actual has been stored", () => {
    const points = seriesFromHistory(
      [
        { periodEnd: "2026-10-02T16:00:00.000Z", actualKw: null, forecastKw: 1.6826, periodHours: 0.5 },
        { periodEnd: "2026-10-02T18:00:00.000Z", actualKw: 3.2, forecastKw: 2.5, periodHours: 0.5 },
        { periodEnd: "2026-10-03T16:00:00.000Z", actualKw: null, forecastKw: 6.1318, periodHours: 0.5 },
      ],
      now
    );
    expect(points.map((point) => [point.kind, point.kw])).toEqual([
      ["live", 1.6826],
      ["forecast", 1.6826],
      ["live", 3.2],
      ["forecast", 2.5],
      ["forecast", 6.1318],
    ]);
  });

  it("counts a past prediction beside the measured day, not twice", () => {
    const points = seriesFromHistory(
      [{ periodEnd: "2026-10-02T18:00:00.000Z", actualKw: 3.2, forecastKw: 2.5, periodHours: 0.5 }],
      now
    );
    const [day] = dayTotals(points, now);
    expect(day.kwh).toBeCloseTo(1.6);
    expect(day.liveKwh).toBeCloseTo(1.6);
    expect(day.forecastKwh).toBeCloseTo(1.25);
  });
});

describe("forecastStrokes", () => {
  it("keeps the seam point on the solid future stroke", () => {
    const strokes = forecastStrokes(
      [
        { t: Date.parse("2026-10-02T18:00:00.000Z"), kw: 2.5, kind: "forecast", hours: 0.5 },
        { t: Date.parse("2026-10-02T19:00:00.000Z"), kw: 2.0, kind: "forecast", hours: 0.5 },
        { t: Date.parse("2026-10-02T19:30:00.000Z"), kw: 1.4, kind: "forecast", hours: 0.5 },
        { t: Date.parse("2026-10-02T18:00:00.000Z"), kw: 3.2, kind: "live", hours: 0.5 },
      ],
      Date.parse("2026-10-02T19:00:00.000Z")
    );
    expect(strokes.past.map((point) => point.kw)).toEqual([2.5, 2.0]);
    expect(strokes.future.map((point) => point.kw)).toEqual([2.0, 1.4]);
  });

  it("does not bridge a hole where the forecast was left off the gold series", () => {
    const strokes = forecastStrokes(
      [
        { t: Date.parse("2026-10-02T19:00:00.000Z"), kw: 5.0, kind: "forecast", hours: 0.5 },
        { t: Date.parse("2026-10-03T14:30:00.000Z"), kw: 1.6, kind: "forecast", hours: 0.5 },
      ],
      now
    );
    expect(strokes.past.map((point) => point.kw)).toEqual([5.0]);
    expect(strokes.future.map((point) => point.kw)).toEqual([1.6]);
  });
});
