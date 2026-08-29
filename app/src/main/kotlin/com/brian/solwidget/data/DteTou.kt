package com.brian.solwidget.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * DTE Dynamic Peak Pricing (D1.8) windows and this site's ¢/kWh.
 *
 * Hours are year-round. [BASE_*] are DTE's marketing stated base
 * (capacity + non-capacity + distribution). [OFF_PEAK_CENTS] /
 * [MID_PEAK_CENTS] / [PEAK_CENTS] are all-in volumetric *import* rates
 * from the Jun 3–Jul 2, 2026 bill (statement [RATES_AS_OF]): base + PSCR
 * + other volumetric (power-supply surcharges, delivery surcharges,
 * LIEAF) spread over 766.371 kWh inflow. Excludes the $8.50 service
 * charge and sales tax. Recompute from a later bill when PSCR or riders
 * move. PSCR that period: 1.877¢. Other volumetric:
 * ($1.62 + $7.46 + $1.25) / 766.371 ≈ 1.35¢.
 */
object DteTou {
    val ZONE: ZoneId = ZoneId.of("America/Detroit")
    const val PLAN_CODE = "D1.8"
    const val PLAN_NAME = "Residential Dynamic Peak Pricing"
    const val RIDER = "R18 Cat1"
    val RATES_AS_OF: LocalDate = LocalDate.of(2026, 7, 6)

    const val BASE_OFF_PEAK_CENTS = 14.31
    const val BASE_MID_PEAK_CENTS = 18.61
    const val BASE_PEAK_CENTS = 26.92

    const val OFF_PEAK_CENTS = 17.53
    const val MID_PEAK_CENTS = 21.83
    const val PEAK_CENTS = 30.15

    /**
     * Rider 18 (Distributed Generation) outflow credits for residential D1.8,
     * DTE Electric Rate Book Sheet D-115.00, Case U-21860, effective Mar 5, 2026.
     * Tariff figures are **before PSCR**. Credited outflow is tariff + PSCR.
     * Outflow is power-supply credit only (not 1:1 retail net metering).
     */
    const val PSCR_CENTS = 1.877
    const val OUTFLOW_OFF_PEAK_CENTS = 4.583
    const val OUTFLOW_MID_PEAK_CENTS = 8.884
    const val OUTFLOW_PEAK_CENTS = 17.198
    const val OUTFLOW_OFF_PEAK_WITH_PSCR_CENTS = OUTFLOW_OFF_PEAK_CENTS + PSCR_CENTS
    const val OUTFLOW_MID_PEAK_WITH_PSCR_CENTS = OUTFLOW_MID_PEAK_CENTS + PSCR_CENTS
    const val OUTFLOW_PEAK_WITH_PSCR_CENTS = OUTFLOW_PEAK_CENTS + PSCR_CENTS

    enum class Period(val cents: Double, val label: String) {
        OFF_PEAK(OFF_PEAK_CENTS, "Off-peak"),
        MID_PEAK(MID_PEAK_CENTS, "Mid-peak"),
        PEAK(PEAK_CENTS, "Peak")
    }

    data class Band(
        val start: Instant,
        val end: Instant,
        val period: Period
    )

    fun periodAt(instant: Instant): Period {
        val local = instant.atZone(ZONE)
        if (isOffPeakDay(local.toLocalDate())) return Period.OFF_PEAK
        val minutes = local.hour * 60 + local.minute
        return when {
            minutes < 7 * 60 -> Period.OFF_PEAK
            minutes < 15 * 60 -> Period.MID_PEAK
            minutes < 19 * 60 -> Period.PEAK
            minutes < 23 * 60 -> Period.MID_PEAK
            else -> Period.OFF_PEAK
        }
    }

    fun bands(from: Instant, to: Instant): List<Band> {
        if (!from.isBefore(to)) return emptyList()
        val result = ArrayList<Band>()
        var cursor = from
        while (cursor.isBefore(to)) {
            val period = periodAt(cursor)
            val next = minOf(nextBoundary(cursor), to)
            val last = result.lastOrNull()
            if (last != null && last.period == period) {
                result[result.lastIndex] = last.copy(end = next)
            } else {
                result += Band(cursor, next, period)
            }
            cursor = next
        }
        return result
    }

    private fun nextBoundary(instant: Instant): Instant {
        val local = instant.atZone(ZONE)
        val today = local.toLocalDate()
        val candidates = listOf(
            today.atTime(7, 0),
            today.atTime(15, 0),
            today.atTime(19, 0),
            today.atTime(23, 0),
            today.plusDays(1).atStartOfDay()
        )
        val next = candidates
            .map { it.atZone(ZONE).toInstant() }
            .firstOrNull { it.isAfter(instant) }
        return next ?: today.plusDays(1).atStartOfDay(ZONE).toInstant()
    }

    fun isOffPeakDay(date: LocalDate): Boolean {
        val dow = date.dayOfWeek
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY || isDesignatedHoliday(date)
    }

    fun isDesignatedHoliday(date: LocalDate): Boolean {
        val year = date.year
        return date == LocalDate.of(year, Month.JANUARY, 1) ||
            date == goodFriday(year) ||
            date == memorialDay(year) ||
            date == LocalDate.of(year, Month.JULY, 4) ||
            date == laborDay(year) ||
            date == thanksgiving(year) ||
            date == LocalDate.of(year, Month.DECEMBER, 25)
    }

    private fun memorialDay(year: Int): LocalDate =
        LocalDate.of(year, Month.MAY, 1)
            .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY))

    private fun laborDay(year: Int): LocalDate =
        LocalDate.of(year, Month.SEPTEMBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))

    private fun thanksgiving(year: Int): LocalDate =
        LocalDate.of(year, Month.NOVEMBER, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))

    private fun goodFriday(year: Int): LocalDate = easterSunday(year).minusDays(2)

    /** Anonymous Gregorian computus. */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
