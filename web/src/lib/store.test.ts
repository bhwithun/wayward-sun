import { describe, expect, it } from "vitest";
import { isMissingEcobeeTable, isoDate } from "./store";

describe("isoDate", () => {
  it("keeps a date-only string", () => {
    expect(isoDate("2026-10-01")).toBe("2026-10-01");
  });

  it("formats a Date from a Postgres date column", () => {
    expect(isoDate(new Date("2026-10-01T00:00:00.000Z"))).toBe("2026-10-01");
  });

  it("reads an ISO instant as its UTC day", () => {
    expect(isoDate("2026-10-01T00:00:00.000Z")).toBe("2026-10-01");
  });
});

describe("ecobee table", () => {
  it("treats a missing ecobee_samples relation as an empty layer", () => {
    expect(isMissingEcobeeTable({ code: "42P01" })).toBe(true);
    expect(isMissingEcobeeTable(new Error('relation "ecobee_samples" does not exist'))).toBe(true);
    expect(isMissingEcobeeTable(new Error("connection reset"))).toBe(false);
  });
});
