package com.brian.solwidget.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SeriesKind { LIVE, FORECAST }

enum class ChartLayer { TEMPERATURE, DTE_RATES, PRECIPITATION, SOLCAST }

data class ChartLayers(
    val temperature: Boolean = true,
    val dteRates: Boolean = true,
    val precipitation: Boolean = true,
    val solcast: Boolean = true
) {
    fun with(layer: ChartLayer, visible: Boolean): ChartLayers = when (layer) {
        ChartLayer.TEMPERATURE -> copy(temperature = visible)
        ChartLayer.DTE_RATES -> copy(dteRates = visible)
        ChartLayer.PRECIPITATION -> copy(precipitation = visible)
        ChartLayer.SOLCAST -> copy(solcast = visible)
    }
}

data class PowerPoint(
    val periodEnd: Instant,
    val kw: Double,
    val periodHours: Double,
    val kind: SeriesKind
) {
    val energyKwh: Double get() = kw * periodHours
}

data class DayTotals(
    val date: LocalDate,
    val energyKwh: Double,
    val peakKw: Double,
    val liveEnergyKwh: Double,
    val forecastEnergyKwh: Double,
    val complete: Boolean = false
)

data class ForecastSnapshot(
    val resourceId: String,
    val points: List<PowerPoint>,
    val fetchedAt: Instant?,
    val requestsUsed: Int,
    val requestLimit: Int,
    val isDemo: Boolean,
    val errorMessage: String? = null
) {
    val remainingRequests: Int get() = (requestLimit - requestsUsed).coerceAtLeast(0)

    fun currentPower(now: Instant = Instant.now()): PowerPoint? {
        if (points.isEmpty()) return null
        val live = points.filter { it.kind == SeriesKind.LIVE }
            .maxByOrNull { it.periodEnd }
        if (live != null && live.periodEnd.isAfter(now.minusSeconds(90 * 60))) {
            return live
        }
        return points
            .filter { it.kind == SeriesKind.FORECAST && !it.periodEnd.isBefore(now) }
            .minByOrNull { it.periodEnd }
            ?: points.maxByOrNull { it.periodEnd }
    }

    fun totalsFor(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayTotals =
        SolarDayLabels.summaryFor(points, date, zone)

    fun remainingTodayKwh(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Double {
        val today = now.atZone(zone).toLocalDate()
        return points
            .filter { it.kind == SeriesKind.FORECAST }
            .filter { it.periodEnd.isAfter(now) }
            .filter { it.periodEnd.atZone(zone).toLocalDate() == today }
            .sumOf { it.energyKwh }
    }

    fun window(from: Instant, to: Instant): List<PowerPoint> =
        points.filter { !it.periodEnd.isBefore(from) && !it.periodEnd.isAfter(to) }

    /**
     * Two local days before today through the end of two local days after today.
     * The x-axis always uses this window, even if a series is missing an edge day.
     */
    fun displayRange(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<Instant, Instant> = range(now, zone)

    fun displayPoints(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PowerPoint> {
        val (from, to) = range(now, zone)
        return points.filter { !it.periodEnd.isBefore(from) && it.periodEnd.isBefore(to) }
    }

    companion object {
        const val PAST_DAYS = 2L
        const val FUTURE_DAYS = 2L
        const val SYSTEM_CAPACITY_KW = 8.0
        const val SOLAR_MIN_KW = 0.0
        const val SOLAR_MAX_KW = 8.0
        const val TEMP_MIN_F = -20.0
        const val TEMP_MAX_F = 100.0
        const val PRECIP_MIN = 0.0
        const val PRECIP_MAX = 100.0
        const val RATE_MIN_CENTS = 0.0
        const val RATE_MAX_CENTS = 40.0

        fun range(
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault()
        ): Pair<Instant, Instant> {
            val today = now.atZone(zone).toLocalDate()
            val from = today.minusDays(PAST_DAYS).atStartOfDay(zone).toInstant()
            val to = today.plusDays(FUTURE_DAYS + 1).atStartOfDay(zone).toInstant()
            return from to to
        }
    }
}

data class SolcastException(
    val httpCode: Int,
    override val message: String
) : Exception(message)
