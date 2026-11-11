/**
 * Load an Ecobee 5-minute export into ecobee_samples.
 *
 * Re-runnable: rows upsert on sampled_at, so a later export can correct or
 * extend the table. Blank indoor temperatures are skipped. Setpoints that are
 * blank or "off" are stored as null.
 *
 *   DATABASE_URL=postgres://... ECOBEE_BRANCH_ID=br-... \
 *     node scripts/import-ecobee.mjs path/to/ecobee_5min_all.csv
 *
 * Refuses to write unless neon.branch_id matches ECOBEE_BRANCH_ID.
 * Production branch br-green-wind-b4v3k3gk also requires
 * ECOBEE_CONFIRM_PRODUCTION=backup-done, and only after a backup.
 * Applies web/db/004_ecobee.sql before the upsert.
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { neon } from "@neondatabase/serverless";
import { parseEcobeeCsv } from "./ecobee-csv.mjs";

const PROD_BRANCH_ID = "br-green-wind-b4v3k3gk";
const BATCH = 2000;
const here = dirname(fileURLToPath(import.meta.url));

const csvPath = process.argv[2];
if (!csvPath) {
  console.error("Usage: node scripts/import-ecobee.mjs <ecobee.csv>");
  process.exit(1);
}
const databaseUrl = process.env.DATABASE_URL;
const expectedBranch = process.env.ECOBEE_BRANCH_ID;
if (!databaseUrl) {
  console.error("DATABASE_URL is required.");
  process.exit(1);
}
if (!expectedBranch) {
  console.error("Set ECOBEE_BRANCH_ID to the Neon branch you intend to write.");
  process.exit(1);
}

const sql = neon(databaseUrl);
const branchRows = await sql`SELECT current_setting('neon.branch_id', true) AS branch_id`;
const actualBranch = branchRows[0]?.branch_id ?? "";
if (!actualBranch) {
  console.error("This database did not report neon.branch_id. Refusing to write.");
  process.exit(1);
}
if (actualBranch !== expectedBranch) {
  console.error(`Connected branch ${actualBranch} does not match ECOBEE_BRANCH_ID.`);
  process.exit(1);
}
if (actualBranch === PROD_BRANCH_ID && process.env.ECOBEE_CONFIRM_PRODUCTION !== "backup-done") {
  console.error(
    "Refusing to write the production branch. Take a backup, then set ECOBEE_CONFIRM_PRODUCTION=backup-done."
  );
  process.exit(1);
}

const migration = readFileSync(resolve(here, "../db/004_ecobee.sql"), "utf8");
const create = migration
  .split("\n")
  .filter((line) => !line.trim().startsWith("--"))
  .join("\n")
  .trim();
await sql.query(create);

const rows = parseEcobeeCsv(readFileSync(csvPath, "utf8"));
let written = 0;
for (let offset = 0; offset < rows.length; offset += BATCH) {
  const batch = rows.slice(offset, offset + BATCH);
  await sql`
    INSERT INTO ecobee_samples (sampled_at, indoor_f, cool_set_f, heat_set_f, system_mode)
    SELECT * FROM unnest(
      ${batch.map((row) => row.sampledAt)}::timestamptz[],
      ${batch.map((row) => row.indoorF)}::float8[],
      ${batch.map((row) => row.coolSetF)}::float8[],
      ${batch.map((row) => row.heatSetF)}::float8[],
      ${batch.map((row) => row.systemMode)}::text[]
    ) AS t(sampled_at, indoor_f, cool_set_f, heat_set_f, system_mode)
    ON CONFLICT (sampled_at) DO UPDATE SET
      indoor_f = EXCLUDED.indoor_f,
      cool_set_f = EXCLUDED.cool_set_f,
      heat_set_f = EXCLUDED.heat_set_f,
      system_mode = EXCLUDED.system_mode
  `;
  written += batch.length;
  if (written % 10000 === 0 || written === rows.length) {
    console.log(`upserted ${written} / ${rows.length}`);
  }
}
console.log(`Done. ${written} samples on branch ${actualBranch}.`);
