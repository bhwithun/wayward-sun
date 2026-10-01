# Wayward Sun

A household solar dashboard for one rooftop. It is an Android **app**, a **home-screen widget**, and a **website**. All three show how much power the array is making and what Solcast thinks it will make next. Tap the widget to open the full graph.

The main chart is the same idea as the Solcast Toolkit **Graph: Live and Forecasts** view, for a five-day window (two local days before today through two after):

- **Live** (green) — estimated actual production
- **Forecast** (gold) — predicted production
- a **now** marker

The display is **layers you can turn on or off**: solar (live + forecast), **rain / precipitation**, temperature, and **DTE rate periods** (Dynamic Peak Pricing D1.8: off-peak, mid-peak, and peak hours in America/Detroit). It is not a commercial PV monitoring portal, not a utility account, and not a generic weather app. There is no current-kW hero number; the graph is the point.

The website adds a longer view of stored history (7, 30, 90 days, or a custom range). History is half-hour rows: solar actual and forecast, the DTE rate stamped when the row was first written, temperature, and precipitation probability.

Hobbyist Solcast accounts allow **10 HTTP requests per UTC day**. Several phones and widgets would burn that quota independently. So **one Vercel app** is the only Solcast client. It calls Solcast on a Detroit schedule, stores the result in Neon, and every device just downloads that cache. See [Solcast API Utilization](#solcast-api-utilization).

Default rooftop resource ID: `84d7-8b52-33f3-bd7b`.

## How Vercel and Neon are used

| Piece | Role |
|---|---|
| **Next.js app** (`web/`) | The only process that calls Solcast. Serves the dashboard at `/` and JSON at `/cache`. |
| **Neon** | Stores the latest forecasts + estimated actuals, quota counters, and half-hour history. |
| **Neon `solpull`** | Calls `GET /cron` every 30 minutes across the awake window. Solcast runs only on the eight Detroit slots. |
| **Vercel Cron** | `GET /cron` once a day at 12:30 UTC, which is the 8:30am Detroit slot during daylight time. A slot already pulled is skipped. |
| **Secret** | `SOLCAST_API_KEY` lives in Vercel env only. Never in git, never on the phones. |

```
Solcast API  ←  Vercel (secret + Neon, on-demand)  ←  GET /cache  ←  Android app / widget
                                      ↑
                               GET /history  ←  website chart
```

- Solcast runs only from the schedule. A phone `GET /cache` downloads the stored snapshot and does not call Solcast.
- There is no `CACHE_SECRET` and no device `PUT`. The cache URL is readable by anyone who has it.
- Weather for the phone is Open-Meteo on the device. Weather stored in history is Open-Meteo from the server, using `SITE_LAT` and `SITE_LNG`.

### Web commands

```powershell
cd C:\Users\brian\AndroidProjects\sol-widget\web
npm install
npm run dev
npm test
```

Copy `web/.env.example` to `web/.env.local` and set `DATABASE_URL`, `SOLCAST_API_KEY`, `SITE_LAT`, and `SITE_LNG`. Apply `web/db/001_init.sql` on Neon. To carry today's quota across from the old Cloudflare cache: `npm run seed`.

Deploy with the Vercel project `wayward-sun`, rooted at `web/`. The Android default is `https://wayward-sun-brianandkathi.vercel.app`.

Local preview: [http://127.0.0.1:3000/](http://127.0.0.1:3000/). More detail: `web/README.md`.

## Solcast API Utilization

The server calls the hobbyist rooftop endpoints for resource `84d7-8b52-33f3-bd7b`. Manage the site in the [Solcast Toolkit](https://toolkit.solcast.com.au/).

| Call | Endpoint | Requests per UTC day | Window |
|---|---|---|---|
| Forecast | `GET /rooftop_sites/{id}/forecasts?format=json&hours=336` | 8 | 14 days |
| Estimated actuals | `GET /rooftop_sites/{id}/estimated_actuals?format=json&hours=168` | 2 | 7 days |

The account allows **10 HTTP requests per UTC day**. Those 10 are all used:

| Detroit time | Forecast | Actuals | HTTP calls |
|---|---|---|---|
| 6:00am | yes | yes | 2 |
| 8:30am | yes | | 1 |
| 11:00am | yes | | 1 |
| 1:30pm | yes | | 1 |
| 4:00pm | yes | | 1 |
| 6:30pm | yes | yes | 2 |
| 9:00pm | yes | | 1 |
| 11:30pm | yes | | 1 |

Nothing calls Solcast overnight, and nothing in the app or on the website can request a pull. The UTC day changes at 8:00pm Detroit during daylight time and at 7:00pm during standard time, so the 9:00pm and 11:30pm calls are the first two requests of the new UTC day. The morning and afternoon slots of that same UTC day use the other eight. Each UTC day is still 8 forecasts and 2 actuals.

Neon wakes `/cron` every 30 minutes through the hours that contain 6:00am–11:30pm Detroit in both daylight and standard time. The route pulls only when the Detroit clock is on one of the times above, and a second wake in the same slot does not call again.

Each response is written to Neon. Every 30-minute period is upserted into `intervals`: a new actual replaces the stored actual, and a forecast is replaced only while that period is still in the future. Open-Meteo refreshes when the last weather write is older than 1 hour and does not count against this quota. Phones and the widget download the stored snapshot with `GET /cache`.

## Setup

1. Confirm the Vercel app is deployed, `db/001_init.sql` has been applied, and `SOLCAST_API_KEY`, `DATABASE_URL`, `SITE_LAT`, and `SITE_LNG` are set.
2. Install the app. Settings default to `https://wayward-sun-brianandkathi.vercel.app`. A phone still pointed at the old Cloudflare or `sol-widget` Vercel URL is rewritten to that default on the next read. Confirm weather place, then **Save and fetch**.
3. Long-press the Android home screen → **Widgets** → **Wayward Sun**.

## Build

```powershell
cd C:\Users\brian\AndroidProjects\sol-widget
.\gradlew assembleDebug
.\gradlew installDebug
.\gradlew installRelease
```

## Add the widget

After install: home screen → Widgets → **Wayward Sun**. Tap the widget to open the full graph.
