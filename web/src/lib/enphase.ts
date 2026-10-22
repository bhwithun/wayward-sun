import { copyFileSync, readFileSync, renameSync, writeFileSync } from "fs";
import { join } from "path";
import { stampAt } from "./dte";
import { METER_ACTUAL_SOURCE, type IntervalDraft } from "./merge";
import { loadMeterAuth, saveMeterAuth, upsertIntervals, type MeterAuth } from "./store";

const ENPHASE = "https://api.enphaseenergy.com";

/** One telemetry read per hour stays inside the free Watt plan (1,000 hits per month). */
export const HARVEST_AGE_MS = 60 * 60 * 1000;

/** Recent bins are revised as inverters report, so each pull covers the last 36 hours. */
const LOOKBACK_SEC = 36 * 60 * 60;

const FIVE_MIN = 300;
const FIFTEEN_MIN = 900;
const HALF_HOUR = 1800;

/** Access tokens last a day. Refresh a few minutes early, and persist the new refresh token. */
const TOKEN_SKEW_MS = 5 * 60 * 1000;

export type TelemetryInterval = { endAt: number; wh: number };

export type EnphaseConfig = {
  apiKey: string;
  clientId: string;
  clientSecret: string;
  redirectUri: string;
  systemId: string | null;
  refreshToken: string | null;
  accessToken: string | null;
  accessExpiresAt: string | null;
  /** Folder with credentials.json and tokens.json. The live refresh token lives here. */
  tokenDir: string | null;
};

export function enphaseConfig(): EnphaseConfig | null {
  const file = readTokenDir(process.env.ENPHASE_DIR?.trim() || "");
  const apiKey = process.env.ENPHASE_API_KEY?.trim() || file?.apiKey || "";
  const clientId = process.env.ENPHASE_CLIENT_ID?.trim() || file?.clientId || "";
  const clientSecret = process.env.ENPHASE_CLIENT_SECRET?.trim() || file?.clientSecret || "";
  if (!apiKey || !clientId || !clientSecret) return null;
  return {
    apiKey,
    clientId,
    clientSecret,
    redirectUri:
      process.env.ENPHASE_REDIRECT_URI?.trim() ||
      file?.redirectUri ||
      "https://sun.brianandkathi.com/enphase/callback",
    systemId: process.env.ENPHASE_SYSTEM_ID?.trim() || null,
    refreshToken: file?.refreshToken || process.env.ENPHASE_REFRESH_TOKEN?.trim() || null,
    accessToken: file?.accessToken || null,
    accessExpiresAt: file?.accessExpiresAt || null,
    tokenDir: file ? process.env.ENPHASE_DIR?.trim() || null : null,
  };
}

export function authorizeUrl(config: EnphaseConfig, state: string | null): string {
  const url = new URL(`${ENPHASE}/oauth/authorize`);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("client_id", config.clientId);
  url.searchParams.set("redirect_uri", config.redirectUri);
  if (state) url.searchParams.set("state", state);
  return url.toString();
}

export function planMeterHarvest(fetchedAtMs: number | null, nowMs: number, ready: boolean): boolean {
  if (!ready) return false;
  if (fetchedAtMs == null || !Number.isFinite(fetchedAtMs)) return true;
  return nowMs - fetchedAtMs >= HARVEST_AGE_MS;
}

/**
 * Roll site energy into the half-hour period ends the chart already uses.
 * A half-hour is written only when its 5-minute or 15-minute bins cover the full 30 minutes
 * and that half-hour has ended.
 */
export function halfHourActuals(
  intervals: TelemetryInterval[],
  nowMs: number
): { periodEnd: string; actualKw: number }[] {
  const sorted = intervals
    .filter((row) => Number.isFinite(row.endAt) && Number.isFinite(row.wh) && row.endAt * 1000 <= nowMs)
    .sort((a, b) => a.endAt - b.endAt);
  const secondsByEnd = new Map<number, number>();
  for (let i = 0; i < sorted.length; i += 1) {
    const prev = i > 0 ? sorted[i].endAt - sorted[i - 1].endAt : null;
    const next = i + 1 < sorted.length ? sorted[i + 1].endAt - sorted[i].endAt : null;
    const gap = knownStep(prev) ?? knownStep(next);
    if (gap != null) secondsByEnd.set(sorted[i].endAt, gap);
  }

  const buckets = new Map<number, { wh: number; seconds: number }>();
  for (const row of sorted) {
    const seconds = secondsByEnd.get(row.endAt);
    if (seconds == null) continue;
    const remainder = row.endAt % HALF_HOUR;
    const bucketEnd = remainder === 0 ? row.endAt : row.endAt + (HALF_HOUR - remainder);
    if (bucketEnd * 1000 > nowMs) continue;
    const bucket = buckets.get(bucketEnd) ?? { wh: 0, seconds: 0 };
    bucket.wh += row.wh;
    bucket.seconds += seconds;
    buckets.set(bucketEnd, bucket);
  }

  const points: { periodEnd: string; actualKw: number }[] = [];
  for (const [end, bucket] of buckets) {
    if (bucket.seconds !== HALF_HOUR) continue;
    points.push({
      periodEnd: new Date(end * 1000).toISOString(),
      actualKw: Math.round((bucket.wh / 500) * 10000) / 10000,
    });
  }
  points.sort((a, b) => a.periodEnd.localeCompare(b.periodEnd));
  return points;
}

