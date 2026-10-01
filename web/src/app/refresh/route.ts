import { emptyCors, json } from "@/lib/http";
import { serveCache } from "@/lib/solcast";
import { publicCache } from "@/lib/store";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

export function OPTIONS() {
  return emptyCors();
}

export async function POST() {
  try {
    return json(publicCache(await serveCache()));
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return json({ error: message }, 500);
  }
}
