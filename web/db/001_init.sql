-- One Solcast snapshot plus quota counters. Replaces the KV "snapshot" key.
CREATE TABLE IF NOT EXISTS fetch_state (
  id integer PRIMARY KEY CHECK (id = 1),
  resource_id text NOT NULL,
  fetched_at timestamptz,
  source text,
  requests_day date,
  requests_used integer NOT NULL DEFAULT 0,
  auto_fetches_used integer NOT NULL DEFAULT 0,
  actuals_fetches_used integer NOT NULL DEFAULT 0,
  forecast_slot text,
  actuals_slot text,
  actuals_fetched_at timestamptz,
  message text,
  forecasts jsonb,
  actuals jsonb,
  weather_fetched_at timestamptz
);

-- Half-hour facts. period_end is the Solcast interval end (UTC).
CREATE TABLE IF NOT EXISTS intervals (
  period_end timestamptz PRIMARY KEY,
  actual_kw double precision,
  forecast_kw double precision,
  period_hours double precision,
  temp_f double precision,
  precip_pct double precision,
  rate_band text,
  import_cents double precision,
  outflow_cents double precision,
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Sunrise and sunset captured whenever Open-Meteo is fetched.
CREATE TABLE IF NOT EXISTS sun_days (
  day date PRIMARY KEY,
  sunrise timestamptz NOT NULL,
  sunset timestamptz NOT NULL
);
