package com.brian.solwidget.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DayTempRange
import com.brian.solwidget.data.DteTou
import com.brian.solwidget.data.EcobeeBand
import com.brian.solwidget.data.EcobeeMark
import com.brian.solwidget.data.EcobeeSample
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.SolarDayLabels
import com.brian.solwidget.data.SunTimes
import com.brian.solwidget.data.WeatherPoint
import com.brian.solwidget.data.HvacBar
import com.brian.solwidget.data.ecobeeFillSegments
import com.brian.solwidget.data.hvacBars
import com.brian.solwidget.data.forecastStrokes
import com.brian.solwidget.data.formatTempBound
import com.brian.solwidget.data.prepareEcobee
import com.brian.solwidget.data.sharedFahrenheitRange
import com.brian.solwidget.data.temperatureGrid
import com.brian.solwidget.data.dayTempRanges
import com.brian.solwidget.data.isNight
import com.brian.solwidget.data.liveStroke
import com.brian.solwidget.data.sunEventFractions
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.util.Formatters
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

private val DayLabel = DateTimeFormatter.ofPattern("EEE d")

private const val TEMP_STROKE_DP = 1.6f
private const val NIGHT_ALPHA = 0.3f
private const val NIGHT_DASH_ON_DP = 9f
private const val NIGHT_DASH_OFF_DP = 7f
private const val BITMAP_PX_PER_DP = 2.75f
private const val TOO_WARM_ALPHA = 0.40f
private const val TOO_COOL_ALPHA = 0.50f
private const val FREEZE_F = 32.0

private enum class AxisUnits { NONE, SOLAR, TEMP, PRECIP, RATES }

private fun axisUnits(layers: ChartLayers, labelRates: Boolean): AxisUnits {
    val labeled = buildList {
        if (layers.power) add(AxisUnits.SOLAR)
        if (layers.degrees) add(AxisUnits.TEMP)
        if (layers.precipitation) add(AxisUnits.PRECIP)
        if (labelRates && layers.rates) add(AxisUnits.RATES)
    }
    return labeled.singleOrNull() ?: AxisUnits.NONE
}

