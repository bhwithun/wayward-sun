import { describe, expect, it } from "vitest";
import {
  ECOBEE_GAP_MS,
  classifyEcobee,
  downsampleEcobee,
  ecobeeFillSegments,
  hvacBars,
  hvacSummary,
  parseEcobeeSetpoint,
  prepareEcobee,
  sharedFahrenheitRange,
  temperatureGrid,
  type EcobeeSample,
} from "./ecobee";

const t0 = Date.parse("2026-01-15T15:00:00Z");

function sample(t: number, indoorF: number, coolF: number | null, heatF: number | null): EcobeeSample {
  return { t, indoorF, coolF, heatF };
}

describe("ecobee bands", () => {
  it("fills a cooling gap above the cool setpoint", () => {
    const mark = classifyEcobee(sample(t0, 75, 74, 68));
    expect(mark.band).toBe("cooling");
    expect(mark.setF).toBe(74);
  });

  it("fills a heating gap below the heat setpoint", () => {
    const mark = classifyEcobee(sample(t0, 67, 74, 68));
    expect(mark.band).toBe("heating");
    expect(mark.setF).toBe(68);
  });

  it("draws the closer setpoint in the deadband and does not fill", () => {
    const tie = classifyEcobee(sample(t0, 71, 74, 68));
    expect(tie.band).toBe("deadband");
    expect(tie.setF).toBe(74);
    const nearerHeat = classifyEcobee(sample(t0, 69, 74, 68));
    expect(nearerHeat.band).toBe("deadband");
    expect(nearerHeat.setF).toBe(68);
    expect(classifyEcobee(sample(t0, 74, 74, 68))).toMatchObject({ band: "deadband", setF: 74 });
    expect(classifyEcobee(sample(t0, 68, 74, 68))).toMatchObject({ band: "deadband", setF: 68 });
  });

  it("skips a null or off setpoint", () => {
    expect(classifyEcobee(sample(t0, 60, null, 68)).band).toBe("heating");
    expect(classifyEcobee(sample(t0, 80, null, 68))).toMatchObject({ band: "deadband", setF: 68 });
    expect(classifyEcobee(sample(t0, 80, 74, null)).band).toBe("cooling");
    expect(classifyEcobee(sample(t0, 70, null, null)).setF).toBeNull();
    expect(parseEcobeeSetpoint("off")).toBeNull();
    expect(parseEcobeeSetpoint("  ")).toBeNull();
    expect(parseEcobeeSetpoint("74.5")).toBe(74.5);
  });

  it("splits fills on the deadband", () => {
    const run = [
      classifyEcobee(sample(t0, 75, 74, 68)),
      classifyEcobee(sample(t0 + 300_000, 76, 74, 68)),
      classifyEcobee(sample(t0 + 600_000, 71, 74, 68)),
      classifyEcobee(sample(t0 + 900_000, 66, 74, 68)),
      classifyEcobee(sample(t0 + 1_200_000, 65, 74, 68)),
    ];
    const fills = ecobeeFillSegments(run);
    expect(fills.map((segment) => segment.map((mark) => mark.indoorF))).toEqual([
      [75, 76],
      [66, 65],
    ]);
  });
});

describe("hvac runtime", () => {
  it("stacks a cold aux hour and prices the gas", () => {
    const samples = [
      { ...sample(t0, 65, 75, 66), heatPumpSec: 300, auxHeatSec: 180, coolSec: 0, fanSec: 300 },
      { ...sample(t0 + 300_000, 64, 75, 66), heatPumpSec: 60, auxHeatSec: 0, coolSec: 0, fanSec: 60 },
    ];
    const summary = hvacSummary(samples, t0, t0 + 3_600_000, t0 + 3_600_000);
    expect(summary.heatPumpHours).toBeCloseTo(360 / 3600);
    expect(summary.auxHours).toBeCloseTo(180 / 3600);
    expect(summary.gasCostDollars).toBeCloseTo((180 / 3600) * 0.6);
    const bars = hvacBars(samples, t0, t0 + 3_600_000, 2, t0 + 3_600_000);
    expect(bars.length).toBeGreaterThan(0);
    expect(bars.reduce((sum, bar) => sum + bar.auxMin, 0)).toBeCloseTo(3);
  });

  it("keeps a mild hour on the heat pump", () => {
    const samples = [{ ...sample(t0, 68, 75, 66), heatPumpSec: 240, auxHeatSec: 0, coolSec: 0 }];
    const summary = hvacSummary(samples, t0, t0 + 600_000);
    expect(summary.auxHours).toBe(0);
    expect(summary.heatPumpHours).toBeCloseTo(240 / 3600);
    expect(summary.gasCostDollars).toBe(0);
  });
});

