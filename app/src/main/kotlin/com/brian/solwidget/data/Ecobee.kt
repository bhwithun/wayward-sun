package com.brian.solwidget.data

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** How an indoor sample sits relative to the setpoint that applies at that moment. */
enum class EcobeeBand { DEADBAND, COOLING, HEATING }

data class EcobeeSample(
    val at: Instant,
    val indoorF: Double,
    val coolSetF: Double?,
    val heatSetF: Double?,
    val heatPumpSec: Int? = null,
    val auxHeatSec: Int? = null,
    val coolSec: Int? = null,
    val fanSec: Int? = null,
    val outdoorF: Double? = null
)

/**
 * One drawable sample. [setF] is the cool setpoint when indoor is above it, the
 * heat setpoint when indoor is below it, and in the deadband the setpoint closer
 * to indoor. It is null only when both setpoints are missing.
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
 * Cool is checked first. Above the cool setpoint is too warm. Below the heat
 * setpoint is too cool. Between them, or level with a setpoint, there is no fill.
 * The drawn setpoint is the one that decided the band, or the closer one in the
 * deadband. A tie uses the cool setpoint. "Off" arrives as null.
 */
fun EcobeeSample.classify(): EcobeeMark {
    val cool = coolSetF?.takeIf { it.isFinite() }
    val heat = heatSetF?.takeIf { it.isFinite() }
    return when {
        cool != null && indoorF > cool -> EcobeeMark(at, indoorF, cool, EcobeeBand.COOLING)
        heat != null && indoorF < heat -> EcobeeMark(at, indoorF, heat, EcobeeBand.HEATING)
        else -> EcobeeMark(at, indoorF, closerSetpoint(indoorF, cool, heat), EcobeeBand.DEADBAND)
    }
}

private fun closerSetpoint(indoor: Double, cool: Double?, heat: Double?): Double? {
    if (cool == null) return heat
    if (heat == null) return cool
    return if (abs(indoor - cool) <= abs(indoor - heat)) cool else heat
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
    scaleToData: Boolean,
    pad: Boolean = true
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
    if (!pad) return lo to hi
    val margin = ((hi - lo) * 0.08).coerceAtLeast(0.5)
    return (lo - margin) to (hi + margin)
}

fun formatTempBound(value: Double): String = "${round(value).toInt()}°F"

/** Consumers Energy gas furnace, about 60 cents per runtime hour. */
const val GAS_AUX_DOLLARS_PER_HOUR = 0.60

data class HvacBar(
    val at: Instant,
    val heatPumpMin: Double,
    val auxMin: Double,
    val coolMin: Double,
    val bucketMin: Double
)

data class HvacSummary(
    val heatPumpHours: Double,
    val auxHours: Double,
    val coolHours: Double,
    val fanHours: Double
) {
    val gasCostDollars: Double get() = auxHours * GAS_AUX_DOLLARS_PER_HOUR
}

fun hvacSummary(samples: List<EcobeeSample>, from: Instant, to: Instant, now: Instant? = null): HvacSummary {
    var heat = 0.0
    var aux = 0.0
    var cool = 0.0
    var fan = 0.0
    for (sample in samples) {
        if (sample.at.isBefore(from) || !sample.at.isBefore(to)) continue
        if (now != null && sample.at.isAfter(now)) continue
        heat += sample.heatPumpSec ?: 0
        aux += sample.auxHeatSec ?: 0
        cool += sample.coolSec ?: 0
        fan += sample.fanSec ?: 0
    }
    return HvacSummary(heat / 3600.0, aux / 3600.0, cool / 3600.0, fan / 3600.0)
}

fun hvacSummaryLabel(summary: HvacSummary): String {
    return "Heat pump ${hours(summary.heatPumpHours)} · Gas ${hours(summary.auxHours)} · " +
        "Cool ${hours(summary.coolHours)} · Gas ${money(summary.gasCostDollars)}"
}

private fun hours(value: Double): String = String.format("%.1f h", value)

private fun money(value: Double): String = String.format("$%.2f", value)

