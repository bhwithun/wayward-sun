/** Ecobee CSV parsing shared by the import script. Date and Time are America/New_York. */

export function parseEcobeeSetpoint(raw) {
  if (raw == null) return null;
  const text = String(raw).trim().toLowerCase();
  if (text === "" || text === "off" || text === "null" || text === "nan") return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
}

const NEW_YORK = new Intl.DateTimeFormat("en-US", {
  timeZone: "America/New_York",
  hourCycle: "h23",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

/** Offset of America/New_York at a UTC instant, in milliseconds (negative). */
function newYorkOffset(utcMs) {
  const parts = NEW_YORK.formatToParts(new Date(utcMs));
  const pick = (type) => Number(parts.find((part) => part.type === type)?.value);
  const asUtc = Date.UTC(pick("year"), pick("month") - 1, pick("day"), pick("hour"), pick("minute"), pick("second"));
  return asUtc - utcMs;
}

/**
 * America/New_York wall time to a UTC Date. A second pass covers the morning
 * when the offset at the guessed instant is still the pre-transition offset.
 * The skipped spring-forward hour is mapped to the following offset.
 */
export function newYorkToUtc(year, month, day, hour, minute, second) {
  const desired = Date.UTC(year, month - 1, day, hour, minute, second);
  const first = newYorkOffset(desired);
  let utc = desired - first;
  const secondOffset = newYorkOffset(utc);
  if (secondOffset !== first) utc = desired - secondOffset;
  return new Date(utc);
}

export function parseEcobeeCsv(text) {
  const lines = text.split(/\r?\n/);
  if (lines.length === 0) return [];
  const header = splitCsv(lines[0]);
  const column = (name) => {
    const index = header.indexOf(name);
    if (index < 0) throw new Error(`Missing column ${name}`);
    return index;
  };
  const dateIndex = column("Date");
  const timeIndex = column("Time");
  const modeIndex = column("System Mode");
  const coolIndex = column("Cool Set Temp (F)");
  const heatIndex = column("Heat Set Temp (F)");
  const indoorIndex = column("Current Temp (F)");
  const rows = [];
  for (let lineNumber = 1; lineNumber < lines.length; lineNumber += 1) {
    const line = lines[lineNumber];
    if (!line.trim()) continue;
    const cells = splitCsv(line);
    const indoor = parseEcobeeSetpoint(cells[indoorIndex]);
    if (indoor == null) continue;
    const date = cells[dateIndex]?.trim();
    const time = cells[timeIndex]?.trim();
    const dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date ?? "");
    const timeMatch = /^(\d{2}):(\d{2}):(\d{2})$/.exec(time ?? "");
    if (!dateMatch || !timeMatch) continue;
    const sampledAt = newYorkToUtc(
      Number(dateMatch[1]),
      Number(dateMatch[2]),
      Number(dateMatch[3]),
      Number(timeMatch[1]),
      Number(timeMatch[2]),
      Number(timeMatch[3])
    );
    if (Number.isNaN(sampledAt.getTime())) continue;
    const mode = cells[modeIndex]?.trim() || null;
    rows.push({
      sampledAt: sampledAt.toISOString(),
      indoorF: indoor,
      coolSetF: parseEcobeeSetpoint(cells[coolIndex]),
      heatSetF: parseEcobeeSetpoint(cells[heatIndex]),
      systemMode: mode,
    });
  }
  const unique = new Map();
  for (const row of rows) unique.set(row.sampledAt, row);
  return [...unique.values()];
}

function splitCsv(line) {
  const cells = [];
  let current = "";
  let quoted = false;
  for (let index = 0; index < line.length; index += 1) {
    const ch = line[index];
    if (quoted) {
      if (ch === '"') {
        if (line[index + 1] === '"') {
          current += '"';
          index += 1;
        } else {
          quoted = false;
        }
      } else {
        current += ch;
      }
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ",") {
      cells.push(current);
      current = "";
    } else {
      current += ch;
    }
  }
  cells.push(current);
  return cells;
}
