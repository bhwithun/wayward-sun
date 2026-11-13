package com.brian.solwidget.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.brian.solwidget.data.AppStorage
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.data.chartLayersFrom
import com.brian.solwidget.ui.screens.LayerDrawerContent
import com.brian.solwidget.ui.screens.LegendCard
import com.brian.solwidget.ui.screens.LegendModel
import com.brian.solwidget.ui.screens.legendGroups
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.ui.theme.SolWidgetTheme
import com.brian.solwidget.widget.WidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HouseLayerTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun houseChipDefaultsOffAndPersistsWhenSet() {
        assertFalse(ChartLayers().house)
        assertFalse(ChartLayers().isEnabled(ChartLayer.HOUSE))
        assertFalse(chartLayersFrom(emptyPreferences()).house)
        val on = mutablePreferencesOf()
        on[AppStorage.Keys.SHOW_HOUSE] = true
        assertTrue(chartLayersFrom(on).house)
        val off = mutablePreferencesOf()
        off[AppStorage.Keys.SHOW_HOUSE] = false
        assertFalse(chartLayersFrom(off).house)
    }

    @Test
    fun widgetToggleIsSeparateFromTheChartChip() {
        assertFalse(WidgetUpdater.layersFrom(emptyPreferences(), ChartLayers(house = true)).house)
        val glance = mutablePreferencesOf()
        glance[WidgetUpdater.SHOW_HOUSE] = true
        assertTrue(WidgetUpdater.layersFrom(glance, ChartLayers(house = false)).house)
        assertTrue(WidgetUpdater.layersFrom(emptyPreferences(), ChartLayers(), widgetHouse = true).house)

        val written = mutablePreferencesOf()
        written[WidgetUpdater.SHOW_HOUSE] = true
        WidgetUpdater.writeLayers(written, ChartLayers(house = true, solcast = false), widgetHouse = false)
        assertEquals(false, written[WidgetUpdater.SHOW_HOUSE])
        assertEquals(false, written[WidgetUpdater.SHOW_SOLAR])
    }

    @Test
    fun legendRowNamesHouseUseOnlyWhenThatLayerIsShown() {
        val shown = legendGroups(
            ChartLayers(house = true),
            showTooWarm = false,
            showTooCool = false,
            showHeatPump = false,
            showGasAux = false,
            showCoolRun = false,
            showHouse = true
        ).flatten()
        val house = shown.single { it.name == "House use" }
        assertEquals(SolColors.House, house.color)
        val hidden = legendGroups(
            ChartLayers(house = true),
            false,
            false,
            false,
            false,
            false,
            showHouse = false
        ).flatten()
        assertTrue(hidden.none { it.name == "House use" })
    }

    @Test
    fun drawerHidesTheChipUntilTheRangeHasHouseData() {
        rule.setContent {
            SolWidgetTheme {
                LayerDrawerContent(
                    layers = ChartLayers(house = true),
                    versionName = "2.9.0",
                    checkingUpdates = false,
                    onToggle = { _, _ -> },
                    onReset = {},
                    onClose = {},
                    onWebApp = {},
                    onCheckUpdates = {},
                    onSettings = {},
                    showHouse = false
                )
            }
        }
        rule.onNodeWithText("House use").assertDoesNotExist()
        rule.onNodeWithText("On widget").assertDoesNotExist()
    }

    @Test
    fun drawerAndLegendShowHouseUseWhenTheChipIsAvailable() {
        rule.setContent {
            SolWidgetTheme {
                Box(Modifier.fillMaxSize()) {
                    LayerDrawerContent(
                        layers = ChartLayers(house = true),
                        versionName = "2.9.0",
                        checkingUpdates = false,
                        onToggle = { _, _ -> },
                        onReset = {},
                        onClose = {},
                        onWebApp = {},
                        onCheckUpdates = {},
                        onSettings = {},
                        showHouse = true,
                        widgetHouse = false
                    )
                }
            }
        }
        rule.onNodeWithText("House use", substring = false).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("On widget", substring = false).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun houseLabelTogglesTheRowAndTheWidgetRowStaysDisabledUntilThen() {
        var houseOn by mutableStateOf(false)
        var widgetOn by mutableStateOf(true)
        rule.setContent {
            SolWidgetTheme {
                Box(Modifier.fillMaxSize()) {
                    LayerDrawerContent(
                        layers = ChartLayers(house = houseOn),
                        versionName = "2.9.0",
                        checkingUpdates = false,
                        onToggle = { layer, visible ->
                            if (layer == ChartLayer.HOUSE) houseOn = visible
                        },
                        onReset = {},
                        onClose = {},
                        onWebApp = {},
                        onCheckUpdates = {},
                        onSettings = {},
                        showHouse = true,
                        widgetHouse = widgetOn,
                        onWidgetHouse = { widgetOn = it }
                    )
                }
            }
        }
        val widgetRow = hasText("On widget") and hasClickAction()
        rule.onNode(widgetRow).performScrollTo().assertIsNotEnabled()
        assertTrue(widgetOn)
        rule.onNode(hasText("House use") and hasClickAction()).performScrollTo().performClick()
        assertTrue(houseOn)
        rule.onNode(widgetRow).performScrollTo().assertIsEnabled().performClick()
        assertFalse(widgetOn)
    }

    @Test
    fun legendCardShowsTheHouseUseRow() {
        rule.setContent {
            SolWidgetTheme {
                LegendCard(LegendModel(layers = ChartLayers(house = true), showHouse = true))
            }
        }
        rule.onNodeWithText("House use", substring = false).assertIsDisplayed()
    }
}
