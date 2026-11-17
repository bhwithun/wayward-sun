package com.brian.solwidget.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.DetroitCalendar
import com.brian.solwidget.data.EcobeeSample
import com.brian.solwidget.data.HistoryPage
import com.brian.solwidget.data.historyFetchKey
import com.brian.solwidget.ui.screens.CalendarFit
import com.brian.solwidget.ui.screens.HistoryPane
import com.brian.solwidget.ui.screens.PeriodReadout
import com.brian.solwidget.ui.theme.SolWidgetTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Portrait split on a 360×640dp window: chart block and calendar are equal,
 * the calendar does not scroll, and day cells follow [CalendarFit].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h640dp-420dpi")
class HistoryLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun english() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun sixWeekMonthFitsTheBottomHalfWithoutScrolling() {
        val month = YearMonth.of(2026, 8)
        val weeks = DetroitCalendar.monthWeeks(month)
        assertEquals(6, weeks.size)
        val readout = show(month, LocalDate.of(2026, 8, 15), "2026-08-15T16:00:00Z", heatPump = true)
        assertTrue(readout.hvac?.startsWith("Heat pump") == true)
        rule.onNodeWithText("Heat pump", substring = true).assertDoesNotExist()
        rule.onAllNodesWithText("Live").fetchSemanticsNodes().let { nodes ->
            assertTrue(nodes.isEmpty())
        }
        assertSplit(weeks = 6, probe = "Day 2026-08-31", expectUnder40 = true)
    }

    @Test
    fun fiveWeekMonthFitsTheBottomHalfWithoutScrolling() {
        val month = YearMonth.of(2026, 11)
        val weeks = DetroitCalendar.monthWeeks(month)
        assertEquals(5, weeks.size)
        show(month, LocalDate.of(2026, 11, 11), "2026-11-11T17:00:00Z", heatPump = false)
        assertSplit(weeks = 5, probe = "Day 2026-11-30", expectUnder40 = false)
    }

    private fun assertSplit(weeks: Int, probe: String, expectUnder40: Boolean) {
        rule.onNodeWithContentDescription(probe).assertIsDisplayed()
        assertTrue(
            rule.onAllNodesWithContentDescription("Week", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        val calendar = rule.onNodeWithTag("history-calendar").fetchSemanticsNode()
        assertNull(calendar.config.getOrNull(SemanticsActions.ScrollBy))
        val chart = rule.onNodeWithTag("history-chart-half").fetchSemanticsNode().boundsInRoot
        val cal = calendar.boundsInRoot
        assertEquals(chart.height, cal.height, 2f)
        assertEquals(chart.bottom, cal.top, 2f)
        val day = rule.onNodeWithContentDescription(probe).fetchSemanticsNode().boundsInRoot
        val half = with(rule.density) { cal.height.toDp() }
        val expected = CalendarFit.layout(half, weeks).cell
        val actual = with(rule.density) { day.height.toDp() }
        assertEquals(expected.value, actual.value, 1.5f)
        if (expectUnder40 && half <= 300.dp) {
            assertTrue(
                "6-week cell is ${actual.value}dp inside a ${half.value}dp half",
                actual < 40.dp
            )
        }
    }

    private fun show(
        month: YearMonth,
        day: LocalDate,
        nowIso: String,
        heatPump: Boolean
    ): PeriodReadout {
        val now = Instant.parse(nowIso)
        val weeks = DetroitCalendar.monthWeeks(month)
        val (from, to) = DetroitCalendar.monthFetchRange(weeks)
        val sample = EcobeeSample(
            at = now,
            indoorF = 70.0,
            coolSetF = 74.0,
            heatSetF = 68.0,
            heatPumpSec = if (heatPump) 3600 else 0
        )
        val history = HistoryPage(
            from = from,
            to = to,
            points = emptyList(),
            sun = emptyList(),
            ecobee = listOf(sample)
        )
        val readout = AtomicReference(PeriodReadout())
        rule.setContent {
            SolWidgetTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(64.dp))
                    HistoryPane(
                        month = month,
                        selectedWeek = DetroitCalendar.weekStart(day),
                        selectedDay = day,
                        weekSelected = false,
                        history = history,
                        historyKey = historyFetchKey(from, to),
                        historyLoading = false,
                        historyError = null,
                        layers = ChartLayers(hvac = heatPump),
                        now = now,
                        onShiftMonth = {},
                        onShiftWeek = {},
                        onSelectWeek = {},
                        onShiftDay = {},
                        onSelectDay = {},
                        onShowToday = {},
                        gasDollarsPerHour = 0.60,
                        onPeriodReadout = { readout.set(it) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
        rule.waitForIdle()
        return readout.get()
    }
}
