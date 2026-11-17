package com.brian.solwidget.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.junit4.createComposeRule
import com.brian.solwidget.ui.screens.rateHeaderPlan
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
    fun aNarrowColumnUsesMidInsteadOfBreakingTheWord() {
        val plan = rateHeaderPlan(tableWidthDp = 80f) { text -> text.length * 8f }
        assertEquals("Mid", plan.mid)
        assertTrue(plan.midWeight + 0.1f >= 3f * 8f)
    }

    @Test
    fun midPeakStaysOneTokenAt360And411() {
        val found = AtomicReference<List<String>>(emptyList())
        rule.setContent {
            SolWidgetTheme {
                val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                SideEffect {
                    fun width(text: String) = with(density) {
                        measurer.measure(AnnotatedString(text), style = style).size.width.toDp().value
                    }
                    found.set(listOf(360f, 411f).map { screen ->
                        val table = rateTableWidthDp(screen)
                        val plan = rateHeaderPlan(table, ::width)
                        check(plan.mid == "Mid-peak" || plan.mid == "Mid") {
                            "Header ${plan.mid} is not a whole token"
                        }
                        check(!plan.mid.contains(' ') && plan.mid != "Mid-pea")
                        check(width(plan.mid) <= plan.midWeight + 0.5f) {
                            "${plan.mid} is ${width(plan.mid)}dp in a ${plan.midWeight}dp column at ${screen}dp"
                        }
                        check(width("Off-peak") <= plan.offWeight + 0.5f)
                        check(width("Peak") <= plan.peakWeight + 0.5f)
                        "${screen.toInt()}:${plan.mid}"
                    })
                }
            }
        }
        rule.waitForIdle()
        assertEquals(listOf("360:Mid-peak", "411:Mid-peak"), found.get())
    }
}
