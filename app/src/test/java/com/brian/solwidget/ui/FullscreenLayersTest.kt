package com.brian.solwidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.brian.solwidget.ScreenshotActivity
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.HouseSample
import com.brian.solwidget.data.ForecastSnapshot
import com.brian.solwidget.data.HistoryInterval
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.PowerPoint
import com.brian.solwidget.data.SeriesKind
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.ui.screens.ChartControls
import com.brian.solwidget.ui.screens.FullscreenPlotHeadroom
import com.brian.solwidget.ui.screens.HistoryPane
import com.brian.solwidget.ui.screens.HouseCapRise
import com.brian.solwidget.ui.screens.LivePane
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.ui.theme.SolWidgetTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fullscreen layer menu: the button clears the plot, the sheet is the drawer's
 * switches, a toggle updates that shared state, and taps or swipes while it is
 * open do not page or leave fullscreen. Back closes the sheet first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-420dpi")
class FullscreenLayersTest {
    @get:Rule
    val rule = createAndroidComposeRule<ScreenshotActivity>()

    @Before
    fun english() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun menuButtonClearsThePlotAndTheSheetIsOnlyTheLayerSwitches() {
        rule.setContent { HistoryHost(weekSelected = true) }
        val button = rule.onNodeWithTag("fullscreen-layers-button").fetchSemanticsNode().boundsInRoot
        val plot = rule.onNodeWithTag("fullscreen-plot").fetchSemanticsNode().boundsInRoot
        val chart = rule.onNodeWithTag("fullscreen-chart").fetchSemanticsNode().boundsInRoot
        val legend = rule.onNodeWithContentDescription("Show legend").fetchSemanticsNode().boundsInRoot
        val touch = with(rule.density) { 48.dp.toPx() }
        check(button.width >= touch - 1f && button.height >= touch - 1f) {
            "Layers button is ${button.width} x ${button.height}, under 48dp"
        }
        check(button.left <= chart.left + 2f && button.top <= chart.top + 2f) {
            "Layers button $button is not in the top-start corner of $chart"
        }
        check(button.bottom <= plot.top + 1f) {
            "Layers button $button covers the plot $plot"
        }
        check(button.right < legend.left) { "Layers button overlaps the legend" }

        rule.onNodeWithTag("fullscreen-layers-button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-layers-sheet").assertIsDisplayed()
        rule.onNodeWithText(weekOfNov8).assertIsDisplayed()
        listOf("Solcast", "Enphase", "Temp", "Precip", "Buy", "Sell", "Ecobee", "HVAC", "Reset layers")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        listOf("Settings", "Web app", "Check for updates", "On widget").forEach { absent(it) }
        val tops = listOf("Solcast", "Enphase", "Temp", "Precip", "Buy", "Sell", "Ecobee", "HVAC").map { label ->
            rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        }
        check(tops.zipWithNext().all { (above, below) -> above < below }) {
            "Layer switches are out of drawer order: $tops"
        }
        val sheet = rule.onNodeWithTag("fullscreen-layers-sheet").fetchSemanticsNode().boundsInRoot
        val cap = with(rule.density) { 280.dp.toPx() }
        val gap = with(rule.density) { 48.dp.toPx() }
        check(sheet.width <= cap + 1f) { "Layer sheet is ${sheet.width}px, over 280dp" }
        check(chart.width - sheet.width >= gap - 1f) {
            "Layer sheet leaves ${chart.width - sheet.width}px to dismiss, under 48dp"
        }

        rule.onNodeWithTag("layer-switch-Enphase").assertIsToggleable().assertIsOn()
        rule.onNodeWithTag("layer-switch-Enphase").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("layer-switch-Enphase").assertIsOff()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-layers-sheet").assertIsDisplayed()
        rule.onNodeWithText(weekOfNov8).assertIsDisplayed()
    }

