import { json } from "@/lib/http";
import { serveCache } from "@/lib/solcast";
import { publicCache } from "@/lib/store";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

/** Vercel Hobby cron and the Neon solpull function. Same Solcast gate as GET /cache. */
export async function GET(request: Request) {
  const secret = process.env.CRON_SECRET;
  if (secret) {
    const header = request.headers.get("authorization");
    if (header !== `Bearer ${secret}`) {
      return json({ error: "Unauthorized" }, 401);
    }
  }
  try {
    const snapshot = await serveCache();
    return json({ ok: true, ...publicCache(snapshot) });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return json({ error: message }, 500);
  }
}
