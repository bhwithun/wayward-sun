import { describe, expect, it } from "vitest";
import { bucketMinutes, historyCacheSeconds, parseRange } from "./historyRange";

const NOW = new Date("2026-11-23T16:00:00.000Z");
const H = 60 * 60 * 1000;

describe("parseRange", () => {
  it("since is exclusive and runs through the end of tomorrow by default", () => {
    const range = parseRange(new URLSearchParams("since=2026-11-23T15:55:00.000Z"), NOW);
    if ("error" in range) throw new Error(range.error);
    expect(range.from.toISOString()).toBe("2026-11-23T15:55:00.001Z");
    expect(range.to.toISOString()).toBe("2026-11-25T05:00:00.000Z");
  });

  it("rejects a bad day", () => {
    expect("error" in parseRange(new URLSearchParams("day=2026-13-01"), NOW)).toBe(true);
  });
});

describe("bucketMinutes", () => {
  it("auto keeps a day raw, a week at 15 minutes, a month hourly", () => {
    expect(bucketMinutes("auto", 24 * H)).toBeNull();
    expect(bucketMinutes("auto", 7 * 24 * H)).toBe(15);
    expect(bucketMinutes("auto", 42 * 24 * H)).toBe(60);
    expect(bucketMinutes("raw", 42 * 24 * H)).toBeNull();
  });
});

describe("historyCacheSeconds", () => {
  it("is short for live ranges and longer for old ones", () => {
    expect(historyCacheSeconds(new Date(NOW.getTime() - 12 * H), new Date(NOW.getTime() + 12 * H), NOW)).toBe(300);
    expect(historyCacheSeconds(new Date(NOW.getTime() - 30 * 24 * H), new Date(NOW.getTime() + 12 * 24 * H), NOW)).toBe(900);
    expect(historyCacheSeconds(new Date(NOW.getTime() - 60 * 24 * H), new Date(NOW.getTime() - 20 * 24 * H), NOW)).toBe(3600);
  });
});
