import { describe, expect, it } from "vitest";
import { isDesignatedHoliday, periodAt, rateSteps } from "./dte";
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

describe("buy and sell step lines", () => {
  it("steps from mid-peak to peak on a Wednesday afternoon", () => {
    const lines = rateSteps(at(2026, 6, 17, 14, 0), at(2026, 6, 17, 16, 0), []);
    const t15 = at(2026, 6, 17, 15, 0).getTime();
    expect(lines.buy.filter((vertex) => vertex.t === t15).map((vertex) => vertex.cents)).toEqual([
      21.83,
      30.15,
    ]);
    const sellAt15 = lines.sell.filter((vertex) => vertex.t === t15).map((vertex) => vertex.cents);
    expect(sellAt15[0]).toBeCloseTo(10.761);
    expect(sellAt15[1]).toBeCloseTo(19.075);
  });

  it("stays flat off-peak on Saturday", () => {
    const lines = rateSteps(at(2026, 6, 20, 15, 0), at(2026, 6, 20, 16, 0), []);
    expect(lines.buy.map((vertex) => vertex.cents)).toEqual([17.53, 17.53]);
    expect(lines.sell[0].cents).toBeCloseTo(6.46);
    expect(lines.sell[1].cents).toBeCloseTo(6.46);
  });

  it("uses stored cents for the half-hour they cover", () => {
    const from = at(2026, 6, 17, 14, 0);
    const to = at(2026, 6, 17, 16, 0);
    const lines = rateSteps(from, to, [
      {
        periodEnd: at(2026, 6, 17, 15, 30).toISOString(),
        periodHours: 0.5,
        importCents: 12,
        outflowCents: 3,
      },
    ]);
    const atTime = (vertices: { t: number; cents: number }[], hour: number, minute: number) =>
      vertices.filter((vertex) => vertex.t === at(2026, 6, 17, hour, minute).getTime()).map((vertex) => vertex.cents);
    expect(atTime(lines.buy, 15, 0)).toEqual([21.83, 12]);
    expect(atTime(lines.buy, 15, 30)).toEqual([12, 30.15]);
    expect(atTime(lines.sell, 15, 0)[0]).toBeCloseTo(10.761);
    expect(atTime(lines.sell, 15, 0)[1]).toBe(3);
    expect(atTime(lines.sell, 15, 30)[0]).toBe(3);
    expect(atTime(lines.sell, 15, 30)[1]).toBeCloseTo(19.075);
  });
});
