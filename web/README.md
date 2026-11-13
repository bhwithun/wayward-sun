# Wayward Sun web

Next.js app on Vercel. It is the only Solcast client. Neon stores the latest snapshot and a half-hour history of solar, DTE rate, temperature, and precipitation probability.

## Routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Dashboard |
| GET | `/health` | `{ ok, runtime, deployed }` |
| GET | `/cache` | JSON snapshot for the Android app. Does not call Solcast. |
| GET | `/history?from=&to=` | Stored half-hour rows, Ecobee samples when `ecobee_samples` exists, and house energy when `house_energy` / `house_energy_day` exist. `from` and `to` are ISO instants or `YYYY-MM-DD` (Detroit midnight). Max 366 days. |
| GET | `/cron` | Scheduled Solcast pull, and an Enphase production pull when that app is connected and the last one is an hour old. Called by Neon `solpull` every 30 minutes from 10:00 through 04:30 UTC, and by Vercel Cron once a day at 12:30 UTC. Solcast runs only when the America/Detroit clock is on a forecast slot. If `CRON_SECRET` is set, requires `Authorization: Bearer`. |
| GET | `/enphase/connect` | Sends the homeowner to Enphase to approve the Watt-plan app. |
| GET | `/enphase/callback` | Stores the Enphase tokens. Does not show them. |

There is no device `PUT` and no manual refresh.

## Setup

1. Create a Neon database and run `db/001_init.sql`. An existing database also runs `db/002_actuals_fetched_at.sql`, `db/003_meter_actuals.sql`, `db/004_ecobee.sql`, and `db/005_ecobee_runtime.sql`. `db/006_house_energy.sql` adds `house_energy` and `house_energy_day`. Do not apply `006` until the house-energy loader is ready. The web app never calls Tesla. If those tables are missing, `GET /history` still returns and `house` / `houseDays` are empty.

## House energy

`house` is `{ t, homeKw }` for complete 30-minute rows. `homeKw` is `home_wh / 500`. `houseDays` is `{ day, homeKwh, fromSolarKwh, hasHalfHour }` for America/Detroit dates in the requested window. `day` totals use watt-hours divided by 1000. `/cache` does not include these arrays. The phone and the widget already request `/history` for the green trace, and that window covers the widget's prior 48 hours.

## Ecobee

`GET /history` adds an `ecobee` array (`t`, `indoorF`, `coolF`, `heatF`) from `ecobee_samples`. If that table is missing, the array is empty and the rest of history still returns. The chart compares each 5-minute indoor temperature with the cool setpoint, then the heat setpoint. Above cool fills blue, below heat fills red, and the deadband between them is unfilled. A gap longer than 15 minutes breaks the line.

Load a thermostat export only into a non-production branch until production has a backup:

```bash
cd web
DATABASE_URL='postgres://…' ECOBEE_BRANCH_ID='br-…' npm run import:ecobee -- ../path/to/ecobee_5min_all.csv
```

The script applies `db/004_ecobee.sql` if needed and upserts on `sampled_at`, so a later export can be imported again. `Date` and `Time` are America/New_York. Blank indoor rows are skipped. A cool or heat cell that is blank or `off` is stored as null. The script reads `neon.branch_id` and refuses to write unless it matches `ECOBEE_BRANCH_ID`. The production branch `br-green-wind-b4v3k3gk` also requires `ECOBEE_CONFIRM_PRODUCTION=backup-done`.

The preview branch that already holds the August 2025–November 2026 export is `ecobee-preview` (`br-curly-recipe-b41b1sp7`) on Neon project `wayward-sun` (`small-star-07106259`). Production `main` has not been migrated.
2. Copy `.env.example` to `.env.local`. Set `DATABASE_URL`, `SOLCAST_API_KEY`, `SITE_LAT`, and `SITE_LNG` (the same place the phone uses).
3. `npm install`, then `npm run dev`.
4. Optional: `npm run seed` copies the current Cloudflare `/cache` into `fetch_state` so the first request does not spend Solcast quota.

On Vercel, set the same env vars. Root directory is `web/`. The Vercel cron is `30 12 * * *` (Hobby accounts allow one run a day). That instant is the 8:30am Detroit slot during daylight time. Neon function `solpull` (`functions/solpull.ts`) is the half-hour watcher, with two UTC crons because the window crosses midnight: `0,30 10-23 * * *` and `0,30 0-4 * * *`. `/cron` ignores runs that are not one of the eight Detroit slots, so standard time stays on the same wall-clock times. Deploy it with `neon functions deploy solpull` and set `CACHE_URL` and `CRON_SECRET` on the function. Do not commit the secret.

## Quota

10 Solcast HTTP requests per UTC day: 8 forecast calls (`hours=336`) and 2 estimated-actuals calls (`hours=168`). Forecasts run at 6:00am, 8:30am, 11:00am, 1:30pm, 4:00pm, 6:30pm, 9:00pm, and 11:30pm America/Detroit. Actuals run with the 6:00am and 6:30pm forecasts. Open-Meteo is separate and refreshes when the last weather write is older than 1 hour.

Measured production is a separate Enphase call from `/cron`, at most once an hour, and it does not spend the Solcast quota. A half-hour that came from Enphase is kept when a later Solcast estimate arrives. The free Watt plan allows 1,000 hits per month. The access token lasts a day and the refresh token lasts a month. Each refresh returns a new refresh token, which is stored in `fetch_state` and is not returned by `/cache` or `/history`.
