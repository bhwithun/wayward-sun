package com.brian.solwidget.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarDayLabelsTest {

    private val zone: ZoneId = ZoneId.of("America/Detroit")
    private val day: LocalDate = LocalDate.of(2026, 6, 21)

    @Test
    fun fullSineDayWithNightZerosIsComplete() {
        val points = sineDay(includeNight = true)
        val summary = SolarDayLabels.summaryFor(points, day, zone)
        assertTrue(summary.complete)
        assertEquals(points.sumOf { it.energyKwh }, summary.energyKwh, 1e-9)
        assertTrue(summary.energyKwh > 20.0)
    }

    @Test
    fun daylightOnlyRampIsComplete() {
        val points = sineDay(includeNight = false)
        val summary = SolarDayLabels.summaryFor(points, day, zone)
        assertTrue(summary.complete)
        assertEquals(points.sumOf { it.energyKwh }, summary.energyKwh, 1e-9)
    }

    @Test
    fun seriesStartingMidMorningAtHighKwIsIncomplete() {
        val points = sineDay(includeNight = true)
            .filter { it.periodEnd.atZone(zone).toLocalTime() >= LocalTime.of(11, 0) }
        val summary = SolarDayLabels.summaryFor(points, day, zone)
        assertFalse(summary.complete)
        assertTrue(summary.peakKw > 3.0)
        assertTrue(summary.energyKwh > 0.0)
    }

    @Test
    fun seriesEndingMidAfternoonWhileProducingIsIncomplete() {
        val points = sineDay(includeNight = true)
            .filter { it.periodEnd.atZone(zone).toLocalTime() <= LocalTime.of(15, 0) }
        val summary = SolarDayLabels.summaryFor(points, day, zone)
        assertFalse(summary.complete)
    }

    @Test
    fun liveThroughAfternoonPlusForecastThroughDuskIsComplete() {
        val points = sineDay(includeNight = true).map { point ->
            val hour = point.periodEnd.atZone(zone).hour
            val kind = if (hour >= 15) SeriesKind.FORECAST else SeriesKind.LIVE
            point.copy(kind = kind)
        }
        val summary = SolarDayLabels.summaryFor(points, day, zone)
        assertTrue(summary.complete)
        assertTrue(summary.liveEnergyKwh > 0.0)
        assertTrue(summary.forecastEnergyKwh > 0.0)
    }

    @Test
    fun interiorTwoHourGapIsIncomplete() {
        val points = sineDay(includeNight = true).filter { point ->
            val t = point.periodEnd.atZone(zone).toLocalTime()
            t < LocalTime.of(11, 0) || t >= LocalTime.of(13, 0)
        }
        val summary = SolarDayLabels.summaryFor(points, day, zone)
        assertFalse(summary.complete)
    }

    @Test
    fun allZeroDayHasNoWholeDayLabel() {
        val points = (0 until 48).map { index ->
            val time = day.atStartOfDay(zone).toInstant().plusSeconds(index * 30L * 60L)
            PowerPoint(time, 0.0, 0.5, SeriesKind.LIVE)
        }
        val summary = SolarDayLabels.summaryFor(points, day, zone)
        assertFalse(summary.complete)
        assertEquals(0.0, summary.energyKwh, 0.0)
        assertEquals(0.0, summary.peakKw, 0.0)
    }

    private fun sineDay(includeNight: Boolean): List<PowerPoint> {
        return (0 until 48).mapNotNull { index ->
            val time = day.atStartOfDay(zone).toInstant().plusSeconds(index * 30L * 60L)
            val local = time.atZone(zone)
            val hour = local.hour + local.minute / 60.0
            val sun = max(0.0, sin(((hour - 6.0) / 12.0) * Math.PI))
            val kw = 6.4 * sun * sun
            if (!includeNight && kw <= SolarDayLabels.PRODUCING_KW) null
            else PowerPoint(time, kw, 0.5, SeriesKind.LIVE)
        }
    }
}
