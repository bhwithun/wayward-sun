import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

export function GET() {
  const deployed = process.env.VERCEL === "1";
  return json({
    ok: true,
    runtime: deployed ? "vercel" : "local-next",
    deployed,
  });
}
