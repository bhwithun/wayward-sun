# Wayward Sun — Agent Instructions

Pure Android app (Kotlin, Compose, Material 3, Glance widget) bootstrapped from `_template`, plus a Next.js app on Vercel that is the only Solcast client. Neon Postgres stores the latest snapshot and half-hour history.

## Domain

- Package: `com.brian.solwidget`
- Solcast hobbyist rooftop endpoints only (not the commercial lat/lng PV power API)
- Default resource ID: `84d7-8b52-33f3-bd7b`
- Default cache URL: `https://wayward-sun-brianandkathi.vercel.app` (`AppStorage.DEFAULT_CACHE_URL`). The previous `sol-widget` Vercel host and `*.workers.dev` URLs are legacy and rewrite to this default.
- Never hardcode a Solcast API key. Never put it in the APK, git, or a committed env file. Store it only as the Vercel env var `SOLCAST_API_KEY`.
- The web app (`web/`) is the **only** Solcast HTTP client. Live and the widget `GET /cache` (`CacheApi`). Live, the widget, and the phone History tab also `GET /history` for the green trace. They must not call `api.solcast.com.au`.
- No `CACHE_SECRET`. No device `PUT /cache`. Cache JSON is public at the site URL.
- Daily hobbyist limit is 10 HTTP requests per UTC day. A forecast call requests 14 days (`hours=336`) and an estimated-actuals call requests 7 days (`hours=168`). Solcast runs only from `GET /cron`, and only on eight America/Detroit slots from 6:00am through 11:30pm (every 2.5 hours). The 6:00am and 6:30pm slots also call estimated actuals. That is 8 forecast requests and 2 actuals requests. `GET /cache` never calls Solcast. There is no manual refresh.
- Neon function `solpull` (`web/functions/solpull.ts`) on Neon project `wayward-sun` calls `GET /cron` every 30 minutes across the UTC hours that contain that awake window in both daylight and standard time (`0,30 10-23 * * *` and `0,30 0-4 * * *`). The route decides whether the Detroit clock is on a slot. The function only forwards the request; it does not call Solcast. Vercel Hobby cron calls `GET /cron` once a day at 12:30 UTC (the 8:30am daylight-time slot). A repeated slot does not call Solcast again. Set `CRON_SECRET` on Vercel and on the Neon function so the route requires `Authorization: Bearer`.
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
├── src/app/       # /, /cache, /health, /history, /cron
└── src/lib/       # quota gate, DTE port, interval upsert
```

## Vercel and Neon

- Project root is `web/`. Database is Neon via `DATABASE_URL`.
- `fetch_state` is the latest Solcast snapshot and quota counters. `intervals` is one row per Solcast `period_end` (actual kW, forecast kW, temp °F, precip %, rate band and cents). `sun_days` holds sunrise and sunset.
- Forecast kW is replaced only while that period is still in the future. Rate cents stick at first insert. Temp and precip take the newest Open-Meteo value.
- `GET /history` serves the web chart, the phone History tab, and the green trace on Live and the widget. The cache forecast starts at the latest pull, so past half-hours come from stored history (estimated actuals, or the frozen forecast until actuals arrive). The History tab shares the Live layer chips (Solar, Temp, Precip, Buy, Sell).
- Do not add `CACHE_SECRET` back. Do not put the Solcast key in the Android app.
- Weather on the phone stays Open-Meteo and does not go through Solcast.

## Rules

- Dark solar dashboard palette in `SolColors` (web uses the same hex values)
- Version catalog for dependencies
- No Retrofit / Hilt / Room unless the app outgrows HttpURLConnection + DataStore
- No Prisma. SQL lives in `web/db/` and `web/src/lib/store.ts`
- Widget chart is a bitmap (`ChartBitmapRenderer`); the in-app chart is Compose Canvas; the web chart is a canvas
- Keep live (green) and forecast (gold) visually distinct, with a now marker
- Fixed axis ranges: solar 0–8 kW, precip 0–100%, temp -20–100°F, rates 0–40¢. No dashed 8 kW capacity line. Buy and sell are separate layer toggles and share the cent scale. That 0–40¢ axis is labeled every 10¢ when buy, sell, or both are on and solar, temperature, and precipitation are off. It is shown on the app and the website only.
- DTE Dynamic Peak Pricing (D1.8) hours live in `DteTou.kt` and `web/src/lib/dte.ts` (`America/Detroit`). Hours are year-round. Keep the cent constants in sync. The chart fills a buy area (effective import cents, dusty rose `#C9898C`) and a sell area (Rider 18 outflow plus PSCR, slate `#8AA4C4`) as steps down to 0¢, including off-peak. Each area fades from a light wash at the top of the rate scale to clear at 0¢, with a thin edge. Sell is drawn over buy. The areas sit behind solar, temperature, and precipitation. The web chart uses stored half-hour cents when present and fills gaps from the schedule. App and web Rates metadata show plan id (`D1.8` / Rider 18 Cat1), DTE marketing base cents, and this site's effective volumetric import cents from a dated bill (`RATES_AS_OF`: base + PSCR + other volumetric). Do not scrape DTE. Show cents on the app and the website, not the widget.
- Local weather on the phone is Open-Meteo only (no API key, do not use Solcast). Overlay on the power chart: precip bars + white temp line (blue glow below 32°F, orange glow above 90°F; dashed and 30% opaque at night). Hourly `weather_refresh` WorkManager + in-app hourly weather refresh. Solcast stays on Vercel and runs only on the Detroit slot schedule (8 forecasts, 2 actuals, 10 requests per UTC day). App WorkManager only re-downloads `/cache`. Do not show a current-kW hero number.
- The app Live chart uses `ForecastSnapshot.range()`: 2 local days before today through the end of 2 local days after (fixed x-axis, not data extents). The widget uses `widgetChartRange()`: the prior 48 hours through the next 72 hours. The web live view uses its own America/Detroit window. The phone History tab charts the selected Sunday–Saturday week in America/Detroit.
