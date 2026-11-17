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
    fun sixWeekMonthOnA360By640PhoneStaysUnder40dpAndDoesNotNeedAFloor() {
        val half = CalendarFit.calendarHalf(CalendarFit.smallPhoneHeight)
        assertEquals(252f, half.value, 0.01f)
        val six = CalendarFit.dayCellHeight(half, 6)
        val five = CalendarFit.dayCellHeight(half, 5)
        assertEquals(31.333f, six.value, 0.05f)
        assertEquals(38f, five.value, 0.05f)
        assertTrue(six < CalendarFit.comfortableCell)
        assertTrue(five < CalendarFit.comfortableCell)
        assertEquals(64f, CalendarFit.chromeHeight(6).value, 0.01f)
        assertEquals(62f, CalendarFit.chromeHeight(5).value, 0.01f)
    }

    @Test
    fun sixWeeksStillMiss40dpWhen640dpIsAlreadyBelowTheSystemBars() {
        val half = CalendarFit.calendarHalf(
            CalendarFit.smallPhoneHeight,
            statusBar = 0.dp,
            navigationBar = 0.dp
        )
        assertEquals(288f, half.value, 0.01f)
        val six = CalendarFit.dayCellHeight(half, 6)
        val five = CalendarFit.dayCellHeight(half, 5)
        assertEquals(37.333f, six.value, 0.05f)
        assertEquals(45.2f, five.value, 0.05f)
        assertTrue(six < CalendarFit.comfortableCell)
        assertTrue(five >= CalendarFit.comfortableCell)
    }

    @Test
    fun aTypical411By914PhoneFitsBothMonthLengthsAt40dp() {
        val half = CalendarFit.calendarHalf(CalendarFit.typicalPhoneHeight)
        assertEquals(389f, half.value, 0.01f)
        val six = CalendarFit.dayCellHeight(half, 6)
        val five = CalendarFit.dayCellHeight(half, 5)
        assertEquals(54.167f, six.value, 0.05f)
        assertEquals(65.4f, five.value, 0.05f)
        assertTrue(six >= CalendarFit.comfortableCell)
        assertTrue(five >= CalendarFit.comfortableCell)
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
