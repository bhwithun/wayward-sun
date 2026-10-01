# Sol Widget — Agent Instructions

Pure Android app (Kotlin, Compose, Material 3, Glance widget) bootstrapped from `_template`, plus a Next.js app on Vercel that is the only Solcast client. Neon Postgres stores the latest snapshot and half-hour history.

## Domain

- Package: `com.brian.solwidget`
- Solcast hobbyist rooftop endpoints only (not the commercial lat/lng PV power API)
- Default resource ID: `84d7-8b52-33f3-bd7b`
- Default cache URL: `https://sol-widget-brianandkathi.vercel.app` (`AppStorage.DEFAULT_CACHE_URL`). The previous `*.workers.dev` URLs are legacy and rewrite to this default.
- Never hardcode a Solcast API key. Never put it in the APK, git, or a committed env file. Store it only as the Vercel env var `SOLCAST_API_KEY`.
- The web app (`web/`) is the **only** Solcast HTTP client. The Android app and widget only `GET /cache` (`CacheApi`). They must not call `api.solcast.com.au`.
- No `CACHE_SECRET`. No device `PUT /cache`. Cache JSON is public at the site URL.
- Daily hobbyist limit is 10 HTTP requests. A full pull is 2 HTTP calls. Solcast runs only on `GET /cache`, `POST /refresh`, or `GET /cron` when the snapshot is older than `MIN_AUTO_AGE` (4 hours), capped at 5 cycles per UTC day (`DAILY_AUTO_LIMIT`). There is no public force refresh.
- Neon function `solpull` (`web/functions/solpull.ts`) on project `sol-widget` calls `GET /cron` at 8:00am, 12:00pm, 4:00pm, 8:00pm, 12:00am, and 4:00am America/Detroit during daylight time (`0 12,16,20,0,4,8 * * *` UTC). In standard time those instants are one hour earlier. The function only forwards the request; it does not call Solcast. Vercel Hobby cron still calls `GET /cron` once a day at 12:00 UTC. Both use the same 4-hour gate. Set `CRON_SECRET` on Vercel and on the Neon function so the route requires `Authorization: Bearer`.
- Open-Meteo for stored history is fetched by the web app when `SITE_LAT` and `SITE_LNG` are set and the last weather write is older than 1 hour. The phone still fetches its own Open-Meteo for the widget.

## Layout

```
com.brian.solwidget/
├── data/          # AppStorage, CacheApi, SolcastApi (parse only), ForecastRepository, models
├── ui/            # screens, PowerChart, theme
├── viewmodel/
├── widget/        # Glance ForecastWidget
├── work/          # 5-hour WorkManager cache download; hourly weather
└── util/

web/               # Next.js on Vercel (only Solcast client)
├── db/001_init.sql
├── src/app/       # /, /cache, /refresh, /health, /history, /cron
└── src/lib/       # quota gate, DTE port, interval upsert
```

## Vercel and Neon

- Project root is `web/`. Database is Neon via `DATABASE_URL`.
- `fetch_state` is the latest Solcast snapshot and quota counters. `intervals` is one row per Solcast `period_end` (actual kW, forecast kW, temp °F, precip %, rate band and cents). `sun_days` holds sunrise and sunset.
- Forecast kW is replaced only while that period is still in the future. Rate cents stick at first insert. Temp and precip take the newest Open-Meteo value.
- `GET /history` is for the web chart. Android does not call it.
- Do not add `CACHE_SECRET` back. Do not put the Solcast key in the Android app.
- Weather on the phone stays Open-Meteo and does not go through Solcast.

## Rules

- Dark solar dashboard palette in `SolColors` (web uses the same hex values)
- Version catalog for dependencies
- No Retrofit / Hilt / Room unless the app outgrows HttpURLConnection + DataStore
- No Prisma. SQL lives in `web/db/` and `web/src/lib/store.ts`
- Widget chart is a bitmap (`ChartBitmapRenderer`); the in-app chart is Compose Canvas; the web chart is a canvas
- Keep live (green) and forecast (gold) visually distinct, with a now marker
- Fixed axis ranges: solar 0–8 kW, precip 0–100%, temp -20–100°F. No dashed 8 kW capacity line.
- DTE Dynamic Peak Pricing (D1.8) bands live in `DteTou.kt` and `web/src/lib/dte.ts` (`America/Detroit`). Hours are year-round. Keep the cent constants in sync. App and web Rates metadata show plan id (`D1.8` / Rider 18 Cat1), DTE marketing base cents, and this site's effective volumetric import cents from a dated bill (`RATES_AS_OF`: base + PSCR + other volumetric). Do not scrape DTE. Show cents on the app and the website, not the widget.
- Local weather on the phone is Open-Meteo only (no API key, do not use Solcast). Overlay on the power chart: precip bars + white temp line (blue glow below 32°F, orange glow above 90°F; dashed and 30% opaque at night). Hourly `weather_refresh` WorkManager + in-app hourly weather refresh. Solcast stays on Vercel (on-demand when `/cache` is fetched and the snapshot is older than 4 hours, max 5 auto pulls per UTC day, plus the same gate from cron). App WorkManager only re-downloads `/cache`. Do not show a current-kW hero number.
- App and widget charts share `ForecastSnapshot.range()`: 2 local days before today through the end of 2 local days after (fixed x-axis, not data extents). The web 5-day view uses the same window in America/Detroit.
