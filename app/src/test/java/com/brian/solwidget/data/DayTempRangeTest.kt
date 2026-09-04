package com.brian.solwidget.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DayTempRangeTest {

    private val zone: ZoneId = ZoneId.of("America/Detroit")
    private val day: LocalDate = LocalDate.of(2026, 6, 21)

    @Test
    fun formatsLowToHighWholeDegrees() {
        val points = listOf(
            point(6, 58.6),
            point(14, 77.6),
            point(21, 62.2)
        )
        val range = dayTempRanges(points, zone).getValue(day)
        assertEquals(59, range.lowF)
        assertEquals(78, range.highF)
        assertEquals("59-78", range.label)
    }

    @Test
    fun equalLowHighDropsTheDash() {
        val points = listOf(point(12, 70.2), point(13, 69.6))
        val range = dayTempRanges(points, zone).getValue(day)
        assertEquals("70", range.label)
    }

    private fun point(hour: Int, tempF: Double): WeatherPoint {
        val time = day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant()
        return WeatherPoint(time, tempF, popPercent = null)
    }
}