describe("ecobee geometry", () => {
  it("keeps a 15 minute step and breaks a longer hole", () => {
    const samples = [
      sample(t0, 75, 74, 68),
      sample(t0 + ECOBEE_GAP_MS, 75.2, 74, 68),
      sample(t0 + ECOBEE_GAP_MS + 15 * 60 * 1000 + 1000, 66, 74, 68),
    ];
    const runs = prepareEcobee(samples, t0, t0 + 3_600_000, 400);
    expect(runs).toHaveLength(2);
    expect(runs[0]).toHaveLength(2);
    expect(runs[1][0].band).toBe("heating");
  });

  it("does not bridge a gap when the whole window is one pixel", () => {
    const samples = [sample(t0 + 60_000, 75, 74, 68), sample(t0 + 60_000 + 16 * 60_000, 66, 74, 68)];
    const runs = prepareEcobee(samples, t0, t0 + 3_600_000, 1);
    expect(runs).toHaveLength(2);
    expect(runs.map((run) => run[0].t)).toEqual(samples.map((sample) => sample.t));
  });

  it("downsamples to the pixel columns and keeps the cooling extreme", () => {
    const samples = Array.from({ length: 100 }, (_, index) =>
      sample(t0 + index * 5_000, index === 40 ? 80 : 71, 74, 68)
    );
    const run = prepareEcobee(samples, t0, t0 + 500_000, 10)[0];
    expect(run.length).toBeLessThanOrEqual(10);
    expect(run.length).toBe(10);
    expect(run.map((mark) => mark.t)).toEqual([...run.map((mark) => mark.t)].sort((a, b) => a - b));
    const times = new Set(samples.map((sample) => sample.t));
    expect(run.every((mark) => times.has(mark.t))).toBe(true);
    expect(run.some((mark) => mark.band === "cooling" && mark.indoorF === 80)).toBe(true);
  });

  it("keeps cooling when a later deadband sample shares the pixel", () => {
    const mark = downsampleEcobee(
      [sample(t0 + 1000, 80, 74, 68), sample(t0 + 2000, 71, 74, 68)],
      t0,
      100_000,
      1
    )[0];
    expect(mark.band).toBe("cooling");
    expect(mark.indoorF).toBe(80);
  });

  it("puts temperature gridlines on a round step and skips 32", () => {
    const wide = temperatureGrid(-24, 86, true);
    expect(wide.step).toBe(25);
    expect(wide.min).toBe(-25);
    expect(wide.max).toBe(100);
    expect(wide.ticks.every((tick) => Math.abs(tick - 32) > 3)).toBe(true);
    const covered = temperatureGrid(-16, 80, true);
    expect(covered).toMatchObject({ step: 20, min: -20, max: 80 });
    const tight = temperatureGrid(68, 78, true);
    expect(tight.step).toBe(5);
    expect(tight.ticks).toContain(70);
  });

  it("stops the layer at now", () => {
    const samples = [sample(t0, 75, 74, 68), sample(t0 + 300_000, 76, 74, 68), sample(t0 + 900_000, 80, 74, 68)];
    const now = t0 + 300_000;
    const run = prepareEcobee(samples, t0, t0 + 3_600_000, 80, ECOBEE_GAP_MS, now)[0];
    expect(run.map((mark) => mark.t)).toEqual([t0, now]);
    expect(run.every((mark) => mark.t <= now)).toBe(true);
  });

  it("scales the shared axis to outdoor and indoor together", () => {
    const range = sharedFahrenheitRange([10, 40], [sample(t0 + 60_000, 72, 74, 68)], t0, t0 + 3_600_000, true);
    expect(range.min).toBeLessThan(10);
    expect(range.max).toBeGreaterThan(74);
    expect(sharedFahrenheitRange([70], [], t0, t0 + 1000, false)).toEqual({ min: -20, max: 100 });
  });
});
