import { describe, expect, it } from "vitest";
import { housePointsFromRows } from "./store";
import {
  detroitDayBounds,
  homeKwFromWh,
  houseChipVisible,
  houseStepRuns,
  houseSummary,
  houseSummaryLabel,
  isMissingHouseTable,
  kwhFromWh,
  type HouseDay,
  type HouseSample,
} from "./house";

const DETROIT_DAY_FROM = Date.parse("2026-11-11T05:00:00.000Z");
const DETROIT_DAY_TO = Date.parse("2026-11-12T05:00:00.000Z");
const NOON = Date.parse("2026-11-11T17:00:00.000Z");

describe("house watt-hours", () => {
  it("converts a 30-minute bucket to average kW", () => {
    expect(homeKwFromWh(500)).toBe(1);
    expect(homeKwFromWh(2500)).toBe(5);
    expect(kwhFromWh(18400)).toBeCloseTo(18.4);
  });

  it("drops an incomplete bucket", () => {
    const rows = housePointsFromRows([
      { period_end: "2026-11-11T17:00:00.000Z", home_wh: 500, complete: true },
      { period_end: "2026-11-11T17:30:00.000Z", home_wh: 2500, complete: false },
    ]);
    expect(rows).toEqual([{ t: "2026-11-11T17:00:00.000Z", homeKw: 1 }]);
  });

  it("bounds an exclusive Detroit day", () => {
    expect(detroitDayBounds("2026-11-11T05:00:00.000Z", "2026-11-12T05:00:00.000Z")).toEqual({
      fromDay: "2026-11-11",
      toDay: "2026-11-11",
    });
  });
});

describe("house step line", () => {
  const first = DETROIT_DAY_FROM + 12 * 60 * 60 * 1000;
  const second = first + 30 * 60 * 1000;
  const afterGap = second + 60 * 60 * 1000;

  it("steps adjacent buckets and stops at the last reading", () => {
    const runs = houseStepRuns([
      { t: second, kw: 3 },
      { t: first, kw: 2 },
    ]);
    expect(runs).toHaveLength(1);
    expect(runs[0].map((vertex) => vertex.t)).toEqual([first - 30 * 60 * 1000, first, first, second]);
    expect(runs[0].map((vertex) => vertex.kw)).toEqual([2, 2, 3, 3]);
    expect(Math.max(...runs[0].map((vertex) => vertex.t))).toBe(second);
    expect(runs[0].some((vertex) => vertex.t > second)).toBe(false);
  });

  it("does not draw across a gap or past a single reading", () => {
    const runs = houseStepRuns([
      { t: first, kw: 2 },
      { t: afterGap, kw: 4 },
    ]);
    expect(runs).toHaveLength(2);
    expect(runs[0]).toEqual([
      { t: first - 30 * 60 * 1000, kw: 2 },
      { t: first, kw: 2 },
    ]);
    expect(runs[1][0].t).toBe(afterGap - 30 * 60 * 1000);
    expect(runs[1][runs[1].length - 1].t).toBe(afterGap);
    expect(runs[0].some((vertex) => vertex.t === runs[1][0].t)).toBe(false);
  });
});

describe("house summary", () => {
  const day = (hasHalfHour: boolean, homeKwh = 18.4, fromSolarKwh = 11.408): HouseDay => ({
    day: "2026-11-11",
    homeKwh,
    fromSolarKwh,
    hasHalfHour,
  });

  it("shows the daily total without a line when half-hours are missing", () => {
    const summary = houseSummary([], [day(false)], DETROIT_DAY_FROM, DETROIT_DAY_TO, NOON, true);
    expect(summary).toMatchObject({
      homeKwh: 18.4,
      coveredPercent: null,
      hourlyDetailUnavailable: true,
    });
    expect(houseSummaryLabel(summary!)).toBe("House 18.4 kWh");
    expect(houseChipVisible([], [day(false)], DETROIT_DAY_FROM, DETROIT_DAY_TO)).toBe(true);
  });

  it("reports solar covered from from_solar over home", () => {
    const summary = houseSummary([], [day(true)], DETROIT_DAY_FROM, DETROIT_DAY_TO, NOON, true);
    expect(summary?.coveredPercent).toBe(62);
    expect(houseSummaryLabel(summary!)).toBe("House 18.4 kWh · Solar covered 62%");
  });

  it("hides the percentage when solar is off or a day lacks half-hours", () => {
    expect(houseSummary([], [day(true)], DETROIT_DAY_FROM, DETROIT_DAY_TO, NOON, false)?.coveredPercent).toBeNull();
    const days: HouseDay[] = [
      day(true, 10, 6),
      { day: "2026-11-12", homeKwh: 8, fromSolarKwh: 4, hasHalfHour: false },
    ];
    const from = DETROIT_DAY_FROM;
    const to = Date.parse("2026-11-13T05:00:00.000Z");
    expect(houseSummary([], days, from, to, Date.parse("2026-11-12T17:00:00.000Z"), true)?.coveredPercent).toBeNull();
  });

  it("does not let a future day block coverage", () => {
    const days: HouseDay[] = [day(true)];
    const to = Date.parse("2026-11-14T05:00:00.000Z");
    expect(houseSummary([], days, DETROIT_DAY_FROM, to, NOON, true)?.coveredPercent).toBe(62);
  });

  it("hides coverage when a started day has no row", () => {
    const from = Date.parse("2026-11-10T05:00:00.000Z");
    expect(houseSummary([], [day(true)], from, DETROIT_DAY_TO, NOON, true)?.coveredPercent).toBeNull();
  });

  it("hides the chip when the range has neither samples nor a daily total", () => {
    const samples: HouseSample[] = [{ t: NOON, kw: 2 }];
    expect(houseChipVisible([], [], DETROIT_DAY_FROM, DETROIT_DAY_TO)).toBe(false);
    expect(houseChipVisible(samples, [], Date.parse("2026-11-12T05:00:00.000Z"), Date.parse("2026-11-13T05:00:00.000Z"))).toBe(
      false
    );
    expect(houseChipVisible(samples, [], DETROIT_DAY_FROM, DETROIT_DAY_TO)).toBe(true);
  });
});

describe("missing house tables", () => {
  it("recognizes a missing relation and ignores other failures", () => {
    expect(isMissingHouseTable({ code: "42P01" })).toBe(true);
    expect(isMissingHouseTable(new Error('relation "house_energy" does not exist'))).toBe(true);
    expect(isMissingHouseTable(new Error('relation "house_energy_day" does not exist'))).toBe(true);
    expect(isMissingHouseTable(new Error("connection reset"))).toBe(false);
  });
});
