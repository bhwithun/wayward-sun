import { describe, expect, it } from "vitest";
import { isDesignatedHoliday, periodAt } from "./dte";
import { zonedTimeToUtc } from "./time";

function at(year: number, month: number, day: number, hour: number, minute: number) {
  return zonedTimeToUtc(year, month, day, hour, minute);
}

describe("DTE D1.8 bands", () => {
  it("follows weekday hours in America/Detroit", () => {
    expect(periodAt(at(2026, 6, 17, 6, 59))).toBe("OFF_PEAK");
    expect(periodAt(at(2026, 6, 17, 7, 0))).toBe("MID_PEAK");
    expect(periodAt(at(2026, 6, 17, 14, 59))).toBe("MID_PEAK");
    expect(periodAt(at(2026, 6, 17, 15, 0))).toBe("PEAK");
    expect(periodAt(at(2026, 6, 17, 18, 59))).toBe("PEAK");
    expect(periodAt(at(2026, 6, 17, 19, 0))).toBe("MID_PEAK");
    expect(periodAt(at(2026, 6, 17, 22, 59))).toBe("MID_PEAK");
    expect(periodAt(at(2026, 6, 17, 23, 0))).toBe("OFF_PEAK");
  });

  it("is off-peak all weekend", () => {
    expect(periodAt(at(2026, 6, 20, 16, 0))).toBe("OFF_PEAK");
  });

  it("is off-peak on New Year's Day", () => {
    expect(isDesignatedHoliday({ year: 2026, month: 1, day: 1 })).toBe(true);
    expect(periodAt(at(2026, 1, 1, 16, 0))).toBe("OFF_PEAK");
  });
});
