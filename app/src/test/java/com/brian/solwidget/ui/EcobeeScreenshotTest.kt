package com.brian.solwidget.ui

import android.graphics.Bitmap
import android.os.Looper
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.brian.solwidget.ScreenshotActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.EcobeeSample
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.SeriesKind
import com.brian.solwidget.data.WeatherPoint
import com.brian.solwidget.ui.components.ChartBitmapRenderer
import com.brian.solwidget.ui.components.PowerChart
import com.brian.solwidget.ui.screens.ChartLegend
import com.brian.solwidget.ui.screens.LayerDrawerContent
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.ui.theme.SolWidgetTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-420dpi")
class EcobeeScreenshotTest {
    private val zone: ZoneId = ZoneId.of("America/Detroit")
    private val now: Instant = Instant.parse("2026-11-11T17:00:00Z")
    private val past: Instant = now.minus(18, ChronoUnit.HOURS)
    private val future: Instant = now.plus(6, ChronoUnit.HOURS)

    @Test
    fun drawerOpen() {
        capture("drawer-open.png") {
            PhoneFrame(fills(), past, future, layers(temp = true, ecobee = true), drawerOpen = true)
        }
    }

    @Test
    fun coolingAndHeatingFills() {
        saveChart("ecobee-temp-fills.png", fills(), past, future, layers(temp = true, ecobee = true))
    }

    @Test
    fun deadbandStretch() {
        saveChart("ecobee-deadband.png", deadband(), past, future, layers(temp = true, ecobee = true))
    }

    @Test
    fun dataGap() {
        saveChart("ecobee-gap.png", gap(), past, future, layers(temp = false, ecobee = true))
    }

    @Test
    fun widgetWithEcobee() {
        val from = now.minus(48, ChronoUnit.HOURS)
        val to = now.plus(72, ChronoUnit.HOURS)
        val chart = ChartBitmapRenderer.render(
            points = solar(from, now.plus(24, ChronoUnit.HOURS)),
            now = now,
            width = 1080,
            height = 520,
            rangeFrom = from,
            rangeTo = to,
            weather = outdoor(from, to),
            layers = layers(temp = true, ecobee = true, solar = true),
            ecobee = fills()
        )
        capture("widget-ecobee.png") {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SolColors.Navy)
                    .padding(24.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(SolColors.Panel)
                        .padding(14.dp)
                ) {
                    Image(
                        bitmap = chart.asImageBitmap(),
                        contentDescription = "Widget chart with Ecobee",
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            }
        }
    }

    @Test
    fun widgetPrecipBuySell() {
        val from = now.minus(48, ChronoUnit.HOURS)
        val to = now.plus(72, ChronoUnit.HOURS)
        val chart = ChartBitmapRenderer.render(
            points = emptyList(),
            now = now,
            width = 1080,
            height = 520,
            rangeFrom = from,
            rangeTo = to,
            weather = outdoor(from, to, precip = true),
            layers = ChartLayers(
                temperature = false,
                buy = true,
                sell = true,
                precipitation = true,
                solcast = false,
                enphase = false,
                ecobee = false
            )
        )
        capture("widget-precip-buy-sell.png") {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SolColors.Navy)
                    .padding(24.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(SolColors.Panel)
                        .padding(14.dp)
                ) {
                    Image(
                        bitmap = chart.asImageBitmap(),
                        contentDescription = "Widget chart with precipitation, buy, and sell",
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            }
        }
    }

