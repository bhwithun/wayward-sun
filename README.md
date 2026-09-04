# Sol Widget

Android app + home-screen widget for a home PV system on [Solcast](https://toolkit.solcast.com.au/).

It plots the same series as the toolkit **Graph: Live and Forecasts** view:

- **Live** — rooftop `estimated_actuals`
- **Forecast** — rooftop `forecasts`

Default rooftop resource ID: `84d7-8b52-33f3-bd7b`.

Phones never call Solcast. A small Cloudflare Worker is the only Solcast client; every device downloads a shared cache.

## How Cloudflare is used

Cloudflare hosts one Worker named **`sol-cache`**. Think of it as a tiny program behind an HTTPS URL, plus a key/value store, plus a clock.

| Piece | Role |
|---|---|
| **Worker** (`worker/`) | The only process that calls Solcast. Serves a dashboard at `/` and JSON at `/cache`. |
| **Workers KV** | Stores the latest forecasts + estimated actuals (`snapshot` key). |
| **Cron** | `0 0,6,12,18 * * *` UTC — up to four automatic pulls per UTC day. |
| **Wrangler** | CLI to run the Worker locally (`npm run dev`) and deploy it (`npx wrangler deploy`). |
| **Secret** | `SOLCAST_API_KEY` lives on Cloudflare only (`npx wrangler secret put SOLCAST_API_KEY`). Never in git, never on the phones. |

Live URL: [https://sol-cache.brian-952.workers.dev](https://sol-cache.brian-952.workers.dev)

```
Solcast API  ←  Worker (secret + cron + KV)  ←  GET /cache  ←  Android app / widget
```

- **Ask for update** on the Worker dashboard (`POST /refresh`) uses the same 6-hour / 4-per-day rules as cron. There is no public force-refresh, so strangers with the URL cannot dump the hobbyist quota.
- There is no `CACHE_SECRET` and no device `PUT`. The cache URL is readable by anyone who has it.
- Weather (Open-Meteo) does **not** go through Cloudflare or Solcast.

Account layout: one Worker, one KV namespace, one cron trigger, named `sol-cache`. Keep other Cloudflare apps as separate Workers.

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
- auto-pulls at most **4 times per UTC day** (about every 6 hours)
- skips Solcast if the snapshot is younger than 6 hours
- serves that snapshot to every device

Refresh in the Android app re-downloads `/cache`. It does not call Solcast.

## Setup

1. Confirm the Worker is deployed and has `SOLCAST_API_KEY`.
2. Install the app. Settings default to `https://sol-cache.brian-952.workers.dev`. Confirm weather place, then **Save and fetch**.
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
