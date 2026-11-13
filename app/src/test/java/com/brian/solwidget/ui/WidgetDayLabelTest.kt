package com.brian.solwidget.ui

import com.brian.solwidget.ui.components.WidgetDayColumn
import com.brian.solwidget.ui.components.planWidgetDayLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDayLabelTest {
    @Test
    fun shrinksUntilEveryDayColumnFits() {
        val columns = listOf(
            WidgetDayColumn(center = 100f, widthAtFullSize = 40f),
            WidgetDayColumn(center = 180f, widthAtFullSize = 80f)
        )
        val plan = planWidgetDayLabels(columns, left = 0f, right = 200f)
        assertEquals(20f, plan.textSize)
        assertEquals(listOf(0, 1), plan.visible)
    }

    @Test
    fun dropsAColumnThatStillOverflowsAtTheMinimumSize() {
        val columns = listOf(
            WidgetDayColumn(center = 80f, widthAtFullSize = 40f),
            WidgetDayColumn(center = 190f, widthAtFullSize = 80f)
        )
        val plan = planWidgetDayLabels(columns, left = 0f, right = 200f, fullSize = 40f, minSize = 18f)
        assertEquals(18f, plan.textSize)
        assertEquals(listOf(0), plan.visible)
    }
}
