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
    expect(rows.hasRuntime).toBe(false);
    expect(rows[0].heatPumpSec).toBeNull();
  });

  it("skips a portal comment header and reads runtime seconds", () => {
    const csv = [
      "\uFEFF#,Thermostat,name,HVAC",
      "#,Start,date,2026-11-01",
      "Date,Time,System Mode,Cool Set Temp (F),Heat Set Temp (F),Current Temp (F),Outdoor Temp (F),Cool Stage 1 (sec),Heat Stage 1 (sec),Aux Heat 1 (sec),Fan (sec)",
      "2026-11-08,06:00:00,compressorHeatStage1On,75,66,65.0,28.4,0,300,120,300",
      "2026-11-10,14:00:00,compressorHeatStage1On,75,66,68.0,48.0,0,180,0,180",
    ].join("\n");
    const rows = parseEcobeeCsv(csv);
    expect(rows.hasRuntime).toBe(true);
    expect(rows[0]).toMatchObject({
      sampledAt: "2026-11-08T11:00:00.000Z",
      indoorF: 65,
      outdoorF: 28.4,
      heatPumpSec: 300,
      auxHeatSec: 120,
      coolSec: 0,
      fanSec: 300,
    });
    expect(rows[1].auxHeatSec).toBe(0);
    expect(rows[1].heatPumpSec).toBe(180);
  });
});