@Composable
fun PowerChart(
    points: List<PowerPoint>,
    now: Instant,
    modifier: Modifier = Modifier,
    capacityKw: Double = ForecastSnapshot.SYSTEM_CAPACITY_KW,
    weather: List<WeatherPoint> = emptyList(),
    rangeFrom: Instant? = null,
    rangeTo: Instant? = null,
    layers: ChartLayers = ChartLayers(),
    sunDays: List<SunTimes> = emptyList(),
    isDemo: Boolean = false,
    zone: ZoneId = ZoneId.systemDefault(),
    ecobee: List<EcobeeSample> = emptyList()
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (points.size < 2 && weather.size < 2 && !layers.rates && ecobee.size < 2) return@Canvas

        val units = axisUnits(layers, labelRates = true)
        val showTempLabels = layers.degrees
        val left = if (showTempLabels || units != AxisUnits.NONE) 52.dp.toPx() else 12.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val dayBaseline = 16.dp.toPx()
        val tempBaseline = 38.dp.toPx()
        val top = if (layers.temperature) 50.dp.toPx() else 28.dp.toPx()
        val bottom = size.height - if (layers.power) 28.dp.toPx() else 12.dp.toPx()
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)

        val startInstant = rangeFrom ?: points.firstOrNull()?.periodEnd
            ?: weather.firstOrNull()?.time ?: ecobee.firstOrNull()?.at
        val endInstant = rangeTo ?: points.lastOrNull()?.periodEnd
            ?: weather.lastOrNull()?.time ?: ecobee.lastOrNull()?.at
        if (startInstant == null || endInstant == null) return@Canvas
        val minTime = startInstant.toEpochMilli().toFloat()
        val maxTime = endInstant.toEpochMilli().toFloat()
        val span = max(1f, maxTime - minTime)
        val solarMax = ForecastSnapshot.SOLAR_MAX_KW

        fun xOf(time: Instant): Float =
            left + ((time.toEpochMilli() - minTime) / span) * width

        fun yOf(kw: Double): Float =
            bottom - ((kw / solarMax).toFloat().coerceIn(0f, 1f) * height)

        val gridPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E8EEF7")
            textSize = 28f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val kwhPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E8EEF7")
            textSize = 26f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val unitPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E8EEF7")
            textSize = 30f
            isAntiAlias = true
        }

        val native = drawContext.canvas.nativeCanvas
        val from = Instant.ofEpochMilli(minTime.toLong())
        val to = Instant.ofEpochMilli(maxTime.toLong())
        val outdoorForScale = if (layers.temperature) {
            weather.filter { !it.time.isBefore(from) && it.time.isBefore(to) }.map { it.tempF }
        } else {
            emptyList()
        }
        val rawTemps = sharedFahrenheitRange(
            outdoorForScale,
            if (layers.ecobee) ecobee.filter { !it.at.isAfter(now) } else emptyList(),
            from,
            to,
            scaleToData = layers.ecobee,
            pad = !layers.ecobee
        )
        val tempGrid = temperatureGrid(rawTemps.first, rawTemps.second, snapEnds = layers.ecobee)
        val tempMin = tempGrid.min
        val tempMax = tempGrid.max
        val tempSpan = (tempMax - tempMin).takeIf { it > 0.0 } ?: 1.0
        fun yOfTemp(temp: Double): Float =
            bottom - (((temp - tempMin) / tempSpan).toFloat().coerceIn(0f, 1f) * height)
        fun yOfPop(pop: Double): Float =
            bottom - ((pop / ForecastSnapshot.PRECIP_MAX).toFloat().coerceIn(0f, 1f) * height)
        fun yOfRate(cents: Double): Float =
            bottom - (((cents - ForecastSnapshot.RATE_MIN_CENTS) /
                (ForecastSnapshot.RATE_MAX_CENTS - ForecastSnapshot.RATE_MIN_CENTS))
                .toFloat().coerceIn(0f, 1f) * height)

        if (layers.rates) {
            val bands = DteTou.bands(from, to)
            if (layers.buy) drawRateArea(bands, ::xOf, ::yOfRate, SolColors.Buy) { it.cents }
            if (layers.sell) drawRateArea(bands, ::xOf, ::yOfRate, SolColors.Sell) { it.sellCents }
        }

        if (layers.hvac) {
            drawHvacBars(
                hvacBars(ecobee, from, to, width.toInt().coerceAtLeast(1), now),
                ::xOf,
                bottom,
                height,
                width
            )
        }

        if (layers.precipitation && weather.size >= 2) {
            val barWidth = (width / weather.size.toFloat()) * 0.65f
            weather.forEach { point ->
                val pop = point.popPercent ?: return@forEach
                if (pop <= 0.0) return@forEach
                val x = xOf(point.time)
                if (x in left..right) {
                    drawRect(
                        color = SolColors.Precip,
                        topLeft = Offset(x - barWidth / 2f, yOfPop(pop)),
                        size = Size(barWidth, bottom - yOfPop(pop))
                    )
                }
            }
        }

        if (showTempLabels) {
            for (tick in tempGrid.ticks) {
                val y = yOfTemp(tick)
                drawLine(SolColors.Grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                val labelY = when {
                    abs(tick - tempMax) < 0.01 -> top + 16f
                    abs(tick - tempMin) < 0.01 -> bottom + 6f
                    else -> y + 8f
                }
                native.drawText(formatTempBound(tick), 6f, labelY, unitPaint)
            }
        } else {
            for (step in 0..2) {
                val kw = solarMax * step / 2.0
                val y = yOf(kw)
                drawLine(SolColors.Grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
        }
        when (units) {
            AxisUnits.SOLAR -> {
                native.drawText("8 kW", 8f, top + 16f, unitPaint)
                native.drawText("4 kW", 8f, yOf(4.0) + 8f, unitPaint)
                native.drawText("0 kW", 8f, bottom + 6f, unitPaint)
            }
            AxisUnits.TEMP -> Unit
            AxisUnits.PRECIP -> {
                native.drawText("100%", 8f, top + 16f, unitPaint)
                native.drawText("50%", 8f, yOfPop(50.0) + 8f, unitPaint)
                native.drawText("0%", 8f, bottom + 6f, unitPaint)
            }
            AxisUnits.RATES -> {
                for (cents in 0..40 step 10) {
                    val y = when (cents) {
                        40 -> top + 16f
                        0 -> bottom + 6f
                        else -> yOfRate(cents.toDouble())
                    }
                    native.drawText("$cents¢", 8f, y, unitPaint)
                }
            }
            AxisUnits.NONE -> Unit
        }

        if (layers.degrees) {
            drawFreezeReference(native, ::yOfTemp, left, right, tempMin, tempMax)
        }

        val ecobeeRuns = if (layers.ecobee) {
            prepareEcobee(ecobee, from, to, width.toInt().coerceAtLeast(1), now = now)
        } else {
            emptyList()
        }
        if (ecobeeRuns.isNotEmpty()) {
            drawEcobeeFills(ecobeeRuns, ::xOf, ::yOfTemp)
        }

        if (layers.solcast) {
            val (past, future) = forecastStrokes(points, now)
            drawSeries(past, ::xOf, ::yOf, SolColors.Forecast, dotted = isDemo, alpha = 0.5f, breakGaps = true)
            drawSeries(future, ::xOf, ::yOf, SolColors.Forecast, dotted = isDemo, breakGaps = true)
        }
        if (layers.enphase) {
            drawSeries(liveStroke(points), ::xOf, ::yOf, SolColors.Live, dotted = isDemo)
        }

        if (layers.temperature && weather.size >= 2) {
            drawTempLine(weather, ::xOf, ::yOfTemp, sunDays)
        }
        if (ecobeeRuns.isNotEmpty()) {
            drawEcobeeLines(ecobeeRuns, ::xOf, ::yOfTemp)
        }

        if (!now.isBefore(startInstant) && now.isBefore(endInstant)) {
            val nowX = xOf(now)
            drawLine(
                color = SolColors.Now,
                start = Offset(nowX, top),
                end = Offset(nowX, bottom),
                strokeWidth = 2.dp.toPx()
            )
        }

        val dayTotals = if (layers.power) {
            SolarDayLabels.summaries(points, zone).associateBy { it.date }
        } else {
            emptyMap()
        }
        val tempRanges = if (layers.temperature) dayTempRanges(weather, zone) else emptyMap()
        val firstDay = Instant.ofEpochMilli(minTime.toLong()).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(maxTime.toLong()).minusSeconds(1).atZone(zone).toLocalDate()
        var day = firstDay
        while (!day.isAfter(lastDay)) {
            val midnight = day.atStartOfDay(zone).toInstant()
            val noon = day.atTime(12, 0).atZone(zone).toInstant()
            val midnightX = xOf(midnight)
            if (midnightX in left..right) {
                drawLine(
                    SolColors.Grid,
                    Offset(midnightX, top),
                    Offset(midnightX, bottom),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val label = DayLabel.format(day.atStartOfDay(zone))
            val labelX = xOf(noon)
            if (labelX in left..right) {
                native.drawText(label, labelX, dayBaseline, gridPaint)
                if (layers.power) {
                    val totals = dayTotals[day]
                    if (totals?.complete == true) {
                        native.drawText(
                            Formatters.kwh(totals.energyKwh),
                            labelX,
                            size.height - 6f,
                            kwhPaint
                        )
                    }
                }
                val temps = tempRanges[day]
                if (temps != null) {
                    drawTempRangeLabel(native, temps, labelX, tempBaseline, 26f)
                }
            }
            day = day.plusDays(1)
        }

        if (isDemo) {
            drawDemoWatermark(native, left, top, right, bottom)
        }
    }
}

private fun drawTempRangeLabel(
    canvas: AndroidCanvas,
    range: DayTempRange,
    centerX: Float,
    baselineY: Float,
    textSize: Float
) {
    fun paint(color: Int) = Paint().apply {
        this.color = color
        this.textSize = textSize
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    val hyphenPaint = paint(SolColors.Ink.toArgb())
    if (range.lowF == range.highF) {
        hyphenPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("${range.lowF}", centerX, baselineY, hyphenPaint)
        return
    }
    val low = "${range.lowF}"
    val hyphen = "-"
    val high = "${range.highF}"
    val lowPaint = paint(SolColors.TempLow.toArgb())
    val highPaint = paint(SolColors.TempHot.toArgb())
    val total = lowPaint.measureText(low) + hyphenPaint.measureText(hyphen) + highPaint.measureText(high)
    var x = centerX - total / 2f
    canvas.drawText(low, x, baselineY, lowPaint)
    x += lowPaint.measureText(low)
    canvas.drawText(hyphen, x, baselineY, hyphenPaint)
    x += hyphenPaint.measureText(hyphen)
    canvas.drawText(high, x, baselineY, highPaint)
}

private fun drawDemoWatermark(
    canvas: AndroidCanvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
) {
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val width = (right - left).coerceAtLeast(1f)
    val paint = Paint().apply {
        color = 0xFFE8EEF7.toInt()
        alpha = 58
        textSize = (width * 0.16f).coerceIn(40f, 92f)
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.save()
    canvas.rotate(-22f, cx, cy)
    canvas.drawText("DEMO DATA", cx, cy + paint.textSize * 0.35f, paint)
    canvas.restore()
}

private fun contiguousSeries(series: List<PowerPoint>): List<List<PowerPoint>> {
    if (series.isEmpty()) return emptyList()
    val runs = ArrayList<List<PowerPoint>>()
    val current = ArrayList<PowerPoint>()
    for (point in series) {
        val previous = current.lastOrNull()
        if (previous != null && Duration.between(previous.periodEnd, point.periodEnd) > SolarDayLabels.MAX_GAP) {
            runs.add(current.toList())
            current.clear()
        }
        current.add(point)
    }
    if (current.isNotEmpty()) runs.add(current.toList())
    return runs
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    series: List<PowerPoint>,
    xOf: (Instant) -> Float,
    yOf: (Double) -> Float,
    color: Color,
    dotted: Boolean = false,
    alpha: Float = 1f,
    breakGaps: Boolean = false
) {
    if (breakGaps) {
        contiguousSeries(series).forEach { run ->
            drawSeries(run, xOf, yOf, color, dotted, alpha, breakGaps = false)
        }
        return
    }
    if (series.size < 2) return
    val line = ComposePath()
    val fill = ComposePath()
    series.forEachIndexed { index, point ->
        val x = xOf(point.periodEnd)
        val y = yOf(point.kw)
        if (index == 0) {
            line.moveTo(x, y)
            fill.moveTo(x, yOf(0.0))
            fill.lineTo(x, y)
        } else {
            line.lineTo(x, y)
            fill.lineTo(x, y)
        }
    }
    fill.lineTo(xOf(series.last().periodEnd), yOf(0.0))
    fill.close()
    if (!dotted) {
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.35f * alpha), color.copy(alpha = 0.02f * alpha))
            )
        )
    }
    val strokeW = 3.dp.toPx()
    drawPath(
        path = line,
        color = color.copy(alpha = alpha),
        style = Stroke(
            width = strokeW,
            cap = StrokeCap.Round,
            pathEffect = if (dotted) {
                PathEffect.dashPathEffect(floatArrayOf(strokeW, strokeW * 1.8f), 0f)
            } else {
                null
            }
        )
    )
}

private fun coreTempColor(night: Boolean): Color =
    if (night) SolColors.Temp.copy(alpha = NIGHT_ALPHA) else SolColors.Temp

private fun coreTempArgb(night: Boolean): Int {
    val a = if (night) (0xFF * NIGHT_ALPHA).toInt().coerceIn(0, 0xFF) else 0xFF
    return (SolColors.Temp.toArgb() and 0x00FFFFFF) or (a shl 24)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFreezeReference(
    native: android.graphics.Canvas,
    yOfTemp: (Double) -> Float,
    left: Float,
    right: Float,
    tempMin: Double,
    tempMax: Double
) {
    if (!(tempMin < FREEZE_F && tempMax > FREEZE_F)) return
    val y = yOfTemp(FREEZE_F)
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = 1.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx()))
    )
    val label = android.graphics.Paint().apply {
        color = 0x40FFFFFF
        textSize = 22f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    native.drawText("32°", right - 4f, y - 6f, label)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTempLine(
    weather: List<WeatherPoint>,
    xOf: (Instant) -> Float,
    yOfTemp: (Double) -> Float,
    sunDays: List<SunTimes>
) {
    val stroke = TEMP_STROKE_DP.dp.toPx()
    val nightDash = PathEffect.dashPathEffect(
        floatArrayOf(NIGHT_DASH_ON_DP.dp.toPx(), NIGHT_DASH_OFF_DP.dp.toPx()),
        0f
    )
    for (i in 0 until weather.lastIndex) {
        val a = weather[i]
        val b = weather[i + 1]
        val x1 = xOf(a.time)
        val y1 = yOfTemp(a.tempF)
        val x2 = xOf(b.time)
        val y2 = yOfTemp(b.tempF)
        val splits = (listOf(0f, 1f) + sunEventFractions(a.time, b.time, sunDays)).distinct().sorted()
        for (s in 0 until splits.lastIndex) {
            val t0 = splits[s]
            val t1 = splits[s + 1]
            val midT = (t0 + t1) / 2f
            val midMillis = a.time.toEpochMilli() +
                ((b.time.toEpochMilli() - a.time.toEpochMilli()) * midT).toLong()
            val night = isNight(Instant.ofEpochMilli(midMillis), sunDays)
            val start = Offset(x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0)
            val end = Offset(x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1)
            drawLine(
                color = coreTempColor(night),
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = if (night) StrokeCap.Butt else StrokeCap.Round,
                pathEffect = if (night) nightDash else null
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHvacBars(
    bars: List<HvacBar>,
    xOf: (Instant) -> Float,
    bottom: Float,
    plotH: Float,
    plotW: Float
) {
    val columns = bars.size.coerceAtLeast(1)
    val span = if (bars.size >= 2) abs(xOf(bars.last().at) - xOf(bars.first().at)) else plotW
    val barW = ((if (bars.size >= 2) span / (columns - 1) else plotW) * 1.08f).coerceAtLeast(2f)
    for (bar in bars) {
        val total = bar.heatPumpMin + bar.auxMin + bar.coolMin
        if (total <= 0.0 || bar.bucketMin <= 0.0) continue
        val scale = if (total > bar.bucketMin) bar.bucketMin / total else 1.0
        var cursor = bottom
        val parts = listOf(
            bar.heatPumpMin to SolColors.HeatPump,
            bar.auxMin to SolColors.AuxHeat,
            bar.coolMin to SolColors.CoolRun
        )
        for ((minutes, color) in parts) {
            val bandH = ((minutes * scale / bar.bucketMin).toFloat() * plotH).coerceAtLeast(0f)
            if (bandH <= 0.4f) continue
            cursor -= bandH
            drawRect(
                color = color.copy(alpha = 0.78f),
                topLeft = Offset(xOf(bar.at) - barW / 2f, cursor),
                size = Size(barW, bandH)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRateArea(
    bands: List<DteTou.Band>,
    xOf: (Instant) -> Float,
    yOf: (Double) -> Float,
    color: Color,
    centsOf: (DteTou.Period) -> Double
) {
    if (bands.isEmpty()) return
    val baseline = yOf(ForecastSnapshot.RATE_MIN_CENTS)
    val ceiling = yOf(ForecastSnapshot.RATE_MAX_CENTS)
    val path = ComposePath()
    val edge = ComposePath()
    path.moveTo(xOf(bands.first().start), baseline)
    bands.forEachIndexed { index, band ->
        val y = yOf(centsOf(band.period))
        val x1 = xOf(band.start)
        val x2 = xOf(band.end)
        path.lineTo(x1, y)
        path.lineTo(x2, y)
        if (index == 0) edge.moveTo(x1, y) else edge.lineTo(x1, y)
        edge.lineTo(x2, y)
    }
    path.lineTo(xOf(bands.last().end), baseline)
    path.close()
    drawPath(
        path,
        Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.20f), Color.Transparent),
            startY = ceiling,
            endY = baseline
        )
    )
    drawPath(
        edge,
        color.copy(alpha = 0.55f),
        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Butt)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEcobeeFills(
    runs: List<List<EcobeeMark>>,
    xOf: (Instant) -> Float,
    yOfTemp: (Double) -> Float
) {
    for (run in runs) {
        for (segment in ecobeeFillSegments(run)) {
            val path = ComposePath()
            segment.forEachIndexed { index, mark ->
                val x = xOf(mark.at)
                val y = yOfTemp(mark.indoorF)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (mark in segment.asReversed()) {
                path.lineTo(xOf(mark.at), yOfTemp(mark.setF ?: mark.indoorF))
            }
            path.close()
            val warm = segment.first().band == EcobeeBand.COOLING
            val color = if (warm) SolColors.CoolGap else SolColors.HeatGap
            drawPath(path, color.copy(alpha = if (warm) TOO_WARM_ALPHA else TOO_COOL_ALPHA))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEcobeeLines(
    runs: List<List<EcobeeMark>>,
    xOf: (Instant) -> Float,
    yOfTemp: (Double) -> Float
) {
    val edge = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val hairline = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Butt)
    for (run in runs) {
        for (segment in ecobeeFillSegments(run)) {
            val color = if (segment.first().band == EcobeeBand.COOLING) SolColors.CoolGap else SolColors.HeatGap
            val path = ComposePath()
            segment.forEachIndexed { index, mark ->
                val x = xOf(mark.at)
                val y = yOfTemp(mark.indoorF)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = edge)
        }
        val setpoint = ComposePath()
        var drawing = false
        for (mark in run) {
            val set = mark.setF
            if (set == null) {
                drawing = false
                continue
            }
            val x = xOf(mark.at)
            val y = yOfTemp(set)
            if (!drawing) {
                setpoint.moveTo(x, y)
                drawing = true
            } else {
                setpoint.lineTo(x, y)
            }
        }
        drawPath(setpoint, SolColors.Setpoint, style = hairline)
    }
}

object ChartBitmapRenderer {
    fun render(
        points: List<PowerPoint>,
        now: Instant,
        width: Int,
        height: Int,
        capacityKw: Double = ForecastSnapshot.SYSTEM_CAPACITY_KW,
        rangeFrom: Instant? = null,
        rangeTo: Instant? = null,
        weather: List<WeatherPoint> = emptyList(),
        layers: ChartLayers = ChartLayers(),
        sunDays: List<SunTimes> = emptyList(),
        isDemo: Boolean = false,
        ecobee: List<EcobeeSample> = emptyList()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(8), height.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        if (points.size < 2 && weather.size < 2 && !layers.rates && ecobee.size < 2) return bitmap
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(0x00152136)

        val units = axisUnits(layers, labelRates = false)
        val showTempLabels = layers.degrees
        val left = if (showTempLabels || units != AxisUnits.NONE) 52f else 8f
        val right = width - 8f
        val dayBaseline = 36f
        val tempBaseline = 84f
        val top = if (layers.temperature) 100f else 52f
        val bottom = height - if (layers.power) 52f else 8f
        val chartW = (right - left).coerceAtLeast(1f)
        val chartH = (bottom - top).coerceAtLeast(1f)
        val startInstant = rangeFrom ?: points.firstOrNull()?.periodEnd
            ?: weather.firstOrNull()?.time ?: ecobee.firstOrNull()?.at
        val endInstant = rangeTo ?: points.lastOrNull()?.periodEnd
            ?: weather.lastOrNull()?.time ?: ecobee.lastOrNull()?.at
        if (startInstant == null || endInstant == null) return bitmap
        val minTime = startInstant.toEpochMilli().toFloat()
        val maxTime = endInstant.toEpochMilli().toFloat()
        val span = max(1f, maxTime - minTime)
        val solarMax = ForecastSnapshot.SOLAR_MAX_KW

        fun xOf(time: Instant) = left + ((time.toEpochMilli() - minTime) / span) * chartW
        fun yOf(kw: Double) = bottom - ((kw / solarMax).toFloat().coerceIn(0f, 1f) * chartH)

        val from = Instant.ofEpochMilli(minTime.toLong())
        val to = Instant.ofEpochMilli(maxTime.toLong())
        val outdoorForScale = if (layers.temperature) {
            weather.filter { !it.time.isBefore(from) && it.time.isBefore(to) }.map { it.tempF }
        } else {
            emptyList()
        }
        val rawTemps = sharedFahrenheitRange(
            outdoorForScale,
            if (layers.ecobee) ecobee.filter { !it.at.isAfter(now) } else emptyList(),
            from,
            to,
            scaleToData = layers.ecobee,
            pad = !layers.ecobee
        )
        val tempGrid = temperatureGrid(rawTemps.first, rawTemps.second, snapEnds = layers.ecobee)
        val tempMin = tempGrid.min
        val tempMax = tempGrid.max
        val tempSpan = (tempMax - tempMin).takeIf { it > 0.0 } ?: 1.0

        fun yOfTemp(temp: Double) =
            bottom - (((temp - tempMin) / tempSpan).toFloat().coerceIn(0f, 1f) * chartH)
        fun yOfPop(pop: Double) =
            bottom - ((pop / ForecastSnapshot.PRECIP_MAX).toFloat().coerceIn(0f, 1f) * chartH)
        fun yOfRate(cents: Double) =
            bottom - (((cents - ForecastSnapshot.RATE_MIN_CENTS) /
                (ForecastSnapshot.RATE_MAX_CENTS - ForecastSnapshot.RATE_MIN_CENTS))
                .toFloat().coerceIn(0f, 1f) * chartH)

        if (layers.rates) {
            val bands = DteTou.bands(from, to)
            if (layers.buy) drawAndroidRateArea(canvas, bands, ::xOf, ::yOfRate, 0xFFE57FD8.toInt()) { it.cents }
            if (layers.sell) drawAndroidRateArea(canvas, bands, ::xOf, ::yOfRate, 0xFF7BD88F.toInt()) { it.sellCents }
        }

        if (layers.precipitation && weather.size >= 2) {
            val barWidth = (chartW / weather.size.toFloat()) * 0.65f
            val precipPaint = Paint().apply {
                color = 0x664DD0C8
                isAntiAlias = false
            }
            weather.forEach { point ->
                val pop = point.popPercent ?: return@forEach
                if (pop <= 0.0) return@forEach
                val x = xOf(point.time)
                if (x in left..right) {
                    canvas.drawRect(x - barWidth / 2f, yOfPop(pop), x + barWidth / 2f, bottom, precipPaint)
                }
            }
        }

        val grid = Paint().apply {
            color = 0x334A6288
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        if (!showTempLabels) {
            for (step in 0..2) {
                val y = yOf(solarMax * step / 2.0)
                canvas.drawLine(left, y, right, y, grid)
            }
        }

        val unitPaint = Paint().apply {
            color = 0xFFE8EEF7.toInt()
            textSize = 26f
            isAntiAlias = true
        }
        if (showTempLabels) {
            for (tick in tempGrid.ticks) {
                val y = yOfTemp(tick)
                canvas.drawLine(left, y, right, y, grid)
                val labelY = when {
                    abs(tick - tempMax) < 0.01 -> top + 16f
                    abs(tick - tempMin) < 0.01 -> bottom
                    else -> y + 8f
                }
                canvas.drawText(formatTempBound(tick), 2f, labelY, unitPaint)
            }
        }
        when (units) {
            AxisUnits.SOLAR -> {
                canvas.drawText("8 kW", 2f, top + 16f, unitPaint)
                canvas.drawText("4 kW", 2f, yOf(4.0) + 8f, unitPaint)
                canvas.drawText("0 kW", 2f, bottom, unitPaint)
            }
            AxisUnits.TEMP -> Unit
            AxisUnits.PRECIP -> {
                canvas.drawText("100%", 2f, top + 16f, unitPaint)
                canvas.drawText("50%", 2f, yOfPop(50.0) + 8f, unitPaint)
                canvas.drawText("0%", 2f, bottom, unitPaint)
            }
            AxisUnits.RATES, AxisUnits.NONE -> Unit
        }

        val zone = ZoneId.systemDefault()
        val dayTotals = if (layers.power) {
            SolarDayLabels.summaries(points, zone).associateBy { it.date }
        } else {
            emptyMap()
        }
        val tempRanges = if (layers.temperature) dayTempRanges(weather, zone) else emptyMap()
        var day = Instant.ofEpochMilli(minTime.toLong()).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(maxTime.toLong()).minusSeconds(1).atZone(zone).toLocalDate()
        val dayPaint = Paint().apply {
            color = 0xFFE8EEF7.toInt()
            textSize = 40f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val kwhPaint = Paint().apply {
            color = 0xFFE8EEF7.toInt()
            textSize = 40f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        while (!day.isAfter(lastDay)) {
            val midnightX = xOf(day.atStartOfDay(zone).toInstant())
            if (midnightX in left..right) {
                canvas.drawLine(midnightX, top, midnightX, bottom, grid)
            }
            val label = DayLabel.format(day.atStartOfDay(zone))
            val labelX = xOf(day.atTime(12, 0).atZone(zone).toInstant())
            if (labelX in left + 12f..right - 12f) {
                canvas.drawText(label, labelX, dayBaseline, dayPaint)
                if (layers.power) {
                    val totals = dayTotals[day]
                    if (totals?.complete == true) {
                        canvas.drawText(Formatters.kwh(totals.energyKwh), labelX, height - 8f, kwhPaint)
                    }
                }
                val temps = tempRanges[day]
                if (temps != null) {
                    drawTempRangeLabel(canvas, temps, labelX, tempBaseline, 40f)
                }
            }
            day = day.plusDays(1)
        }

        if (layers.degrees) {
            drawAndroidFreezeReference(canvas, ::yOfTemp, left, right, tempMin, tempMax)
        }

        val ecobeeRuns = if (layers.ecobee) {
            prepareEcobee(ecobee, from, to, chartW.toInt().coerceAtLeast(1), now = now)
        } else {
            emptyList()
        }
        if (ecobeeRuns.isNotEmpty()) {
            drawAndroidEcobeeFills(canvas, ecobeeRuns, ::xOf, ::yOfTemp)
        }

        if (layers.solcast) {
            val (past, future) = forecastStrokes(points, now)
            drawAndroidSeries(canvas, past, ::xOf, ::yOf, 0xFFF5C542.toInt(), bottom, dotted = isDemo, alpha = 0.5f, breakGaps = true)
            drawAndroidSeries(canvas, future, ::xOf, ::yOf, 0xFFF5C542.toInt(), bottom, dotted = isDemo, breakGaps = true)
        }
        if (layers.enphase) {
            drawAndroidSeries(canvas, liveStroke(points), ::xOf, ::yOf, 0xFF2EE6A6.toInt(), bottom, dotted = isDemo)
        }

        if (layers.temperature && weather.size >= 2) {
            drawAndroidTempLine(canvas, weather, ::xOf, ::yOfTemp, sunDays)
        }
        if (ecobeeRuns.isNotEmpty()) {
            drawAndroidEcobeeLines(canvas, ecobeeRuns, ::xOf, ::yOfTemp)
        }

        val nowPaint = Paint().apply {
            color = 0x99FFFFFF.toInt()
            strokeWidth = 3f
            isAntiAlias = true
        }
        val nowX = xOf(now).coerceIn(left, right)
        canvas.drawLine(nowX, top, nowX, bottom, nowPaint)
        if (isDemo) {
            drawDemoWatermark(canvas, left, top, right, bottom)
        }
        return bitmap
    }

    private fun drawAndroidSeries(
        canvas: AndroidCanvas,
        series: List<PowerPoint>,
        xOf: (Instant) -> Float,
        yOf: (Double) -> Float,
        color: Int,
        baseline: Float,
        dotted: Boolean = false,
        alpha: Float = 1f,
        breakGaps: Boolean = false
    ) {
        if (breakGaps) {
            contiguousSeries(series).forEach { run ->
                drawAndroidSeries(canvas, run, xOf, yOf, color, baseline, dotted, alpha, breakGaps = false)
            }
            return
        }
        if (series.size < 2) return
        val linePath = Path()
        val fillPath = Path()
        series.forEachIndexed { index, point ->
            val x = xOf(point.periodEnd)
            val y = yOf(point.kw)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, baseline)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(xOf(series.last().periodEnd), baseline)
        fillPath.close()

        if (!dotted) {
            val fillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                val wash = (0x59 * alpha).toInt().coerceIn(0, 255)
                shader = LinearGradient(
                    0f, 0f, 0f, baseline,
                    color and 0x00FFFFFF or (wash shl 24),
                    color and 0x00FFFFFF,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(fillPath, fillPaint)
        }
        val linePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            this.color = color
            this.alpha = (255f * alpha).toInt().coerceIn(0, 255)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dotted) {
                pathEffect = DashPathEffect(floatArrayOf(3.5f, 7f), 0f)
            }
        }
        canvas.drawPath(linePath, linePaint)
    }

    private fun drawAndroidRateArea(
        canvas: AndroidCanvas,
        bands: List<DteTou.Band>,
        xOf: (Instant) -> Float,
        yOf: (Double) -> Float,
        color: Int,
        centsOf: (DteTou.Period) -> Double
    ) {
        if (bands.isEmpty()) return
        val baseline = yOf(ForecastSnapshot.RATE_MIN_CENTS)
        val ceiling = yOf(ForecastSnapshot.RATE_MAX_CENTS)
        val path = Path()
        val edge = Path()
        path.moveTo(xOf(bands.first().start), baseline)
        bands.forEachIndexed { index, band ->
            val y = yOf(centsOf(band.period))
            val x1 = xOf(band.start)
            val x2 = xOf(band.end)
            path.lineTo(x1, y)
            path.lineTo(x2, y)
            if (index == 0) edge.moveTo(x1, y) else edge.lineTo(x1, y)
            edge.lineTo(x2, y)
        }
        path.lineTo(xOf(bands.last().end), baseline)
        path.close()
        val rgb = color and 0x00FFFFFF
        val fill = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f,
                ceiling,
                0f,
                baseline,
                rgb or (0x33 shl 24),
                rgb,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(path, fill)
        val edgePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            this.color = rgb or (0x8C shl 24)
            strokeCap = Paint.Cap.BUTT
            strokeJoin = Paint.Join.MITER
        }
        canvas.drawPath(edge, edgePaint)
    }

    private fun drawAndroidTempLine(
        canvas: AndroidCanvas,
        weather: List<WeatherPoint>,
        xOf: (Instant) -> Float,
        yOfTemp: (Double) -> Float,
        sunDays: List<SunTimes>
    ) {
        val stroke = TEMP_STROKE_DP * BITMAP_PX_PER_DP
        val paint = Paint().apply {
            strokeWidth = stroke
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val nightDash = DashPathEffect(
            floatArrayOf(NIGHT_DASH_ON_DP * BITMAP_PX_PER_DP, NIGHT_DASH_OFF_DP * BITMAP_PX_PER_DP),
            0f
        )
        for (i in 0 until weather.lastIndex) {
            val a = weather[i]
            val b = weather[i + 1]
            val x1 = xOf(a.time)
            val y1 = yOfTemp(a.tempF)
            val x2 = xOf(b.time)
            val y2 = yOfTemp(b.tempF)
            val splits = (listOf(0f, 1f) + sunEventFractions(a.time, b.time, sunDays)).distinct().sorted()
            for (s in 0 until splits.lastIndex) {
                val t0 = splits[s]
                val t1 = splits[s + 1]
                val midT = (t0 + t1) / 2f
                val midMillis = a.time.toEpochMilli() +
                    ((b.time.toEpochMilli() - a.time.toEpochMilli()) * midT).toLong()
                val night = isNight(Instant.ofEpochMilli(midMillis), sunDays)
                val xStart = x1 + (x2 - x1) * t0
                val yStart = y1 + (y2 - y1) * t0
                val xEnd = x1 + (x2 - x1) * t1
                val yEnd = y1 + (y2 - y1) * t1
                paint.color = coreTempArgb(night)
                paint.strokeWidth = stroke
                paint.strokeCap = if (night) Paint.Cap.BUTT else Paint.Cap.ROUND
                paint.pathEffect = if (night) nightDash else null
                canvas.drawLine(xStart, yStart, xEnd, yEnd, paint)
            }
        }
    }

    private fun drawAndroidFreezeReference(
        canvas: AndroidCanvas,
        yOfTemp: (Double) -> Float,
        left: Float,
        right: Float,
        tempMin: Double,
        tempMax: Double
    ) {
        if (!(tempMin < FREEZE_F && tempMax > FREEZE_F)) return
        val y = yOfTemp(FREEZE_F)
        val line = Paint().apply {
            color = 0x40FFFFFF
            strokeWidth = BITMAP_PX_PER_DP
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(1.5f * BITMAP_PX_PER_DP, 3f * BITMAP_PX_PER_DP), 0f)
        }
        canvas.drawLine(left, y, right, y, line)
        val label = Paint().apply {
            color = 0x40FFFFFF
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("32°", right - 4f, y - 6f, label)
    }

    private fun drawAndroidEcobeeFills(
        canvas: AndroidCanvas,
        runs: List<List<EcobeeMark>>,
        xOf: (Instant) -> Float,
        yOfTemp: (Double) -> Float
    ) {
        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        for (run in runs) {
            for (segment in ecobeeFillSegments(run)) {
                val path = Path()
                segment.forEachIndexed { index, mark ->
                    val x = xOf(mark.at)
                    val y = yOfTemp(mark.indoorF)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (mark in segment.asReversed()) {
                    path.lineTo(xOf(mark.at), yOfTemp(mark.setF ?: mark.indoorF))
                }
                path.close()
                val warm = segment.first().band == EcobeeBand.COOLING
                val rgb = if (warm) 0x004DA3FF else 0x00FF9800
                val alpha = (255f * (if (warm) TOO_WARM_ALPHA else TOO_COOL_ALPHA)).toInt().coerceIn(0, 255)
                paint.color = rgb or (alpha shl 24)
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawAndroidEcobeeLines(
        canvas: AndroidCanvas,
        runs: List<List<EcobeeMark>>,
        xOf: (Instant) -> Float,
        yOfTemp: (Double) -> Float
    ) {
        val edge = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeWidth = 1f * BITMAP_PX_PER_DP
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val setpoint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            color = SolColors.Setpoint.toArgb()
            strokeWidth = 1f * BITMAP_PX_PER_DP
            strokeCap = Paint.Cap.BUTT
        }
        for (run in runs) {
            for (segment in ecobeeFillSegments(run)) {
                edge.color = if (segment.first().band == EcobeeBand.COOLING) {
                    SolColors.CoolGap.toArgb()
                } else {
                    SolColors.HeatGap.toArgb()
                }
                val path = Path()
                segment.forEachIndexed { index, mark ->
                    val x = xOf(mark.at)
                    val y = yOfTemp(mark.indoorF)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, edge)
            }
            val path = Path()
            var drawing = false
            for (mark in run) {
                val set = mark.setF
                if (set == null) {
                    drawing = false
                    continue
                }
                val x = xOf(mark.at)
                val y = yOfTemp(set)
                if (!drawing) {
                    path.moveTo(x, y)
                    drawing = true
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, setpoint)
        }
    }
}