export function readTelemetryIntervals(body: unknown): TelemetryInterval[] {
  if (!body || typeof body !== "object") return [];
  const intervals = (body as { intervals?: unknown }).intervals;
  if (!Array.isArray(intervals)) return [];
  const out: TelemetryInterval[] = [];
  for (const row of intervals) {
    if (!row || typeof row !== "object") continue;
    const record = row as { end_at?: unknown; wh_del?: unknown; enwh?: unknown };
    const endAt = Number(record.end_at);
    const wh = Number(record.wh_del ?? record.enwh);
    if (!Number.isFinite(endAt) || !Number.isFinite(wh)) continue;
    out.push({ endAt, wh });
  }
  return out;
}

export function meterDrafts(
  points: { periodEnd: string; actualKw: number }[]
): IntervalDraft[] {
  return points.map((point) => {
    const stamp = stampAt(new Date(point.periodEnd));
    return {
      periodEnd: point.periodEnd,
      actualKw: point.actualKw,
      actualSource: METER_ACTUAL_SOURCE,
      forecastKw: null,
      periodHours: 0.5,
      tempF: null,
      precipPct: null,
      rateBand: stamp.rateBand,
      importCents: stamp.importCents,
      outflowCents: stamp.outflowCents,
    };
  });
}

type TokenSet = { accessToken: string; refreshToken: string; expiresIn: number };

export function readTokenSet(body: unknown): TokenSet | null {
  if (!body || typeof body !== "object") return null;
  const record = body as { access_token?: unknown; refresh_token?: unknown; expires_in?: unknown };
  const accessToken = typeof record.access_token === "string" ? record.access_token : "";
  const refreshToken = typeof record.refresh_token === "string" ? record.refresh_token : "";
  const expiresIn = Number(record.expires_in);
  if (!accessToken || !refreshToken || !Number.isFinite(expiresIn) || expiresIn <= 0) return null;
  return { accessToken, refreshToken, expiresIn };
}

/**
 * Pull measured production when Enphase is connected and the last pull is an hour old.
 * Failures stay here so a meter error does not fail the Solcast cron.
 */
export async function harvestEnphase(now: Date): Promise<{ pulled: boolean; intervals: number }> {
  const config = enphaseConfig();
  if (!config) return { pulled: false, intervals: 0 };
  const stored = await loadMeterAuth();
  const refreshToken = config.refreshToken || stored.refreshToken;
  const firstPull = !stored.meterFetchedAt;
  if (!planMeterHarvest(stored.meterFetchedAt ? Date.parse(stored.meterFetchedAt) : null, now.getTime(), Boolean(refreshToken))) {
    return { pulled: false, intervals: 0 };
  }
  if (!refreshToken) return { pulled: false, intervals: 0 };

  try {
    const accessToken = await ensureAccessToken(config, { ...stored, refreshToken }, refreshToken, now);
    const systemId = await ensureSystemId(config, stored, accessToken);
    const started = await loadMeterAuth();
    const lookbackSec = firstPull ? 7 * 24 * 60 * 60 : LOOKBACK_SEC;
    const kind = await readProduction(config, systemId, accessToken, started.meterKind, now, lookbackSec);
    const points = halfHourActuals(readTelemetryIntervals(kind.body), now.getTime());
    const drafts = meterDrafts(points);
    if (drafts.length > 0) await upsertIntervals(drafts, now);
    const latest = await loadMeterAuth();
    await saveMeterAuth(resourceId(), {
      ...latest,
      systemId,
      meterKind: kind.kind,
      meterFetchedAt: now.toISOString(),
    });
    return { pulled: true, intervals: drafts.length };
  } catch (error) {
    // A failed hour still counts, so a down API cannot spend the monthly Watt-plan budget every 30 minutes.
    try {
      const latest = await loadMeterAuth();
      await saveMeterAuth(resourceId(), { ...latest, meterFetchedAt: now.toISOString() });
    } catch {
      // The original Enphase error is the one the cron logs.
    }
    throw error;
  }
}

