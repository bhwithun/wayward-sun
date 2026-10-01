import { json } from "@/lib/http";
import { loadHistory } from "@/lib/store";
import { parseYmd, zonedTimeToUtc } from "@/lib/time";

export const dynamic = "force-dynamic";

const MAX_SPAN_MS = 366 * 24 * 60 * 60 * 1000 + 2 * 60 * 60 * 1000;

export async function GET(request: Request) {
  const url = new URL(request.url);
  const fromRaw = url.searchParams.get("from");
  const toRaw = url.searchParams.get("to");
  if (!fromRaw || !toRaw) {
    return json({ error: "from and to are required" }, 400);
  }
  const from = parseBound(fromRaw);
  const to = parseBound(toRaw);
  if (!from || !to) return json({ error: "from and to must be ISO instants or YYYY-MM-DD" }, 400);
  if (to.getTime() <= from.getTime()) return json({ error: "to must be after from" }, 400);
  if (to.getTime() - from.getTime() > MAX_SPAN_MS) {
    return json({ error: "Range is longer than 366 days" }, 400);
  }
  try {
    const history = await loadHistory(from.toISOString(), to.toISOString());
    return json({ from: from.toISOString(), to: to.toISOString(), ...history });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return json({ error: message }, 500);
  }
}

/** A date-only value is midnight in America/Detroit. Instant values are used as given. */
function parseBound(value: string): Date | null {
  const ymd = parseYmd(value);
  if (ymd) return zonedTimeToUtc(ymd.year, ymd.month, ymd.day);
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return null;
  return parsed;
}
