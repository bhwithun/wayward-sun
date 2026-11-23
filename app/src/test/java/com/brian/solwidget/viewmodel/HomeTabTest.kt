package com.brian.solwidget.viewmodel

import com.brian.solwidget.data.DetroitCalendar
import java.time.LocalDate
import java.time.YearMonth
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
    fun todayButtonIsHiddenOnlyOnTodaysDay() {
        assertFalse(showTodayButton(weekSelected = false, selectedDay = null, today = today))
        assertFalse(showTodayButton(weekSelected = false, selectedDay = today, today = today))
        assertTrue(showTodayButton(weekSelected = true, selectedDay = today, today = today))
        assertTrue(showTodayButton(weekSelected = false, selectedDay = yesterday, today = today))
        assertTrue(showTodayButton(weekSelected = false, selectedDay = today.plusDays(3), today = today))
    }

    @Test
    fun returningToTheCurrentMonthSelectsTodayNotTheFirst() {
        val december = YearMonth.of(2026, 12)
        val november = YearMonth.from(today)
        val decWeeks = DetroitCalendar.monthWeeks(december)
        val novWeeks = DetroitCalendar.monthWeeks(november)
        val decFirst = LocalDate.of(2026, 12, 1)
        assertTrue(novWeeks.any { week -> week.days.any { it.date == decFirst } })

        assertEquals(decFirst, dayForMonthShift(december, today, today, decWeeks))
        assertEquals(today, dayForMonthShift(november, decFirst, today, novWeeks))
        assertEquals(LocalDate.of(2026, 10, 1), dayForMonthShift(YearMonth.of(2026, 10), today, today, DetroitCalendar.monthWeeks(YearMonth.of(2026, 10))))
    }

    @Test
    fun pagingOntoTheFirstOfTheCurrentMonthSelectsToday() {
        assertEquals(today, dayAfterPagingIntoMonth(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 31), today))
        assertEquals(LocalDate.of(2026, 11, 30), dayAfterPagingIntoMonth(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 1), today))
        assertEquals(LocalDate.of(2026, 12, 1), dayAfterPagingIntoMonth(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 30), today))
        assertEquals(LocalDate.of(2026, 11, 1), dayAfterPagingIntoMonth(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), today))
    }

    @Test
    fun todayPollsOnlyTodayEveryFiveMinutesWhileResumed() {
        assertEquals(30_000L, HOME_CLOCK_MS)
        assertEquals(300_000L, TODAY_POLL_MS)
        assertFalse(todayPollDue(sinceLastMs = HOME_CLOCK_MS, viewingToday = true, foreground = true))
        assertFalse(todayPollDue(sinceLastMs = TODAY_POLL_MS - 1, viewingToday = true, foreground = true))
        assertTrue(todayPollDue(sinceLastMs = TODAY_POLL_MS, viewingToday = true, foreground = true))
        assertFalse(todayPollDue(sinceLastMs = TODAY_POLL_MS * 4, viewingToday = false, foreground = true))
        assertFalse(todayPollDue(sinceLastMs = TODAY_POLL_MS * 4, viewingToday = true, foreground = false))
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
