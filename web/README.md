# Wayward Sun web

Next.js app on Vercel. It is the only Solcast client. Neon stores the latest snapshot and a half-hour history of solar, DTE rate, temperature, and precipitation probability.

## Routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Dashboard |
| GET | `/health` | `{ ok, runtime, deployed }` |
| GET | `/cache` | JSON snapshot for the Android app. Does not call Solcast. |
| GET | `/history?from=&to=` | Stored half-hour rows. `from` and `to` are ISO instants or `YYYY-MM-DD` (Detroit midnight). Max 366 days. |
| GET | `/cron` | Scheduled Solcast pull. Called by Neon `solpull` every 30 minutes from 10:00 through 04:30 UTC, and by Vercel Cron once a day at 12:30 UTC. Solcast runs only when the America/Detroit clock is on a forecast slot. If `CRON_SECRET` is set, requires `Authorization: Bearer`. |

There is no device `PUT` and no manual refresh.

## Setup

1. Create a Neon database and run `db/001_init.sql`. An existing database also runs `db/002_actuals_fetched_at.sql`.
2. Copy `.env.example` to `.env.local`. Set `DATABASE_URL`, `SOLCAST_API_KEY`, `SITE_LAT`, and `SITE_LNG` (the same place the phone uses).
3. `npm install`, then `npm run dev`.
4. Optional: `npm run seed` copies the current Cloudflare `/cache` into `fetch_state` so the first request does not spend Solcast quota.

On Vercel, set the same env vars. Root directory is `web/`. The Vercel cron is `30 12 * * *` (Hobby accounts allow one run a day). That instant is the 8:30am Detroit slot during daylight time. Neon function `solpull` (`functions/solpull.ts`) is the half-hour watcher, with two UTC crons because the window crosses midnight: `0,30 10-23 * * *` and `0,30 0-4 * * *`. `/cron` ignores runs that are not one of the eight Detroit slots, so standard time stays on the same wall-clock times. Deploy it with `neon functions deploy solpull` and set `CACHE_URL` and `CRON_SECRET` on the function. Do not commit the secret.

## Quota

10 Solcast HTTP requests per UTC day: 8 forecast calls (`hours=336`) and 2 estimated-actuals calls (`hours=168`). Forecasts run at 6:00am, 8:30am, 11:00am, 1:30pm, 4:00pm, 6:30pm, 9:00pm, and 11:30pm America/Detroit. Actuals run with the 6:00am and 6:30pm forecasts. Open-Meteo is separate and refreshes when the last weather write is older than 1 hour.
