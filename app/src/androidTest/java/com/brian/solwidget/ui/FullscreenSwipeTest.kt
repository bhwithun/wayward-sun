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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
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
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brian.solwidget.ScreenshotActivity
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.HistoryInterval
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.ui.screens.ChartControls
import com.brian.solwidget.ui.screens.HistoryPane
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.ui.theme.SolWidgetTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fullscreen paging on a real compose host. A drag past about 30% of the width
 * lands on the adjacent period. A short drag springs back. A tap and system Back leave fullscreen.
 */
@RunWith(AndroidJUnit4::class)
class FullscreenSwipeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ScreenshotActivity>()

    @Before
    fun english() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun swipeLeftAndRightChangeTheWeekAndAShortDragSpringsBack() {
        rule.setContent { HistoryHost(weekSelected = true) }
        rule.onNodeWithText(weekOfNov8).assertIsDisplayed()

        rule.onNodeWithTag("fullscreen-chart").performTouchInput {
            swipe(
                start = Offset(width * 0.58f, height * 0.55f),
                end = Offset(width * 0.46f, height * 0.55f),
                durationMillis = 800
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText(weekOfNov8).assertIsDisplayed()
        check(rule.onAllNodesWithText(weekOfNov1).fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeRight() }
        rule.waitUntil(timeoutMillis = 4_000) {
            rule.onAllNodesWithText(weekOfNov1).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(weekOfNov1).assertIsDisplayed()

        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 4_000) {
            rule.onAllNodesWithText(weekOfNov8).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 4_000) {
            rule.onAllNodesWithText(weekOfNov15).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(weekOfNov15).assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
    }

    @Test
    fun swipeRightChangesTheDay() {
        rule.setContent { HistoryHost(weekSelected = false) }
        rule.onNodeWithText(wednesday).assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeRight() }
        rule.waitUntil(timeoutMillis = 4_000) {
            rule.onAllNodesWithText(tuesday).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(tuesday).assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 4_000) {
            rule.onAllNodesWithText(wednesday).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun tapOnTheChartLeavesFullscreen() {
        rule.setContent { HistoryHost(weekSelected = true) }
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { click(center) }
        rule.waitForIdle()
        absent("fullscreen-chart")
        rule.onNodeWithTag("history-pane").assertIsDisplayed()
        rule.onNodeWithText(weekOfNov8).assertIsDisplayed()
    }

    @Test
    fun systemBackLeavesFullscreenAndLegendConsumesTheFirstBack() {
        rule.setContent { HistoryHost(weekSelected = true) }
        rule.onNodeWithContentDescription("Show legend").performClick()
        rule.onNodeWithText("Solcast").assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        pressBack()
        rule.waitForIdle()
        check(rule.onAllNodesWithText("Solcast").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
        pressBack()
        rule.waitForIdle()
        absent("fullscreen-chart")
        rule.onNodeWithTag("history-pane").assertIsDisplayed()
    }

    private fun absent(tag: String) {
        check(rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) { "Still showing $tag" }
    }

    private fun pressBack() {
        rule.runOnIdle {
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Composable
    private fun HistoryHost(weekSelected: Boolean) {
        // Noon in Detroit on Wednesday of Nov 15–21, so that week is the last page.
        val now = Instant.parse("2026-11-18T17:00:00Z")
        val month = YearMonth.of(2026, 11)
        val weeks = DetroitCalendar.monthWeeks(month)
        val (from, to) = DetroitCalendar.monthFetchRange(weeks)
        val page = HistoryPage(
            from = from,
            to = to,
            points = intervals(from),
            sun = emptyList()
        )
        var expanded by remember { mutableStateOf(true) }
        var legendOpen by remember { mutableStateOf(false) }
        var week by remember { mutableStateOf(LocalDate.of(2026, 11, 8)) }
        var day by remember { mutableStateOf(LocalDate.of(2026, 11, 11)) }
        BackHandler(enabled = legendOpen) { legendOpen = false }
        BackHandler(enabled = expanded && !legendOpen) { expanded = false }
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
                    layers = ChartLayers(
                        temperature = true,
                        solcast = true,
                        enphase = false,
                        ecobee = false,
                        precipitation = false,
                        buy = false,
                        sell = false
                    ),
                    now = now,
                    onShiftMonth = {},
                    onShiftWeek = { delta -> week = week.plusDays(7L * delta) },
                    onSelectWeek = { week = it },
                    onShiftDay = { delta -> day = day.plusDays(delta.toLong()) },
                    onSelectDay = { day = it },
                    onShowToday = {},
                    gasDollarsPerHour = 0.60,
                    modifier = Modifier.fillMaxSize(),
                    controls = ChartControls(
                        expanded = expanded,
                        expandProgress = if (expanded) 1f else 0f,
                        onChartTap = {
                            legendOpen = false
                            expanded = !expanded
                        },
                        legendOpen = legendOpen,
                        onToggleLegend = { legendOpen = !legendOpen },
                        onDismissLegend = { legendOpen = false }
                    )
                )
            }
        }
    }

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

    private companion object {
        const val EN = "\u2013"
        val weekOfNov8 = "Nov 8 $EN 14"
        val weekOfNov1 = "Nov 1 $EN 7"
        val weekOfNov15 = "Nov 15 $EN 21"
        const val wednesday = "Wed, Nov 11"
        const val tuesday = "Tue, Nov 10"
    }
}
