package com.brian.solwidget.data

import java.time.Instant
import java.time.ZoneId

data class WeatherPoint(
    val time: Instant,
    val tempF: Double,
    val popPercent: Double?
)

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
    val errorMessage: String? = null
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