/** One stacked bar per horizontal pixel. Heights are minutes inside that pixel's time span. */
fun hvacBars(
    samples: List<EcobeeSample>,
    from: Instant,
    to: Instant,
    plotWidthPx: Int,
    now: Instant? = null
): List<HvacBar> {
    val end = if (now != null && now.isBefore(to)) now else to
    if (plotWidthPx < 1 || !end.isAfter(from)) return emptyList()
    val span = end.toEpochMilli() - from.toEpochMilli()
    if (span <= 0L) return emptyList()
    val bucketMin = span.toDouble() / plotWidthPx / 60_000.0
    val seconds = HashMap<Int, DoubleArray>()
    for (sample in samples) {
        if (sample.at.isBefore(from) || !sample.at.isBefore(to)) continue
        if (now != null && sample.at.isAfter(now)) continue
        val px = (((sample.at.toEpochMilli() - from.toEpochMilli()).toDouble() / span) * plotWidthPx)
            .toInt()
            .coerceIn(0, plotWidthPx - 1)
        val slot = seconds.getOrPut(px) { DoubleArray(3) }
        slot[0] += (sample.heatPumpSec ?: 0).toDouble()
        slot[1] += (sample.auxHeatSec ?: 0).toDouble()
        slot[2] += (sample.coolSec ?: 0).toDouble()
    }
    return seconds.entries.sortedBy { it.key }.map { (px, slot) ->
        val at = Instant.ofEpochMilli(from.toEpochMilli() + ((px + 0.5) / plotWidthPx * span).toLong())
        HvacBar(at, slot[0] / 60.0, slot[1] / 60.0, slot[2] / 60.0, bucketMin)
    }
}

data class TemperatureGrid(
    val min: Double,
    val max: Double,
    val step: Double,
    val ticks: List<Double>
)

/**
 * Horizontal °F lines land on a round step. When the axis is fitted to the data,
 * the step is the one whose snapped range just covers the values without jumping
 * to a much wider scale. A tick within 3° of freezing is omitted so the dotted
 * 32° line keeps that label.
 */
fun temperatureGrid(dataMin: Double, dataMax: Double, snapEnds: Boolean): TemperatureGrid {
    var lo = dataMin
    var hi = dataMax
    if (hi - lo < 1.0) {
        lo -= 5.0
        hi += 5.0
    }
    val step = if (snapEnds) {
        val fit = tightestTempFit(lo, hi)
        lo = fit.min
        hi = fit.max
        fit.step
    } else {
        chooseTempStep(hi - lo)
    }
    val ticks = ArrayList<Double>()
    fun add(value: Double) {
        if (value < lo - 1e-6 || value > hi + 1e-6) return
        if (abs(value - 32.0) <= 3.0) return
        if (ticks.any { abs(it - value) < 0.01 }) return
        ticks.add(value)
    }
    add(lo)
    add(hi)
    var tick = ceil(lo / step - 1e-9) * step
    while (tick <= hi + 1e-6) {
        add(tick)
        tick += step
    }
    ticks.sort()
    return TemperatureGrid(lo, hi, step, ticks)
}

private data class TempFit(val step: Double, val min: Double, val max: Double)

/** Smallest snapped range whose step still leaves a readable number of intervals. */
private fun tightestTempFit(dataMin: Double, dataMax: Double): TempFit {
    val steps = doubleArrayOf(5.0, 10.0, 20.0, 25.0, 50.0, 100.0)
    val fits = steps.map { step ->
        val lo = floor(dataMin / step) * step
        var hi = ceil(dataMax / step) * step
        if (hi <= lo) hi = lo + step
        TempFit(step, lo, hi)
    }
    val readable = fits.filter { fit ->
        val intervals = (fit.max - fit.min) / fit.step
        intervals in 2.0..6.0
    }
    return (readable.ifEmpty { fits }).minWith(compareBy({ it.max - it.min }, { -it.step }))
}

fun chooseTempStep(span: Double): Double {
    val options = doubleArrayOf(5.0, 10.0, 20.0, 50.0, 100.0)
    return options.minBy { step ->
        val intervals = span / step
        when {
            intervals < 2.0 -> 10.0 + (2.0 - intervals)
            intervals > 6.0 -> 10.0 + (intervals - 6.0)
            else -> abs(intervals - 3.0)
        }
    }
}
