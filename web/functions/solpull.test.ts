import { afterEach, describe, expect, it, vi } from "vitest";
import { pull } from "./solpull";

afterEach(() => {
  vi.unstubAllGlobals();
  delete process.env.CRON_SECRET;
  delete process.env.CACHE_URL;
});

describe("solpull", () => {
  it("rejects a call that did not come from a Neon trigger", async () => {
    const response = await pull(new Request("https://example.test/"));
    expect(response.status).toBe(403);
  });

  it("calls Vercel /cron with the bearer secret", async () => {
    process.env.CRON_SECRET = "test-secret";
    process.env.CACHE_URL = "https://cache.example";
    const fetchMock = vi.fn(async () => new Response('{"ok":true}', { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await pull(
      new Request("https://example.test/", {
        headers: { "x-neon-trigger-invocation-id": "inv-1" },
      }),
    );

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith(
      "https://cache.example/cron",
      expect.objectContaining({
        headers: { Authorization: "Bearer test-secret" },
      }),
    );
  });
});
