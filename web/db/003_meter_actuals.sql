-- Measured production from the array. Enphase wins over a Solcast estimate.
ALTER TABLE intervals ADD COLUMN IF NOT EXISTS actual_source text;

-- Tokens stay in the database because Enphase rotates the refresh token.
-- Do not select these columns from /cache or /history.
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS enphase_refresh_token text;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS enphase_access_token text;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS enphase_access_expires_at timestamptz;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS enphase_system_id text;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS meter_kind text;
ALTER TABLE fetch_state ADD COLUMN IF NOT EXISTS meter_fetched_at timestamptz;

-- The deployed upsert does not know about actual_source. Keep a measured row
-- unless this statement opts in with wayward.allow_meter.
CREATE OR REPLACE FUNCTION keep_enphase_actual()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF OLD.actual_source = 'enphase'
     AND current_setting('wayward.allow_meter', true) IS DISTINCT FROM '1'
     AND NEW.actual_kw IS DISTINCT FROM OLD.actual_kw THEN
    NEW.actual_kw := OLD.actual_kw;
    NEW.actual_source := OLD.actual_source;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS keep_enphase_actual ON intervals;
CREATE TRIGGER keep_enphase_actual
BEFORE UPDATE ON intervals
FOR EACH ROW
EXECUTE FUNCTION keep_enphase_actual();
