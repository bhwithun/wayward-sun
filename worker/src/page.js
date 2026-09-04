import { combinedPoints } from "./demo.js";

const NAVY = "#0B1220";
const PANEL = "#152036";
const PANEL_ALT = "#1C2B45";
const LIVE = "#2EE6A6";
const FORECAST = "#F5C542";
const NOW = "#FF8A4C";
const INK = "#E8EEF7";
const MUTED = "#9AA8BF";

export function renderPage(snapshot, env, { local = false } = {}) {
  const points = combinedPoints(snapshot);
  const chart = svgChart(points, snapshot?.source === "demo");
  const fetched = snapshot?.fetchedAt
    ? new Date(snapshot.fetchedAt).toLocaleString()
    : "never";
  const hasKey = Boolean(env.SOLCAST_API_KEY);
  const where = local
    ? "This page is served by a Cloudflare Worker running <strong>on this computer</strong> via Wrangler."
    : "This page is served by a Cloudflare Worker on <strong>Cloudflare’s network</strong>. Phones only download <code>GET /cache</code>. Only this Worker calls Solcast.";

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Sol cache Worker</title>
  <style>
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body {
      margin: 0; font-family: ui-sans-serif, system-ui, sans-serif;
      background: ${NAVY}; color: ${INK}; line-height: 1.45;
    }
    main { max-width: 960px; margin: 0 auto; padding: 28px 20px 64px; }
    h1 { font-size: 1.4rem; font-weight: 650; margin: 0 0 6px; }
    p { color: ${MUTED}; margin: 0 0 12px; }
    .banner {
      background: ${PANEL}; border: 1px solid ${PANEL_ALT};
      border-left: 4px solid ${FORECAST}; padding: 12px 14px; margin: 16px 0 22px;
      font-size: 0.92rem; color: ${INK};
    }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    @media (max-width: 720px) { .grid { grid-template-columns: 1fr; } }
    .card {
      background: ${PANEL}; border-radius: 12px; padding: 14px 16px;
    }
    .card h2 { font-size: 0.78rem; letter-spacing: 0.06em; text-transform: uppercase;
      color: ${MUTED}; margin: 0 0 10px; font-weight: 600; }
    .stat { font-size: 1.35rem; font-weight: 650; }
    .muted { color: ${MUTED}; font-size: 0.85rem; }
    .row { display: flex; gap: 8px; flex-wrap: wrap; margin: 16px 0; }
    button {
      background: ${PANEL_ALT}; color: ${INK}; border: 0; border-radius: 8px;
      padding: 8px 14px; font-weight: 600; cursor: pointer;
    }
    button.gold { background: ${FORECAST}; color: ${NAVY}; }
    pre {
      background: ${PANEL_ALT}; color: ${INK}; padding: 12px; border-radius: 8px;
      overflow: auto; font-size: 0.78rem; max-height: 280px;
    }
    .legend span { margin-right: 14px; font-size: 0.85rem; }
    svg { width: 100%; height: auto; display: block; }
    code { color: ${FORECAST}; }
    a { color: ${LIVE}; }
    #status { min-height: 1.2em; color: ${FORECAST}; font-size: 0.9rem; }
  </style>
</head>
<body>
  <main>
    <h1>Shared Solcast cache</h1>
    <p>${where}</p>
    <div class="banner">
      Automatic pulls are capped at 4 per UTC day (about every 6 hours) and 10 Solcast HTTP requests.
      The dashboard Refresh button uses those same rules — it cannot bypass them, so a public URL cannot burn extra quota.
    </div>
    <div class="grid">
      <div class="card">
        <h2>Snapshot</h2>
        <div class="stat">${escapeHtml(snapshot?.source || "empty")}</div>
        <div class="muted">as of ${escapeHtml(fetched)}</div>
        <div class="muted" style="margin-top:8px">resource ${escapeHtml(snapshot?.resourceId || env.RESOURCE_ID)}</div>
      </div>
      <div class="card">
        <h2>Quota (UTC day ${escapeHtml(snapshot?.requestsDay || "—")})</h2>
        <div class="stat">${snapshot?.requestsUsed ?? 0} / 10 requests</div>
        <div class="muted">${snapshot?.autoFetchesUsed ?? 0} / 4 automatic pulls</div>
        <div class="muted" style="margin-top:8px">Solcast key in Worker: ${hasKey ? "yes" : "no (demo only)"}</div>
      </div>
    </div>
    <div class="card" style="margin-top:12px">
      <h2>Power (kW)</h2>
      <div class="legend" style="margin-bottom:8px">
        <span style="color:${LIVE}">● Live</span>
        <span style="color:${FORECAST}">● Forecast</span>
        <span style="color:${NOW}">● Now</span>
      </div>
      ${chart}
    </div>
    <div class="row">
      <button class="gold" data-action="refresh">Ask for update</button>
    </div>
    <p id="status"></p>
    <div class="card">
      <h2>What phones download — GET /cache</h2>
      <pre id="json">${escapeHtml(previewJson(snapshot))}</pre>
    </div>
    <p class="muted" style="margin-top:18px">
      Public API: <code>GET /cache</code>, <code>POST /refresh</code> (auto rules only).
      No shared password. Solcast credentials stay on the Worker.
    </p>
  </main>
  <script>
    const statusEl = document.getElementById("status");
    document.querySelector("[data-action=refresh]").addEventListener("click", async () => {
      statusEl.textContent = "Working…";
      const res = await fetch("/refresh", { method: "POST" });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        statusEl.textContent = body.error || ("HTTP " + res.status);
        return;
      }
      if (body.message && /skipped|limit reached/i.test(body.message)) {
        statusEl.textContent = body.message;
        return;
      }
      location.reload();
    });
  </script>
