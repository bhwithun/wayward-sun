package com.brian.solwidget.data

import java.time.Instant

data class HistoryInterval(
    val periodEnd: Instant,
    val actualKw: Double?,
    val forecastKw: Double?,
    val periodHours: Double,
    val tempF: Double?,
    val precipPct: Double?
)

data class HistoryPage(
    val from: Instant,
    val to: Instant,
    val points: List<HistoryInterval>,
    val sun: List<SunTimes>
)

data class HistoryChart(
    val points: List<PowerPoint>,
    val weather: List<WeatherPoint>,
    val sunDays: List<SunTimes>
)

/**
 * Past half-hours use estimated actuals, or the frozen forecast until those arrive.
 * Future half-hours use the forecast.
 */
fun HistoryInterval.toPowerPoint(now: Instant): PowerPoint? {
    val hours = if (periodHours > 0.0) periodHours else 0.5
    return if (!periodEnd.isAfter(now)) {
        val kw = actualKw ?: forecastKw ?: return null
        PowerPoint(periodEnd, kw, hours, SeriesKind.LIVE)
    } else {
        val forecast = forecastKw
        if (forecast != null) {
            PowerPoint(periodEnd, forecast, hours, SeriesKind.FORECAST)
        } else {
            val actual = actualKw ?: return null
            PowerPoint(periodEnd, actual, hours, SeriesKind.LIVE)
        }
    }
}

fun HistoryPage.chartFor(from: Instant, to: Instant, now: Instant): HistoryChart {
    val rows = points.filter { !it.periodEnd.isBefore(from) && it.periodEnd.isBefore(to) }
    val weather = rows.mapNotNull { row ->
        val temp = row.tempF ?: return@mapNotNull null
        WeatherPoint(row.periodEnd, temp, row.precipPct)
    }
    val sunDays = sun.filter { day -> !day.sunset.isBefore(from) && day.sunrise.isBefore(to) }
    return HistoryChart(
        points = rows.mapNotNull { it.toPowerPoint(now) },
        weather = weather,
        sunDays = sunDays
    )
}

fun historyFetchKey(from: Instant, to: Instant): String = "$from|$to"

/**
 * Past half-hours come from stored history, drawn as the green trace.
 * The latest cache forecast supplies the gold trace after now.
 * Until history arrives, the cache snapshot is used as-is.
 */
fun liveSolarPoints(
    pastFromHistory: List<PowerPoint>?,
    snapshotPoints: List<PowerPoint>,
    from: Instant,
    to: Instant,
    now: Instant
): List<PowerPoint> {
    val inWindow = snapshotPoints.filter { !it.periodEnd.isBefore(from) && it.periodEnd.isBefore(to) }
    val past = pastFromHistory
        ?.filter { !it.periodEnd.isBefore(from) && !it.periodEnd.isAfter(now) }
        .orEmpty()
    if (past.isEmpty()) return inWindow
    val future = inWindow
        .filter { it.periodEnd.isAfter(now) }
        .map { point ->
            if (point.kind == SeriesKind.FORECAST) point else point.copy(kind = SeriesKind.FORECAST)
        }
    return (past + future).sortedBy { it.periodEnd }
}