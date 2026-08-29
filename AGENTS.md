# Sol Widget — Agent Instructions

Pure Android app (Kotlin, Compose, Material 3, Glance widget) bootstrapped from `_template`.

## Domain

- Package: `com.brian.solwidget`
- Solcast hobbyist rooftop endpoints only (not the commercial lat/lng PV power API)
- Default resource ID: `84d7-8b52-33f3-bd7b`
- Never hardcode a Solcast API key
- Daily hobbyist limit is 10 HTTP requests. A full refresh is 2 HTTP calls. Automatic Solcast pulls are capped at 4 cycles per UTC day (`DAILY_AUTO_LIMIT`); manual Refresh is exempt from that 4. Cache + `MIN_AUTO_AGE` (6 hours).

## Layout

```
com.brian.solwidget/
├── data/          # AppStorage, SolcastApi, ForecastRepository, models
├── ui/            # screens, PowerChart, theme
├── viewmodel/
├── widget/        # Glance ForecastWidget
├── work/          # 6-hour WorkManager refresh
└── util/
```

## Rules

- Dark solar dashboard palette in `SolColors`
- Version catalog for dependencies
- No Retrofit / Hilt / Room unless the app outgrows HttpURLConnection + DataStore
- Widget chart is a bitmap (`ChartBitmapRenderer`); the in-app chart is Compose Canvas
- Keep live (green) and forecast (gold) visually distinct, with a now marker
- Fixed axis ranges: solar 0–8 kW, precip 0–100%, temp -20–100°F. No dashed 8 kW capacity line.
- DTE Dynamic Peak Pricing (D1.8) bands live in `DteTou.kt` (`America/Detroit`). Hours are year-round. App Rates metadata shows plan id (`D1.8` / Rider 18 Cat1), DTE marketing base cents, and this site's effective volumetric import cents from a dated bill (`RATES_AS_OF`: base + PSCR + other volumetric). Do not scrape DTE. Show cents on the app only, not the widget.
- Local weather is Open-Meteo only (no API key, do not use Solcast). Overlay on the power chart: precip bars + thin white temp line (blue below 32°F). Hourly `weather_refresh` WorkManager + in-app hourly weather refresh; Solcast stays 6h / 4 auto pulls. Do not show a current-kW hero number.
- App and widget charts share `ForecastSnapshot.range()`: 2 local days before today through the end of 2 local days after (fixed x-axis, not data extents)
