package com.brian.solwidget.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.round

/** Average kW over a 30-minute bucket: Wh / 0.5 h / 1000 = Wh / 500. */
const val HOUSE_WH_PER_KW = 500.0

val HOUSE_BUCKET: Duration = Duration.ofMinutes(30)

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
 * Step polylines for average kW on each half-hour.
 * A run's last vertex is the last real period end. Nothing is added after it,
 * and a gap longer or shorter than [HOUSE_BUCKET] starts a new run.
 */
fun houseStepRuns(samples: List<HouseSample>): List<List<HouseVertex>> {
    val ordered = samples.sortedBy { it.periodEnd }
    if (ordered.isEmpty()) return emptyList()
    val groups = ArrayList<List<HouseSample>>()
    val current = ArrayList<HouseSample>()
    for (sample in ordered) {
        val previous = current.lastOrNull()
        if (previous != null && Duration.between(previous.periodEnd, sample.periodEnd) != HOUSE_BUCKET) {
            groups.add(current.toList())
            current.clear()
        }
        current.add(sample)
    }
    if (current.isNotEmpty()) groups.add(current.toList())
    return groups.map { stepVertices(it) }
}

private fun stepVertices(samples: List<HouseSample>): List<HouseVertex> {
    val vertices = ArrayList<HouseVertex>(samples.size * 2)
    samples.forEachIndexed { index, sample ->
        if (index == 0) {
            vertices.add(HouseVertex(sample.periodEnd.minus(HOUSE_BUCKET), sample.homeKw))
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
    val samplesInRange = samples.filter { !it.periodEnd.isBefore(from) && it.periodEnd.isBefore(to) }
    if (inRangeDays.isEmpty() && samplesInRange.isEmpty()) return null

    val coveredDates = inRangeDays.map { it.day }.toHashSet()
    val sampleKwh = samplesInRange.sumOf { sample ->
        val date = sample.periodEnd.atZone(zone).toLocalDate()
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