export async function exchangeAuthorizationCode(config: EnphaseConfig, code: string): Promise<void> {
  const tokens = await requestTokens(config, {
    grant_type: "authorization_code",
    redirect_uri: config.redirectUri,
    code,
  });
  const now = new Date();
  writeTokenFile(config.tokenDir, tokens, now);
  const stored = await loadMeterAuth();
  const expiresAt = new Date(now.getTime() + tokens.expiresIn * 1000).toISOString();
  await saveMeterAuth(resourceId(), {
    ...stored,
    refreshToken: tokens.refreshToken,
    accessToken: tokens.accessToken,
    accessExpiresAt: expiresAt,
  });
}

async function ensureAccessToken(
  config: EnphaseConfig,
  stored: MeterAuth,
  refreshToken: string,
  now: Date
): Promise<string> {
  const fileAccess = config.accessToken;
  const fileExpires = config.accessExpiresAt ? Date.parse(config.accessExpiresAt) : 0;
  const storedExpires = stored.accessExpiresAt ? Date.parse(stored.accessExpiresAt) : 0;
  const accessToken = fileExpires >= storedExpires ? fileAccess : stored.accessToken;
  const expiresMs = Math.max(fileExpires, storedExpires);
  if (accessToken && expiresMs - now.getTime() > TOKEN_SKEW_MS) return accessToken;
  const tokens = await requestTokens(config, {
    grant_type: "refresh_token",
    refresh_token: refreshToken,
  });
  const expiresAt = new Date(now.getTime() + tokens.expiresIn * 1000).toISOString();
  writeTokenFile(config.tokenDir, tokens, now);
  await saveMeterAuth(resourceId(), {
    ...stored,
    refreshToken: tokens.refreshToken,
    accessToken: tokens.accessToken,
    accessExpiresAt: expiresAt,
  });
  return tokens.accessToken;
}

async function ensureSystemId(config: EnphaseConfig, stored: MeterAuth, accessToken: string): Promise<string> {
  if (config.systemId) return config.systemId;
  if (stored.systemId) return stored.systemId;
  const url = new URL(`${ENPHASE}/api/v4/systems`);
  url.searchParams.set("key", config.apiKey);
  const { status, body } = await enphaseFetch(url, {
    headers: { Authorization: `Bearer ${accessToken}`, key: config.apiKey },
  });
  if (status !== 200) throw new Error(enphaseError("Enphase systems", status, body));
  const systems = (body as { systems?: { system_id?: unknown }[] } | null)?.systems ?? [];
  if (systems.length !== 1) {
    throw new Error(`Enphase returned ${systems.length} systems. Set ENPHASE_SYSTEM_ID.`);
  }
  const id = String(systems[0]?.system_id ?? "");
  if (!id) throw new Error("Enphase system list had no system_id.");
  return id;
}

async function readProduction(
  config: EnphaseConfig,
  systemId: string,
  accessToken: string,
  meterKind: string | null,
  now: Date,
  lookbackSec: number
): Promise<{ kind: string | null; body: unknown }> {
  const startAt = Math.floor(now.getTime() / 1000) - lookbackSec;
  if (meterKind === "production_meter" || meterKind === "production_micro") {
    return { kind: meterKind, body: await telemetry(config, accessToken, systemId, meterKind, startAt) };
  }

  let meterBody: unknown = null;
  try {
    meterBody = await telemetry(config, accessToken, systemId, "production_meter", startAt);
  } catch {
    meterBody = null;
  }
  if (meterBody && hasProduction(meterBody)) return { kind: "production_meter", body: meterBody };

  const microBody = await telemetry(config, accessToken, systemId, "production_micro", startAt);
  return { kind: hasProduction(microBody) ? "production_micro" : null, body: microBody };
}

function hasProduction(body: unknown): boolean {
  if (readTelemetryIntervals(body).length > 0) return true;
  const devices = Number((body as { total_devices?: unknown } | null)?.total_devices);
  return Number.isFinite(devices) && devices > 0;
}

