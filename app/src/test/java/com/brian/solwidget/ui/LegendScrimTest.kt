package com.brian.solwidget.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.HistoryInterval
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.ui.screens.ChartControls
import com.brian.solwidget.ui.screens.HistoryPane
import com.brian.solwidget.ui.screens.HomeScaffold
import com.brian.solwidget.ui.theme.SolWidgetTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opening Legend on a non-zoomed chart used to expand the top-bar scrim to the
 * screen and collapse the scaffold body. The legend and the chart both stay up,
 * and a tap on the chart (outside the card) closes the popover.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-420dpi")
class LegendScrimTest {
    @get:Rule
    val rule = createComposeRule()

    init {
        Locale.setDefault(Locale.US)
    }

    @Before
    fun english() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun dayLegendKeepsTheChart() {
        rule.setContent { ChartHost() }
        openLegendAndDismiss("Wed, Nov 11", "history-chart")
    }

    private fun openLegendAndDismiss(title: String, chartTag: String) {
        rule.onNodeWithText("Wayward Sun").assertIsDisplayed()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag(chartTag).assertIsDisplayed()
        check(rule.onAllNodesWithText("Solcast").fetchSemanticsNodes().isEmpty()) {
            "Legend was open before the tap"
        }
        assertBodyKeepsTheChart(chartTag)

        rule.onNodeWithText("Legend").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Solcast").assertIsDisplayed()
        rule.onNodeWithText("Enphase").assertIsDisplayed()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag(chartTag).assertIsDisplayed()
        assertLegendSitsOnTheChart()
        assertBodyKeepsTheChart(chartTag)
        check(rule.onAllNodesWithContentDescription("Dismiss legend").fetchSemanticsNodes().size == 2) {
            "Legend scrims should cover the top bar and the chart"
        }

        rule.onNodeWithTag("home-body").performTouchInput {
            click(Offset(width * 0.2f, height * 0.85f))
        }
        rule.waitForIdle()

        check(rule.onAllNodesWithText("Solcast").fetchSemanticsNodes().isEmpty()) {
            "Tapping outside the legend left it open"
        }
        check(rule.onAllNodesWithContentDescription("Dismiss legend").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Legend").assertIsDisplayed()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag(chartTag).assertIsDisplayed()
        assertBodyKeepsTheChart(chartTag)
    }

    private fun assertBodyKeepsTheChart(chartTag: String) {
        val bodyNode = rule.onNodeWithTag("home-body").fetchSemanticsNode()
        val body = bodyNode.boundsInRoot
        val root = generateSequence(bodyNode) { it.parent }.last().boundsInRoot
        check(body.height > root.height * 0.45f) {
            "Chart body collapsed to ${body.height} px of ${root.height}"
        }
        val chart = rule.onNodeWithTag(chartTag).fetchSemanticsNode().boundsInRoot
        check(chart.height > 100f) { "$chartTag collapsed to ${chart.height} px" }
        check(chart.top >= body.top - 1f && chart.bottom <= body.bottom + 1f) {
            "$chartTag $chart fell outside the chart body $body"
        }
    }

    private fun assertLegendSitsOnTheChart() {
        val body = rule.onNodeWithTag("home-body").fetchSemanticsNode().boundsInRoot
        val legend = rule.onNodeWithText("Solcast").fetchSemanticsNode().boundsInRoot
        check(legend.height > 0f && legend.width > 0f) { "Legend collapsed to $legend" }
        check(legend.top >= body.top - 1f && legend.bottom <= body.bottom + 1f) {
            "Legend $legend is outside the chart body $body"
        }
    }

    @Composable
    private fun ChartHost() {
        var legendOpen by remember { mutableStateOf(false) }
        val controls = ChartControls(
            expanded = false,
            legendOpen = legendOpen,
            onToggleLegend = { legendOpen = !legendOpen },
            onDismissLegend = { legendOpen = false }
        )
        val week = DetroitCalendar.weekStart(day)
        val weeks = DetroitCalendar.monthWeeks(month)
        val (from, to) = DetroitCalendar.monthFetchRange(weeks)
        val history = HistoryPage(
            from = from,
            to = to,
            points = listOf(HistoryInterval(now, 1.2, 1.0, 0.5, 48.0, 10.0)),
            sun = emptyList()
        )
        SolWidgetTheme {
            Box(Modifier.fillMaxSize()) {
                HomeScaffold(
                    covering = false,
                    legendOpen = legendOpen,
                    onOpenDrawer = {},
                    onDismissLegend = { legendOpen = false },
                    pane = {
                        HistoryPane(
                            month = month,
                            selectedWeek = week,
                            selectedDay = day,
                            weekSelected = false,
                            history = history,
                            historyKey = historyFetchKey(from, to),
                            historyLoading = false,
                            historyError = null,
                            layers = ChartLayers(),
                            now = now,
                            onShiftMonth = {},
                            onShiftWeek = {},
                            onSelectWeek = {},
                            onShiftDay = {},
                            onSelectDay = {},
                            onShowToday = {},
                            gasDollarsPerHour = 0.60,
                            controls = controls
                        )
                    }
                )
            }
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2026-11-11T17:00:00Z")
        val day: LocalDate = LocalDate.of(2026, 11, 11)
        val month: YearMonth = YearMonth.of(2026, 11)
    }
}
