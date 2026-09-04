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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DteTou
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.SeriesKind
import com.brian.solwidget.data.SolarDayLabels
import com.brian.solwidget.data.WeatherPoint
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.util.Formatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val DayLabel = DateTimeFormatter.ofPattern("EEE d")

private enum class AxisUnits { NONE, SOLAR, TEMP, PRECIP }

private fun axisUnits(layers: ChartLayers): AxisUnits {
    val labeled = buildList {
        if (layers.solcast) add(AxisUnits.SOLAR)
        if (layers.temperature) add(AxisUnits.TEMP)
        if (layers.precipitation) add(AxisUnits.PRECIP)
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
    isDemo: Boolean = false
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (points.size < 2 && weather.size < 2) return@Canvas

        val units = axisUnits(layers)
        val left = if (units != AxisUnits.NONE) 52.dp.toPx() else 12.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = size.height - if (layers.solcast) 48.dp.toPx() else 32.dp.toPx()
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)

        val minTime = (rangeFrom ?: points.firstOrNull()?.periodEnd ?: weather.first().time)
            .toEpochMilli().toFloat()
        val maxTime = (rangeTo ?: points.lastOrNull()?.periodEnd ?: weather.last().time)
            .toEpochMilli().toFloat()
        val span = max(1f, maxTime - minTime)
        val solarMax = ForecastSnapshot.SOLAR_MAX_KW
        val tempMin = ForecastSnapshot.TEMP_MIN_F
        val tempMax = ForecastSnapshot.TEMP_MAX_F

        fun xOf(time: Instant): Float =
            left + ((time.toEpochMilli() - minTime) / span) * width

        fun yOf(kw: Double): Float =
            bottom - ((kw / solarMax).toFloat().coerceIn(0f, 1f) * height)

        val gridPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#4A6288")
            alpha = 90
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
        if (layers.dteRates) {
            for (band in DteTou.bands(from, to)) {
                if (band.period == DteTou.Period.OFF_PEAK) continue
                val x1 = xOf(band.start).coerceIn(left, right)
                val x2 = xOf(band.end).coerceIn(left, right)
                if (x2 > x1) {
                    drawRect(
                        color = touColor(band.period),
                        topLeft = Offset(x1, top),
                        size = Size(x2 - x1, height)
                    )
                }
            }
        }
        fun yOfTemp(temp: Double): Float =
            bottom - (((temp - tempMin) / (tempMax - tempMin)).toFloat().coerceIn(0f, 1f) * height)
        fun yOfPop(pop: Double): Float =
            bottom - ((pop / ForecastSnapshot.PRECIP_MAX).toFloat().coerceIn(0f, 1f) * height)

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
            AxisUnits.NONE -> Unit
        }

        if (layers.solcast) {
            val live = points.filter { it.kind == SeriesKind.LIVE }
            val forecast = points.filter { it.kind == SeriesKind.FORECAST }
            drawSeries(live, ::xOf, ::yOf, SolColors.Live, dotted = isDemo)
            drawSeries(forecast, ::xOf, ::yOf, SolColors.Forecast, dotted = isDemo)
        }

        if (layers.temperature && weather.size >= 2) {
            drawTempLine(weather, ::xOf, ::yOfTemp)
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
            }
            day = day.plusDays(1)
        }

        if (isDemo) {
            drawDemoWatermark(native, left, top, right, bottom)
        }
    }
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

private fun tempBandColor(tempF: Double): Color = when {
    tempF < FREEZE_F -> SolColors.TempFreeze
    tempF > HOT_F -> SolColors.TempHot
    else -> SolColors.Temp
}

