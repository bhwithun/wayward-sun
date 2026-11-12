package com.brian.solwidget.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Forward history navigation when the loaded month ends before today.
 * The clock is 15 December 2026, so November is on the way to the current month.
 */
@RunWith(AndroidJUnit4::class)
class HistoryForwardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ScreenshotActivity>()

    @Before
    fun english() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun octoberCalendarPagesForwardThroughNovemberToTheCurrentMonth() {
        rule.setContent {
            HistoryHost(
                weekSelected = true,
                expanded = false,
                initialMonth = YearMonth.of(2026, 10),
                initialWeek = LocalDate.of(2026, 10, 4),
                initialDay = LocalDate.of(2026, 10, 31)
            )
        }
        assertMonth("October 2026")
        shot("history-october-before-forward")

        rule.onNodeWithContentDescription("Previous month").performScrollTo().performClick()
        assertMonth("September 2026")

        rule.onNodeWithTag("history-calendar").performScrollTo().performTouchInput { swipeLeft() }
        assertMonth("October 2026")

        rule.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        assertMonth("November 2026")
        rule.onNodeWithContentDescription("Next month").performScrollTo().performClick()
        assertMonth("December 2026")
        shot("history-december-after-forward")

        rule.onNodeWithContentDescription("Next month").performScrollTo().assertIsNotEnabled()
        check(rule.onAllNodesWithText("January 2027").fetchSemanticsNodes().isEmpty()) {
            "Calendar moved past the current month"
        }
        rule.onNodeWithTag("history-calendar").performScrollTo().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertMonth("December 2026")
    }

    @Test
    fun historyChartSwipesForwardFromTheLastOctoberWeek() {
        rule.setContent {
            HistoryHost(
                weekSelected = true,
                expanded = false,
                initialMonth = YearMonth.of(2026, 10),
                initialWeek = LocalDate.of(2026, 10, 25),
                initialDay = LocalDate.of(2026, 10, 31)
            )
        }
        rule.onNodeWithText(weekOfOct25).assertIsDisplayed()
        swipeChart(weekOfNov1)
        swipeChart(weekOfNov8)
    }

    @Test
    fun fullscreenWeekFromAugustThirtiethSwipesForwardAcrossMonths() {
        rule.setContent {
            HistoryHost(
                weekSelected = true,
                expanded = true,
                initialMonth = YearMonth.of(2026, 8),
                initialWeek = LocalDate.of(2026, 8, 30),
                initialDay = LocalDate.of(2026, 8, 30)
            )
        }
        rule.onNodeWithText(weekOfAug30).assertIsDisplayed()
        shot("fullscreen-aug30-before-swipe")
        swipeFullscreen(weekOfSep6)
        swipeFullscreen(weekOfSep13)
        swipeFullscreen(weekOfSep20)
        swipeFullscreen(weekOfSep27)
        swipeFullscreen(weekOfOct4)
        shot("fullscreen-oct4-after-swipes")
        rule.onNodeWithTag("fullscreen-chart").assertIsDisplayed()
    }

    @Test
    fun fullscreenDayNearMonthEndSwipesForward() {
        rule.setContent {
            HistoryHost(
                weekSelected = false,
                expanded = true,
                initialMonth = YearMonth.of(2026, 10),
                initialWeek = LocalDate.of(2026, 10, 25),
                initialDay = LocalDate.of(2026, 10, 31)
            )
        }
        rule.onNodeWithText(saturday).assertIsDisplayed()
        shot("fullscreen-oct31-before-swipe")
        swipeFullscreen(sunday)
        swipeFullscreen(monday)
        swipeFullscreen(tuesday)
        shot("fullscreen-nov3-after-swipes")
    }

    @Test
    fun fullscreenWeekStopsAtTheCurrentWeek() {
        rule.setContent {
            HistoryHost(
                weekSelected = true,
                expanded = true,
                initialMonth = YearMonth.of(2026, 12),
                initialWeek = LocalDate.of(2026, 12, 13),
                initialDay = LocalDate.of(2026, 12, 15)
            )
        }
        rule.onNodeWithText(currentWeek).assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText(currentWeek).assertIsDisplayed()
        check(rule.onAllNodesWithText(weekOfDec20).fetchSemanticsNodes().isEmpty()) {
            "Week pager moved past the current week"
        }
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeRight() }
        rule.waitUntil(timeoutMillis = 8_000) {
            rule.onAllNodesWithText(weekOfDec6).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(weekOfDec6).assertIsDisplayed()
    }

    @Test
    fun fullscreenDayStopsAtToday() {
        rule.setContent {
            HistoryHost(
                weekSelected = false,
                expanded = true,
                initialMonth = YearMonth.of(2026, 12),
                initialWeek = LocalDate.of(2026, 12, 13),
                initialDay = LocalDate.of(2026, 12, 15)
            )
        }
        rule.onNodeWithText(todayLabel).assertIsDisplayed()
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText(todayLabel).assertIsDisplayed()
        check(rule.onAllNodesWithText(tomorrowLabel).fetchSemanticsNodes().isEmpty()) {
            "Day pager moved past today"
        }
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeRight() }
        rule.waitUntil(timeoutMillis = 8_000) {
            rule.onAllNodesWithText(yesterdayLabel).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMonth(name: String) {
        rule.onNodeWithText(name).performScrollTo().assertIsDisplayed()
    }

    private fun swipeChart(next: String) {
        rule.onNodeWithTag("history-chart").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 8_000) {
            rule.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(next).assertIsDisplayed()
    }

    private fun swipeFullscreen(next: String) {
        rule.onNodeWithTag("fullscreen-chart").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 8_000) {
            rule.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(next).assertIsDisplayed()
    }

    private fun shot(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot() ?: return
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir
            ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Composable
    private fun HistoryHost(
        weekSelected: Boolean,
        expanded: Boolean,
        initialMonth: YearMonth,
        initialWeek: LocalDate,
        initialDay: LocalDate
    ) {
        val today = DetroitCalendar.today(NOW)
        var month by remember { mutableStateOf(initialMonth) }
        var week by remember { mutableStateOf(initialWeek) }
        var day by remember { mutableStateOf(initialDay) }
        val loadedWeeks = DetroitCalendar.monthWeeks(month)
        val (loadedFrom, loadedTo) = DetroitCalendar.monthFetchRange(loadedWeeks)
        val loaded = HistoryPage(
            from = loadedFrom,
            to = loadedTo,
            points = intervals(loadedFrom, loadedTo),
            sun = emptyList()
        )
        SolWidgetTheme {
            Column(Modifier.fillMaxSize().background(SolColors.Navy)) {
                HistoryPane(
                    month = month,
                    selectedWeek = week,
                    selectedDay = day,
                    weekSelected = weekSelected,
                    history = loaded,
                    historyKey = historyFetchKey(loaded.from, loaded.to),
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
                    now = NOW,
                    onShiftMonth = { delta ->
                        val next = month.plusMonths(delta.toLong())
                        val weeks = DetroitCalendar.monthWeeks(next)
                        month = next
                        week = weeks.find { it.id == week }?.id
                            ?: weeks.find { days -> days.days.any { it.date == today } }?.id
                            ?: weeks.first().id
                        val visible = weeks.any { days -> days.days.any { it.date == day } }
                        if (!visible) {
                            day = if (weeks.any { days -> days.days.any { it.date == today && it.inMonth } }) {
                                today
                            } else {
                                LocalDate.of(next.year, next.monthValue, 1)
                            }
                        }
                    },
                    onShiftWeek = { delta ->
                        val focus = DetroitCalendar.shiftWeek(month, week, delta)
                        month = focus.month
                        week = focus.week
                    },
                    onSelectWeek = { week = DetroitCalendar.weekStart(it) },
                    onShiftDay = { delta ->
                        val next = day.plusDays(delta.toLong())
                        day = next
                        month = YearMonth.from(next)
                    },
                    onSelectDay = { picked ->
                        day = picked
                        month = YearMonth.from(picked)
                    },
                    onShowToday = {
                        month = YearMonth.from(today)
                        week = DetroitCalendar.weekStart(today)
                        day = today
                    },
                    gasDollarsPerHour = 0.60,
                    modifier = Modifier.fillMaxSize(),
                    controls = ChartControls(
                        expanded = expanded,
                        expandProgress = if (expanded) 1f else 0f
                    )
                )
            }
        }
    }

    private fun intervals(from: Instant, to: Instant): List<HistoryInterval> {
        val points = ArrayList<HistoryInterval>()
        var cursor = from
        while (cursor.isBefore(to)) {
            points += HistoryInterval(cursor, 1.0, 1.0, 0.5, 48.0, 0.0)
            cursor = cursor.plusSeconds(12 * 3600)
        }
        return points
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-12-15T17:00:00Z")
        const val EN = "\u2013"
        val weekOfAug30 = "Aug 30 $EN Sep 5"
        val weekOfSep6 = "Sep 6 $EN 12"
        val weekOfSep13 = "Sep 13 $EN 19"
        val weekOfSep20 = "Sep 20 $EN 26"
        val weekOfSep27 = "Sep 27 $EN Oct 3"
        val weekOfOct4 = "Oct 4 $EN 10"
        val weekOfOct25 = "Oct 25 $EN 31"
        val weekOfNov1 = "Nov 1 $EN 7"
        val weekOfNov8 = "Nov 8 $EN 14"
        val weekOfDec6 = "Dec 6 $EN 12"
        val currentWeek = "Dec 13 $EN 19"
        val weekOfDec20 = "Dec 20 $EN 26"
        const val saturday = "Sat, Oct 31"
        const val sunday = "Sun, Nov 1"
        const val monday = "Mon, Nov 2"
        const val tuesday = "Tue, Nov 3"
        const val yesterdayLabel = "Mon, Dec 14"
        const val todayLabel = "Tue, Dec 15"
        const val tomorrowLabel = "Wed, Dec 16"
    }
}
