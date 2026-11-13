package com.brian.solwidget.data

import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredLivePast(
    val key: String,
    val savedAt: Instant,
    val points: List<PowerPoint>,
    val ecobee: List<EcobeeSample> = emptyList(),
    val house: List<HouseSample> = emptyList(),
    val houseDays: List<HouseDay> = emptyList()
)

data class LiveHistory(
    val points: List<PowerPoint>,
    val ecobee: List<EcobeeSample>,
    val house: List<HouseSample> = emptyList(),
    val houseDays: List<HouseDay> = emptyList()
)

/**
 * Past half-hours for Live and the widget. The shared cache forecast starts at
 * the latest pull, so both read stored history for everything up to now.
 * Each row keeps its series: measured production stays green, and a stored
 * Solcast forecast stays gold.
 */
object LivePastStore {
    val MAX_AGE: Duration = Duration.ofMinutes(45)

    fun encode(stored: StoredLivePast): String = buildString {
        append(stored.key)
        append('\n')
        append(stored.savedAt.toEpochMilli())
        append('\n')
        for (point in stored.points) {
            append(point.periodEnd.toEpochMilli())
            append(',')
            append(point.kw)
            append(',')
            append(point.periodHours)
            append(',')
            append(if (point.kind == SeriesKind.FORECAST) "F" else "L")
            append('\n')
        }
        for (sample in stored.ecobee) {
            append("E,")
            append(sample.at.toEpochMilli())
            append(',')
            append(sample.indoorF)
            append(',')
            append(sample.coolSetF?.toString().orEmpty())
            append(',')
            append(sample.heatSetF?.toString().orEmpty())
            append(',')
            append(sample.heatPumpSec?.toString().orEmpty())
            append(',')
            append(sample.auxHeatSec?.toString().orEmpty())
            append(',')
            append(sample.coolSec?.toString().orEmpty())
            append(',')
            append(sample.fanSec?.toString().orEmpty())
            append(',')
            append(sample.outdoorF?.toString().orEmpty())
            append('\n')
        }
        for (sample in stored.house) {
            append("H,")
            append(sample.periodEnd.toEpochMilli())
            append(',')
            append(sample.homeKw)
            append('\n')
        }
        for (day in stored.houseDays) {
            append("D,")
            append(day.day)
            append(',')
            append(day.homeKwh)
            append(',')
            append(day.fromSolarKwh)
            append(',')
            append(if (day.hasHalfHour) "1" else "0")
            append('\n')
        }
    }

    fun decode(text: String?): StoredLivePast? {
        if (text.isNullOrBlank()) return null
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return null
        val savedAt = lines[1].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val points = mutableListOf<PowerPoint>()
        val ecobee = mutableListOf<EcobeeSample>()
        val house = mutableListOf<HouseSample>()
        val houseDays = mutableListOf<HouseDay>()
        for (line in lines.drop(2)) {
            if (line.startsWith("E,")) {
                parseEcobee(line)?.let { ecobee.add(it) }
                continue
            }
            if (line.startsWith("H,")) {
                parseHouse(line)?.let { house.add(it) }
                continue
            }
            if (line.startsWith("D,")) {
                parseHouseDay(line)?.let { houseDays.add(it) }
                continue
            }
            val row = parseRow(line) ?: continue
            val kind = row.kind ?: if (
                points.lastOrNull()?.periodEnd == row.end &&
                points.last().kind == SeriesKind.LIVE
            ) {
                SeriesKind.FORECAST
            } else {
                SeriesKind.LIVE
            }
            points.add(PowerPoint(row.end, row.kw, row.hours, kind))
        }
        return StoredLivePast(
            lines[0],
            savedAt,
            points.sortedBy { it.periodEnd },
            ecobee.sortedBy { it.at },
            house.sortedBy { it.periodEnd },
            houseDays.sortedBy { it.day }
        )
    }

    private fun parseHouse(line: String): HouseSample? {
        val parts = line.split(',')
        if (parts.size < 3 || parts[0] != "H") return null
        val end = parts[1].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val kw = parts[2].toDoubleOrNull() ?: return null
        return HouseSample(end, kw)
    }

