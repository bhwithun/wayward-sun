package com.brian.solwidget.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height budget for the history calendar in the bottom half of the screen.
 * The chart and the calendar stay an even split on every size. The calendar
 * does not scroll.
 *
 * Room to spare uses a 32dp month header, 2dp row gaps, and a 14sp day number.
 * When that would make a day cell shorter than [tightenBelow], the header
 * drops to [headerTight] and the gaps drop to [gapTight] before the day
 * number changes. The day number then goes to [dayMinSp] and no smaller.
 * Cells stop at [cellFloor] while the half can hold them.
 *
 * If the half is still shorter than a 26dp header, a 10dp weekday row, and
 * [cellFloor] for every week, the floor is dropped. Padding and gaps go to
 * zero, the weekday row is 10dp, and the weeks share what remains, even when
 * that is under 30dp. The 50/50 split stays, and the calendar still does not
 * scroll. [CalendarMetrics.holdsFloor] is false in that case.
 *
 * A 360×640dp phone (status bar 24dp, 3-button navigation 48dp, app bar 64dp)
 * leaves 252dp for the calendar half. A 411×914dp phone leaves 389dp.
 */
internal data class CalendarMetrics(
    val cell: Dp,
    val header: Dp,
    val gap: Dp,
    val weekday: Dp,
    val verticalPadding: Dp,
    val daySp: Float,
    val holdsFloor: Boolean
)

internal object CalendarFit {
    val appBar = 64.dp
    val statusBar = 24.dp
    val navigationBar = 48.dp

    val headerRoomy = 32.dp
    val headerTight = 26.dp
    val gapRoomy = 2.dp
    val gapTight = 1.dp
    val weekdayRoomy = 14.dp
    val weekdayCollapsed = 10.dp
    val paddingRoomy = 4.dp
    val tightenBelow = 36.dp
    val cellFloor = 30.dp
    val dayRoomySp = 14f
    val dayMinSp = 13f
    val preferredCell = 40.dp

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

    fun chromeHeight(weekCount: Int, header: Dp, gap: Dp, weekday: Dp, verticalPadding: Dp): Dp =
        verticalPadding + header + weekday + gap * gapCount(weekCount)

    fun layout(available: Dp, weekCount: Int): CalendarMetrics {
        if (weekCount <= 0) {
            return CalendarMetrics(
                cell = 0.dp,
                header = headerRoomy,
                gap = gapRoomy,
                weekday = weekdayRoomy,
                verticalPadding = paddingRoomy,
                daySp = dayRoomySp,
                holdsFloor = true
            )
        }
        val roomy = cellHeight(available, weekCount, headerRoomy, gapRoomy, weekdayRoomy, paddingRoomy)
        if (roomy >= tightenBelow) {
            return metrics(roomy, headerRoomy, gapRoomy, weekdayRoomy, paddingRoomy, dayRoomySp, holdsFloor = true)
        }
        val tight = cellHeight(available, weekCount, headerTight, gapTight, weekdayRoomy, paddingRoomy)
        if (tight >= cellFloor) {
            val day = if (tight < tightenBelow) dayMinSp else dayRoomySp
            return metrics(tight, headerTight, gapTight, weekdayRoomy, paddingRoomy, day, holdsFloor = true)
        }
        val collapsed = cellHeight(available, weekCount, headerTight, 0.dp, weekdayCollapsed, 0.dp)
        if (collapsed >= cellFloor) {
            return metrics(collapsed, headerTight, 0.dp, weekdayCollapsed, 0.dp, dayMinSp, holdsFloor = true)
        }
        return metrics(
            collapsed.coerceAtLeast(0.dp),
            headerTight,
            0.dp,
            weekdayCollapsed,
            0.dp,
            dayMinSp,
            holdsFloor = false
        )
    }

    /** Wrap-content calendar used when the parent does not bound the height. */
    fun preferred(weekCount: Int): CalendarMetrics = CalendarMetrics(
        cell = preferredCell,
        header = headerRoomy,
        gap = gapRoomy,
        weekday = weekdayRoomy,
        verticalPadding = paddingRoomy,
        daySp = dayRoomySp,
        holdsFloor = true
    )

    fun dayCellHeight(available: Dp, weekCount: Int): Dp = layout(available, weekCount).cell

    private fun cellHeight(
        available: Dp,
        weekCount: Int,
        header: Dp,
        gap: Dp,
        weekday: Dp,
        verticalPadding: Dp
    ): Dp {
        val remaining = available - chromeHeight(weekCount, header, gap, weekday, verticalPadding)
        if (remaining.value <= 0f) return 0.dp
        return remaining / weekCount
    }

    private fun metrics(
        cell: Dp,
        header: Dp,
        gap: Dp,
        weekday: Dp,
        verticalPadding: Dp,
        daySp: Float,
        holdsFloor: Boolean
    ): CalendarMetrics = CalendarMetrics(
        cell = cell,
        header = header,
        gap = gap,
        weekday = weekday,
        verticalPadding = verticalPadding,
        daySp = daySp.coerceAtLeast(dayMinSp),
        holdsFloor = holdsFloor
    )
}
