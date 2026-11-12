package com.brian.solwidget.ui

import com.brian.solwidget.ui.screens.CHART_EXPAND_MILLIS
import com.brian.solwidget.ui.screens.chartTapFires
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.ui.screens.MetaPanel
import com.brian.solwidget.ui.screens.liveFullscreenCards
import com.brian.solwidget.ui.screens.pagerDelta
import com.brian.solwidget.ui.screens.livePeriodLabel
import com.brian.solwidget.ui.screens.periodRangeLabel
import com.brian.solwidget.ui.screens.showChartExpandHint
import com.brian.solwidget.viewmodel.HomeUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartExpandTest {
    @Test
    fun liveTitleMatchesTheChartWindow() {
        val zone = ZoneId.of("America/Detroit")
        assertEquals("Nov 9 – 13", livePeriodLabel(Instant.parse("2026-11-11T17:00:00Z"), zone))
        assertEquals(
            "Oct 30 – Nov 3",
            periodRangeLabel(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 3))
        )
    }

    @Test
    fun aQuarterWidthDragTurnsThePageAndAShortDragDoesNot() {
        assertEquals(0, pagerDelta(dx = 40f, width = 400f, canPrev = true, canNext = true))
        assertEquals(1, pagerDelta(dx = -120f, width = 400f, canPrev = true, canNext = true))
        assertEquals(0, pagerDelta(dx = -120f, width = 400f, canPrev = true, canNext = false))
        assertEquals(-1, pagerDelta(dx = 120f, width = 400f, canPrev = true, canNext = false))
    }

    @Test
    fun liveFullscreenFollowsTheCardsThatAreOn() {
        val cards = liveFullscreenCards(
            ChartLayers(
                temperature = true,
                precipitation = false,
                buy = false,
                sell = false,
                solcast = true,
                enphase = false,
                ecobee = false
            )
        )
        assertEquals(listOf(MetaPanel.SOLAR, MetaPanel.TEMP), cards)
    }

    @Test
    fun expandAnimationIsTwoHundredMilliseconds() {
        assertEquals(200, CHART_EXPAND_MILLIS)
    }

    @Test
    fun onlyAShortUntouchedTapExpandsTheChart() {
        assertTrue(chartTapFires(consumed = false, moved = false, heldMillis = 80, longPressMillis = 400))
        assertFalse(chartTapFires(consumed = true, moved = false, heldMillis = 80, longPressMillis = 400))
        assertFalse(chartTapFires(consumed = false, moved = true, heldMillis = 80, longPressMillis = 400))
        assertFalse(chartTapFires(consumed = false, moved = false, heldMillis = 400, longPressMillis = 400))
    }

    @Test
    fun expandHintShowsOnceBeforeFullscreen() {
        assertTrue(showChartExpandHint(seen = false, expanded = false))
        assertFalse(showChartExpandHint(seen = true, expanded = false))
        assertFalse(showChartExpandHint(seen = false, expanded = true))
    }

    @Test
    fun fullscreenAndTheSelectedDayStayOnTheSameState() {
        val day = LocalDate.of(2026, 11, 11)
        val week = LocalDate.of(2026, 11, 8)
        val state = HomeUiState(
            chartExpanded = true,
            selectedDay = day,
            selectedWeek = week,
            historyWeekSelected = true
        )
        val ticked = state.copy(now = state.now.plusSeconds(30))
        assertTrue(ticked.chartExpanded)
        assertEquals(day, ticked.selectedDay)
        assertEquals(week, ticked.selectedWeek)
        assertTrue(ticked.historyWeekSelected)
    }
}
