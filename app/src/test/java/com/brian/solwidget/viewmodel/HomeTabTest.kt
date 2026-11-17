package com.brian.solwidget.viewmodel

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val yesterday = today.minusDays(1)

    @Test
    fun appOpensOnTodaysDayView() {
        val state = HomeUiState()
        assertFalse(state.historyWeekSelected)
        assertNull(state.selectedDay)
        assertNull(state.historyMonth)
        assertTrue(viewingTodayDay(state.historyWeekSelected, state.selectedDay, today))
    }

    @Test
    fun todayIsTheDayChartAndAWeekOrAnotherDayIsNot() {
        assertTrue(viewingTodayDay(weekSelected = false, selectedDay = null, today = today))
        assertTrue(viewingTodayDay(weekSelected = false, selectedDay = today, today = today))
        assertFalse(viewingTodayDay(weekSelected = true, selectedDay = today, today = today))
        assertFalse(viewingTodayDay(weekSelected = false, selectedDay = yesterday, today = today))
    }

    @Test
    fun todayRefreshesOnTheSameThirtySecondClockLiveUsed() {
        assertEquals(30_000L, HOME_CLOCK_MS)
        assertFalse(todayClockDue(elapsedMs = 29_999L, viewingToday = true))
        assertTrue(todayClockDue(elapsedMs = HOME_CLOCK_MS, viewingToday = true))
        assertFalse(todayClockDue(elapsedMs = HOME_CLOCK_MS, viewingToday = false))
        assertFalse(todayClockDue(elapsedMs = HOME_CLOCK_MS * 4, viewingToday = false))
    }

    @Test
    fun anOpenTodayFollowsMidnightAndOtherDaysStayPut() {
        assertEquals(today, followToday(false, yesterday, yesterday, today))
        assertEquals(today, followToday(false, null, yesterday, today))
        assertNull(followToday(false, yesterday, yesterday, yesterday))
        assertNull(followToday(true, yesterday, yesterday, today))
        assertNull(followToday(false, yesterday.minusDays(3), yesterday, today))
    }
}
