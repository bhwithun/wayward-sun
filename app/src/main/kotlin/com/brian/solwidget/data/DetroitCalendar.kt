package com.brian.solwidget.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean
)

data class CalendarWeek(
    /** Sunday that starts the week. */
    val id: LocalDate,
    val days: List<CalendarDay>
)

/** Visible month plus the Sunday–Saturday week drawn on the history chart. */
data class HistoryFocus(
    val month: YearMonth,
    val week: LocalDate
)

/**
 * Sunday–Saturday weeks in America/Detroit, matching the website history calendar.
 */
object DetroitCalendar {
    val ZONE: ZoneId = ZoneId.of("America/Detroit")

    /** Sunday-start week, and the week that contains January 1 is week 1. */
    private val US_WEEKS: WeekFields = WeekFields.of(DayOfWeek.SUNDAY, 1)

    fun today(now: Instant = Instant.now()): LocalDate = now.atZone(ZONE).toLocalDate()

    fun weekStart(date: LocalDate): LocalDate {
        val daysFromSunday = date.dayOfWeek.value % 7
        return date.minusDays(daysFromSunday.toLong())
    }

    /** US week-of-year for the Sunday that starts [sunday]. Jan 1's week is week 1. */
    fun weekNumber(sunday: LocalDate): Int = weekStart(sunday).get(US_WEEKS.weekOfWeekBasedYear())

    fun monthWeeks(year: Int, month: Int): List<CalendarWeek> {
        val firstOfMonth = LocalDate.of(year, month, 1)
        val first = weekStart(firstOfMonth)
        val lastSunday = weekStart(firstOfMonth.plusMonths(1).minusDays(1))
        val weeks = ArrayList<CalendarWeek>()
        var cursor = first
        while (!cursor.isAfter(lastSunday)) {
            val days = (0..6).map { offset ->
                val day = cursor.plusDays(offset.toLong())
                CalendarDay(day, day.year == year && day.monthValue == month)
            }
            weeks += CalendarWeek(cursor, days)
            cursor = cursor.plusDays(7)
        }
        return weeks
    }

    fun monthWeeks(month: YearMonth): List<CalendarWeek> = monthWeeks(month.year, month.monthValue)

    /**
     * Local midnight through the next midnight.
     * America/Detroit shares this clock with America/New_York.
     */
    fun dayRange(date: LocalDate): Pair<Instant, Instant> {
        val from = date.atStartOfDay(ZONE).toInstant()
        val to = date.plusDays(1).atStartOfDay(ZONE).toInstant()
        return from to to
    }

    /** Sunday 00:00 through the next Sunday 00:00 in America/Detroit. */
    fun weekRange(sunday: LocalDate): Pair<Instant, Instant> {
        val start = weekStart(sunday)
        val from = start.atStartOfDay(ZONE).toInstant()
        val to = start.plusDays(7).atStartOfDay(ZONE).toInstant()
        return from to to
    }

    /**
     * Moves one Sunday–Saturday week. A week still on the visible month stays there.
     * Otherwise the month moves with [delta] so the new week is on the calendar.
     */
    fun shiftWeek(month: YearMonth, week: LocalDate, delta: Int): HistoryFocus {
        val nextWeek = weekStart(week).plusDays(7L * delta)
        if (monthWeeks(month).any { it.id == nextWeek }) {
            return HistoryFocus(month, nextWeek)
        }
        val adjacent = month.plusMonths(delta.toLong())
        val selected = monthWeeks(adjacent).find { it.id == nextWeek }?.id ?: nextWeek
        return HistoryFocus(adjacent, selected)
    }

    /** First Sunday of the visible month through the day after its last Saturday. */
    fun monthFetchRange(weeks: List<CalendarWeek>): Pair<Instant, Instant> {
        val start = weeks.first().id
        val endSunday = weeks.last().id
        val from = start.atStartOfDay(ZONE).toInstant()
        val to = endSunday.plusDays(7).atStartOfDay(ZONE).toInstant()
        return from to to
    }
}
