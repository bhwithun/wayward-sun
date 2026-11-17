package com.brian.solwidget.ui

import androidx.compose.ui.unit.dp
import com.brian.solwidget.data.HouseSummary
import com.brian.solwidget.ui.screens.CalendarFit
import com.brian.solwidget.ui.screens.periodReadout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarFitTest {
    @Test
    fun sixAndFiveWeekMonthsOnA360By640Phone() {
        val half = CalendarFit.calendarHalf(CalendarFit.smallPhoneHeight)
        assertEquals(252f, half.value, 0.01f)
        val six = CalendarFit.layout(half, 6)
        assertEquals(33.5f, six.cell.value, 0.05f)
        assertEquals(26f, six.header.value, 0.01f)
        assertEquals(1f, six.gap.value, 0.01f)
        assertEquals(CalendarFit.dayMinSp, six.daySp, 0.01f)
        assertTrue(six.holdsFloor)
        assertTrue(six.cell >= CalendarFit.cellFloor)

        val five = CalendarFit.layout(half, 5)
        assertEquals(38f, five.cell.value, 0.05f)
        assertEquals(32f, five.header.value, 0.01f)
        assertEquals(2f, five.gap.value, 0.01f)
        assertEquals(CalendarFit.dayRoomySp, five.daySp, 0.01f)
        assertTrue(five.holdsFloor)
    }

    @Test
    fun fiveAndSixWeekMonthsOnA411By914Phone() {
        val half = CalendarFit.calendarHalf(CalendarFit.typicalPhoneHeight)
        assertEquals(389f, half.value, 0.01f)
        val six = CalendarFit.layout(half, 6)
        assertEquals(54.167f, six.cell.value, 0.05f)
        assertEquals(32f, six.header.value, 0.01f)
        assertEquals(CalendarFit.dayRoomySp, six.daySp, 0.01f)
        assertTrue(six.cell >= CalendarFit.tightenBelow)

        val five = CalendarFit.layout(half, 5)
        assertEquals(65.4f, five.cell.value, 0.05f)
        assertEquals(32f, five.header.value, 0.01f)
        assertEquals(CalendarFit.dayRoomySp, five.daySp, 0.01f)
        assertTrue(five.holdsFloor)
    }

    @Test
    fun aHalfThatCannotHold30dpSharesTheSpaceWithoutScrolling() {
        val metrics = CalendarFit.layout(200.dp, 6)
        assertFalse(metrics.holdsFloor)
        assertTrue(metrics.cell < CalendarFit.cellFloor)
        assertEquals(26f, metrics.header.value, 0.01f)
        assertEquals(0f, metrics.gap.value, 0.01f)
        assertEquals(10f, metrics.weekday.value, 0.01f)
        assertEquals(CalendarFit.dayMinSp, metrics.daySp, 0.01f)
        assertEquals(27.333f, metrics.cell.value, 0.05f)
    }

    @Test
    fun periodReadoutKeepsHvacAndHouseForTheDrawer() {
        val kept = periodReadout(
            hvacEnabled = true,
            hvacLabel = "Heat pump 3.7 h",
            house = HouseSummary(homeKwh = 18.4, coveredPercent = 62, hourlyDetailUnavailable = false)
        )
        assertEquals("Heat pump 3.7 h", kept.hvac)
        assertEquals("House 18.4 kWh · Solar covered 62%", kept.house)
        assertFalse(kept.hourlyDetailUnavailable)

        val hidden = periodReadout(hvacEnabled = false, hvacLabel = "Heat pump 3.7 h", house = null)
        assertNull(hidden.hvac)
        assertNull(hidden.house)
        assertNull(periodReadout(true, "   ", null).hvac)
    }
}
