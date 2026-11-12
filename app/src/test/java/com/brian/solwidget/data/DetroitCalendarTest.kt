package com.brian.solwidget.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetroitCalendarTest {
    @Test
    fun october2026StartsOnTheSundayBeforeTheFirst() {
        val weeks = DetroitCalendar.monthWeeks(2026, 10)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 27), weeks.first().id)
        assertEquals(LocalDate.of(2026, 10, 25), weeks.last().id)
        assertFalse(weeks.first().days.first().inMonth)
        assertTrue(weeks.first().days.first { it.date == LocalDate.of(2026, 10, 1) }.inMonth)
        assertEquals(7, weeks.first().days.size)
        assertEquals(LocalDate.of(2026, 10, 3), weeks.first().days.last().date)
    }

    @Test
    fun shiftWeekStaysInOctoberUntilTheWeekLeavesTheMonth() {
        val october = YearMonth.of(2026, 10)
        val earlier = DetroitCalendar.shiftWeek(october, LocalDate.of(2026, 10, 4), -1)
        assertEquals(october, earlier.month)
        assertEquals(LocalDate.of(2026, 9, 27), earlier.week)

        val intoSeptember = DetroitCalendar.shiftWeek(october, LocalDate.of(2026, 9, 27), -1)
        assertEquals(YearMonth.of(2026, 9), intoSeptember.month)
        assertEquals(LocalDate.of(2026, 9, 20), intoSeptember.week)

        val intoNovember = DetroitCalendar.shiftWeek(october, LocalDate.of(2026, 10, 25), 1)
        assertEquals(YearMonth.of(2026, 11), intoNovember.month)
        assertEquals(LocalDate.of(2026, 11, 1), intoNovember.week)
    }

    @Test
    fun weekNumberUsesSundayStartAndCountsTheJanuaryFirstWeekAsOne() {
        assertEquals(1, DetroitCalendar.weekNumber(LocalDate.of(2025, 12, 28)))
        assertEquals(52, DetroitCalendar.weekNumber(LocalDate.of(2025, 12, 21)))
        assertEquals(2, DetroitCalendar.weekNumber(LocalDate.of(2026, 1, 4)))
        assertEquals(46, DetroitCalendar.weekNumber(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun dayRangeIsEasternMidnightThroughTheNextMidnight() {
        val (from, to) = DetroitCalendar.dayRange(LocalDate.of(2026, 11, 11))
        assertEquals(Instant.parse("2026-11-11T05:00:00Z"), from)
        assertEquals(Instant.parse("2026-11-12T05:00:00Z"), to)
    }

    @Test
    fun fullscreenSwipeStopsAtTodayAndAtTheFirstSampleInTheLoadedWindow() {
        val today = LocalDate.of(2026, 11, 11)
        val windowFrom = LocalDate.of(2026, 11, 1)
        val windowTo = LocalDate.of(2026, 12, 1)
        val earliest = LocalDate.of(2026, 11, 8)
        assertFalse(DetroitCalendar.canStepDay(today, 1, today, windowFrom, windowTo, earliest))
        assertTrue(DetroitCalendar.canStepDay(today, -1, today, windowFrom, windowTo, earliest))
        assertFalse(DetroitCalendar.canStepDay(earliest, -1, today, windowFrom, windowTo, earliest))
        assertTrue(DetroitCalendar.canStepDay(LocalDate.of(2026, 11, 1), -1, today, null, null, null))
        assertFalse(DetroitCalendar.canStepWeek(LocalDate.of(2026, 11, 8), 1, today, windowFrom, windowTo, earliest))
        assertFalse(DetroitCalendar.canStepWeek(LocalDate.of(2026, 11, 8), -1, today, windowFrom, windowTo, earliest))
        assertTrue(
            DetroitCalendar.canStepWeek(
                LocalDate.of(2026, 11, 8),
                -1,
                today,
                windowFrom,
                windowTo,
                LocalDate.of(2026, 11, 1)
            )
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 8)),
            DetroitCalendar.fullscreenWeeks(LocalDate.of(2026, 11, 8), today, windowFrom, windowTo, earliest)
        )
        assertEquals(
            listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)),
            DetroitCalendar.fullscreenWeeks(
                LocalDate.of(2026, 11, 8),
                today,
                windowFrom,
                windowTo,
                LocalDate.of(2026, 11, 1)
            )
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), today),
            DetroitCalendar.fullscreenDays(today, today, windowFrom, windowTo, earliest)
        )
    }

    @Test
    fun forwardReachesTodayWhenTheLoadedMonthEndsEarlier() {
        val today = LocalDate.of(2026, 12, 15)
        val octoberFrom = LocalDate.of(2026, 9, 27)
        val octoberTo = LocalDate.of(2026, 11, 1)
        val earliest = LocalDate.of(2026, 9, 28)
        assertTrue(
            DetroitCalendar.canStepWeek(
                LocalDate.of(2026, 10, 25),
                1,
                today,
                octoberFrom,
                octoberTo,
                earliest
            )
        )
        val weeks = DetroitCalendar.fullscreenWeeks(
            LocalDate.of(2026, 10, 25),
            today,
            octoberFrom,
            octoberTo,
            earliest
        )
        assertTrue(weeks.contains(LocalDate.of(2026, 11, 1)))
        assertTrue(weeks.contains(LocalDate.of(2026, 12, 13)))
        assertFalse(weeks.contains(LocalDate.of(2026, 12, 20)))
        assertEquals(LocalDate.of(2026, 12, 13), weeks.last())
        assertFalse(
            DetroitCalendar.canStepWeek(
                LocalDate.of(2026, 12, 13),
                1,
                today,
                octoberFrom,
                octoberTo,
                earliest
            )
        )

        val augustFrom = LocalDate.of(2026, 7, 26)
        val augustTo = LocalDate.of(2026, 9, 6)
        assertTrue(
            DetroitCalendar.canStepWeek(
                LocalDate.of(2026, 8, 30),
                1,
                today,
                augustFrom,
                augustTo,
                LocalDate.of(2025, 8, 1)
            )
        )
        val fromAugust = DetroitCalendar.fullscreenWeeks(
            LocalDate.of(2026, 8, 30),
            today,
            augustFrom,
            augustTo,
            LocalDate.of(2025, 8, 1)
        )
        assertTrue(fromAugust.contains(LocalDate.of(2026, 9, 6)))
        assertTrue(fromAugust.contains(LocalDate.of(2026, 10, 4)))
        assertEquals(LocalDate.of(2026, 12, 13), fromAugust.last())

        assertTrue(
            DetroitCalendar.canStepDay(
                LocalDate.of(2026, 10, 31),
                1,
                today,
                octoberFrom,
                octoberTo,
                earliest
            )
        )
        assertFalse(DetroitCalendar.canStepDay(today, 1, today, octoberFrom, octoberTo, earliest))
        val days = DetroitCalendar.fullscreenDays(
            LocalDate.of(2026, 10, 31),
            today,
            octoberFrom,
            octoberTo,
            earliest
        )
        assertTrue(days.contains(LocalDate.of(2026, 11, 1)))
        assertEquals(today, days.last())
        assertFalse(days.contains(today.plusDays(1)))

        assertTrue(DetroitCalendar.canShiftMonth(YearMonth.of(2026, 10), 1, today))
        assertTrue(DetroitCalendar.canShiftMonth(YearMonth.of(2026, 11), 1, today))
        assertFalse(DetroitCalendar.canShiftMonth(YearMonth.of(2026, 12), 1, today))
        assertTrue(DetroitCalendar.canShiftMonth(YearMonth.of(2026, 12), -1, today))
        assertTrue(DetroitCalendar.canShiftMonth(YearMonth.of(2026, 10), -1, today))
    }

    @Test
    fun dayPagerReachesTodayFromTheFirstEcobeeMonth() {
        val today = LocalDate.of(2026, 12, 15)
        val days = DetroitCalendar.fullscreenDays(
            LocalDate.of(2025, 8, 30),
            today,
            LocalDate.of(2025, 7, 27),
            LocalDate.of(2025, 9, 7),
            LocalDate.of(2025, 8, 1)
        )
        assertEquals(today, days.last())
        assertTrue(days.contains(LocalDate.of(2025, 8, 30)))
        assertTrue(days.contains(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun weekRangeIsDetroitMidnightThroughTheNextSunday() {
        val (from, to) = DetroitCalendar.weekRange(LocalDate.of(2026, 9, 27))
        assertEquals(Instant.parse("2026-09-27T04:00:00Z"), from)
        assertEquals(Instant.parse("2026-10-04T04:00:00Z"), to)
    }
}
