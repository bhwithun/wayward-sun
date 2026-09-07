# Sol Widget

A household solar dashboard for one rooftop. It is an Android **app** plus a **home-screen widget**. Both show how much power the array is making and what Solcast thinks it will make next. Tap the widget to open the full graph.

The main chart is the same idea as the Solcast Toolkit **Graph: Live and Forecasts** view, for a five-day window (two local days before today through two after):

- **Live** (green) — estimated actual production
- **Forecast** (gold) — predicted production
- a **now** marker

The display is **layers you can turn on or off** individually in the app and on the widget: solar (live + forecast), **rain / precipitation**, temperature, and **DTE rate periods** (Dynamic Peak Pricing D1.8: off-peak, mid-peak, and peak hours in America/Detroit). It is not a commercial PV monitoring portal, not a utility account, and not a generic weather app. There is no current-kW hero number; the graph is the point.

Hobbyist Solcast accounts allow **10 HTTP requests per UTC day**, and a full live+forecast pull uses **2**. Several phones and widgets would burn that quota independently. So **one Cloudflare Worker** is the only Solcast client. It pulls Solcast when a phone downloads the cache and that snapshot is older than 4 hours, stores the result, and every device just downloads that cache.

Default rooftop resource ID: `84d7-8b52-33f3-bd7b`.

## How Cloudflare is used

Cloudflare hosts one Worker named **`solcast-cache-worker`**. Think of it as a tiny program behind an HTTPS URL, plus a key/value store.

| Piece | Role |
|---|---|
| **Worker** (`worker/`) | The only process that calls Solcast. Serves a dashboard at `/` and JSON at `/cache`. |
| **Workers KV** | Stores the latest forecasts + estimated actuals (`snapshot` key). |
| **Wrangler** | CLI to run the Worker locally (`npm run dev`) and deploy it (`npx wrangler deploy`). |
| **Secret** | `SOLCAST_API_KEY` lives on Cloudflare only (`npx wrangler secret put SOLCAST_API_KEY`). Never in git, never on the phones. |

Live URL: [https://solcast-cache-worker.brian-952.workers.dev](https://solcast-cache-worker.brian-952.workers.dev)

```
Solcast API  ←  Worker (secret + KV, on-demand)  ←  GET /cache  ←  Android app / widget
```

- A phone `GET /cache` (and the dashboard **Ask for update** button) pulls Solcast only if the snapshot is older than 4 hours, at most 5 times per UTC day. There is no cron and no public force-refresh, so strangers with the URL cannot dump the hobbyist quota.
- There is no `CACHE_SECRET` and no device `PUT`. The cache URL is readable by anyone who has it.
- Weather (Open-Meteo) does **not** go through Cloudflare or Solcast.

Account layout: one Worker named `solcast-cache-worker`, one KV namespace (`sol-cache-cache`). Keep other Cloudflare apps as separate Workers.

### Worker commands

```powershell
cd C:\Users\brian\GrokProjects\sol-widget\worker
npm install
npm run dev
npx wrangler login
npx wrangler deploy
npx wrangler secret put SOLCAST_API_KEY
```

Local preview: [http://127.0.0.1:8787/](http://127.0.0.1:8787/). More Worker detail: `worker/README.md`.

## Quota

Hobbyist Solcast accounts allow **10 requests per UTC day**. Each Worker pull uses **2** (live + forecast). The Worker:

- writes the last successful response to KV
- pulls Solcast only when a device downloads `/cache` and the snapshot is older than 4 hours
- auto-pulls at most **5 times per UTC day**
- serves that snapshot to every device

Refresh in the Android app re-downloads `/cache`. The phone never calls Solcast; the Worker may, if the snapshot is stale.

## Setup

1. Confirm the Worker is deployed and has `SOLCAST_API_KEY`.
2. Install the app. Settings default to `https://solcast-cache-worker.brian-952.workers.dev`. Confirm weather place, then **Save and fetch**.
3. Long-press the Android home screen → **Widgets** → **PV Live & Forecast**.

## Build

```powershell
cd C:\Users\brian\GrokProjects\sol-widget
.\gradlew assembleDebug
.\gradlew installDebug
.\gradlew installRelease
```

## Add the widget

After install: home screen → Widgets → **Sol Widget** / **PV Live & Forecast**. Tap the widget to open the full graph.
