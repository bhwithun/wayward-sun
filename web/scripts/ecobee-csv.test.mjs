import { describe, expect, it } from "vitest";
import { newYorkToUtc, parseEcobeeCsv, parseEcobeeSetpoint } from "./ecobee-csv.mjs";

describe("ecobee csv", () => {
  it("treats blank and off setpoints as missing", () => {
    expect(parseEcobeeSetpoint("off")).toBeNull();
    expect(parseEcobeeSetpoint("")).toBeNull();
    expect(parseEcobeeSetpoint("74.0")).toBe(74);
  });

  it("reads New York wall time as UTC", () => {
    expect(newYorkToUtc(2025, 8, 11, 0, 0, 0).toISOString()).toBe("2025-08-11T04:00:00.000Z");
    expect(newYorkToUtc(2026, 1, 15, 0, 0, 0).toISOString()).toBe("2026-01-15T05:00:00.000Z");
    expect(newYorkToUtc(2026, 3, 8, 6, 0, 0).toISOString()).toBe("2026-03-08T10:00:00.000Z");
    expect(newYorkToUtc(2026, 3, 8, 7, 0, 0).toISOString()).toBe("2026-03-08T11:00:00.000Z");
  });

  it("skips a blank indoor temperature and keeps an off cool setpoint null", () => {
    const csv = [
      "Date,Time,System Setting,System Mode,Calendar Event,Program Mode,Cool Set Temp (F),Heat Set Temp (F),Current Temp (F)",
      "2025-08-11,00:00:00,cool,compressorCoolOff,,Sleep,74.0,68.0,73.9",
      "2026-01-15,00:00:00,heat,heatOff,,Home,off,68.0,66.0",
      "2026-11-12,00:00:00,,,,,,",
    ].join("\n");
    const rows = parseEcobeeCsv(csv);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({
      sampledAt: "2025-08-11T04:00:00.000Z",
      indoorF: 73.9,
      coolSetF: 74,
      heatSetF: 68,
      systemMode: "compressorCoolOff",
    });
    expect(rows[1].coolSetF).toBeNull();
    expect(rows[1].indoorF).toBe(66);
  });
});
