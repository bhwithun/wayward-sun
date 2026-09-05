package com.brian.solwidget.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunTimesTest {

    private val zone: ZoneId = ZoneId.of("America/Detroit")
    private val day: LocalDate = LocalDate.of(2026, 9, 4)
    private val sun = SunTimes(
        date = day,
        sunrise = day.atTime(LocalTime.of(7, 0)).atZone(zone).toInstant(),
        sunset = day.atTime(LocalTime.of(20, 0)).atZone(zone).toInstant()
    )
    private val next = SunTimes(
        date = day.plusDays(1),
        sunrise = day.plusDays(1).atTime(LocalTime.of(7, 1)).atZone(zone).toInstant(),
        sunset = day.plusDays(1).atTime(LocalTime.of(19, 58)).atZone(zone).toInstant()
    )

    @Test
    fun nightIsBeforeSunriseAndAfterSunset() {
        val days = listOf(sun, next)
        assertTrue(isNight(day.atTime(6, 30).atZone(zone).toInstant(), days))
        assertFalse(isNight(day.atTime(12, 0).atZone(zone).toInstant(), days))
        assertTrue(isNight(day.atTime(20, 30).atZone(zone).toInstant(), days))
        assertFalse(isNight(day.plusDays(1).atTime(8, 0).atZone(zone).toInstant(), days))
    }

    @Test
    fun sunriseAndSunsetAreTheDayNightBoundary() {
        val days = listOf(sun)
        assertFalse(isNight(sun.sunrise, days))
        assertTrue(isNight(sun.sunset, days))
    }

    @Test
    fun emptySunDaysStaySolidDaytime() {
        assertFalse(isNight(day.atTime(2, 0).atZone(zone).toInstant(), emptyList()))
    }

    @Test
    fun sunEventFractionsSplitAtSunset() {
        val from = day.atTime(19, 0).atZone(zone).toInstant()
        val to = day.atTime(21, 0).atZone(zone).toInstant()
        val marks = sunEventFractions(from, to, listOf(sun))
        assertEquals(1, marks.size)
        assertEquals(0.5f, marks[0], 0.02f)
    }
}
