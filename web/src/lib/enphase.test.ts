import { describe, expect, it } from "vitest";
import { halfHourActuals, planMeterHarvest, readTelemetryIntervals, HARVEST_AGE_MS } from "./enphase";

const noon = Date.parse("2026-06-17T15:30:00Z");

describe("halfHourActuals", () => {
  it("averages two 15-minute meter bins into the half-hour", () => {
    const points = halfHourActuals(
      [
        { endAt: noon / 1000 - 900, wh: 100 },
        { endAt: noon / 1000, wh: 200 },
      ],
      noon + 60_000
    );
    expect(points).toEqual([{ periodEnd: "2026-06-17T15:30:00.000Z", actualKw: 0.6 }]);
  });

  it("averages six 5-minute micro bins", () => {
    const intervals = [300, 600, 900, 1200, 1500, 1800].map((seconds) => ({
      endAt: noon / 1000 - 1800 + seconds,
      wh: 50,
    }));
    const points = halfHourActuals(intervals, noon + 60_000);
    expect(points).toEqual([{ periodEnd: "2026-06-17T15:30:00.000Z", actualKw: 0.6 }]);
  });

  it("keeps a finished half-hour of zeros", () => {
    const points = halfHourActuals(
      [
        { endAt: noon / 1000 - 900, wh: 0 },
        { endAt: noon / 1000, wh: 0 },
      ],
      noon + 60_000
    );
    expect(points[0]?.actualKw).toBe(0);
  });

  it("skips a half-hour that is still open or short a bin", () => {
    const open = halfHourActuals(
      [
        { endAt: noon / 1000 - 900, wh: 100 },
        { endAt: noon / 1000, wh: 200 },
      ],
      noon - 60_000
    );
    const short = halfHourActuals([{ endAt: noon / 1000, wh: 200 }], noon + 60_000);
    expect(open).toEqual([]);
    expect(short).toEqual([]);
  });
});

describe("readTelemetryIntervals", () => {
  it("reads watt-hours delivered", () => {
    expect(readTelemetryIntervals({
      intervals: [{ end_at: 100, wh_del: 12, devices_reporting: 1 }, { end_at: "nope" }],
    })).toEqual([{ endAt: 100, wh: 12 }]);
  });
});

describe("planMeterHarvest", () => {
  it("pulls when connected and the last read is an hour old", () => {
    expect(planMeterHarvest(null, noon, true)).toBe(true);
    expect(planMeterHarvest(noon - HARVEST_AGE_MS + 1, noon, true)).toBe(false);
    expect(planMeterHarvest(noon - HARVEST_AGE_MS, noon, true)).toBe(true);
    expect(planMeterHarvest(null, noon, false)).toBe(false);
  });
});
