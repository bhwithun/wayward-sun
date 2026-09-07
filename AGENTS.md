# Sol Widget — Agent Instructions

Pure Android app (Kotlin, Compose, Material 3, Glance widget) bootstrapped from `_template`, plus a Cloudflare Worker that is the only Solcast client.

## Domain

- Package: `com.brian.solwidget`
- Solcast hobbyist rooftop endpoints only (not the commercial lat/lng PV power API)
- Default resource ID: `84d7-8b52-33f3-bd7b`
- Default cache URL: `https://solcast-cache-worker.brian-952.workers.dev` (`AppStorage.DEFAULT_CACHE_URL`)
- Never hardcode a Solcast API key. Never put it in the APK, git, `wrangler.jsonc`, or `.dev.vars` committed to source. Store it only as the Wrangler secret `SOLCAST_API_KEY`.
- The Worker (`worker/`) is the **only** Solcast HTTP client. The Android app and widget only `GET /cache` (`CacheApi`). They must not call `api.solcast.com.au`.
- No `CACHE_SECRET`. No device `PUT /cache`. Cache JSON is public at the Worker URL.
- Daily hobbyist limit is 10 HTTP requests. A full Worker pull is 2 HTTP calls. The Worker calls Solcast only on `GET /cache` or `POST /refresh` when the snapshot is older than `MIN_AUTO_AGE` (4 hours), capped at 5 cycles per UTC day (`DAILY_AUTO_LIMIT`). There is no cron and no public or device-side Solcast force refresh.

## Layout

```
com.brian.solwidget/
├── data/          # AppStorage, CacheApi, SolcastApi (parse only), ForecastRepository, models
├── ui/            # screens, PowerChart, theme
├── viewmodel/
├── widget/        # Glance ForecastWidget
├── work/          # 5-hour WorkManager cache download; hourly weather
└── util/

worker/            # Cloudflare Worker solcast-cache-worker (Wrangler)
├── src/index.js   # fetch; on-demand Solcast + KV
├── src/page.js    # public dashboard
└── wrangler.jsonc # KV binding CACHE; no cron
```

## Cloudflare

- Worker name: `solcast-cache-worker`. KV binding: `CACHE` (one key, `snapshot`). No cron; Solcast is on-demand from `GET /cache`.
- Public routes: `GET /`, `GET /health`, `GET /cache`, `POST /refresh` (pull only if snapshot older than 4 hours).
- Deploy from `worker/`: `npx wrangler deploy`. Do not recreate the app from a dashboard Hello World template.
- Do not add `CACHE_SECRET` back. Do not share this KV with other Cloudflare apps.
- Weather stays Open-Meteo from the phone; it does not go through the Worker.

## Rules

- Dark solar dashboard palette in `SolColors`
- Version catalog for dependencies
- No Retrofit / Hilt / Room unless the app outgrows HttpURLConnection + DataStore
- Widget chart is a bitmap (`ChartBitmapRenderer`); the in-app chart is Compose Canvas
- Keep live (green) and forecast (gold) visually distinct, with a now marker
- Fixed axis ranges: solar 0–8 kW, precip 0–100%, temp -20–100°F. No dashed 8 kW capacity line.
- DTE Dynamic Peak Pricing (D1.8) bands live in `DteTou.kt` (`America/Detroit`). Hours are year-round. App Rates metadata shows plan id (`D1.8` / Rider 18 Cat1), DTE marketing base cents, and this site's effective volumetric import cents from a dated bill (`RATES_AS_OF`: base + PSCR + other volumetric). Do not scrape DTE. Show cents on the app only, not the widget.
- Local weather is Open-Meteo only (no API key, do not use Solcast). Overlay on the power chart: precip bars + white temp line (blue glow below 32°F, orange glow above 90°F; dashed and 30% opaque at night). Hourly `weather_refresh` WorkManager + in-app hourly weather refresh. Solcast stays on the Worker (on-demand when `/cache` is fetched and the snapshot is older than 4 hours, max 5 auto pulls per UTC day). App WorkManager only re-downloads `/cache`. Do not show a current-kW hero number.
- App and widget charts share `ForecastSnapshot.range()`: 2 local days before today through the end of 2 local days after (fixed x-axis, not data extents)
