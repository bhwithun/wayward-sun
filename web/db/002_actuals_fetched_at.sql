-- Slot bookkeeping for the scheduled Solcast pulls.
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS actuals_fetches_used integer NOT NULL DEFAULT 0;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS forecast_slot text;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS actuals_slot text;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS actuals_fetched_at timestamptz;
