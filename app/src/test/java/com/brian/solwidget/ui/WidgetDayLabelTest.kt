package com.brian.solwidget.ui

import com.brian.solwidget.ui.components.WidgetDayColumn
import com.brian.solwidget.ui.components.chartDomainMillis
import com.brian.solwidget.ui.components.croppedWidgetRange
import com.brian.solwidget.ui.components.daysWithNoonInRange
import com.brian.solwidget.ui.components.planWidgetDayLabels
import com.brian.solwidget.ui.components.widgetRateWash
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDayLabelTest {
    @Test
    fun shrinksUntilEveryDayColumnFits() {
        val columns = listOf(
            WidgetDayColumn(center = 100f, widthAtFullSize = 40f),
            WidgetDayColumn(center = 180f, widthAtFullSize = 80f)
        )
        val plan = planWidgetDayLabels(columns, left = 0f, right = 200f)
        assertEquals(20f, plan.textSize)
        assertEquals(listOf(0, 1), plan.visible)
    }

    @Test
    fun dropsAColumnThatStillOverflowsAtTheMinimumSize() {
        val columns = listOf(
            WidgetDayColumn(center = 80f, widthAtFullSize = 40f),
            WidgetDayColumn(center = 190f, widthAtFullSize = 80f)
        )
        val plan = planWidgetDayLabels(columns, left = 0f, right = 200f, fullSize = 40f, minSize = 18f)
        assertEquals(18f, plan.textSize)
        assertEquals(listOf(0), plan.visible)
    }

    @Test
    fun cropsThePlotToDaysThatHaveLabels() {
        val zone = ZoneId.of("America/Detroit")
        val monday = LocalDate.of(2026, 11, 9)
        val from = monday.atTime(18, 0).atZone(zone).toInstant()
        val friday = monday.plusDays(4)
        val to = friday.atTime(10, 0).atZone(zone).toInstant()
        val noons = daysWithNoonInRange(from, to, zone)
        assertEquals(
            listOf(monday.plusDays(1), monday.plusDays(2), monday.plusDays(3)),
            noons
        )
        val (cropFrom, cropTo) = croppedWidgetRange(from, to, noons, zone)
        assertEquals(monday.plusDays(1).atStartOfDay(zone).toInstant(), cropFrom)
        assertEquals(friday.atStartOfDay(zone).toInstant(), cropTo)
    }

    @Test
    fun chartDomainKeepsTheMidnightAFloatAxisWouldDrop() {
        val zone = ZoneId.of("America/New_York")
        val end = LocalDate.of(2026, 11, 11).atStartOfDay(zone).toInstant()
        val start = LocalDate.of(2026, 11, 10).atStartOfDay(zone).toInstant()
        val (domainFrom, domainTo) = chartDomainMillis(start, end)
        assertEquals(start.toEpochMilli().toDouble(), domainFrom, 0.0)
        assertEquals(end.toEpochMilli().toDouble(), domainTo, 0.0)
        assertEquals(end, Instant.ofEpochMilli(domainTo.toLong()))
    }

    @Test
    fun houseUseDimsWidgetRatesToMatchTheApp() {
        val on = widgetRateWash(true)
        val off = widgetRateWash(false)
        assertEquals(0x1A, on.fillAlpha)
        assertEquals(0x59, on.edgeAlpha)
        assertEquals(0.10f, on.fillAlpha / 255f, 0.01f)
        assertEquals(0.35f, on.edgeAlpha / 255f, 0.01f)
        assertEquals(0x33, off.fillAlpha)
        assertEquals(0x8C, off.edgeAlpha)
    }
}
