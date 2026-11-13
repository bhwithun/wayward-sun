-- Whole-house energy from the Tesla Energy Gateway.
-- A separate loader fills these tables. This app does not call Tesla and does not apply this file.
-- period_end is the 30-minute bucket END, on the same axis as intervals.period_end.
-- house_energy_day.day is an America/Detroit date.

CREATE TABLE house_energy (
  period_end timestamptz PRIMARY KEY,  -- 30-min bucket END, same axis as intervals.period_end
  home_wh double precision NOT NULL,
  from_solar_wh double precision NOT NULL DEFAULT 0,
  from_battery_wh double precision NOT NULL DEFAULT 0,
  from_grid_wh double precision NOT NULL DEFAULT 0,
  complete boolean NOT NULL DEFAULT true,
  source_period text,
  fetched_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE house_energy_day (
  day date PRIMARY KEY,  -- America/Detroit date
  home_wh double precision NOT NULL,
  from_solar_wh double precision NOT NULL DEFAULT 0,
  from_battery_wh double precision NOT NULL DEFAULT 0,
  from_grid_wh double precision NOT NULL DEFAULT 0,
  has_half_hour boolean NOT NULL DEFAULT false,
  fetched_at timestamptz NOT NULL DEFAULT now()
);
