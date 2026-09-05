package com.brian.solwidget.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class WeatherPoint(
    val time: Instant,
    val tempF: Double,
    val popPercent: Double?
)

data class SunTimes(
    val date: LocalDate,
    val sunrise: Instant,
    val sunset: Instant
)

fun isNight(time: Instant, sunDays: List<SunTimes>): Boolean {
    if (sunDays.isEmpty()) return false
    val ordered = sunDays.sortedBy { it.sunrise }
    if (time.isBefore(ordered.first().sunrise)) return true
    for (i in ordered.indices) {
        val day = ordered[i]
        if (!time.isBefore(day.sunrise) && time.isBefore(day.sunset)) return false
        val nextRise = ordered.getOrNull(i + 1)?.sunrise
        if (!time.isBefore(day.sunset) && (nextRise == null || time.isBefore(nextRise))) return true
    }
    return true
}

fun sunEventFractions(from: Instant, to: Instant, sunDays: List<SunTimes>): List<Float> {
    val span = (to.toEpochMilli() - from.toEpochMilli()).toDouble()
    if (span <= 0.0 || sunDays.isEmpty()) return emptyList()
    return sunDays.flatMap { listOf(it.sunrise, it.sunset) }.mapNotNull { event ->
        val t = ((event.toEpochMilli() - from.toEpochMilli()) / span).toFloat()
        t.takeIf { it > 0f && it < 1f }
    }
}

data class DayTempRange(
    val date: LocalDate,
    val lowF: Int,
    val highF: Int
) {
    val label: String get() = if (lowF == highF) "$lowF" else "$lowF-$highF"
}

fun dayTempRanges(weather: List<WeatherPoint>, zone: ZoneId): Map<LocalDate, DayTempRange> {
    if (weather.isEmpty()) return emptyMap()
    return weather.groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { (date, points) ->
        val rounded = points.map { it.tempF.roundToInt() }
        val low = rounded.min()
        val high = rounded.max()
        DayTempRange(date, low, high)
    }
}

data class GeoPlace(
    val query: String,
    val label: String,
    val latitude: Double,
    val longitude: Double
)

data class WeatherSnapshot(
    val place: GeoPlace?,
    val points: List<WeatherPoint>,
    val fetchedAt: Instant?,
    val errorMessage: String? = null,
    val sunDays: List<SunTimes> = emptyList()
) {
    fun current(now: Instant = Instant.now()): WeatherPoint? {
        if (points.isEmpty()) return null
        return points.minByOrNull { kotlin.math.abs(it.time.toEpochMilli() - now.toEpochMilli()) }
    }

    fun inRange(from: Instant, to: Instant): List<WeatherPoint> =
        points.filter { !it.time.isBefore(from) && it.time.isBefore(to) }

    fun alignedWithPower(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<WeatherPoint> {
        val (from, to) = ForecastSnapshot.range(now, zone)
        return inRange(from, to)
    }
}