private fun tempBandArgb(tempF: Double): Int = when {
    tempF < FREEZE_F -> 0xFF64B5F6.toInt()
    tempF > HOT_F -> 0xFFFF8A4C.toInt()
    else -> 0xFFFFFFFF.toInt()
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
    yOfTemp: (Double) -> Float
) {
    val strokeW = 0.7.dp.toPx()
    for (i in 0 until weather.lastIndex) {
        val a = weather[i]
        val b = weather[i + 1]
        val x1 = xOf(a.time)
        val y1 = yOfTemp(a.tempF)
        val x2 = xOf(b.time)
        val y2 = yOfTemp(b.tempF)
        val splits = tempSplitTs(a.tempF, b.tempF)
        for (s in 0 until splits.lastIndex) {
            val t0 = splits[s]
            val t1 = splits[s + 1]
            val midT = (t0 + t1) / 2f
            val midTemp = a.tempF + (b.tempF - a.tempF) * midT
            drawLine(
                color = tempBandColor(midTemp),
                start = Offset(x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0),
                end = Offset(x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
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
        isDemo: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(8), height.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        if (points.size < 2 && weather.size < 2) return bitmap
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(0x00152136)

        val units = axisUnits(layers)
        val left = if (units != AxisUnits.NONE) 52f else 8f
        val right = width - 8f
        val top = 6f
        val bottom = height - if (layers.solcast) 80f else 22f
        val chartW = (right - left).coerceAtLeast(1f)
        val chartH = (bottom - top).coerceAtLeast(1f)
        val minTime = (rangeFrom ?: points.firstOrNull()?.periodEnd ?: weather.first().time)
            .toEpochMilli().toFloat()
        val maxTime = (rangeTo ?: points.lastOrNull()?.periodEnd ?: weather.last().time)
            .toEpochMilli().toFloat()
        val span = max(1f, maxTime - minTime)
        val solarMax = ForecastSnapshot.SOLAR_MAX_KW
        val tempMin = ForecastSnapshot.TEMP_MIN_F
        val tempMax = ForecastSnapshot.TEMP_MAX_F

        fun xOf(time: Instant) = left + ((time.toEpochMilli() - minTime) / span) * chartW
        fun yOf(kw: Double) = bottom - ((kw / solarMax).toFloat().coerceIn(0f, 1f) * chartH)

        val from = Instant.ofEpochMilli(minTime.toLong())
        val to = Instant.ofEpochMilli(maxTime.toLong())
        if (layers.dteRates) {
            val bandPaint = Paint().apply { isAntiAlias = false }
            for (band in DteTou.bands(from, to)) {
                if (band.period == DteTou.Period.OFF_PEAK) continue
                val x1 = xOf(band.start).coerceIn(left, right)
                val x2 = xOf(band.end).coerceIn(left, right)
                if (x2 > x1) {
                    bandPaint.color = touArgb(band.period)
                    canvas.drawRect(x1, top, x2, bottom, bandPaint)
                }
            }
        }

        fun yOfTemp(temp: Double) =
            bottom - (((temp - tempMin) / (tempMax - tempMin)).toFloat().coerceIn(0f, 1f) * chartH)
        fun yOfPop(pop: Double) =
            bottom - ((pop / ForecastSnapshot.PRECIP_MAX).toFloat().coerceIn(0f, 1f) * chartH)

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
            AxisUnits.NONE -> Unit
        }

        val zone = ZoneId.systemDefault()
        val dayTotals = if (layers.solcast) {
            SolarDayLabels.summaries(points, zone).associateBy { it.date }
        } else {
            emptyMap()
        }
        var day = Instant.ofEpochMilli(minTime.toLong()).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(maxTime.toLong()).minusSeconds(1).atZone(zone).toLocalDate()
        val dayPaint = Paint().apply {
            color = 0x994A6288.toInt()
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
            drawAndroidTempLine(canvas, weather, ::xOf, ::yOfTemp)
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

    private fun drawAndroidTempLine(
        canvas: AndroidCanvas,
        weather: List<WeatherPoint>,
        xOf: (Instant) -> Float,
        yOfTemp: (Double) -> Float
    ) {
        val paint = Paint().apply {
            strokeWidth = 2f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until weather.lastIndex) {
            val a = weather[i]
            val b = weather[i + 1]
            val x1 = xOf(a.time)
            val y1 = yOfTemp(a.tempF)
            val x2 = xOf(b.time)
            val y2 = yOfTemp(b.tempF)
            val splits = tempSplitTs(a.tempF, b.tempF)
            for (s in 0 until splits.lastIndex) {
                val t0 = splits[s]
                val t1 = splits[s + 1]
                val midTemp = a.tempF + (b.tempF - a.tempF) * ((t0 + t1) / 2f)
                paint.color = tempBandArgb(midTemp)
                canvas.drawLine(
                    x1 + (x2 - x1) * t0,
                    y1 + (y2 - y1) * t0,
                    x1 + (x2 - x1) * t1,
                    y1 + (y2 - y1) * t1,
                    paint
                )
            }
        }
    }
}

private fun touColor(period: DteTou.Period): Color = when (period) {
    DteTou.Period.OFF_PEAK -> Color.Transparent
    DteTou.Period.MID_PEAK -> SolColors.TouMid
    DteTou.Period.PEAK -> SolColors.TouPeak
}

private fun touArgb(period: DteTou.Period): Int = when (period) {
    DteTou.Period.OFF_PEAK -> android.graphics.Color.TRANSPARENT
    DteTou.Period.MID_PEAK -> 0x33C9A227
    DteTou.Period.PEAK -> 0x4DE07070
}