    @Test
    fun tapsOnTheButtonSheetAndScrimDoNotLeaveFullscreenAndBackClosesTheSheetFirst() {
        rule.setContent { HistoryHost(weekSelected = true) }
        rule.onNodeWithTag("fullscreen-layers-button").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Solar").performTouchInput { click() }
        rule.waitForIdle()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-layers-sheet").assertIsDisplayed()

        rule.onNodeWithContentDescription("Close menu").performClick()
        rule.waitForIdle()
        absentTag("fullscreen-layers-sheet")
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()

        rule.onNodeWithTag("fullscreen-layers-button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fullscreen-layers-scrim").performTouchInput {
            click(Offset(width - 8.dp.toPx(), height / 2f))
        }
        rule.waitForIdle()
        absentTag("fullscreen-layers-sheet")
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        absentTag("history-pane")

        rule.onNodeWithTag("fullscreen-layers-button").performClick()
        rule.waitForIdle()
        pressBack()
        rule.waitForIdle()
        absentTag("fullscreen-layers-sheet")
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        pressBack()
        rule.waitForIdle()
        absentTag("fullscreen-chart")
        rule.onNodeWithTag("history-pane").assertIsDisplayed()
    }

    @Test
    fun openMenuBlocksWeekPagingUntilItCloses() {
        assertPagingBlocked(weekSelected = true, staying = weekOfNov8, next = weekOfNov15)
    }

    @Test
    fun openMenuBlocksDayPagingUntilItCloses() {
        assertPagingBlocked(weekSelected = false, staying = wednesday, next = thursday)
    }

    @Test
    fun houseUseToggleRedrawsTheSummaryAndClearsTheButton() {
        rule.setContent {
            HistoryHost(
                weekSelected = false,
                house = listOf(
                    HouseSample(Instant.parse("2026-11-11T17:00:00Z"), 16.4),
                    HouseSample(Instant.parse("2026-11-11T17:30:00Z"), 4.0)
                ),
                showHouse = true
            )
        }
        val button = rule.onNodeWithTag("fullscreen-layers-button").fetchSemanticsNode().boundsInRoot
        val plot = rule.onNodeWithTag("fullscreen-plot").fetchSemanticsNode().boundsInRoot
        val capClearance = with(rule.density) { (FullscreenPlotHeadroom - HouseCapRise).toPx() }
        check(plot.top + capClearance >= button.bottom - 1f) {
            "House-use cap markers would sit under the layers button"
        }
        rule.onNodeWithTag("fullscreen-layers-button").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("House use").assertIsDisplayed()
        absent("On widget")
        val hvacTop = rule.onNodeWithText("HVAC").fetchSemanticsNode().boundsInRoot.top
        val houseTop = rule.onNodeWithText("House use").fetchSemanticsNode().boundsInRoot.top
        check(hvacTop < houseTop) { "House use is not under HVAC" }
        rule.onNodeWithTag("layer-switch-House use").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("layer-switch-House use").assertIsOn()
        val summary = rule.onNodeWithTag("fullscreen-house-summary").fetchSemanticsNode().boundsInRoot
        check(summary.top >= button.bottom - 1f) {
            "House summary $summary overlaps the layers button $button"
        }
        rule.onNodeWithText("House 10.2 kWh").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Show legend").performClick()
        rule.onNodeWithText("House use · Peak 16.4 kW (half-hour)").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
    }

    @Test
    fun liveFullscreenUsesTheSameLayerSheet() {
        rule.setContent { LiveHost() }
        val button = rule.onNodeWithTag("fullscreen-layers-button").fetchSemanticsNode().boundsInRoot
        val plot = rule.onNodeWithTag("fullscreen-plot").fetchSemanticsNode().boundsInRoot
        check(button.bottom <= plot.top + 1f) { "Live layers button covers the plot" }
        rule.onNodeWithTag("fullscreen-layers-button").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Solcast").assertIsDisplayed()
        absent("Settings")
        absent("On widget")
        rule.onNodeWithTag("layer-switch-Temp").assertIsOn().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("layer-switch-Temp").assertIsOff()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-layers-sheet").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("fullscreen-layers-sheet").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
    }

