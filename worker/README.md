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

Live URL: [https://solcast-cache-worker.brian-952.workers.dev](https://solcast-cache-worker.brian-952.workers.dev)

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Dashboard |
| GET | `/health` | `{ ok, runtime, deployed }` |
| GET | `/cache` | JSON snapshot for devices (public). Pulls Solcast if the snapshot is older than 4 hours. |
| POST | `/refresh` | Same on-demand rules as `GET /cache` (dashboard button) |

There is no device `PUT`. There is no cron and no force-refresh on the public URL (that would let anyone spend quota).

## Quota

- Hobbyist limit: **10** HTTP requests per UTC day
- One pull: **2** calls (forecasts + estimated actuals)
- Solcast is called only on `GET /cache` or `POST /refresh` when the snapshot is older than **4 hours**
- At most **5** such pulls per UTC day
