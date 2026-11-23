import { json } from "@/lib/http";
import { loadHistory } from "@/lib/store";
import { bucketMinutes, historyCacheSeconds, parseRange, parseRes } from "@/lib/historyRange";

export const dynamic = "force-dynamic";

const MAX_SPAN_MS = 366 * 24 * 60 * 60 * 1000 + 2 * 60 * 60 * 1000;

/**
 * GET /history
 *
 * - `from` and `to`: ISO instants or YYYY-MM-DD (midnight America/Detroit). Unchanged.
 * - `day=YYYY-MM-DD`: that Detroit day only (same as from=day&to=day+1).
 * - `since=<instant>`: rows strictly after that instant, through `to` when given,
 *   otherwise through the end of tomorrow in Detroit. For polling only new rows.
 * - `res=raw|15m|1h|auto`: Ecobee bucket size. Default `raw` keeps 5-minute rows,
 *   so older apps that draw a day chart from the month page are unchanged.
 *   `auto` is raw up to 2 days, 15-minute up to 8 days, hourly above that.
 *
 * The response shape is the same for every form. Successful responses carry a
 * short CDN cache so repeated identical requests do not reach Neon.
 */
export async function GET(request: Request) {
  const url = new URL(request.url);
  const range = parseRange(url.searchParams, new Date());
  if ("error" in range) return json({ error: range.error }, 400);
  const { from, to } = range;
  if (to.getTime() <= from.getTime()) return json({ error: "to must be after from" }, 400);
  if (to.getTime() - from.getTime() > MAX_SPAN_MS) {
    return json({ error: "Range is longer than 366 days" }, 400);
  }
  const res = parseRes(url.searchParams.get("res"));
  if (res === "invalid") return json({ error: "res must be raw, 15m, 1h, or auto" }, 400);
  const ecobeeBucketMinutes = bucketMinutes(res, to.getTime() - from.getTime());
  try {
    const history = await loadHistory(from.toISOString(), to.toISOString(), { ecobeeBucketMinutes });
    const response = json({ from: from.toISOString(), to: to.toISOString(), ...history });
    const ttl = historyCacheSeconds(from, to, new Date());
    response.headers.set("Cache-Control", "public, max-age=0, must-revalidate");
    response.headers.set(
      "Vercel-CDN-Cache-Control",
      `max-age=${ttl}, stale-while-revalidate=${ttl}`
    );
    return response;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return json({ error: message }, 500);
  }
}
