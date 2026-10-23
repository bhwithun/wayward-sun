package com.brian.solwidget.data

import java.time.Duration
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
 * Past half-hours keep the measured actual and the stored forecast. A forecast
 * with no measurement yet still stands in on the green trace, and it stays on
 * the gold line. Future half-hours use the forecast.
 */
fun HistoryInterval.toPowerPoints(now: Instant): List<PowerPoint> {
    val hours = if (periodHours > 0.0) periodHours else 0.5
    if (!periodEnd.isAfter(now)) {
        val points = ArrayList<PowerPoint>(2)
        if (actualKw != null) {
            points.add(PowerPoint(periodEnd, actualKw, hours, SeriesKind.LIVE))
        } else if (forecastKw != null) {
            points.add(PowerPoint(periodEnd, forecastKw, hours, SeriesKind.LIVE))
        }
        if (forecastKw != null) {
            points.add(PowerPoint(periodEnd, forecastKw, hours, SeriesKind.FORECAST))
        }
        return points
    }
    val forecast = forecastKw
    if (forecast != null) return listOf(PowerPoint(periodEnd, forecast, hours, SeriesKind.FORECAST))
    val actual = actualKw ?: return emptyList()
    return listOf(PowerPoint(periodEnd, actual, hours, SeriesKind.LIVE))
}

fun HistoryInterval.toPowerPoint(now: Instant): PowerPoint? {
    val points = toPowerPoints(now)
    return points.firstOrNull { it.kind == SeriesKind.LIVE } ?: points.firstOrNull()
}

fun HistoryPage.chartFor(from: Instant, to: Instant, now: Instant): HistoryChart {
    val rows = points.filter { !it.periodEnd.isBefore(from) && it.periodEnd.isBefore(to) }
    val weather = rows.mapNotNull { row ->
        val temp = row.tempF ?: return@mapNotNull null
        WeatherPoint(row.periodEnd, temp, row.precipPct)
    }
    val sunDays = sun.filter { day -> !day.sunset.isBefore(from) && day.sunrise.isBefore(to) }
    return HistoryChart(
        points = rows.flatMap { it.toPowerPoints(now) },
        weather = weather,
        sunDays = sunDays
    )
}

fun historyFetchKey(from: Instant, to: Instant): String = "$from|$to"

/**
 * Past half-hours are the green trace. Stored history supplies them when it
 * has arrived. The cache forecast still holds the half-hour that just ended,
 * which history does not include until the next fetch, so that point stays
 * in the series and is drawn green. Without it the join is an hour wide and
 * the day total is withheld.
 * The latest cache forecast supplies the gold trace after now.
 */
fun liveSolarPoints(
    pastFromHistory: List<PowerPoint>?,
    snapshotPoints: List<PowerPoint>,
    from: Instant,
    to: Instant,
    now: Instant
): List<PowerPoint> {
    val inWindow = snapshotPoints.filter { !it.periodEnd.isBefore(from) && it.periodEnd.isBefore(to) }
    val historyPast = pastFromHistory
        ?.filter { !it.periodEnd.isBefore(from) && !it.periodEnd.isAfter(now) }
        .orEmpty()
    val covered = historyPast.map { it.periodEnd }.toHashSet()
    val snapshotPast = inWindow
        .filter { !it.periodEnd.isAfter(now) && it.periodEnd !in covered }
        .flatMap { point ->
            if (point.kind == SeriesKind.FORECAST) {
                listOf(point.copy(kind = SeriesKind.LIVE), point)
            } else {
                listOf(point)
            }
        }
    val future = inWindow
        .filter { it.periodEnd.isAfter(now) }
        .map { point ->
            if (point.kind == SeriesKind.FORECAST) point else point.copy(kind = SeriesKind.FORECAST)
        }
    return (historyPast + snapshotPast + future).sortedBy { it.periodEnd }
}

/**
 * The live stroke includes the first forecast point so the curve meets the
 * gold line. The extra point is for drawing only. Day energy still counts
 * each half-hour once.
 */
/**
 * Past forecast is drawn at half opacity. The last past point is repeated on
 * the future stroke so the line stays continuous across now.
 */
fun forecastStrokes(points: List<PowerPoint>, now: Instant): Pair<List<PowerPoint>, List<PowerPoint>> {
    val forecast = points.filter { it.kind == SeriesKind.FORECAST }.sortedBy { it.periodEnd }
    val past = forecast.filter { !it.periodEnd.isAfter(now) }
    val ahead = forecast.filter { it.periodEnd.isAfter(now) }
    val seam = past.lastOrNull()
    val next = ahead.firstOrNull()
    val joined = seam != null && next != null &&
        Duration.between(seam.periodEnd, next.periodEnd).compareTo(SolarDayLabels.MAX_GAP) <= 0
    val future = if (joined) listOf(seam) + ahead else ahead
    return past to future
}

fun liveStroke(points: List<PowerPoint>): List<PowerPoint> {
    val live = points.filter { it.kind == SeriesKind.LIVE }
    if (live.isEmpty()) return live
    val next = points.firstOrNull { it.kind == SeriesKind.FORECAST && it.periodEnd.isAfter(live.last().periodEnd) }
        ?: return live
    return live + next
}