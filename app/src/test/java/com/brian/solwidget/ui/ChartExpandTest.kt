package com.brian.solwidget.ui

import com.brian.solwidget.ui.screens.CHART_EXPAND_MILLIS
import com.brian.solwidget.ui.screens.chartTapFires
import com.brian.solwidget.ui.screens.showChartExpandHint
import com.brian.solwidget.viewmodel.HomeUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartExpandTest {
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
