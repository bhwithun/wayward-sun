/**
 * Copy the live Cloudflare cache into fetch_state so the first Vercel
 * request does not spend a Solcast pull. Skips demo payloads.
 *
 * Usage (from web/):
 *   DATABASE_URL=postgres://... node scripts/seed-from-worker.mjs
 */
import { neon } from "@neondatabase/serverless";

const SOURCE = process.env.SEED_URL || "https://solcast-cache-worker.brian-952.workers.dev/cache";

const url = process.env.DATABASE_URL;
if (!url) {
  console.error("DATABASE_URL is not set. Seed was not run.");
  process.exit(1);
}

const response = await fetch(SOURCE);
const body = await response.json();
if (!response.ok || body.error || body.source === "demo" || !body.forecasts || !body.actuals) {
  console.error("Seed skipped. Source was not a live Solcast snapshot.");
  process.exit(1);
}

const sql = neon(url);
await sql`
  INSERT INTO fetch_state (
    id, resource_id, fetched_at, source, requests_day, requests_used,
    auto_fetches_used, message, forecasts, actuals, weather_fetched_at
  ) VALUES (
    1,
    ${body.resourceId},
    ${body.fetchedAt},
    ${body.source},
    ${body.requestsDay},
    ${body.requestsUsed ?? 0},
    ${body.autoFetchesUsed ?? 0},
    ${body.message ?? "Seeded from the Cloudflare cache."},
    ${JSON.stringify(body.forecasts)}::jsonb,
    ${JSON.stringify(body.actuals)}::jsonb,
    NULL
  )
  ON CONFLICT (id) DO UPDATE SET
    resource_id = EXCLUDED.resource_id,
    fetched_at = EXCLUDED.fetched_at,
    source = EXCLUDED.source,
    requests_day = EXCLUDED.requests_day,
    requests_used = EXCLUDED.requests_used,
    auto_fetches_used = EXCLUDED.auto_fetches_used,
    message = EXCLUDED.message,
    forecasts = EXCLUDED.forecasts,
    actuals = EXCLUDED.actuals
`;
console.log(`Seeded fetch_state from ${SOURCE} (${body.source}, ${body.fetchedAt}).`);
