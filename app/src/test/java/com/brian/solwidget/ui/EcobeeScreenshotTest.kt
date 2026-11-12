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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.ecobeeFillPresence
import com.brian.solwidget.data.hvacPresence
import com.brian.solwidget.data.hvacSummary
import com.brian.solwidget.data.hvacSummaryLabel
import com.brian.solwidget.data.EcobeeSample
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.SeriesKind
import com.brian.solwidget.data.WeatherPoint
import com.brian.solwidget.ui.components.ChartBitmapRenderer
import com.brian.solwidget.ui.components.PowerChart
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.HistoryInterval
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.ui.screens.ChartControls
import com.brian.solwidget.ui.screens.HistoryCalendar
import com.brian.solwidget.ui.screens.HistoryPane
import com.brian.solwidget.ui.screens.HomeTabs
import com.brian.solwidget.ui.screens.LayerDrawerContent
import com.brian.solwidget.ui.screens.LivePane
import com.brian.solwidget.viewmodel.HomeTab
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.ui.theme.SolWidgetTheme
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
    @Config(sdk = [33], qualifiers = "w384dp-h800dp-420dpi")
    fun drawerClosesFromTheButton() {
        val activity = Robolectric.buildActivity(ScreenshotActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        val density = 420f / 160f
        val width = (384 * density).roundToInt()
        val height = (800 * density).roundToInt()
        activity.setContentView(composeView, FrameLayout.LayoutParams(width, height))
        composeView.setContent {
            PhoneFrame(fills(), past, future, layers(temp = true, ecobee = true), drawerOpen = true)
        }
        composeView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        looper.idle()
        val open = draw(composeView, width, height, "drawer-open-2.5.1.png")
        clickContentDescription(composeView, "Close menu")
        repeat(45) {
            looper.idle()
            Robolectric.getForegroundThreadScheduler().advanceBy(16, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        looper.idle()
        val closed = draw(composeView, width, height, "drawer-closed-2.5.1.png")
        val drawerX = (8 * density).roundToInt()
        val scrimX = (340 * density).roundToInt()
        val sampleY = (220 * density).roundToInt()
        check(open.getPixel(drawerX, sampleY) != closed.getPixel(drawerX, sampleY)) {
            "Closing the menu left the drawer in place."
        }
        check(open.getPixel(scrimX, sampleY) != open.getPixel(drawerX, sampleY)) {
            "The open drawer covers the scrim."
        }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w384dp-h800dp-420dpi")
    fun hvacRuntimeOnAColdDayAndAMildDay() {
        val density = 420f / 160f
        val width = (384 * density).roundToInt()
        val height = (800 * density).roundToInt()
        val hvacLayers = ChartLayers(
            temperature = true,
            solcast = true,
            enphase = false,
            ecobee = true,
            hvac = true,
            precipitation = false,
            buy = false,
            sell = false
        )
        captureSized(width, height, "hvac-cold-2.7.0.png") {
            PhoneFrame(runtimeDay(aux = true), past, future, hvacLayers, drawerOpen = false)
        }
        captureSized(width, height, "hvac-mild-2.7.0.png") {
            PhoneFrame(runtimeDay(aux = false), past, future, hvacLayers, drawerOpen = false)
        }
        val from = now.minus(48, ChronoUnit.HOURS)
        val to = now.plus(72, ChronoUnit.HOURS)
        val chart = ChartBitmapRenderer.render(
            points = solar(from, now.plus(24, ChronoUnit.HOURS)),
            now = now,
            width = width,
            height = 640,
            rangeFrom = from,
            rangeTo = to,
            weather = outdoor(from, to),
            layers = hvacLayers,
            ecobee = runtimeDay(aux = true)
        )
        captureSized(width, (280 * density).roundToInt(), "widget-unchanged-2.6.0.png") {
            Box(Modifier.fillMaxSize().background(SolColors.Navy).padding(12.dp)) {
                Image(
                    bitmap = chart.asImageBitmap(),
                    contentDescription = "Widget chart without a runtime strip",
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
        }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w384dp-h900dp-420dpi")
    fun historyDayAndWeekTabs() {
        val density = 420f / 160f
        val width = (384 * density).roundToInt()
        val height = (900 * density).roundToInt()
        val day = LocalDate.of(2026, 11, 11)
        val month = YearMonth.of(2026, 11)
        val weeks = DetroitCalendar.monthWeeks(month)
        val (fetchFrom, fetchTo) = DetroitCalendar.monthFetchRange(weeks)
        val page = HistoryPage(
            from = fetchFrom,
            to = fetchTo,
            points = historyHours(fetchFrom, fetchTo),
            sun = emptyList(),
            ecobee = coldEasternDay() + earlierWeekRuntime()
        )
        val key = historyFetchKey(fetchFrom, fetchTo)
        val layers = ChartLayers(
            temperature = true,
            solcast = true,
            enphase = false,
            ecobee = true,
            hvac = true,
            precipitation = false,
            buy = false,
            sell = false
        )
        captureSized(width, height, "history-day-2.8.0c.png") {
            historyScreen(day, month, page, key, layers, weekSelected = false)
        }
        captureSized(width, (420 * density).roundToInt(), "weeknum-closeup-unselected-2.8.0d.png") {
            weekCalendar(day, month, weeks, selectedWeek = null, selectedDay = day)
        }
        captureSized(width, (420 * density).roundToInt(), "weeknum-closeup-selected-2.8.0e.png") {
            weekCalendar(day, month, weeks, selectedWeek = DetroitCalendar.weekStart(day), selectedDay = null)
        }
        captureSized(width, height, "history-week-2.8.0f.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true)
        }
        captureSized(width, height, "history-week-legend-closed-2.8.0f.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true)
        }
        captureSized(width, height, "history-week-legend-open-2.8.0g.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true, legendOpen = true)
        }
        captureSized(width, height, "fullscreen-portrait-2.8.0g.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true, expanded = true)
        }
        captureSized((844 * density).roundToInt(), (390 * density).roundToInt(), "fullscreen-landscape-2.8.0f.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true, expanded = true)
        }
        captureSized(width, height, "live-2.8.0g.png") {
            liveScreen()
        }
        captureSized(width, height, "live-fullscreen-2.8.0f.png") {
            liveScreen(expanded = true)
        }
        captureSized(width, height, "fullscreen-portrait-2.8.1.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true, expanded = true)
        }
        captureSized((844 * density).roundToInt(), (390 * density).roundToInt(), "fullscreen-landscape-2.8.1.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true, expanded = true)
        }
        captureSized(width, height, "fullscreen-day-portrait-2.8.1.png") {
            historyScreen(day, month, page, key, layers, weekSelected = false, expanded = true)
        }
        captureSized(width, height, "live-fullscreen-2.8.1.png") {
            liveScreen(expanded = true)
        }
        captureSized(width, height, "fullscreen-swipe-2.8.1.png") {
            historyScreen(day, month, page, key, layers, weekSelected = true, expanded = true, previewDragFraction = 0.42f)
        }
        captureSized(width, (112 * density).roundToInt(), "tabs-closeup-2.8.0f.png") {
            SolWidgetTheme {
                Column(Modifier.fillMaxSize().background(SolColors.Navy)) {
                    Text("Wayward Sun", color = SolColors.Ink, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    HomeTabs(
                        tab = HomeTab.HISTORY,
                        onSelect = {},
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun weekCalendar(
        day: LocalDate,
        month: YearMonth,
        weeks: List<com.brian.solwidget.data.CalendarWeek>,
        selectedWeek: LocalDate?,
        selectedDay: LocalDate?
    ) {
        SolWidgetTheme {
            Column(Modifier.fillMaxSize().background(SolColors.Navy).padding(16.dp)) {
                Text("Wayward Sun", color = SolColors.Ink)
                HomeTabs(tab = HomeTab.HISTORY, onSelect = {}, modifier = Modifier.padding(top = 12.dp))
                HistoryCalendar(
                    month = month,
                    weeks = weeks,
                    selectedWeek = selectedWeek,
                    selectedDay = selectedDay,
                    today = day,
                    onShiftMonth = {},
                    onSelectWeek = {},
                    onSelectDay = {},
                    onShowToday = {}
                )
            }
        }
    }

    @Composable
    private fun liveScreen(expanded: Boolean = false) {
        val from = now.minus(48, ChronoUnit.HOURS)
        val to = now.plus(48, ChronoUnit.HOURS)
        val indoor = runtimeDay(aux = false)
        val chartLayers = layers(temp = true, ecobee = true, solar = true)
        SolWidgetTheme {
            Column(Modifier.fillMaxSize().background(SolColors.Navy)) {
                if (!expanded) {
                    Text("Wayward Sun", color = SolColors.Ink, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    HomeTabs(
                        tab = HomeTab.LIVE,
                        onSelect = {},
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    )
                }
                LivePane(
                    snapshot = ForecastSnapshot(
                        resourceId = "84d7-8b52-33f3-bd7b",
                        points = solar(from, to),
                        fetchedAt = now,
                        requestsUsed = 0,
                        requestLimit = 10,
                        isDemo = false
                    ),
                    weather = null,
                    livePast = solar(from, to),
                    ecobee = indoor,
                    layers = chartLayers,
                    now = now,
                    zone = zone,
                    isLoading = false,
                    onOpenSettings = {},
                    expandedPanel = null,
                    onToggleExpand = {},
                    gasDollarsPerHour = 0.60,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = if (expanded) 0.dp else 16.dp),
                    controls = ChartControls(expanded = expanded, expandProgress = 1f)
                )
            }
        }
    }

    @Composable
    private fun historyScreen(
        day: LocalDate,
        month: YearMonth,
        page: HistoryPage,
        key: String,
        layers: ChartLayers,
        weekSelected: Boolean,
        expanded: Boolean = false,
        legendOpen: Boolean = false,
        previewDragFraction: Float = 0f
    ) {
        SolWidgetTheme {
            Column(Modifier.fillMaxSize().background(SolColors.Navy)) {
                if (!expanded) {
                    Text("Wayward Sun", color = SolColors.Ink, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    HomeTabs(tab = HomeTab.HISTORY, onSelect = {}, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                }
                HistoryPane(
                    month = month,
                    selectedWeek = DetroitCalendar.weekStart(day),
                    selectedDay = day,
                    weekSelected = weekSelected,
                    history = page,
                    historyKey = key,
                    historyLoading = false,
                    historyError = null,
                    layers = layers,
                    now = now,
                    onShiftMonth = {},
                    onShiftWeek = {},
                    onSelectWeek = {},
                    onShiftDay = {},
                    onSelectDay = {},
                    onShowToday = {},
                    gasDollarsPerHour = 0.60,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = if (expanded) 0.dp else 16.dp),
                    controls = ChartControls(
                        expanded = expanded,
                        expandProgress = 1f,
                        legendOpen = legendOpen,
                        previewDragFraction = previewDragFraction
                    )
                )
            }
        }
    }

    private fun coldEasternDay(): List<EcobeeSample> {
        val start = DetroitCalendar.dayRange(LocalDate.of(2026, 11, 11)).first
        val samples = ArrayList<EcobeeSample>()
        var cursor = start
        while (!cursor.isAfter(now)) {
            val hour = cursor.atZone(zone).hour
            val aux = hour < 6
            val pump = !aux && hour < 11
            samples += EcobeeSample(
                at = cursor,
                indoorF = if (hour < 8) 64.0 else if (hour < 12) 70.0 else 78.0,
                coolSetF = if (hour < 12) 75.0 else 74.0,
                heatSetF = if (hour < 12) 66.0 else 68.0,
                heatPumpSec = if (pump) 240 else 0,
                auxHeatSec = if (aux) 300 else 0,
                coolSec = 0,
                fanSec = if (aux || pump) 280 else 20,
                outdoorF = if (aux) 22.0 else 30.0
            )
            cursor = cursor.plusSeconds(5 * 60)
        }
        return samples
    }

    /** Extra runtime on Monday and Tuesday so a week total is not the Wednesday total. */
    private fun earlierWeekRuntime(): List<EcobeeSample> {
        val monday = runtimeBlock(LocalDate.of(2026, 11, 9), 0, 4, aux = true)
        val tuesday = runtimeBlock(LocalDate.of(2026, 11, 10), 6, 14, aux = false)
        return monday + tuesday
    }

    private fun runtimeBlock(date: LocalDate, fromHour: Int, untilHour: Int, aux: Boolean): List<EcobeeSample> {
        val start = DetroitCalendar.dayRange(date).first.plusSeconds(fromHour * 3600L)
        val end = DetroitCalendar.dayRange(date).first.plusSeconds(untilHour * 3600L)
        val samples = ArrayList<EcobeeSample>()
        var cursor = start
        while (cursor.isBefore(end)) {
            samples += EcobeeSample(
                at = cursor,
                indoorF = if (aux) 63.0 else 68.0,
                coolSetF = 75.0,
                heatSetF = 66.0,
                heatPumpSec = if (aux) 0 else 300,
                auxHeatSec = if (aux) 300 else 0,
                coolSec = 0,
                fanSec = 300
            )
            cursor = cursor.plusSeconds(5 * 60)
        }
        return samples
    }

    private fun historyHours(from: Instant, to: Instant): List<HistoryInterval> {
        val rows = ArrayList<HistoryInterval>()
        var cursor = from.plusSeconds(30 * 60)
        while (cursor.isBefore(to)) {
            val local = cursor.atZone(zone)
            val hour = local.hour + local.minute / 60.0
            val temp = 20.0 + 36.0 * kotlin.math.sin((hour - 9.0) / 24.0 * Math.PI * 2)
            val kw = if (hour in 8.0..18.0) kotlin.math.sin((hour - 8.0) / 10.0 * Math.PI) * 6.0 else 0.0
            val future = cursor.isAfter(now)
            rows += HistoryInterval(
                periodEnd = cursor,
                actualKw = if (future) null else kw,
                forecastKw = kw,
                periodHours = 0.5,
                tempF = temp,
                precipPct = null
            )
            cursor = cursor.plusSeconds(30 * 60)
        }
        return rows
    }

    @Test
    @Config(sdk = [33], qualifiers = "w384dp-h800dp-420dpi")
    fun ecobeeRedesignAtPhoneWidth() {
        val density = 420f / 160f
        val width = (384 * density).roundToInt()
        val height = (800 * density).roundToInt()
        captureSized(width, height, "graph-ecobee-2.5.2b.png") {
            PhoneFrame(
                fills(),
                past,
                future,
                ChartLayers(
                    temperature = true,
                    solcast = true,
                    enphase = false,
                    ecobee = true,
                    precipitation = false,
                    buy = false,
                    sell = false
                ),
                drawerOpen = false
            )
        }
        captureSized(width, height, "graph-ecobee-temp-2.5.2b.png") {
            PhoneFrame(
                fills(),
                past,
                future,
                layers(temp = true, ecobee = true),
                drawerOpen = false
            )
        }
        val from = now.minus(48, ChronoUnit.HOURS)
        val to = now.plus(72, ChronoUnit.HOURS)
        val chart = ChartBitmapRenderer.render(
            points = solar(from, now.plus(24, ChronoUnit.HOURS)),
            now = now,
            width = width,
            height = 640,
            rangeFrom = from,
            rangeTo = to,
            weather = outdoor(from, to),
            layers = ChartLayers(
                temperature = true,
                solcast = true,
                enphase = false,
                ecobee = true,
                precipitation = false,
                buy = false,
                sell = false
            ),
            ecobee = fills()
        )
        val widgetHeight = (280 * density).roundToInt()
        captureSized(width, widgetHeight, "widget-2.5.2b.png") {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SolColors.Navy)
                    .padding(12.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(SolColors.Panel)
                        .padding(10.dp)
                ) {
                    Image(
                        bitmap = chart.asImageBitmap(),
                        contentDescription = "Widget chart with indoor and outdoor",
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
            }
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
        val scope = rememberCoroutineScope()
        val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
        SolWidgetTheme {
            BackHandler(enabled = drawerState.isOpen, onBack = closeDrawer)
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.46f),
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(280.dp),
                        drawerContainerColor = SolColors.Panel
                    ) {
                        LayerDrawerContent(
                            layers = layers,
                            versionName = "2.5.2",
                            checkingUpdates = false,
                            onToggle = { _, _ -> },
                            onReset = {},
                            onClose = closeDrawer,
                            onWebApp = closeDrawer,
                            onCheckUpdates = closeDrawer,
                            onSettings = closeDrawer
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
                            points = if (layers.solcast || layers.enphase) solar(from, to) else emptyList(),
                            now = now,
                            weather = if (layers.temperature) outdoor(from, to) else emptyList(),
                            rangeFrom = from,
                            rangeTo = to,
                            layers = layers,
                            ecobee = indoor
                        )
                    }
                    if (layers.hvac) {
                        Text(
                            text = hvacSummaryLabel(hvacSummary(indoor, from, to, now)),
                            color = SolColors.Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureSized(1080, 2400, name, content)
    }

    private fun captureSized(width: Int, height: Int, name: String, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ScreenshotActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView, FrameLayout.LayoutParams(width, height))
        composeView.setContent { content() }
        composeView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        draw(composeView, width, height, name)
    }

    private fun draw(composeView: ComposeView, width: Int, height: Int, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))
        val dir = File("/opt/cursor/artifacts").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun clickContentDescription(composeView: ComposeView, description: String) {
        val root = (composeView.getChildAt(0) as ViewRootForTest).semanticsOwner.rootSemanticsNode
        val node = findNode(root, description) ?: error("Missing $description")
        val action = node.config.getOrNull(SemanticsActions.OnClick) ?: error("No click on $description")
        check(action.action?.invoke() == true)
    }

    private fun findNode(node: SemanticsNode, description: String): SemanticsNode? {
        val labels = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        if (description in labels) return node
        node.children.forEach { child ->
            findNode(child, description)?.let { return it }
        }
        return null
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

    private fun runtimeDay(aux: Boolean): List<EcobeeSample> {
        return samples(past, hours = 18) { hour ->
            if (aux) {
                when {
                    hour < 8 -> 64.0 to (66.0 to 75.0)
                    hour < 12 -> 70.0 to (66.0 to 75.0)
                    else -> 78.0 to (68.0 to 74.0)
                }
            } else {
                when {
                    hour < 8 -> 67.5 to (66.0 to 75.0)
                    else -> 71.0 to (68.0 to 75.0)
                }
            }
        }.filter { !it.at.isAfter(now) }.map { sample ->
            val hour = sample.at.atZone(zone).hour
            when {
                aux && hour < 6 -> sample.copy(heatPumpSec = 0, auxHeatSec = 300, coolSec = 0, fanSec = 300, outdoorF = 22.0)
                aux && hour < 11 -> sample.copy(heatPumpSec = 240, auxHeatSec = 0, coolSec = 0, fanSec = 260, outdoorF = 30.0)
                !aux && hour in 5..10 -> sample.copy(heatPumpSec = 220, auxHeatSec = 0, coolSec = 0, fanSec = 240, outdoorF = 46.0)
                else -> sample.copy(heatPumpSec = 0, auxHeatSec = 0, coolSec = 0, fanSec = 20, outdoorF = if (aux) 34.0 else 50.0)
            }
        }
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
