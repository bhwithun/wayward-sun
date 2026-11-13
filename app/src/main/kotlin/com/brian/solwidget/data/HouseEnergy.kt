package com.brian.solwidget.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.round

/** Average kW over a 30-minute bucket: Wh / 0.5 h / 1000 = Wh / 500. */
const val HOUSE_WH_PER_KW = 500.0

/** Fixed solar axis. A higher half-hour is drawn on this edge and marked as clipped. */
const val HOUSE_SCALE_MAX_KW = 8.0

val HOUSE_BUCKET: Duration = Duration.ofMinutes(30)

val HOUSE_HOUR: Duration = Duration.ofHours(1)

/** Half-hour steps on a single day. Week and Live windows use hourly averages. */
fun houseDetailIsHalfHour(from: Instant, to: Instant): Boolean {
    val minutes = Duration.between(from, to).toMinutes()
    return minutes in 1..(26 * 60)
}

data class HouseSample(
    val periodEnd: Instant,
    val homeKw: Double
)

data class HouseVertex(
    val at: Instant,
    val kw: Double
)

data class HouseDay(
    val day: LocalDate,
    val homeKwh: Double,
    val fromSolarKwh: Double,
    val hasHalfHour: Boolean
)

data class HouseSummary(
    val homeKwh: Double,
    /** Null when solar is hidden or any started day in the range lacks half-hour data. */
    val coveredPercent: Int?,
    val hourlyDetailUnavailable: Boolean
)

fun homeKwFromWh(homeWh: Double): Double = homeWh / HOUSE_WH_PER_KW

fun kwhFromWh(wh: Double): Double = wh / 1000.0

/**
 * Step polylines. A run's last vertex is the last real period end.
 * A gap that is not exactly [bucket] starts a new run.
 */
fun houseStepRuns(
    samples: List<HouseSample>,
    bucket: Duration = HOUSE_BUCKET
): List<List<HouseVertex>> {
    val ordered = samples.sortedBy { it.periodEnd }
    if (ordered.isEmpty()) return emptyList()
    val groups = ArrayList<List<HouseSample>>()
    val current = ArrayList<HouseSample>()
    for (sample in ordered) {
        val previous = current.lastOrNull()
        if (previous != null && Duration.between(previous.periodEnd, sample.periodEnd) != bucket) {
            groups.add(current.toList())
            current.clear()
        }
        current.add(sample)
    }
    if (current.isNotEmpty()) groups.add(current.toList())
    return groups.map { stepVertices(it, bucket) }
}

/**
 * One point per clock hour: the mean kW of the half-hours whose midpoint falls in that hour.
 * The point's period end is the end of the hour. Hours with no samples are absent, so the line stops.
 */
fun houseHourlySamples(samples: List<HouseSample>, zone: ZoneId): List<HouseSample> {
    if (samples.isEmpty()) return emptyList()
    val grouped = samples.groupBy { sample ->
        val midpoint = sample.periodEnd.minus(Duration.ofMinutes(15))
        midpoint.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
    }
    return grouped.entries.sortedBy { it.key }.map { (hour, rows) ->
        val end = hour.plusHours(1).atZone(zone).toInstant()
        HouseSample(end, rows.sumOf { it.homeKw } / rows.size)
    }
}

/** `period_end` is the bucket end, so a day owns `(midnight, next midnight]`. */
fun housePeriodInRange(periodEnd: Instant, from: Instant, to: Instant): Boolean =
    periodEnd.isAfter(from) && !periodEnd.isAfter(to)

/** The half-hour ending at midnight belongs to the day that just finished. */
fun houseReadingDay(periodEnd: Instant, zone: ZoneId): LocalDate =
    periodEnd.minusMillis(1).atZone(zone).toLocalDate()

fun housePlotSamples(
    samples: List<HouseSample>,
    from: Instant,
    to: Instant,
    zone: ZoneId
): List<HouseSample> {
    val visible = samples.filter { housePeriodInRange(it.periodEnd, from, to) }
    return if (houseDetailIsHalfHour(from, to)) visible else houseHourlySamples(visible, zone)
}

/** Max half-hour kW in `(from, to]`, before any hourly average. */
fun houseHalfHourPeak(samples: List<HouseSample>, from: Instant, to: Instant): Double? =
    samples.filter { housePeriodInRange(it.periodEnd, from, to) }.maxOfOrNull { it.homeKw }

