import { emptyCors, json } from "@/lib/http";
import { serveCache } from "@/lib/solcast";
import { publicCache } from "@/lib/store";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

export function OPTIONS() {
  return emptyCors();
}

export async function GET() {
  try {
    const response = json(publicCache(await serveCache()));
    // One Neon read per minute at most for every phone and the widget worker.
    response.headers.set("Cache-Control", "public, max-age=0, must-revalidate");
    response.headers.set("Vercel-CDN-Cache-Control", "max-age=60, stale-while-revalidate=60");
    return response;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return json({ error: message }, 500);
  }
}
