import { describe, expect, it } from "vitest";
import { liveWindow, monthWeeks, weekStart } from "./time";

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

describe("liveWindow", () => {
  it("covers four days before today through two days after", () => {
    expect(liveWindow({ year: 2026, month: 10, day: 1 })).toEqual({
      from: { year: 2026, month: 9, day: 27 },
      to: { year: 2026, month: 10, day: 4 },
    });
  });
});
