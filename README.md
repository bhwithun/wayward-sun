# Sol Widget

A household solar dashboard for one rooftop. It is an Android **app**, a **home-screen widget**, and a **website**. All three show how much power the array is making and what Solcast thinks it will make next. Tap the widget to open the full graph.

The main chart is the same idea as the Solcast Toolkit **Graph: Live and Forecasts** view, for a five-day window (two local days before today through two after):

- **Live** (green) — estimated actual production
- **Forecast** (gold) — predicted production
- a **now** marker

The display is **layers you can turn on or off**: solar (live + forecast), **rain / precipitation**, temperature, and **DTE rate periods** (Dynamic Peak Pricing D1.8: off-peak, mid-peak, and peak hours in America/Detroit). It is not a commercial PV monitoring portal, not a utility account, and not a generic weather app. There is no current-kW hero number; the graph is the point.

The website adds a longer view of stored history (7, 30, 90 days, or a custom range). History is half-hour rows: solar actual and forecast, the DTE rate stamped when the row was first written, temperature, and precipitation probability.

Hobbyist Solcast accounts allow **10 HTTP requests per UTC day**, and a full live+forecast pull uses **2**. Several phones and widgets would burn that quota independently. So **one Vercel app** is the only Solcast client. It pulls Solcast when something asks for the cache and that snapshot is older than 4 hours, stores the result in Neon, and every device just downloads that cache.

Default rooftop resource ID: `84d7-8b52-33f3-bd7b`.

## How Vercel and Neon are used

| Piece | Role |
|---|---|
| **Next.js app** (`web/`) | The only process that calls Solcast. Serves the dashboard at `/` and JSON at `/cache`. |
| **Neon** | Stores the latest forecasts + estimated actuals, quota counters, and half-hour history. |
| **Vercel Cron** | `GET /cron` once a day at 12:00 UTC. Same 4-hour and 5-pull cap as a phone download. |
| **Secret** | `SOLCAST_API_KEY` lives in Vercel env only. Never in git, never on the phones. |

```
Solcast API  ←  Vercel (secret + Neon, on-demand)  ←  GET /cache  ←  Android app / widget
                                      ↑
                               GET /history  ←  website chart
```

- A phone `GET /cache`, the dashboard **Ask for update** button, and the cron all pull Solcast only if the snapshot is older than 4 hours, at most 5 times per UTC day.
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

Deploy with the Vercel project rooted at `web/`. The Android default is `https://sol-widget.vercel.app`. If the project URL is different, change `AppStorage.DEFAULT_CACHE_URL` to match.

Local preview: [http://127.0.0.1:3000/](http://127.0.0.1:3000/). More detail: `web/README.md`.

## Quota

Hobbyist Solcast accounts allow **10 requests per UTC day**. Each pull uses **2** (live + forecast). The server:

- writes the last successful response to Neon
- upserts each 30-minute period into `intervals` (actuals update in place; a past forecast is kept once the period has started)
- pulls Solcast only when `/cache`, `/refresh`, or `/cron` runs and the snapshot is older than 4 hours
- auto-pulls at most **5 times per UTC day**
- refreshes Open-Meteo when the last weather write is older than 1 hour
- serves that snapshot to every device

Refresh in the Android app re-downloads `/cache`. The phone never calls Solcast.

## Setup

1. Confirm the Vercel app is deployed, `db/001_init.sql` has been applied, and `SOLCAST_API_KEY`, `DATABASE_URL`, `SITE_LAT`, and `SITE_LNG` are set.
2. Install the app. Settings default to `https://sol-widget.vercel.app`. A phone still pointed at the old Cloudflare URL is rewritten to that default on the next read. Confirm weather place, then **Save and fetch**.
3. Long-press the Android home screen → **Widgets** → **PV Live & Forecast**.

## Build

```powershell
cd C:\Users\brian\AndroidProjects\sol-widget
.\gradlew assembleDebug
.\gradlew installDebug
.\gradlew installRelease
```

## Add the widget

After install: home screen → Widgets → **Sol Widget** / **PV Live & Forecast**. Tap the widget to open the full graph.