    private fun saveChart(
        name: String,
        indoor: List<EcobeeSample>,
        from: Instant,
        to: Instant,
        layers: ChartLayers
    ) {
        capture(name) { PhoneFrame(indoor, from, to, layers, drawerOpen = false) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PhoneFrame(
        indoor: List<EcobeeSample>,
        from: Instant,
        to: Instant,
        layers: ChartLayers,
        drawerOpen: Boolean
    ) {
        val drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)
        SolWidgetTheme {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.46f),
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = SolColors.Panel) {
                        LayerDrawerContent(
                            layers = layers,
                            versionName = "2.5.0",
                            checkingUpdates = false,
                            onToggle = { _, _ -> },
                            onReset = {},
                            onWebApp = {},
                            onCheckUpdates = {},
                            onSettings = {}
                        )
                    }
                }
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(SolColors.Navy)
                ) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open navigation", tint = SolColors.Ink)
                            }
                        },
                        title = { Text("Wayward Sun") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SolColors.Navy,
                            titleContentColor = SolColors.Ink
                        )
                    )
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Live", color = SolColors.Ink, modifier = Modifier.weight(1f))
                        Text("History", color = SolColors.Muted, modifier = Modifier.weight(1f))
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(360.dp)
                            .background(SolColors.Panel)
                    ) {
                        PowerChart(
                            points = emptyList(),
                            now = now,
                            weather = if (layers.temperature) outdoor(from, to) else emptyList(),
                            rangeFrom = from,
                            rangeTo = to,
                            layers = layers,
                            ecobee = indoor
                        )
                    }
                    ChartLegend(layers = layers, onOpen = {}, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ScreenshotActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView, FrameLayout.LayoutParams(1080, 2400))
        composeView.setContent { content() }
        composeView.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(2400, MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, 1080, 2400)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))
        val dir = File("/opt/cursor/artifacts").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Heating, a deadband, then cooling. The last sample is the one at now. */
    private fun fills(): List<EcobeeSample> {
        return samples(past, hours = 18) { hour ->
            when {
                hour < 6 -> 64.0 to (68.0 to 75.0)
                hour < 12 -> 71.0 to (68.0 to 75.0)
                else -> 78.0 to (68.0 to 74.0)
            }
        }.filter { !it.at.isAfter(now) }
    }

    /** Cooling, a long deadband, then heating, all ending at now. */
    private fun deadband(): List<EcobeeSample> {
        return samples(past, hours = 18) { hour ->
            when {
                hour < 2 -> 78.0 to (68.0 to 74.0)
                hour < 14 -> 71.0 to (68.0 to 75.0)
                else -> 63.0 to (68.0 to 75.0)
            }
        }.filter { !it.at.isAfter(now) }
    }

    /** Two stretches with a three-hour hole between them, both before now. */
    private fun gap(): List<EcobeeSample> {
        val start = now.minus(16, ChronoUnit.HOURS)
        val first = samples(start, hours = 6) { _ -> 78.0 to (68.0 to 74.0) }
        val second = samples(start.plus(9, ChronoUnit.HOURS), hours = 6) { _ -> 63.0 to (68.0 to 74.0) }
        return (first + second).filter { !it.at.isAfter(now) }
    }

    private fun samples(
        start: Instant,
        hours: Int,
        value: (Double) -> Pair<Double, Pair<Double, Double>>
    ): List<EcobeeSample> {
        val count = hours * 12 + 1
        return List(count) { index ->
            val at = start.plusSeconds(index * 5L * 60L)
            val hour = index / 12.0
            val (indoor, sets) = value(hour)
            EcobeeSample(at, indoor, sets.second, sets.first)
        }
    }

    private fun outdoor(from: Instant, to: Instant, precip: Boolean = false): List<WeatherPoint> {
        val hours = ChronoUnit.HOURS.between(from, to).toInt().coerceAtLeast(1)
        return List(hours + 1) { index ->
            val time = from.plus(index.toLong(), ChronoUnit.HOURS)
            val localHour = time.atZone(zone).hour
            val temp = 20.0 + 36.0 * kotlin.math.sin((localHour - 9) / 24.0 * Math.PI * 2)
            val pop = if (!precip) null else if (localHour in 13..20) 55.0 else 8.0
            WeatherPoint(time, temp, popPercent = pop)
        }
    }

    private fun solar(from: Instant, to: Instant): List<PowerPoint> {
        val steps = ChronoUnit.HOURS.between(from, to).toInt() * 2
        return List(steps) { index ->
            val time = from.plusSeconds(index * 30L * 60L)
            val hour = time.atZone(zone).hour + time.atZone(zone).minute / 60.0
            val kw = if (hour in 8.0..18.0) kotlin.math.sin((hour - 8) / 10.0 * Math.PI) * 6.0 else 0.0
            PowerPoint(time, kw, 0.5, if (time.isAfter(now)) SeriesKind.FORECAST else SeriesKind.LIVE)
        }
    }

    private fun layers(temp: Boolean, ecobee: Boolean, solar: Boolean = false) = ChartLayers(
        temperature = temp,
        buy = false,
        sell = false,
        precipitation = false,
        solcast = solar,
        enphase = solar,
        ecobee = ecobee
    )
}
