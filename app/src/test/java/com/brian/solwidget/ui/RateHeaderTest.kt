package com.brian.solwidget.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.junit4.createComposeRule
import com.brian.solwidget.ui.screens.RateTablePlan
import com.brian.solwidget.ui.screens.rateTablePlan
import com.brian.solwidget.ui.screens.rateTableWidthDp
import com.brian.solwidget.ui.theme.SolWidgetTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RateHeaderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aTinyTableDropsToTheShortestWholeTokens() {
        val plan = rateTablePlan(tableWidthDp = 40f, headerWidth = { it.length * 8f }, bodyWidth = { it.length * 8f })
        assertEquals("Mid", plan.midHeader)
        assertEquals("¢/kWh", plan.unitNote)
        assertTrue(plan.labels.all { !it.contains(' ') || it.length < 8 })
    }

    @Test
    fun everyRateCellFitsOnOneLineAt360And411() {
        val found = AtomicReference<List<RateTablePlan>>(emptyList())
        rule.setContent {
            SolWidgetTheme {
                val header = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                val body = MaterialTheme.typography.bodySmall
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                SideEffect {
                    fun width(text: String, style: TextStyle) = with(density) {
                        measurer.measure(AnnotatedString(text), style = style).size.width.toDp().value
                    }
                    found.set(listOf(360f, 411f).map { screen ->
                        val table = rateTableWidthDp(screen)
                        val plan = rateTablePlan(
                            table,
                            headerWidth = { width(it, header) },
                            bodyWidth = { width(it, body) }
                        )
                        check(plan.fits(table)) { "Rates table does not fit a ${screen.toInt()}dp phone" }
                        check(plan.midHeader == "Mid-peak" || plan.midHeader == "Mid")
                        fun fits(text: String, column: Float, style: TextStyle, name: String) {
                            val measured = width(text, style)
                            check(measured <= column + 0.5f) {
                                "$name '$text' is ${measured}dp in a ${column}dp column at ${screen.toInt()}dp"
                            }
                        }
                        plan.labels.forEach { fits(it, plan.labelWidth, body, "label") }
                        fits(plan.offHeader, plan.offWidth, header, "off header")
                        fits(plan.midHeader, plan.midWidth, header, "mid header")
                        fits(plan.peakHeader, plan.peakWidth, header, "peak header")
                        plan.offValues.forEach { fits(it, plan.offWidth, body, "off") }
                        plan.midValues.forEach { fits(it, plan.midWidth, body, "mid") }
                        plan.peakValues.forEach { fits(it, plan.peakWidth, body, "peak") }
                        plan
                    })
                }
            }
        }
        rule.waitForIdle()
        assertEquals(2, found.get().size)
    }
}
