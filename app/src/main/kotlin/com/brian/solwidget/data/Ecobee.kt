package com.brian.solwidget.data

import java.time.Duration
import java.time.Instant
import kotlin.math.round

/** How an indoor sample sits relative to the setpoint that applies at that moment. */
enum class EcobeeBand { DEADBAND, COOLING, HEATING }

data class EcobeeSample(
    val at: Instant,
    val indoorF: Double,
    val coolSetF: Double?,
    val heatSetF: Double?
)

/**
 * One drawable sample. [setF] is the setpoint being compared, and it is null in
 * the deadband so the chart never draws two dashed lines at once.
 */
data class EcobeeMark(
    val at: Instant,
    val indoorF: Double,
    val setF: Double?,
    val band: EcobeeBand
)

/** A hole longer than this breaks the line and the fill. Fifteen minutes stays connected. */
val ECOBEE_GAP: Duration = Duration.ofMinutes(15)

/**
 * Cool is checked first. Above the cool setpoint is a cooling gap. Below the
 * heat setpoint is a heating gap. Between them, or when that setpoint is missing,
 * there is no fill. "Off" arrives as null.
 */
fun EcobeeSample.classify(): EcobeeMark {
    val cool = coolSetF?.takeIf { it.isFinite() }
    val heat = heatSetF?.takeIf { it.isFinite() }
    return when {
        cool != null && indoorF > cool -> EcobeeMark(at, indoorF, cool, EcobeeBand.COOLING)
        heat != null && indoorF < heat -> EcobeeMark(at, indoorF, heat, EcobeeBand.HEATING)
        else -> EcobeeMark(at, indoorF, null, EcobeeBand.DEADBAND)
    }
}

/** A setpoint cell from an Ecobee export. Blank and "off" are not a temperature. */
fun parseEcobeeSetpoint(raw: String?): Double? {
    val text = raw?.trim()?.lowercase() ?: return null
    if (text.isEmpty() || text == "off" || text == "null" || text == "nan") return null
    return text.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Contiguous cooling or heating stretches long enough to fill. Deadband splits them. */
fun ecobeeFillSegments(run: List<EcobeeMark>): List<List<EcobeeMark>> {
    if (run.isEmpty()) return emptyList()
    val segments = ArrayList<List<EcobeeMark>>()
    var index = 0
    while (index < run.size) {
        val band = run[index].band
        var end = index + 1
        while (end < run.size && run[end].band == band) end++
        if (band != EcobeeBand.DEADBAND && end - index >= 2) {
            segments.add(run.subList(index, end).toList())
        }
        index = end
    }
    return segments
}

/**
 * Samples inside [from, to), split wherever a raw gap is longer than [gap],
 * then reduced to one mark per horizontal pixel. The gap split happens before
 * the pixel buckets, so a hole is never bridged by downsampling.
 */
fun prepareEcobee(
    samples: List<EcobeeSample>,
    from: Instant,
    to: Instant,
    plotWidthPx: Int,
    gap: Duration = ECOBEE_GAP,
    now: Instant? = null
): List<List<EcobeeMark>> {
    val end = if (now != null && now.isBefore(to)) now else to
    if (plotWidthPx < 1 || !end.isAfter(from)) return emptyList()
    val window = samples.asSequence()
        .filter { sample ->
            sample.indoorF.isFinite() &&
                !sample.at.isBefore(from) &&
                sample.at.isBefore(to) &&
                (now == null || !sample.at.isAfter(now))
        }
        .sortedBy { it.at }
        .toList()
    if (window.isEmpty()) return emptyList()
    val span = end.toEpochMilli() - from.toEpochMilli()
    val runs = ArrayList<List<EcobeeSample>>()
    val current = ArrayList<EcobeeSample>()
    for (sample in window) {
        val previous = current.lastOrNull()
        if (previous != null && Duration.between(previous.at, sample.at) > gap) {
            runs.add(current.toList())
            current.clear()
        }
        current.add(sample)
    }
    if (current.isNotEmpty()) runs.add(current.toList())
    return runs.map { downsampleEcobee(it, from, span, plotWidthPx) }
}

/**
 * One mark per pixel column. A cooling or heating sample beats a deadband
 * sample in the same column so a gap that shares a pixel is still filled.
 * Equal gaps keep the later sample.
 */
fun downsampleEcobee(
    run: List<EcobeeSample>,
    from: Instant,
    spanMillis: Long,
    plotWidthPx: Int
): List<EcobeeMark> {
    if (run.isEmpty() || plotWidthPx < 1 || spanMillis <= 0L) return emptyList()
    val buckets = LinkedHashMap<Int, EcobeeMark>(minOf(plotWidthPx, run.size))
    for (sample in run) {
        val mark = sample.classify()
        val px = (((mark.at.toEpochMilli() - from.toEpochMilli()).toDouble() / spanMillis) * plotWidthPx)
            .toInt()
            .coerceIn(0, plotWidthPx - 1)
        val existing = buckets[px]
        buckets[px] = if (existing == null || gapScore(mark) >= gapScore(existing)) mark else existing
    }
    return buckets.values.toList()
}

private fun gapScore(mark: EcobeeMark): Double = when (mark.band) {
    EcobeeBand.COOLING -> mark.indoorF - (mark.setF ?: mark.indoorF)
    EcobeeBand.HEATING -> (mark.setF ?: mark.indoorF) - mark.indoorF
    EcobeeBand.DEADBAND -> 0.0
}

/**
 * Fixed −20–100°F until Ecobee is on. Then the axis is the union of the outdoor
 * temperatures passed in (already limited to the visible window) and the indoor
 * line plus both setpoints.
 */
fun sharedFahrenheitRange(
    outdoorF: List<Double>,
    indoor: List<EcobeeSample>,
    from: Instant,
    to: Instant,
    scaleToData: Boolean
): Pair<Double, Double> {
    if (!scaleToData) return ForecastSnapshot.TEMP_MIN_F to ForecastSnapshot.TEMP_MAX_F
    val values = ArrayList<Double>()
    for (temp in outdoorF) if (temp.isFinite()) values.add(temp)
    for (sample in indoor) {
        if (!sample.indoorF.isFinite() || sample.at.isBefore(from) || !sample.at.isBefore(to)) continue
        values.add(sample.indoorF)
        sample.coolSetF?.takeIf { it.isFinite() }?.let { values.add(it) }
        sample.heatSetF?.takeIf { it.isFinite() }?.let { values.add(it) }
    }
    if (values.isEmpty()) return ForecastSnapshot.TEMP_MIN_F to ForecastSnapshot.TEMP_MAX_F
    var lo = values.min()
    var hi = values.max()
    if (hi - lo < 6.0) {
        val mid = (lo + hi) / 2.0
        lo = mid - 3.0
        hi = mid + 3.0
    }
    val pad = ((hi - lo) * 0.08).coerceAtLeast(0.5)
    return (lo - pad) to (hi + pad)
}

fun formatTempBound(value: Double): String = "${round(value).toInt()}°F"