    private fun parseHouseDay(line: String): HouseDay? {
        val parts = line.split(',')
        if (parts.size < 5 || parts[0] != "D") return null
        val day = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        val home = parts[2].toDoubleOrNull() ?: return null
        val solar = parts[3].toDoubleOrNull() ?: return null
        return HouseDay(day, home, solar, parts[4] == "1")
    }

    private fun parseEcobee(line: String): EcobeeSample? {
        val parts = line.split(',')
        if (parts.size < 5 || parts[0] != "E") return null
        val at = parts[1].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val indoor = parts[2].toDoubleOrNull() ?: return null
        return EcobeeSample(
            at = at,
            indoorF = indoor,
            coolSetF = parts[3].toDoubleOrNull(),
            heatSetF = parts[4].toDoubleOrNull(),
            heatPumpSec = parts.getOrNull(5)?.toIntOrNull(),
            auxHeatSec = parts.getOrNull(6)?.toIntOrNull(),
            coolSec = parts.getOrNull(7)?.toIntOrNull(),
            fanSec = parts.getOrNull(8)?.toIntOrNull(),
            outdoorF = parts.getOrNull(9)?.toDoubleOrNull()
        )
    }

    private data class ParsedRow(
        val end: Instant,
        val kw: Double,
        val hours: Double,
        val kind: SeriesKind?
    )

    private fun parseRow(line: String): ParsedRow? {
        val parts = line.split(',')
        if (parts.size !in 3..4) return null
        val end = parts[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val kw = parts[1].toDoubleOrNull() ?: return null
        val hours = parts[2].toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.5
        val kind = when (parts.getOrNull(3)) {
            "F" -> SeriesKind.FORECAST
            "L" -> SeriesKind.LIVE
            null -> null
            else -> SeriesKind.LIVE
        }
        return ParsedRow(end, kw, hours, kind)
    }

    fun isFresh(stored: StoredLivePast, now: Instant, zone: ZoneId): Boolean {
        if (stored.points.isEmpty() && stored.ecobee.isEmpty() && stored.house.isEmpty() && stored.houseDays.isEmpty()) {
            return false
        }
        val (from, to) = ForecastSnapshot.range(now, zone)
        if (stored.key != historyFetchKey(from, to)) return false
        return !stored.savedAt.isBefore(now.minus(MAX_AGE))
    }
}

class LivePastRepository(context: Context) {
    private val storage = AppStorage(context.applicationContext)
    private val api = HistoryApi()

    /** Fresh stored history, otherwise a new fetch, otherwise the last stored series. */
    suspend fun forChart(now: Instant, zone: ZoneId): LiveHistory? {
        fresh(now, zone)?.let { return it.toHistory() }
        return fetch(now, zone).getOrNull() ?: stored(now, zone)?.toHistory()
    }

    suspend fun fetch(now: Instant, zone: ZoneId): Result<LiveHistory> = withContext(Dispatchers.IO) {
        runCatching {
            val (from, to) = ForecastSnapshot.range(now, zone)
            val page = api.fetch(storage.cacheUrlOnce(), from, to)
            val points = page.livePastPoints(now)
            val history = LiveHistory(points, page.ecobee, page.house, page.houseDays)
            storage.saveLivePast(
                LivePastStore.encode(
                    StoredLivePast(
                        key = historyFetchKey(from, to),
                        savedAt = Instant.now(),
                        points = points,
                        ecobee = page.ecobee,
                        house = page.house,
                        houseDays = page.houseDays
                    )
                )
            )
            history
        }
    }

    private suspend fun fresh(now: Instant, zone: ZoneId): StoredLivePast? {
        val stored = stored(now, zone) ?: return null
        return stored.takeIf { LivePastStore.isFresh(stored, now, zone) }
    }

    private suspend fun stored(now: Instant, zone: ZoneId): StoredLivePast? {
        val stored = LivePastStore.decode(storage.cachedLivePast()) ?: return null
        val (from, to) = ForecastSnapshot.range(now, zone)
        if (stored.key != historyFetchKey(from, to)) return null
        return stored
    }
}

private fun HistoryPage.livePastPoints(now: Instant): List<PowerPoint> =
    chartFor(from, to, now).points.filter { !it.periodEnd.isAfter(now) }

private fun StoredLivePast.toHistory(): LiveHistory = LiveHistory(points, ecobee, house, houseDays)
