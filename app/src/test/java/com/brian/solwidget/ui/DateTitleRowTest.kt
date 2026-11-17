package com.brian.solwidget.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.solwidget.ui.screens.DateTitleRow
import com.brian.solwidget.ui.screens.LegendButton
import com.brian.solwidget.ui.screens.periodRangeLabel
import com.brian.solwidget.ui.theme.SolWidgetTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h640dp-160dpi")
class DateTitleRowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun todayIsCenteredOnTheFullRowAndClearsALongWeekTitle() {
        val week = periodRangeLabel(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 5))
        assertEquals("Oct 30 – Nov 5", week)
        rule.setContent {
            SolWidgetTheme {
                DateTitleRow(
                    title = week,
                    titleStyle = TextStyle(fontSize = 16.sp),
                    showToday = true,
                    onToday = {},
                    modifier = Modifier.width(328.dp),
                    trailing = {
                        LegendButton(open = false, onClick = {})
                    }
                )
            }
        }
        rule.waitForIdle()
        assertClearOfToday()
    }

    @Test
    fun fullscreenRowKeepsTheSameHeightWithOrWithoutToday() {
        val week = periodRangeLabel(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 5))
        var shown by mutableStateOf(true)
        rule.setContent {
            SolWidgetTheme {
                DateTitleRow(
                    title = week,
                    titleStyle = TextStyle(fontSize = 15.sp),
                    showToday = shown,
                    onToday = {},
                    leading = { Spacer(Modifier.width(48.dp)) },
                    leadingWidth = 48.dp,
                    titleStart = 4.dp,
                    modifier = Modifier.width(360.dp),
                    trailing = {
                        LegendButton(open = false, onClick = {}, compact = true)
                    }
                )
            }
        }
        rule.waitForIdle()
        assertClearOfToday()
        val withToday = rule.onNodeWithTag("date-row").fetchSemanticsNode().boundsInRoot.height
        shown = false
        rule.waitForIdle()
        val without = rule.onNodeWithTag("date-row").fetchSemanticsNode().boundsInRoot.height
        assertEquals(withToday, without, 1f)
        val expected = with(rule.density) { 48.dp.toPx() }
        assertEquals(expected, withToday, 1f)
    }

    private fun assertClearOfToday() {
        val row = rule.onNodeWithTag("date-row").fetchSemanticsNode().boundsInRoot
        val today = rule.onNodeWithTag("today-button").fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithTag("date-title").fetchSemanticsNode().boundsInRoot
        val center = row.left + row.width / 2f
        assertEquals(center, today.left + today.width / 2f, 1f)
        assertTrue(
            "Title $title overlaps Today $today",
            title.right <= today.left + 1f
        )
        val rowHeight = with(rule.density) { 48.dp.toPx() }
        assertEquals(rowHeight, row.height, 1f)
        assertEquals(rowHeight, today.height, 1f)
    }
}