/** Readings above the fixed 0–8 kW scale. Each one gets a cap marker. */
fun houseClippedSamples(
    samples: List<HouseSample>,
    limitKw: Double = HOUSE_SCALE_MAX_KW
): List<HouseSample> = samples.filter { it.homeKw > limitKw }

fun housePeakKw(samples: List<HouseSample>): Double? = samples.maxOfOrNull { it.homeKw }

fun houseLegendLabel(peakKw: Double?): String {
    if (peakKw == null || !peakKw.isFinite()) return "House use"
    return String.format(Locale.US, "House use · Peak %.1f kW (half-hour)", peakKw)
}

/** The widget row under House use stays visible but does not toggle while House use is off. */
fun widgetHouseRowEnabled(houseOn: Boolean): Boolean = houseOn

/** Each value covers `[periodEnd - bucket, periodEnd)`. The vertex at `periodEnd` is the right edge. */
private fun stepVertices(samples: List<HouseSample>, bucket: Duration): List<HouseVertex> {
    val vertices = ArrayList<HouseVertex>(samples.size * 2)
    samples.forEachIndexed { index, sample ->
        if (index == 0) {
            vertices.add(HouseVertex(sample.periodEnd.minus(bucket), sample.homeKw))
        } else {
            vertices.add(HouseVertex(samples[index - 1].periodEnd, sample.homeKw))
        }
        vertices.add(HouseVertex(sample.periodEnd, sample.homeKw))
    }
    return vertices
}

fun detroitDatesInRange(from: Instant, to: Instant, zone: ZoneId): List<LocalDate> {
    if (!to.isAfter(from)) return emptyList()
    val first = from.atZone(zone).toLocalDate()
    val last = to.minusMillis(1).atZone(zone).toLocalDate()
    val dates = ArrayList<LocalDate>()
    var cursor = first
    while (!cursor.isAfter(last) && dates.size < 400) {
        dates.add(cursor)
        cursor = cursor.plusDays(1)
    }
    return dates
}

fun houseSummary(
    samples: List<HouseSample>,
    days: List<HouseDay>,
    from: Instant,
    to: Instant,
    zone: ZoneId,
    now: Instant,
    solarVisible: Boolean
): HouseSummary? {
    val dates = detroitDatesInRange(from, to, zone)
    val byDay = days.associateBy { it.day }
    val inRangeDays = dates.mapNotNull { byDay[it] }
    val samplesInRange = samples.filter { housePeriodInRange(it.periodEnd, from, to) }
    if (inRangeDays.isEmpty() && samplesInRange.isEmpty()) return null

    val coveredDates = inRangeDays.map { it.day }.toHashSet()
    val sampleKwh = samplesInRange.sumOf { sample ->
        val date = houseReadingDay(sample.periodEnd, zone)
        if (date in coveredDates) 0.0 else sample.homeKw * 0.5
    }
    val homeKwh = inRangeDays.sumOf { it.homeKwh } + sampleKwh

    val today = now.atZone(zone).toLocalDate()
    val started = dates.filter { !it.isAfter(today) }
    val coveredPercent = if (
        solarVisible &&
        started.isNotEmpty() &&
        started.all { byDay[it]?.hasHalfHour == true }
    ) {
        val home = started.sumOf { byDay.getValue(it).homeKwh }
        val solar = started.sumOf { byDay.getValue(it).fromSolarKwh }
        if (home > 0.0 && home.isFinite() && solar.isFinite()) round(100.0 * solar / home).toInt() else null
    } else {
        null
    }

    return HouseSummary(
        homeKwh = homeKwh,
        coveredPercent = coveredPercent,
        hourlyDetailUnavailable = inRangeDays.any { !it.hasHalfHour }
    )
}

fun houseSummaryLabel(summary: HouseSummary): String {
    val total = String.format(Locale.US, "House %.1f kWh", summary.homeKwh)
    val covered = summary.coveredPercent ?: return total
    return "$total · Solar covered $covered%"
}

fun houseChipVisible(
    samples: List<HouseSample>,
    days: List<HouseDay>,
    from: Instant,
    to: Instant,
    zone: ZoneId
): Boolean = houseSummary(samples, days, from, to, zone, to, solarVisible = false) != null
