package com.brian.solwidget.data

import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredLivePast(
    val key: String,
    val savedAt: Instant,
    val points: List<PowerPoint>
)

/**
 * Past half-hours for the green trace. The shared cache forecast starts at the
 * latest pull, so Live and the widget read stored history for everything up to now.
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
            append('\n')
        }
    }

    fun decode(text: String?): StoredLivePast? {
        if (text.isNullOrBlank()) return null
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return null
        val savedAt = lines[1].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val points = lines.drop(2).mapNotNull { line ->
            val parts = line.split(',')
            if (parts.size != 3) return@mapNotNull null
            val end = parts[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
            val kw = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val hours = parts[2].toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.5
            PowerPoint(end, kw, hours, SeriesKind.LIVE)
        }
        return StoredLivePast(lines[0], savedAt, points.sortedBy { it.periodEnd })
    }

    fun isFresh(stored: StoredLivePast, now: Instant, zone: ZoneId): Boolean {
        if (stored.points.isEmpty()) return false
        val (from, to) = ForecastSnapshot.range(now, zone)
        if (stored.key != historyFetchKey(from, to)) return false
        return !stored.savedAt.isBefore(now.minus(MAX_AGE))
    }
}

class LivePastRepository(context: Context) {
    private val storage = AppStorage(context.applicationContext)
    private val api = HistoryApi()

    /** Fresh stored history, otherwise a new fetch, otherwise the last stored series. */
    suspend fun forChart(now: Instant, zone: ZoneId): List<PowerPoint>? {
        fresh(now, zone)?.let { return it }
        return fetch(now, zone).getOrNull() ?: stored(now, zone)?.points
    }

    suspend fun fetch(now: Instant, zone: ZoneId): Result<List<PowerPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            val (from, to) = ForecastSnapshot.range(now, zone)
            val points = api.fetch(storage.cacheUrlOnce(), from, to).livePastPoints(now)
            storage.saveLivePast(
                LivePastStore.encode(
                    StoredLivePast(
                        key = historyFetchKey(from, to),
                        savedAt = Instant.now(),
                        points = points
                    )
                )
            )
            points
        }
    }

    private suspend fun fresh(now: Instant, zone: ZoneId): List<PowerPoint>? {
        val stored = stored(now, zone) ?: return null
        return stored.points.takeIf { LivePastStore.isFresh(stored, now, zone) }
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
