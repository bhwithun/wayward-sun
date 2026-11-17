-- HVAC runtime from the Ecobee portal Download Data export.
-- Seconds are the equipment run time inside that 5-minute row.
-- Additive: older rows stay null until a file that has these columns is imported.
-- heat_pump_sec is Heat Stage 1 and aux_heat_sec is Aux Heat 1 (gas furnace) on older exports.
-- After the portal dropped Aux Heat 1, Heat Stage 1 is classified by System Mode
-- (compressor* -> heat pump; heatStage1On / heatOff -> furnace). cool_sec is Cool Stage 1.

ALTER TABLE ecobee_samples
  ADD COLUMN IF NOT EXISTS heat_pump_sec integer,
  ADD COLUMN IF NOT EXISTS aux_heat_sec integer,
  ADD COLUMN IF NOT EXISTS cool_sec integer,
  ADD COLUMN IF NOT EXISTS fan_sec integer,
  ADD COLUMN IF NOT EXISTS outdoor_f double precision;
