package com.brian.solwidget.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height budget for the history calendar in the bottom half of the portrait screen.
 *
 * Chrome is the vertical padding, the month header, the weekday row, and one gap
 * after the header, after the weekdays, and between week rows. Day cells share
 * whatever height is left. There is no 40dp floor: a floor would push a 6-week
 * month past the bottom of a 360×640dp phone.
 *
 * A 360×640dp phone (status bar 24dp, 3-button navigation 48dp, app bar 64dp)
 * leaves 252dp for the calendar half. A 411×914dp phone, the size used by the
 * UI tests, leaves 389dp.
 */
internal object CalendarFit {
    val appBar = 64.dp
    val statusBar = 24.dp
    val navigationBar = 48.dp
    val verticalPadding = 4.dp
    val header = 32.dp
    val weekday = 14.dp
    val gap = 2.dp
    val comfortableCell = 40.dp

    /** Full-screen dp height of a small phone and of the Pixel-class test phone. */
    val smallPhoneHeight = 640.dp
    val typicalPhoneHeight = 914.dp

    fun belowAppBar(
        screenHeight: Dp,
        statusBar: Dp = this.statusBar,
        navigationBar: Dp = this.navigationBar,
        appBar: Dp = this.appBar
    ): Dp = screenHeight - statusBar - navigationBar - appBar

    fun calendarHalf(
        screenHeight: Dp,
        statusBar: Dp = this.statusBar,
        navigationBar: Dp = this.navigationBar,
        appBar: Dp = this.appBar
    ): Dp = belowAppBar(screenHeight, statusBar, navigationBar, appBar) / 2

    /** Gaps: after the month header, after the weekday labels, and between week rows. */
    fun gapCount(weekCount: Int): Int = weekCount + 1

    fun chromeHeight(weekCount: Int): Dp =
        verticalPadding + header + weekday + gap * gapCount(weekCount)

    fun dayCellHeight(available: Dp, weekCount: Int): Dp {
        if (weekCount <= 0) return 0.dp
        val remaining = available - chromeHeight(weekCount)
        if (remaining.value <= 0f) return 0.dp
        return remaining / weekCount
    }
}