</body>
</html>`;
}

function previewJson(snapshot) {
  if (!snapshot) return "{}";
  const copy = {
    resourceId: snapshot.resourceId,
    fetchedAt: snapshot.fetchedAt,
    source: snapshot.source,
    requestsUsed: snapshot.requestsUsed,
    autoFetchesUsed: snapshot.autoFetchesUsed,
    requestsDay: snapshot.requestsDay,
    message: snapshot.message,
    actualsCount: snapshot.actuals?.estimated_actuals?.length ?? 0,
    forecastsCount: snapshot.forecasts?.forecasts?.length ?? 0,
    actualsHead: (snapshot.actuals?.estimated_actuals || []).slice(0, 2),
    forecastsHead: (snapshot.forecasts?.forecasts || []).slice(0, 2),
  };
  return JSON.stringify(copy, null, 2);
}

function svgChart(points, dotted = false) {
  const w = 880;
  const h = 220;
  const padL = 36;
  const padR = 12;
  const padT = 12;
  const padB = 24;
  const now = Date.now();
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - 2);
  const end = new Date();
  end.setHours(0, 0, 0, 0);
  end.setDate(end.getDate() + 3);
  const x0 = start.getTime();
  const x1 = end.getTime();
  const yMin = 0;
  const yMax = 8;
  const xOf = (t) => padL + ((t - x0) / (x1 - x0)) * (w - padL - padR);
  const yOf = (kw) => padT + (1 - (kw - yMin) / (yMax - yMin)) * (h - padT - padB);

  const live = points.filter((p) => p.kind === "live");
  const forecast = points.filter((p) => p.kind === "forecast");
  const path = (series) => {
    if (!series.length) return "";
    return series
      .map((p, i) => `${i === 0 ? "M" : "L"}${xOf(p.t).toFixed(1)} ${yOf(p.kw).toFixed(1)}`)
      .join(" ");
  };

  const ticks = [0, 2, 4, 6, 8]
    .map((kw) => {
      const y = yOf(kw);
      return `<line x1="${padL}" x2="${w - padR}" y1="${y}" y2="${y}" stroke="#4A628833" />
        <text x="4" y="${y + 4}" fill="${MUTED}" font-size="11">${kw}</text>`;
    })
    .join("");

  const nx = xOf(now);
  const mark = dotted
    ? `<text x="${w / 2}" y="${h / 2}" fill="${INK}" fill-opacity="0.22" font-size="52" font-weight="700" text-anchor="middle" transform="rotate(-22 ${w / 2} ${h / 2})">DEMO DATA</text>`
    : "";
  return `<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="Power chart">
    <rect width="${w}" height="${h}" fill="${PANEL_ALT}" rx="8" />
    ${ticks}
    <path d="${path(live)}" fill="none" stroke="${LIVE}" stroke-width="2.2" stroke-linecap="round" ${dotted ? 'stroke-dasharray="4 7"' : ""} />
    <path d="${path(forecast)}" fill="none" stroke="${FORECAST}" stroke-width="2.2" stroke-linecap="round" ${dotted ? 'stroke-dasharray="4 7"' : ""} />
    <line x1="${nx}" x2="${nx}" y1="${padT}" y2="${h - padB}" stroke="${NOW}" stroke-width="1.5" />
    ${mark}
  </svg>`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
