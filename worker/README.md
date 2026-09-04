# Sol cache Worker

This Worker is the **only** Solcast client. Phones download `GET /cache`. They never send a Solcast API key and never call Solcast.

`SOLCAST_API_KEY` is a Wrangler secret on Cloudflare. There is no `CACHE_SECRET`.

## Run locally

```powershell
cd C:\Users\brian\GrokProjects\sol-widget\worker
npm install
npm run dev
```

Open [http://127.0.0.1:8787/](http://127.0.0.1:8787/).

## Deploy

```powershell
npx wrangler login
npx wrangler deploy
npx wrangler secret put SOLCAST_API_KEY
```

Live URL: [https://sol-cache.brian-952.workers.dev](https://sol-cache.brian-952.workers.dev)

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Dashboard |
| GET | `/health` | `{ ok, runtime, deployed }` |
| GET | `/cache` | JSON snapshot for devices (public) |
| POST | `/refresh` | Try an automatic Solcast pull (same 6h / 4-per-day rules as cron) |

There is no device `PUT`. There is no force-refresh on the public URL (that would let anyone spend quota).

## Quota

- Hobbyist limit: **10** HTTP requests per UTC day
- One pull: **2** calls (forecasts + estimated actuals)
- Automatic pulls: at most **4** per UTC day, skipped if the cache is younger than **6 hours**
- Cron: `0 0,6,12,18 * * *` (UTC)
