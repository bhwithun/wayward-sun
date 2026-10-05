package com.brian.solwidget.data

import java.time.Instant
import java.time.LocalDate
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
    fun weekRangeIsDetroitMidnightThroughTheNextSunday() {
        val (from, to) = DetroitCalendar.weekRange(LocalDate.of(2026, 9, 27))
        assertEquals(Instant.parse("2026-09-27T04:00:00Z"), from)
        assertEquals(Instant.parse("2026-10-04T04:00:00Z"), to)
    }
}
