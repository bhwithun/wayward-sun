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

    /**
     * One day step that stays on stored days.
     * The future stops at [newest]. Inside a loaded window, days before [earliest]
     * are empty. An empty window blocks another step past its start.
     */
    fun canStepDay(
        day: LocalDate,
        delta: Int,
        newest: LocalDate,
        windowFrom: LocalDate?,
        windowToExclusive: LocalDate?,
        earliest: LocalDate?
    ): Boolean {
        if (delta == 0) return false
        val next = day.plusDays(delta.toLong())
        if (next.isAfter(newest)) return false
        if (windowFrom != null && windowToExclusive != null && earliest != null) {
            val inside = !next.isBefore(windowFrom) && next.isBefore(windowToExclusive)
            if (inside && next.isBefore(earliest)) return false
        }
        if (earliest == null && windowFrom != null && delta < 0 && next.isBefore(windowFrom)) return false
        return true
    }

    /** One Sunday–Saturday step. A week after [newest]'s week, or entirely before the oldest sample, does not move. */
    fun canStepWeek(
        week: LocalDate,
        delta: Int,
        newest: LocalDate,
        windowFrom: LocalDate?,
        windowToExclusive: LocalDate?,
        earliest: LocalDate?
    ): Boolean {
        if (delta == 0) return false
        val next = weekStart(week).plusDays(7L * delta)
        if (next.isAfter(weekStart(newest))) return false
        val saturday = next.plusDays(6)
        if (windowFrom != null && windowToExclusive != null && earliest != null) {
            val overlaps = !saturday.isBefore(windowFrom) && next.isBefore(windowToExclusive)
            if (overlaps && saturday.isBefore(earliest)) return false
        }
        if (earliest == null && windowFrom != null && delta < 0 && next.isBefore(windowFrom)) return false
        return true
    }

    /**
     * Weeks the fullscreen pager can land on. Includes every loaded week that is not
     * entirely before the oldest sample, through [newest]'s week, plus one older week
     * when [canStepWeek] allows it so a swipe can still cross into the previous month.
     */
    fun fullscreenWeeks(
        anchor: LocalDate,
        newest: LocalDate,
        windowFrom: LocalDate?,
        windowToExclusive: LocalDate?,
        earliest: LocalDate?
    ): List<LocalDate> {
        val end = weekStart(newest)
        var start = weekStart(windowFrom ?: anchor)
        if (earliest != null) {
            var guard = 0
            while (start.plusDays(6).isBefore(earliest) && !start.isAfter(end) && guard++ < 400) {
                start = start.plusDays(7)
            }
        }
        if (start.isAfter(end)) start = end
        val pages = ArrayList<LocalDate>()
        var cursor = start
        var guard = 0
        while (!cursor.isAfter(end) && guard++ < 400) {
            pages += cursor
            cursor = cursor.plusDays(7)
        }
        val first = pages.firstOrNull() ?: weekStart(anchor)
        if (canStepWeek(first, -1, newest, windowFrom, windowToExclusive, earliest)) {
            val previous = first.minusDays(7)
            if (pages.isEmpty() || pages.first() != previous) pages.add(0, previous)
        }
        val anchorWeek = weekStart(anchor)
        if (anchorWeek !in pages) {
            pages += anchorWeek
            pages.sort()
        }
        return pages
    }

    /**
     * Days the fullscreen pager can land on. Same window as [canStepDay], plus one
     * older day when that step is legal.
     */
    fun fullscreenDays(
        anchor: LocalDate,
        newest: LocalDate,
        windowFrom: LocalDate?,
        windowToExclusive: LocalDate?,
        earliest: LocalDate?
    ): List<LocalDate> {
        var start = windowFrom ?: earliest ?: anchor
        if (earliest != null && start.isBefore(earliest)) start = earliest
        var end = newest
        if (start.isAfter(end)) start = end
        val pages = ArrayList<LocalDate>()
        var cursor = start
        var guard = 0
        while (!cursor.isAfter(end) && guard++ < 400) {
            pages += cursor
            cursor = cursor.plusDays(1)
        }
        val first = pages.firstOrNull() ?: anchor
        if (canStepDay(first, -1, newest, windowFrom, windowToExclusive, earliest)) {
            val previous = first.minusDays(1)
            if (pages.isEmpty() || pages.first() != previous) pages.add(0, previous)
        }
        if (anchor !in pages) {
            pages += anchor
            pages.sort()
        }
        return pages
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
