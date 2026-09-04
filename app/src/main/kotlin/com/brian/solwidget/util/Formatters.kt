package com.brian.solwidget.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateTime = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault())
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun kw(value: Double): String =
        if (value >= 10.0) String.format(Locale.US, "%.1f kW", value)
        else String.format(Locale.US, "%.2f kW", value)

    fun kwh(value: Double): String =
        String.format(Locale.US, "%d kWh", kotlin.math.round(value).toInt())

    fun cents(value: Double): String = String.format(Locale.US, "%.2f¢", value)

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        time.format(instant.atZone(zone))

    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(instant.atZone(zone))

    fun date(value: LocalDate): String = date.format(value)

    fun asOf(at: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        if (at == null) return "As of unknown"
        return "As of ${dateTime(at, zone)} (${relativeAge(at, now)})"
    }

    fun asOf(on: LocalDate, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = on.atStartOfDay(zone).toInstant()
        return "As of ${date(on)} (${relativeAge(at, now)})"
    }

    fun relativeAge(at: Instant, now: Instant): String {
        val duration = Duration.between(at, now)
        if (duration.isNegative || duration.isZero) return "just now"
        val days = duration.toDays()
        if (days >= 1L) return if (days == 1L) "1 day ago" else "$days days ago"
        val hours = duration.toHours()
        if (hours >= 1L) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
        return "less than an hour ago"
    }
}
