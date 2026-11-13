import { beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => {
  process.env.DATABASE_URL = "postgres://fixture.local/wayward-sun";
  const box = { mode: "present" as "present" | "missing" };
  function missingRelation(name: string): never {
    const error = new Error(`relation "${name}" does not exist`) as Error & { code: string };
    error.code = "42P01";
    throw error;
  }
  function query(text: string): unknown[] {
    if (text.includes("fetch_state")) return [];
    if (text.includes("actual_kw IS NOT NULL")) return [];
    if (text.includes("FROM sun_days")) return [];
    if (text.includes("ecobee_samples")) return [];
    if (text.includes("house_energy_day")) {
      if (box.mode === "missing") missingRelation("house_energy_day");
      return [
        {
          day: "2026-11-11",
          home_wh: 18400,
          from_solar_wh: 11408,
          has_half_hour: true,
        },
      ];
    }
    if (text.includes("house_energy")) {
      if (box.mode === "missing") missingRelation("house_energy");
      return [
        { period_end: "2026-11-11T17:00:00.000Z", home_wh: 500, complete: true },
        { period_end: "2026-11-11T17:30:00.000Z", home_wh: 2500, complete: false },
      ];
    }
    if (text.includes("FROM intervals")) {
      return [
        {
          period_end: "2026-11-11T17:00:00.000Z",
          actual_kw: 1.2,
          forecast_kw: 1.4,
          period_hours: 0.5,
          temp_f: 48,
          precip_pct: 10,
          rate_band: "off",
          import_cents: 12,
          outflow_cents: 8,
        },
      ];
    }
    throw new Error(`unexpected sql: ${text}`);
  }
  return { box, query };
});

vi.mock("@neondatabase/serverless", () => ({
  neon: () => (strings: TemplateStringsArray) => state.query(strings.join(" ")),
}));

import { GET } from "./route";

const FROM = "2026-11-11T05:00:00.000Z";
const TO = "2026-11-12T05:00:00.000Z";

describe("GET /history house energy", () => {
  beforeEach(() => {
    state.box.mode = "present";
  });

  it("returns complete half-hours and daily totals when the tables exist", async () => {
    const response = await GET(new Request(`http://localhost/history?from=${FROM}&to=${TO}`));
    expect(response.status).toBe(200);
    const body = (await response.json()) as {
      points: unknown[];
      house: Array<{ t: string; homeKw: number }>;
      houseDays: Array<{ day: string; homeKwh: number; fromSolarKwh: number; hasHalfHour: boolean }>;
    };
    expect(body.points).toHaveLength(1);
    expect(body.house).toEqual([{ t: "2026-11-11T17:00:00.000Z", homeKw: 1 }]);
    expect(body.houseDays).toHaveLength(1);
    expect(body.houseDays[0].day).toBe("2026-11-11");
    expect(body.houseDays[0].homeKwh).toBeCloseTo(18.4);
    expect(body.houseDays[0].fromSolarKwh).toBeCloseTo(11.408);
    expect(body.houseDays[0].hasHalfHour).toBe(true);
  });

  it("returns empty house arrays when the tables are missing", async () => {
    state.box.mode = "missing";
    const response = await GET(new Request(`http://localhost/history?from=${FROM}&to=${TO}`));
    expect(response.status).toBe(200);
    const body = (await response.json()) as { points: unknown[]; house: unknown[]; houseDays: unknown[]; error?: string };
    expect(body.error).toBeUndefined();
    expect(body.points).toHaveLength(1);
    expect(body.house).toEqual([]);
    expect(body.houseDays).toEqual([]);
  });
});
