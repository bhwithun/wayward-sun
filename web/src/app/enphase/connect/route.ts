import { authorizeUrl, enphaseConfig } from "@/lib/enphase";

export const dynamic = "force-dynamic";

/** One-time homeowner grant. Open this in a browser after the Enphase app credentials are set. */
export async function GET() {
  const config = enphaseConfig();
  if (!config) {
    return new Response("Enphase app credentials are not set.", { status: 404 });
  }
  return Response.redirect(authorizeUrl(config, process.env.CRON_SECRET?.trim() || null), 302);
}