async function telemetry(
  config: EnphaseConfig,
  accessToken: string,
  systemId: string,
  kind: string,
  startAt: number
): Promise<unknown> {
  const path = kind === "production_micro"
    ? `/api/v4/systems/${encodeURIComponent(systemId)}/production_micro`
    : `/api/v4/systems/${encodeURIComponent(systemId)}/telemetry/production_meter`;
  const url = new URL(`${ENPHASE}${path}`);
  url.searchParams.set("key", config.apiKey);
  url.searchParams.set("start_at", String(startAt));
  url.searchParams.set("granularity", "week");
  const { status, body } = await enphaseFetch(url, {
    headers: { Authorization: `Bearer ${accessToken}`, key: config.apiKey },
  });
  if (status !== 200) throw new Error(enphaseError(`Enphase ${kind}`, status, body));
  return body;
}

async function requestTokens(config: EnphaseConfig, fields: Record<string, string>): Promise<TokenSet> {
  const url = new URL(`${ENPHASE}/oauth/token`);
  for (const [key, value] of Object.entries(fields)) url.searchParams.set(key, value);
  const { status, body } = await enphaseFetch(url, {
    method: "POST",
    headers: {
      Authorization: `Basic ${Buffer.from(`${config.clientId}:${config.clientSecret}`).toString("base64")}`,
    },
  });
  const tokens = readTokenSet(body);
  if (status !== 200 || !tokens) throw new Error(enphaseError("Enphase token", status, body));
  return tokens;
}

async function enphaseFetch(url: URL, init: RequestInit): Promise<{ status: number; body: unknown }> {
  const response = await fetch(url, init);
  const text = await response.text();
  if (!text) return { status: response.status, body: null };
  try {
    return { status: response.status, body: JSON.parse(text) as unknown };
  } catch {
    return { status: response.status, body: { message: text.slice(0, 180) } };
  }
}

function enphaseError(label: string, status: number, body: unknown): string {
  const record = body && typeof body === "object" ? body as { message?: unknown; error?: unknown; details?: unknown } : {};
  const detail = [record.error, record.message, record.details]
    .filter((part) => typeof part === "string" && part.length > 0)
    .join(": ")
    .slice(0, 180);
  return detail ? `${label} ${status}: ${detail}` : `${label} ${status}`;
}

function knownStep(gap: number | null): number | null {
  if (gap === FIVE_MIN || gap === FIFTEEN_MIN) return gap;
  return null;
}

function resourceId(): string {
  return process.env.RESOURCE_ID?.trim() || "84d7-8b52-33f3-bd7b";
}

type TokenFile = {
  apiKey: string;
  clientId: string;
  clientSecret: string;
  refreshToken: string | null;
  accessToken: string | null;
  accessExpiresAt: string | null;
  redirectUri: string | null;
};

function readTokenDir(dir: string): TokenFile | null {
  if (!dir) return null;
  try {
    const cred = JSON.parse(readFileSync(join(dir, "credentials.json"), "utf8")) as Record<string, unknown>;
    const tokens = JSON.parse(readFileSync(join(dir, "tokens.json"), "utf8")) as Record<string, unknown>;
    const apiKey = text(cred.api_key);
    const clientId = text(cred.client_id);
    const clientSecret = text(cred.client_secret);
    if (!apiKey || !clientId || !clientSecret) return null;
    const expiresAt = Number(tokens.expires_at);
    return {
      apiKey,
      clientId,
      clientSecret,
      refreshToken: text(tokens.refresh_token),
      accessToken: text(tokens.access_token),
      accessExpiresAt: Number.isFinite(expiresAt) && expiresAt > 0 ? new Date(expiresAt * 1000).toISOString() : null,
      redirectUri: text(tokens.redirect_uri),
    };
  } catch {
    return null;
  }
}

/** Keep the existing HomeEnergyManager token file current. A second copy would invalidate it. */
function writeTokenFile(dir: string | null, tokens: TokenSet, now: Date): void {
  if (!dir) return;
  const path = join(dir, "tokens.json");
  const current = JSON.parse(readFileSync(path, "utf8")) as Record<string, unknown>;
  const next = {
    ...current,
    access_token: tokens.accessToken,
    refresh_token: tokens.refreshToken,
    expires_in: tokens.expiresIn,
    expires_at: now.getTime() / 1000 + tokens.expiresIn,
    refreshed_at: now.getTime() / 1000,
  };
  const tmp = `${path}.tmp`;
  copyFileSync(path, `${path}.prev`);
  writeFileSync(tmp, JSON.stringify(next, null, 2));
  renameSync(tmp, path);
}

function text(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed || null;
}
