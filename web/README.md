# Wayward Sun web

Next.js app on Vercel. It is the only Solcast client. Neon stores the latest snapshot and a half-hour history of solar, DTE rate, temperature, and precipitation probability.

## Routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Dashboard |
| GET | `/health` | `{ ok, runtime, deployed }` |
| GET | `/cache` | JSON snapshot for the Android app. Pulls Solcast if the snapshot is older than 4 hours. |
| POST | `/refresh` | Same gate as `GET /cache` |
| GET | `/history?from=&to=` | Stored half-hour rows. `from` and `to` are ISO instants or `YYYY-MM-DD` (Detroit midnight). Max 366 days. |
| GET | `/cron` | Solcast gate. Called by Neon `solpull` every 4 hours (8:00am Detroit daylight time, then every 4 hours) and by Vercel Cron once a day at 12:00 UTC. If `CRON_SECRET` is set, requires `Authorization: Bearer`. |

There is no device `PUT` and no public force-refresh.

## Setup

1. Create a Neon database and run `db/001_init.sql`.
2. Copy `.env.example` to `.env.local`. Set `DATABASE_URL`, `SOLCAST_API_KEY`, `SITE_LAT`, and `SITE_LNG` (the same place the phone uses).
3. `npm install`, then `npm run dev`.
4. Optional: `npm run seed` copies the current Cloudflare `/cache` into `fetch_state` so the first request does not spend Solcast quota.

On Vercel, set the same env vars. Root directory is `web/`. The Vercel cron is `0 12 * * *` because a Hobby account rejects any schedule that runs more than once a day. Neon function `solpull` (`functions/solpull.ts`) is the 4-hour caller. Its cron is `0 12,16,20,0,4,8 * * *` (8:00am America/Detroit during daylight time; one hour earlier in standard time). Deploy it with `neon functions deploy solpull` and set `CACHE_URL` and `CRON_SECRET` on the function. Do not commit the secret.

## Quota

10 Solcast HTTP requests per UTC day. One pull is 2 calls. At most 5 pulls per UTC day, and only when the snapshot is older than 4 hours. Open-Meteo is separate and refreshes when the last weather write is older than 1 hour.
