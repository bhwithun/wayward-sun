const DEFAULT_CACHE_URL = "https://sol-widget-brianandkathi.vercel.app";

/**
 * Neon schedule trigger. Calls the Vercel /cron route, which is the only Solcast client.
 * The function URL is public, so a call without Neon's trigger header is rejected.
 */
export async function pull(request: Request): Promise<Response> {
  if (!request.headers.get("x-neon-trigger-invocation-id")) {
    return Response.json({ error: "not a trigger call" }, { status: 403 });
  }
  const secret = process.env.CRON_SECRET;
  if (!secret) {
    return Response.json({ error: "CRON_SECRET is not set" }, { status: 500 });
  }
  const base = (process.env.CACHE_URL || DEFAULT_CACHE_URL).replace(/\/$/, "");
  const upstream = await fetch(`${base}/cron`, {
    headers: { Authorization: `Bearer ${secret}` },
    signal: AbortSignal.timeout(55_000),
  });
  const body = await upstream.text();
  return new Response(body, {
    status: upstream.status,
    headers: {
      "content-type": upstream.headers.get("content-type") || "application/json",
    },
  });
}

export default { fetch: pull };
