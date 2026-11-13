package com.brian.solwidget.ui

import androidx.compose.ui.unit.dp
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.ui.screens.CHART_EXPAND_MILLIS
import com.brian.solwidget.ui.screens.ChartLayerToggles
import com.brian.solwidget.ui.screens.FullscreenLayerScrimAlpha
import com.brian.solwidget.ui.screens.LayerGroup
import com.brian.solwidget.ui.screens.LayerToggle
import com.brian.solwidget.ui.screens.chartTapFires
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.ui.screens.MetaPanel
import com.brian.solwidget.ui.screens.fullscreenChartGesturesEnabled
import com.brian.solwidget.ui.screens.fullscreenLayerSheetWidth
import com.brian.solwidget.ui.screens.fullscreenPlotInset
import com.brian.solwidget.ui.screens.fullscreenSafePaddingPx
import com.brian.solwidget.ui.screens.layerToggles
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
    fun aThirtyPercentDragTurnsThePageAndAShortDragDoesNot() {
        assertEquals(0, pagerDelta(dx = 40f, width = 400f, canPrev = true, canNext = true))
        assertEquals(0, pagerDelta(dx = -100f, width = 400f, canPrev = true, canNext = true))
        assertEquals(1, pagerDelta(dx = -120f, width = 400f, canPrev = true, canNext = true))
        assertEquals(0, pagerDelta(dx = -120f, width = 400f, canPrev = true, canNext = false))
        assertEquals(-1, pagerDelta(dx = 120f, width = 400f, canPrev = true, canNext = false))
    }

    @Test
    fun liveFullscreenListsEveryOnCardAheadOfTheOpenOne() {
        val cards = liveFullscreenCards(ChartLayers())
        assertEquals(
            listOf(MetaPanel.SOLAR, MetaPanel.TEMP, MetaPanel.PRECIP, MetaPanel.RATES),
            cards
        )
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

    @Test
    fun layerSheetBlocksPagingAndTapToExit() {
        assertTrue(fullscreenChartGesturesEnabled(legendOpen = false, layersOpen = false))
        assertFalse(fullscreenChartGesturesEnabled(legendOpen = true, layersOpen = false))
        assertFalse(fullscreenChartGesturesEnabled(legendOpen = false, layersOpen = true))
        assertFalse(fullscreenChartGesturesEnabled(legendOpen = true, layersOpen = true))
    }

    @Test
    fun plotInsetIsTheButtonHeightOnlyWhenTheButtonWouldCoverTheAxis() {
        assertEquals(0.dp, fullscreenPlotInset(buttonHeight = 48.dp, chromeHeight = 48.dp, plotHeadroom = 28.dp))
        assertEquals(0.dp, fullscreenPlotInset(buttonHeight = 48.dp, chromeHeight = 32.dp, plotHeadroom = 28.dp))
        assertEquals(48.dp, fullscreenPlotInset(buttonHeight = 48.dp, chromeHeight = 0.dp, plotHeadroom = 0.dp))
        assertEquals(48.dp, fullscreenPlotInset(buttonHeight = 48.dp, chromeHeight = 20.dp, plotHeadroom = 10.dp))
    }

    @Test
    fun layerSheetStaysAtMost280AndLeavesRoomToDismiss() {
        assertEquals(240.dp, fullscreenLayerSheetWidth(400.dp))
        assertEquals(224.dp, fullscreenLayerSheetWidth(280.dp))
        assertEquals(144.dp, fullscreenLayerSheetWidth(200.dp))
        assertEquals(280.dp, fullscreenLayerSheetWidth(2000.dp, maxWidth = 400.dp))
        assertTrue(FullscreenLayerScrimAlpha in 0.20f..0.30f)
    }

    @Test
    fun safePaddingKeepsPortraitCutoutsAndLandscapeSideCutouts() {
        val portrait = fullscreenSafePaddingPx(
            cutoutStart = 0,
            cutoutTop = 80,
            cutoutEnd = 0,
            cutoutBottom = 40,
            gestureTop = 20,
            gestureBottom = 48,
            mandatoryTop = 0,
            mandatoryBottom = 0
        )
        assertEquals(0, portrait.start)
        assertEquals(80, portrait.top)
        assertEquals(48, portrait.bottom)

        val landscape = fullscreenSafePaddingPx(
            cutoutStart = 120,
            cutoutTop = 0,
            cutoutEnd = 0,
            cutoutBottom = 0,
            gestureTop = 0,
            gestureBottom = 24,
            mandatoryTop = 0,
            mandatoryBottom = 36
        )
        assertEquals(120, landscape.start)
        assertEquals(0, landscape.top)
        assertEquals(36, landscape.bottom)
    }

    @Test
    fun fullscreenSheetUsesDrawerSwitchesAndDropsOnWidgetRows() {
        assertEquals(
            listOf("Solcast", "Enphase", "Temp", "Precip", "Buy", "Sell", "Ecobee", "HVAC"),
            ChartLayerToggles.map { it.label }
        )
        assertEquals(ChartLayerToggles, layerToggles(includeWidgetRows = true))
        assertTrue(layerToggles(includeWidgetRows = false).none { it.onWidget })
        val withWidget = ChartLayerToggles + LayerToggle(
            ChartLayer.HVAC,
            "On widget",
            LayerGroup.Home,
            onWidget = true
        )
        assertEquals(
            ChartLayerToggles.map { it.label },
            layerToggles(includeWidgetRows = false, rows = withWidget).map { it.label }
        )
        assertTrue(
            layerToggles(includeWidgetRows = true, rows = withWidget).any { it.label == "On widget" }
        )
    }
}
