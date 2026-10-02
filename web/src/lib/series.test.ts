import { describe, expect, it } from "vitest";
import { seriesFromHistory } from "./series";

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
      ["live", 3.2],
      ["forecast", 6.1318],
    ]);
  });
});
