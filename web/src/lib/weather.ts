import { zonedTimeToUtc, type Ymd } from "./time";
import type { WeatherHour } from "./drafts";

const FORECAST = "https://api.open-meteo.com/v1/forecast";

export type SunDay = {
  day: string;
  sunrise: string;
  sunset: string;
};

export type WeatherFetch = {
  hours: WeatherHour[];
  sun: SunDay[];
};

export function siteCoordinates(): { lat: number; lng: number } | null {
  const lat = Number(process.env.SITE_LAT);
  const lng = Number(process.env.SITE_LNG);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
  return { lat, lng };
}

export async function fetchSiteWeather(): Promise<WeatherFetch | null> {
  const site = siteCoordinates();
  if (!site) return null;
  const url =
    `${FORECAST}?latitude=${site.lat}&longitude=${site.lng}` +
    "&hourly=temperature_2m,precipitation_probability" +
    "&daily=sunrise,sunset" +
    "&temperature_unit=fahrenheit" +
    "&past_days=2&forecast_days=3" +
    "&timezone=America%2FDetroit";
  const response = await fetch(url, {
    headers: { Accept: "application/json", "User-Agent": "WaywardSun/2.0" },
  });
  const body = await response.text();
  if (!response.ok) {
    throw new Error(`Open-Meteo HTTP ${response.status}: ${body.slice(0, 180)}`);
  }
  return parseOpenMeteo(body);
}

export function parseOpenMeteo(json: string): WeatherFetch {
  const root = JSON.parse(json) as {
    hourly?: {
      time?: string[];
      temperature_2m?: Array<number | null>;
      precipitation_probability?: Array<number | null>;
    };
    daily?: {
      time?: string[];
      sunrise?: string[];
      sunset?: string[];
    };
  };
  const hours: WeatherHour[] = [];
  const times = root.hourly?.time ?? [];
  const temps = root.hourly?.temperature_2m ?? [];
  const pops = root.hourly?.precipitation_probability ?? [];
  for (let i = 0; i < times.length; i++) {
    const key = hourKey(times[i]);
    const temp = temps[i];
    if (!key || typeof temp !== "number" || !Number.isFinite(temp)) continue;
    const pop = pops[i];
    hours.push({
      key,
      tempF: temp,
      precipPct: typeof pop === "number" && Number.isFinite(pop) ? pop : null,
    });
  }

  const sun: SunDay[] = [];
  const days = root.daily?.time ?? [];
  const rise = root.daily?.sunrise ?? [];
  const set = root.daily?.sunset ?? [];
  for (let i = 0; i < days.length; i++) {
    const day = days[i]?.slice(0, 10);
    const sunrise = parseLocal(rise[i]);
    const sunset = parseLocal(set[i]);
    if (!day || !sunrise || !sunset) continue;
    sun.push({ day, sunrise: sunrise.toISOString(), sunset: sunset.toISOString() });
  }
  return { hours, sun };
}

function hourKey(value: string | undefined): string | null {
  if (!value || value.length < 13) return null;
  return value.slice(0, 13);
}

function parseLocal(value: string | undefined): Date | null {
  if (!value) return null;
  if (value.endsWith("Z") || /[+-]\d{2}:\d{2}$/.test(value)) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  if (!match) return null;
  const ymd: Ymd = {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
  };
  return zonedTimeToUtc(ymd.year, ymd.month, ymd.day, Number(match[4]), Number(match[5]));
}
