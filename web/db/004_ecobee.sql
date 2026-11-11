-- 5-minute Ecobee thermostat samples.
-- sampled_at is the UTC instant of the America/New_York wall clock in the export.
-- The import upserts on this key, so a later export can correct or extend the table.
-- Run this on production only after a backup. The preview branch already has it.

CREATE TABLE IF NOT EXISTS ecobee_samples (
  sampled_at timestamptz PRIMARY KEY,
  indoor_f double precision NOT NULL,
  cool_set_f double precision,
  heat_set_f double precision,
  system_mode text
);
