package com.brian.solwidget.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean
)

data class CalendarWeek(
    /** Sunday that starts the week. */
    val id: LocalDate,
    val days: List<CalendarDay>
)

/**
 * Sunday–Saturday weeks in America/Detroit, matching the website history calendar.
 */
object DetroitCalendar {
    val ZONE: ZoneId = ZoneId.of("America/Detroit")

    fun today(now: Instant = Instant.now()): LocalDate = now.atZone(ZONE).toLocalDate()

    fun weekStart(date: LocalDate): LocalDate {
        val daysFromSunday = date.dayOfWeek.value % 7
        return date.minusDays(daysFromSunday.toLong())
    }

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

    /** Sunday 00:00 through the next Sunday 00:00 in America/Detroit. */
    fun weekRange(sunday: LocalDate): Pair<Instant, Instant> {
        val start = weekStart(sunday)
        val from = start.atStartOfDay(ZONE).toInstant()
        val to = start.plusDays(7).atStartOfDay(ZONE).toInstant()
        return from to to
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
