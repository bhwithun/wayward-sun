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
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.SeriesKind
import com.brian.solwidget.data.SolarDayLabels
import com.brian.solwidget.data.SunTimes
import com.brian.solwidget.data.WeatherPoint
import com.brian.solwidget.data.dayTempRanges
import com.brian.solwidget.data.isNight
import com.brian.solwidget.data.sunEventFractions
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.util.Formatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val DayLabel = DateTimeFormatter.ofPattern("EEE d")

private const val TEMP_STROKE_DP = 1.6f
private const val NIGHT_ALPHA = 0.3f
private const val NIGHT_DASH_ON_DP = 9f
private const val NIGHT_DASH_OFF_DP = 7f
private const val GLOW_INNER_DP = 5f
private const val GLOW_OUTER_DP = 9f
private const val GLOW_INNER_ALPHA = 0.5f
private const val GLOW_OUTER_ALPHA = 0.2f
private const val BITMAP_PX_PER_DP = 2.75f

private enum class AxisUnits { NONE, SOLAR, TEMP, PRECIP, RATES }

private fun axisUnits(layers: ChartLayers, labelRates: Boolean): AxisUnits {
    val labeled = buildList {
        if (layers.solcast) add(AxisUnits.SOLAR)
        if (layers.temperature) add(AxisUnits.TEMP)
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
    isDemo: Boolean = false
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (points.size < 2 && weather.size < 2 && !layers.rates) return@Canvas

        val units = axisUnits(layers, labelRates = true)
        val left = if (units != AxisUnits.NONE) 52.dp.toPx() else 12.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = if (layers.temperature) 32.dp.toPx() else 16.dp.toPx()
        val bottom = size.height - if (layers.solcast) 48.dp.toPx() else 32.dp.toPx()
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)

        val startInstant = rangeFrom ?: points.firstOrNull()?.periodEnd ?: weather.firstOrNull()?.time
        val endInstant = rangeTo ?: points.lastOrNull()?.periodEnd ?: weather.lastOrNull()?.time
        if (startInstant == null || endInstant == null) return@Canvas
        val minTime = startInstant.toEpochMilli().toFloat()
        val maxTime = endInstant.toEpochMilli().toFloat()
        val span = max(1f, maxTime - minTime)
        val solarMax = ForecastSnapshot.SOLAR_MAX_KW
        val tempMin = ForecastSnapshot.TEMP_MIN_F
        val tempMax = ForecastSnapshot.TEMP_MAX_F

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
        fun yOfTemp(temp: Double): Float =
            bottom - (((temp - tempMin) / (tempMax - tempMin)).toFloat().coerceIn(0f, 1f) * height)
        fun yOfPop(pop: Double): Float =
            bottom - ((pop / ForecastSnapshot.PRECIP_MAX).toFloat().coerceIn(0f, 1f) * height)
        fun yOfRate(cents: Double): Float =
            bottom - (((cents - ForecastSnapshot.RATE_MIN_CENTS) /
                (ForecastSnapshot.RATE_MAX_CENTS - ForecastSnapshot.RATE_MIN_CENTS))
                .toFloat().coerceIn(0f, 1f) * height)

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

        for (step in 0..2) {
            val kw = solarMax * step / 2.0
            val y = yOf(kw)
            drawLine(SolColors.Grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        when (units) {
            AxisUnits.SOLAR -> {
                native.drawText("8 kW", 8f, top + 16f, unitPaint)
                native.drawText("0 kW", 8f, bottom + 6f, unitPaint)
            }
            AxisUnits.TEMP -> {
                native.drawText("100°F", 6f, top + 16f, unitPaint)
                native.drawText("-20°F", 6f, bottom + 6f, unitPaint)
            }
            AxisUnits.PRECIP -> {
                native.drawText("100%", 8f, top + 16f, unitPaint)
                native.drawText("0%", 8f, bottom + 6f, unitPaint)
            }
            AxisUnits.RATES -> {
                native.drawText("40¢", 8f, top + 16f, unitPaint)
                native.drawText("0¢", 8f, bottom + 6f, unitPaint)
            }
            AxisUnits.NONE -> Unit
        }

        if (layers.solcast) {
            val live = points.filter { it.kind == SeriesKind.LIVE }
            val forecast = points.filter { it.kind == SeriesKind.FORECAST }
            drawSeries(live, ::xOf, ::yOf, SolColors.Live, dotted = isDemo)
            drawSeries(forecast, ::xOf, ::yOf, SolColors.Forecast, dotted = isDemo)
        }

        if (layers.temperature && weather.size >= 2) {
            drawTempLine(weather, ::xOf, ::yOfTemp, sunDays)
        }

        val bands = if (layers.rates) DteTou.bands(from, to) else emptyList()
        if (layers.buy) {
            drawRateStep(bands, ::xOf, ::yOfRate, SolColors.Buy) { it.cents }
        }
        if (layers.sell) {
            drawRateStep(bands, ::xOf, ::yOfRate, SolColors.Sell) { it.sellCents }
        }

        val nowX = xOf(now).coerceIn(left, right)
        drawLine(
            color = SolColors.Now,
            start = Offset(nowX, top),
            end = Offset(nowX, bottom),
            strokeWidth = 2.dp.toPx()
        )

        val zone = ZoneId.systemDefault()
        val dayTotals = if (layers.solcast) {
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
                if (layers.solcast) {
                    native.drawText(label, labelX, bottom + 18.dp.toPx(), gridPaint)
                    val totals = dayTotals[day]
                    if (totals?.complete == true) {
                        native.drawText(
                            Formatters.kwh(totals.energyKwh),
                            labelX,
                            size.height - 6f,
                            kwhPaint
                        )
                    }
                } else {
                    native.drawText(label, labelX, size.height - 6f, gridPaint)
                }
                val temps = tempRanges[day]
                if (temps != null) {
                    drawTempRangeLabel(native, temps, labelX, 22.dp.toPx(), 26f)
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    series: List<PowerPoint>,
    xOf: (Instant) -> Float,
    yOf: (Double) -> Float,
    color: Color,
    dotted: Boolean = false
) {
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
                colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))
            )
        )
    }
    val strokeW = 3.dp.toPx()
    drawPath(
        path = line,
        color = color,
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

private const val FREEZE_F = 32.0
private const val HOT_F = 90.0

private enum class TempGlow { NONE, FREEZE, HOT }

private fun tempGlow(tempF: Double): TempGlow = when {
    tempF < FREEZE_F -> TempGlow.FREEZE
    tempF > HOT_F -> TempGlow.HOT
    else -> TempGlow.NONE
}

private fun glowColor(kind: TempGlow): Color = when (kind) {
    TempGlow.FREEZE -> SolColors.TempFreeze
    TempGlow.HOT -> SolColors.TempHot
    TempGlow.NONE -> Color.Transparent
}

private fun glowArgb(kind: TempGlow, alpha: Float): Int {
    val rgb = when (kind) {
        TempGlow.FREEZE -> 0x0064B5F6
        TempGlow.HOT -> 0x00FF8A4C
        TempGlow.NONE -> 0
    }
    val a = (0xFF * alpha).toInt().coerceIn(0, 0xFF)
    return rgb or (a shl 24)
}

private fun coreTempColor(night: Boolean): Color =
    if (night) SolColors.Temp.copy(alpha = NIGHT_ALPHA) else SolColors.Temp

private fun coreTempArgb(night: Boolean): Int {
    if (!night) return 0xFFFFFFFF.toInt()
    val a = (0xFF * NIGHT_ALPHA).toInt().coerceIn(0, 0xFF)
    return 0x00FFFFFF or (a shl 24)
}

private fun tempSplitTs(t0: Double, t1: Double): List<Float> {
    val marks = mutableListOf(0f, 1f)
    val delta = t1 - t0
    if (delta != 0.0) {
        for (threshold in listOf(FREEZE_F, HOT_F)) {
            val t = ((threshold - t0) / delta).toFloat()
            if (t > 0f && t < 1f) marks += t
        }
    }
    return marks.distinct().sorted()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTempLine(
    weather: List<WeatherPoint>,
    xOf: (Instant) -> Float,
    yOfTemp: (Double) -> Float,
    sunDays: List<SunTimes>
) {
    val stroke = TEMP_STROKE_DP.dp.toPx()
    val glowInner = GLOW_INNER_DP.dp.toPx()
    val glowOuter = GLOW_OUTER_DP.dp.toPx()
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
        val splits = (tempSplitTs(a.tempF, b.tempF) + sunEventFractions(a.time, b.time, sunDays))
            .distinct()
            .sorted()
        for (s in 0 until splits.lastIndex) {
            val t0 = splits[s]
            val t1 = splits[s + 1]
            val midT = (t0 + t1) / 2f
            val midTemp = a.tempF + (b.tempF - a.tempF) * midT
            val midMillis = a.time.toEpochMilli() +
                ((b.time.toEpochMilli() - a.time.toEpochMilli()) * midT).toLong()
            val night = isNight(Instant.ofEpochMilli(midMillis), sunDays)
            val start = Offset(x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0)
            val end = Offset(x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1)
            val dash = if (night) nightDash else null
            val nightScale = if (night) NIGHT_ALPHA else 1f
            val glow = tempGlow(midTemp)
            if (glow != TempGlow.NONE) {
                val tint = glowColor(glow)
                drawLine(
                    color = tint.copy(alpha = GLOW_OUTER_ALPHA * nightScale),
                    start = start,
                    end = end,
                    strokeWidth = glowOuter,
                    cap = StrokeCap.Round,
                    pathEffect = dash
                )
                drawLine(
                    color = tint.copy(alpha = GLOW_INNER_ALPHA * nightScale),
                    start = start,
                    end = end,
                    strokeWidth = glowInner,
                    cap = StrokeCap.Round,
                    pathEffect = dash
                )
            }
            drawLine(
                color = coreTempColor(night),
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = if (night) StrokeCap.Butt else StrokeCap.Round,
                pathEffect = dash
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRateStep(
    bands: List<DteTou.Band>,
    xOf: (Instant) -> Float,
    yOf: (Double) -> Float,
    color: Color,
    centsOf: (DteTou.Period) -> Double
) {
    if (bands.isEmpty()) return
    val path = ComposePath()
    bands.forEachIndexed { index, band ->
        val y = yOf(centsOf(band.period))
        val x1 = xOf(band.start)
        val x2 = xOf(band.end)
        if (index == 0) path.moveTo(x1, y) else path.lineTo(x1, y)
        path.lineTo(x2, y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
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
        isDemo: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(8), height.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        if (points.size < 2 && weather.size < 2 && !layers.rates) return bitmap
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(0x00152136)

        val units = axisUnits(layers, labelRates = false)
        val left = if (units != AxisUnits.NONE) 52f else 8f
        val right = width - 8f
        val top = if (layers.temperature) 52f else 6f
        val bottom = height - if (layers.solcast) 80f else 22f
        val chartW = (right - left).coerceAtLeast(1f)
        val chartH = (bottom - top).coerceAtLeast(1f)
        val startInstant = rangeFrom ?: points.firstOrNull()?.periodEnd ?: weather.firstOrNull()?.time
        val endInstant = rangeTo ?: points.lastOrNull()?.periodEnd ?: weather.lastOrNull()?.time
        if (startInstant == null || endInstant == null) return bitmap
        val minTime = startInstant.toEpochMilli().toFloat()
        val maxTime = endInstant.toEpochMilli().toFloat()
        val span = max(1f, maxTime - minTime)
        val solarMax = ForecastSnapshot.SOLAR_MAX_KW
        val tempMin = ForecastSnapshot.TEMP_MIN_F
        val tempMax = ForecastSnapshot.TEMP_MAX_F

        fun xOf(time: Instant) = left + ((time.toEpochMilli() - minTime) / span) * chartW
        fun yOf(kw: Double) = bottom - ((kw / solarMax).toFloat().coerceIn(0f, 1f) * chartH)

        val from = Instant.ofEpochMilli(minTime.toLong())
        val to = Instant.ofEpochMilli(maxTime.toLong())

        fun yOfTemp(temp: Double) =
            bottom - (((temp - tempMin) / (tempMax - tempMin)).toFloat().coerceIn(0f, 1f) * chartH)
        fun yOfPop(pop: Double) =
            bottom - ((pop / ForecastSnapshot.PRECIP_MAX).toFloat().coerceIn(0f, 1f) * chartH)
        fun yOfRate(cents: Double) =
            bottom - (((cents - ForecastSnapshot.RATE_MIN_CENTS) /
                (ForecastSnapshot.RATE_MAX_CENTS - ForecastSnapshot.RATE_MIN_CENTS))
                .toFloat().coerceIn(0f, 1f) * chartH)

        if (layers.precipitation && weather.size >= 2) {
            val barWidth = (chartW / weather.size.toFloat()) * 0.65f
            val precipPaint = Paint().apply {
                color = 0x665CA8FF
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
        for (step in 0..2) {
            val y = yOf(solarMax * step / 2.0)
            canvas.drawLine(left, y, right, y, grid)
        }

        val unitPaint = Paint().apply {
            color = 0xFFE8EEF7.toInt()
            textSize = 26f
            isAntiAlias = true
        }
        when (units) {
            AxisUnits.SOLAR -> {
                canvas.drawText("8 kW", 2f, top + 16f, unitPaint)
                canvas.drawText("0 kW", 2f, bottom, unitPaint)
            }
            AxisUnits.TEMP -> {
                canvas.drawText("100°F", 2f, top + 16f, unitPaint)
                canvas.drawText("-20°F", 2f, bottom, unitPaint)
            }
            AxisUnits.PRECIP -> {
                canvas.drawText("100%", 2f, top + 16f, unitPaint)
                canvas.drawText("0%", 2f, bottom, unitPaint)
            }
            AxisUnits.RATES, AxisUnits.NONE -> Unit
        }

        val zone = ZoneId.systemDefault()
        val dayTotals = if (layers.solcast) {
            SolarDayLabels.summaries(points, zone).associateBy { it.date }
        } else {
            emptyMap()
        }
        val tempRanges = if (layers.temperature) dayTempRanges(weather, zone) else emptyMap()
        var day = Instant.ofEpochMilli(minTime.toLong()).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(maxTime.toLong()).minusSeconds(1).atZone(zone).toLocalDate()
        val dayPaint = Paint().apply {
            color = 0xFFE8EEF7.toInt()
            textSize = 22f
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
                if (layers.solcast) {
                    canvas.drawText(label, labelX, bottom + 20f, dayPaint)
                    val totals = dayTotals[day]
                    if (totals?.complete == true) {
                        canvas.drawText(Formatters.kwh(totals.energyKwh), labelX, height - 8f, kwhPaint)
                    }
                } else {
                    canvas.drawText(label, labelX, height - 4f, dayPaint)
                }
                val temps = tempRanges[day]
                if (temps != null) {
                    drawTempRangeLabel(canvas, temps, labelX, 38f, 40f)
                }
            }
            day = day.plusDays(1)
        }

        if (layers.solcast) {
            drawAndroidSeries(
                canvas,
                points.filter { it.kind == SeriesKind.LIVE },
                ::xOf,
                ::yOf,
                0xFF2EE6A6.toInt(),
                bottom,
                dotted = isDemo
            )
            drawAndroidSeries(
                canvas,
                points.filter { it.kind == SeriesKind.FORECAST },
                ::xOf,
                ::yOf,
                0xFFF5C542.toInt(),
                bottom,
                dotted = isDemo
            )
        }

        if (layers.temperature && weather.size >= 2) {
            drawAndroidTempLine(canvas, weather, ::xOf, ::yOfTemp, sunDays)
        }

        if (layers.rates) {
            val bands = DteTou.bands(from, to)
            if (layers.buy) {
                drawAndroidRateStep(canvas, bands, ::xOf, ::yOfRate, 0xFFE07070.toInt()) { it.cents }
            }
            if (layers.sell) {
                drawAndroidRateStep(canvas, bands, ::xOf, ::yOfRate, 0xFF7EB6FF.toInt()) { it.sellCents }
            }
        }

        val nowPaint = Paint().apply {
            color = 0xFFFF8A4C.toInt()
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
        dotted: Boolean = false
    ) {
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
                shader = LinearGradient(
                    0f, 0f, 0f, baseline,
                    color and 0x00FFFFFF or 0x59000000,
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
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (dotted) {
                pathEffect = DashPathEffect(floatArrayOf(3.5f, 7f), 0f)
            }
        }
        canvas.drawPath(linePath, linePaint)
    }

    private fun drawAndroidRateStep(
        canvas: AndroidCanvas,
        bands: List<DteTou.Band>,
        xOf: (Instant) -> Float,
        yOf: (Double) -> Float,
        color: Int,
        centsOf: (DteTou.Period) -> Double
    ) {
        if (bands.isEmpty()) return
        val path = Path()
        bands.forEachIndexed { index, band ->
            val y = yOf(centsOf(band.period))
            val x1 = xOf(band.start)
            val x2 = xOf(band.end)
            if (index == 0) path.moveTo(x1, y) else path.lineTo(x1, y)
            path.lineTo(x2, y)
        }
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            this.color = color
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, paint)
    }

    private fun drawAndroidTempLine(
        canvas: AndroidCanvas,
        weather: List<WeatherPoint>,
        xOf: (Instant) -> Float,
        yOfTemp: (Double) -> Float,
        sunDays: List<SunTimes>
    ) {
        val stroke = TEMP_STROKE_DP * BITMAP_PX_PER_DP
        val glowInner = GLOW_INNER_DP * BITMAP_PX_PER_DP
        val glowOuter = GLOW_OUTER_DP * BITMAP_PX_PER_DP
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
            val splits = (tempSplitTs(a.tempF, b.tempF) + sunEventFractions(a.time, b.time, sunDays))
                .distinct()
                .sorted()
            for (s in 0 until splits.lastIndex) {
                val t0 = splits[s]
                val t1 = splits[s + 1]
                val midT = (t0 + t1) / 2f
                val midTemp = a.tempF + (b.tempF - a.tempF) * midT
                val midMillis = a.time.toEpochMilli() +
                    ((b.time.toEpochMilli() - a.time.toEpochMilli()) * midT).toLong()
                val night = isNight(Instant.ofEpochMilli(midMillis), sunDays)
                val xStart = x1 + (x2 - x1) * t0
                val yStart = y1 + (y2 - y1) * t0
                val xEnd = x1 + (x2 - x1) * t1
                val yEnd = y1 + (y2 - y1) * t1
                val dash = if (night) nightDash else null
                val nightScale = if (night) NIGHT_ALPHA else 1f
                val glow = tempGlow(midTemp)
                if (glow != TempGlow.NONE) {
                    paint.pathEffect = dash
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.color = glowArgb(glow, GLOW_OUTER_ALPHA * nightScale)
                    paint.strokeWidth = glowOuter
                    canvas.drawLine(xStart, yStart, xEnd, yEnd, paint)
                    paint.color = glowArgb(glow, GLOW_INNER_ALPHA * nightScale)
                    paint.strokeWidth = glowInner
                    canvas.drawLine(xStart, yStart, xEnd, yEnd, paint)
                }
                paint.color = coreTempArgb(night)
                paint.strokeWidth = stroke
                paint.strokeCap = if (night) Paint.Cap.BUTT else Paint.Cap.ROUND
                paint.pathEffect = dash
                canvas.drawLine(xStart, yStart, xEnd, yEnd, paint)
            }
        }
    }
}
