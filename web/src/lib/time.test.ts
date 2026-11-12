import { describe, expect, it } from "vitest";
import { livePeriod, liveWindow, monthWeeks, periodRange, usWeekNumber, weekStart } from "./time";

describe("weekStart", () => {
  it("returns the Sunday on or before a Thursday", () => {
    expect(weekStart({ year: 2026, month: 10, day: 1 })).toEqual({
      year: 2026,
      month: 9,
      day: 27,
    });
  });

  it("keeps a Sunday on that Sunday", () => {
    expect(weekStart({ year: 2026, month: 9, day: 27 })).toEqual({
      year: 2026,
      month: 9,
      day: 27,
    });
  });

  it("crosses into the previous year", () => {
    expect(weekStart({ year: 2026, month: 1, day: 1 })).toEqual({
      year: 2025,
      month: 12,
      day: 28,
    });
  });
});

describe("usWeekNumber", () => {
  it("counts the Sunday row that contains January 1 as week 1", () => {
    expect(usWeekNumber({ year: 2025, month: 12, day: 28 })).toBe(1);
    expect(usWeekNumber({ year: 2025, month: 12, day: 21 })).toBe(52);
    expect(usWeekNumber({ year: 2026, month: 1, day: 4 })).toBe(2);
    expect(usWeekNumber({ year: 2026, month: 11, day: 8 })).toBe(46);
  });
});

describe("monthWeeks", () => {
  it("includes the spill days around October 2026", () => {
    const weeks = monthWeeks(2026, 10);
    expect(weeks[0].id).toBe("2026-09-27");
    expect(weeks[0].days[0].inMonth).toBe(false);
    expect(weeks[0].days[4]).toMatchObject({ day: 1, inMonth: true });
    expect(weeks[weeks.length - 1].id).toBe("2026-10-25");
    expect(weeks[weeks.length - 1].days[6]).toMatchObject({ day: 31, inMonth: true });
    expect(weeks.every((week) => week.days.length === 7)).toBe(true);
  });
});

describe("periodRange", () => {
  it("drops the ending month inside one month and keeps it across a boundary", () => {
    expect(periodRange({ year: 2026, month: 11, day: 9 }, { year: 2026, month: 11, day: 13 })).toBe(
      "Nov 9 – 13"
    );
    expect(periodRange({ year: 2026, month: 10, day: 30 }, { year: 2026, month: 11, day: 3 })).toBe(
      "Oct 30 – Nov 3"
    );
  });
});

describe("livePeriod", () => {
  it("names the inclusive live window", () => {
    expect(livePeriod({ year: 2026, month: 11, day: 11 })).toBe("Nov 7 – 13");
    expect(livePeriod({ year: 2026, month: 10, day: 1 })).toBe("Sep 27 – Oct 3");
  });
});

describe("liveWindow", () => {
  it("covers four days before today through two days after", () => {
    expect(liveWindow({ year: 2026, month: 10, day: 1 })).toEqual({
      from: { year: 2026, month: 9, day: 27 },
      to: { year: 2026, month: 10, day: 4 },
    });
  });
});
