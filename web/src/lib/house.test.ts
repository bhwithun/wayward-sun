import { describe, expect, it } from "vitest";
import { housePointsFromRows } from "./store";
import {
  detroitDayBounds,
  homeKwFromWh,
  houseChipVisible,
  houseClippedSamples,
  houseDetailIsHalfHour,
  houseHourlySamples,
  houseHalfHourPeak,
  houseLegendLabel,
  housePeriodInRange,
  houseReadingDay,
  housePlotSamples,
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

  it("keeps a daily total that is ahead of the half-hours and stops the line", () => {
    const last = NOON;
    const earlier = NOON - 30 * 60 * 1000;
    const samples: HouseSample[] = [
      { t: earlier, kw: 2 },
      { t: last, kw: 2 },
    ];
    const summary = houseSummary(samples, [day(true, 2.4, 1.2)], DETROIT_DAY_FROM, DETROIT_DAY_TO, NOON, true);
    expect(summary?.homeKwh).toBeCloseTo(2.4);
    expect(summary?.hourlyDetailUnavailable).toBe(false);
    const runs = houseStepRuns(samples);
    expect(runs).toHaveLength(1);
    expect(runs[0][runs[0].length - 1].t).toBe(last);
    expect(runs[0].some((vertex) => vertex.t > last)).toBe(false);
  });

  it("averages a week into hours and keeps half-hours on a single day", () => {
    const samples: HouseSample[] = [
      { t: Date.parse("2026-11-11T15:30:00.000Z"), kw: 4 },
      { t: Date.parse("2026-11-11T16:00:00.000Z"), kw: 8 },
      { t: Date.parse("2026-11-11T16:30:00.000Z"), kw: 2 },
      { t: Date.parse("2026-11-11T17:00:00.000Z"), kw: 2 },
    ];
    expect(houseDetailIsHalfHour(DETROIT_DAY_FROM, DETROIT_DAY_TO)).toBe(true);
    expect(housePlotSamples(samples, DETROIT_DAY_FROM, DETROIT_DAY_TO)).toEqual(samples);
    const weekTo = Date.parse("2026-11-18T05:00:00.000Z");
    expect(houseDetailIsHalfHour(DETROIT_DAY_FROM, weekTo)).toBe(false);
    const hourly = housePlotSamples(samples, DETROIT_DAY_FROM, weekTo);
    expect(hourly.map((point) => point.kw)).toEqual([6, 2]);
    expect(hourly[0].t).toBe(Date.parse("2026-11-11T16:00:00.000Z"));
    expect(hourly[1].t).toBe(Date.parse("2026-11-11T17:00:00.000Z"));
    const runs = houseStepRuns(hourly, 60 * 60 * 1000);
    expect(runs).toHaveLength(1);
    expect(runs[0][runs[0].length - 1].t).toBe(hourly[1].t);
    const later = houseHourlySamples([{ t: Date.parse("2026-11-11T19:30:00.000Z"), kw: 3 }]);
    const split = houseStepRuns([...hourly, ...later], 60 * 60 * 1000);
    expect(split).toHaveLength(2);
    expect(split[1][split[1].length - 1].t).toBe(later[0].t);
  });

  it("marks buckets above 8 kW and names the peak", () => {
    const samples: HouseSample[] = [
      { t: NOON, kw: 16.4 },
      { t: NOON + 30 * 60 * 1000, kw: 4 },
    ];
    expect(houseClippedSamples(samples).map((sample) => sample.kw)).toEqual([16.4]);
    expect(houseLegendLabel(houseHalfHourPeak(samples, DETROIT_DAY_FROM, DETROIT_DAY_TO))).toBe(
      "House use · Peak 16.4 kW (half-hour)"
    );
    expect(houseLegendLabel(4)).toBe("House use · Peak 4.0 kW (half-hour)");
    expect(houseLegendLabel(null)).toBe("House use");
  });

  it("keeps the closing half-hour on a normal day and on DST days", () => {
    assertDayBoundary(newYorkMidnight(2026, 11, 11), newYorkMidnight(2026, 11, 12), 24 * 60);
    assertDayBoundary(newYorkMidnight(2026, 11, 1), newYorkMidnight(2026, 11, 2), 25 * 60);
    assertDayBoundary(newYorkMidnight(2026, 3, 8), newYorkMidnight(2026, 3, 9), 23 * 60);
  });

  it("legend peak stays the half-hour max when the line is hourly", () => {
    const samples: HouseSample[] = [
      { t: Date.parse("2026-11-11T15:30:00.000Z"), kw: 4 },
      { t: Date.parse("2026-11-11T16:00:00.000Z"), kw: 16.4 },
    ];
    const weekTo = Date.parse("2026-11-18T05:00:00.000Z");
    const hourly = housePlotSamples(samples, DETROIT_DAY_FROM, weekTo);
    expect(hourly).toHaveLength(1);
    expect(hourly[0].kw).toBeCloseTo(10.2);
    expect(houseHalfHourPeak(samples, DETROIT_DAY_FROM, weekTo)).toBeCloseTo(16.4);
    expect(houseLegendLabel(houseHalfHourPeak(samples, DETROIT_DAY_FROM, weekTo))).toBe(
      "House use · Peak 16.4 kW (half-hour)"
    );
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

  it("day totals match house_energy_day for a past day and a past week", () => {
    const nov10Start = Date.parse("2026-11-10T05:00:00.000Z");
    const nov11Start = Date.parse("2026-11-11T05:00:00.000Z");
    const nov13Start = Date.parse("2026-11-13T05:00:00.000Z");
    const closing = { t: nov11Start, kw: 0.828 };
    const oneDay = houseSummary(
      [closing, { t: nov11Start - 30 * 60 * 1000, kw: 1.48 }],
      [{ day: "2026-11-10", homeKwh: 41.56, fromSolarKwh: 10, hasHalfHour: true }],
      nov10Start,
      nov11Start,
      nov11Start,
      true
    );
    expect(oneDay?.homeKwh).toBeCloseTo(41.56);
    expect(houseReadingDay(nov11Start)).toBe("2026-11-10");
    const steps = houseStepRuns(housePlotSamples([closing], nov10Start, nov11Start));
    expect(steps[0][0].t).toBe(nov11Start - 30 * 60 * 1000);
    expect(steps[0][steps[0].length - 1].t).toBe(nov11Start);

    const weekFrom = Date.parse("2026-11-08T05:00:00.000Z");
    const weekTo = Date.parse("2026-11-15T05:00:00.000Z");
    const days: HouseDay[] = [
      { day: "2026-11-10", homeKwh: 41.56, fromSolarKwh: 1, hasHalfHour: true },
      { day: "2026-11-11", homeKwh: 40, fromSolarKwh: 1, hasHalfHour: true },
      { day: "2026-11-12", homeKwh: 43.33, fromSolarKwh: 1, hasHalfHour: true },
    ];
    const week = houseSummary(
      [
        { t: nov11Start, kw: 0.828 },
        { t: nov13Start, kw: 0.74 },
      ],
      days,
      weekFrom,
      weekTo,
      weekTo,
      true
    );
    expect(week?.homeKwh).toBeCloseTo(41.56 + 40 + 43.33);
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

function assertDayBoundary(start: number, end: number, expectedMinutes: number) {
  expect((end - start) / 60000).toBe(expectedMinutes);
  const samples: HouseSample[] = [
    { t: start, kw: 9 },
    { t: start + 30 * 60 * 1000, kw: 2 },
    { t: end, kw: 3 },
  ];
  const plotted = housePlotSamples(samples, start, end);
  expect(plotted.map((sample) => sample.t)).toEqual([start + 30 * 60 * 1000, end]);
  expect(housePeriodInRange(start, start, end)).toBe(false);
  expect(housePeriodInRange(end, start, end)).toBe(true);
  const halfHour = houseStepRuns([{ t: end, kw: 3 }]);
  expect(halfHour[0][0].t).toBe(end - 30 * 60 * 1000);
  expect(halfHour[0][halfHour[0].length - 1].t).toBe(end);
  const hour = houseStepRuns([{ t: end, kw: 4 }], 60 * 60 * 1000);
  expect(hour[0][0].t).toBe(end - 60 * 60 * 1000);
  expect(hour[0][hour[0].length - 1].t).toBe(end);
}

/** Midnight in America/New_York, including the DST transitions. */
function newYorkMidnight(year: number, month: number, day: number): number {
  const format = new Intl.DateTimeFormat("en-US", {
    timeZone: "America/New_York",
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  const asUtc = (instant: number) => {
    const bag: Record<string, string> = {};
    for (const part of format.formatToParts(new Date(instant))) {
      if (part.type !== "literal") bag[part.type] = part.value;
    }
    let hour = Number(bag.hour);
    if (hour === 24) hour = 0;
    return Date.UTC(Number(bag.year), Number(bag.month) - 1, Number(bag.day), hour, Number(bag.minute), Number(bag.second));
  };
  const guess = Date.UTC(year, month - 1, day);
  let offset = asUtc(guess) - guess;
  let instant = guess - offset;
  offset = asUtc(instant) - instant;
  return guess - offset;
}

describe("missing house tables", () => {
  it("recognizes a missing relation and ignores other failures", () => {
    expect(isMissingHouseTable({ code: "42P01" })).toBe(true);
    expect(isMissingHouseTable(new Error('relation "house_energy" does not exist'))).toBe(true);
    expect(isMissingHouseTable(new Error('relation "house_energy_day" does not exist'))).toBe(true);
    expect(isMissingHouseTable(new Error("connection reset"))).toBe(false);
  });
});