    private fun assertPagingBlocked(weekSelected: Boolean, staying: String, next: String) {
        rule.setContent { HistoryHost(weekSelected = weekSelected) }
        rule.onNodeWithText(staying).assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-layers-button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fullscreen-chart").performTouchInput {
            swipe(
                start = Offset(width * 0.92f, height * 0.62f),
                end = Offset(width * 0.55f, height * 0.62f),
                durationMillis = 200
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText(staying).assertIsDisplayed()
        check(rule.onAllNodesWithText(next).fetchSemanticsNodes().isEmpty()) {
            "Swipe while the layer sheet was open moved to $next"
        }
        rule.onNodeWithTag("fullscreen-layers-scrim").performTouchInput {
            click(Offset(width - 8.dp.toPx(), height / 2f))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 4_000) {
            rule.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(next).assertIsDisplayed()
    }

    private fun absent(text: String) {
        check(rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) { "Still showing $text" }
    }

    private fun absentTag(tag: String) {
        check(rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) { "Still showing $tag" }
    }

    private fun pressBack() {
        rule.runOnIdle {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Composable
    private fun HistoryHost(
        weekSelected: Boolean,
        house: List<HouseSample> = emptyList(),
        showHouse: Boolean = false
    ) {
        val now = Instant.parse("2026-11-18T17:00:00Z")
        val month = YearMonth.of(2026, 11)
        val weeks = DetroitCalendar.monthWeeks(month)
        val (from, to) = DetroitCalendar.monthFetchRange(weeks)
        val page = HistoryPage(from = from, to = to, points = intervals(from), sun = emptyList(), house = house)
        var expanded by remember { mutableStateOf(true) }
        var layersOpen by remember { mutableStateOf(false) }
        var legendOpen by remember { mutableStateOf(false) }
        var layers by remember { mutableStateOf(ChartLayers()) }
        var week by remember { mutableStateOf(LocalDate.of(2026, 11, 8)) }
        var day by remember { mutableStateOf(LocalDate.of(2026, 11, 11)) }
        BackHandler(enabled = expanded && !layersOpen) { expanded = false }
        SolWidgetTheme {
            Column(Modifier.fillMaxSize().background(SolColors.Navy)) {
                HistoryPane(
                    month = month,
                    selectedWeek = week,
                    selectedDay = day,
                    weekSelected = weekSelected,
                    history = page,
                    historyKey = historyFetchKey(from, to),
                    historyLoading = false,
                    historyError = null,
                    layers = layers,
                    now = now,
                    onShiftMonth = {},
                    onShiftWeek = { delta -> week = week.plusDays(7L * delta) },
                    onSelectWeek = { week = it },
                    onShiftDay = { delta -> day = day.plusDays(delta.toLong()) },
                    onSelectDay = { day = it },
                    onShowToday = {},
                    gasDollarsPerHour = 0.60,
                    modifier = Modifier.fillMaxSize(),
                    controls = layerControls(
                        expanded = expanded,
                        layersOpen = layersOpen,
                        onChartTap = {
                            layersOpen = false
                            expanded = false
                        },
                        onToggleLayers = { layersOpen = !layersOpen },
                        onDismissLayers = { layersOpen = false },
                        onToggleLayer = { layer, visible -> layers = layers.with(layer, visible) },
                        onResetLayers = { layers = ChartLayers() },
                        legendOpen = legendOpen,
                        onToggleLegend = { legendOpen = !legendOpen },
                        showHouse = showHouse
                    )
                )
            }
        }
    }

    @Composable
    private fun LiveHost() {
        val zone = ZoneId.of("America/Detroit")
        val now = Instant.parse("2026-11-11T17:00:00Z")
        var expanded by remember { mutableStateOf(true) }
        var layersOpen by remember { mutableStateOf(false) }
        var layers by remember { mutableStateOf(ChartLayers()) }
        BackHandler(enabled = expanded && !layersOpen) { expanded = false }
        SolWidgetTheme {
            Column(Modifier.fillMaxSize().background(SolColors.Navy)) {
                LivePane(
                    snapshot = liveSnapshot(now, zone),
                    weather = null,
                    livePast = null,
                    ecobee = emptyList(),
                    layers = layers,
                    now = now,
                    zone = zone,
                    isLoading = false,
                    onOpenSettings = {},
                    expandedPanel = null,
                    onToggleExpand = {},
                    gasDollarsPerHour = 0.60,
                    modifier = Modifier.fillMaxSize(),
                    controls = layerControls(
                        expanded = expanded,
                        layersOpen = layersOpen,
                        onChartTap = {
                            layersOpen = false
                            expanded = false
                        },
                        onToggleLayers = { layersOpen = !layersOpen },
                        onDismissLayers = { layersOpen = false },
                        onToggleLayer = { layer, visible -> layers = layers.with(layer, visible) },
                        onResetLayers = { layers = ChartLayers() }
                    )
                )
            }
        }
    }

    private fun layerControls(
        expanded: Boolean,
        layersOpen: Boolean,
        onChartTap: () -> Unit,
        onToggleLayers: () -> Unit,
        onDismissLayers: () -> Unit,
        onToggleLayer: (com.brian.solwidget.data.ChartLayer, Boolean) -> Unit,
        onResetLayers: () -> Unit,
        legendOpen: Boolean = false,
        onToggleLegend: () -> Unit = {},
        showHouse: Boolean = false
    ) = ChartControls(
        expanded = expanded,
        expandProgress = if (expanded) 1f else 0f,
        onChartTap = onChartTap,
        layersOpen = layersOpen,
        onToggleLayers = onToggleLayers,
        onDismissLayers = onDismissLayers,
        onToggleLayer = onToggleLayer,
        onResetLayers = onResetLayers,
        legendOpen = legendOpen,
        onToggleLegend = onToggleLegend,
        showHouse = showHouse
    )

    private fun intervals(from: Instant): List<HistoryInterval> {
        val stop = LocalDate.of(2026, 11, 21).atStartOfDay(DetroitCalendar.ZONE).toInstant()
        val points = ArrayList<HistoryInterval>()
        var cursor = from
        while (cursor.isBefore(stop)) {
            points += HistoryInterval(cursor, 1.0, 1.0, 0.5, 48.0, 0.0)
            cursor = cursor.plusSeconds(12 * 3600)
        }
        return points
    }

    private fun liveSnapshot(now: Instant, zone: ZoneId): ForecastSnapshot {
        val (from, to) = ForecastSnapshot.range(now, zone)
        val points = ArrayList<PowerPoint>()
        var cursor = from.plusSeconds(30 * 60)
        while (!cursor.isAfter(to)) {
            val hour = cursor.atZone(zone).hour
            points += PowerPoint(
                periodEnd = cursor,
                kw = if (hour in 8..16) 3.0 else 0.0,
                periodHours = 0.5,
                kind = if (cursor.isAfter(now)) SeriesKind.FORECAST else SeriesKind.LIVE
            )
            cursor = cursor.plusSeconds(30 * 60)
        }
        return ForecastSnapshot(
            resourceId = "84d7-8b52-33f3-bd7b",
            points = points,
            fetchedAt = now,
            requestsUsed = 1,
            requestLimit = 10,
            isDemo = false
        )
    }

    private companion object {
        const val EN = "\u2013"
        val weekOfNov8 = "Nov 8 $EN 14"
        val weekOfNov15 = "Nov 15 $EN 21"
        const val wednesday = "Wed, Nov 11"
        const val thursday = "Thu, Nov 12"
    }
}
