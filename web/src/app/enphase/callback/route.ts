import { enphaseConfig, exchangeAuthorizationCode } from "@/lib/enphase";

export const dynamic = "force-dynamic";

/** Enphase redirects here with a one-time code. Tokens are stored and are not shown. */
export async function GET(request: Request) {
  const url = new URL(request.url);
  const denied = url.searchParams.get("error");
  if (denied) return text(`Enphase did not authorize access (${denied.slice(0, 40)}).`, 400);

  const secret = process.env.CRON_SECRET?.trim();
  if (secret && url.searchParams.get("state") !== secret) {
    return text("Enphase returned an unexpected state.", 400);
  }

  const code = url.searchParams.get("code")?.trim();
  const config = enphaseConfig();
  if (!code || !config) return text("Enphase did not return a usable authorization code.", 400);

  try {
    await exchangeAuthorizationCode(config, code);
  } catch (error) {
    const reason = error instanceof Error ? error.message : "token exchange failed";
    return text(reason.slice(0, 200), 502);
  }
  return text("Enphase is connected. Measured production joins the next scheduled pull.");
}

function text(message: string, status = 200) {
  return new Response(message, { status, headers: { "content-type": "text/plain; charset=utf-8" } });
}
