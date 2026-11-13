package com.brian.solwidget.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs

internal const val CHART_EXPAND_MILLIS = 200

/** A drag past this fraction of the page snaps. A slower, shorter drag springs back. */
internal const val PAGER_POSITION_THRESHOLD = 0.30f

/** Touch target for the fullscreen layers button. The header row is this tall so the control sits above the plot. */
internal val FullscreenLayersButtonSize = 48.dp

/** Fullscreen chrome above the chart canvas. Matches the button so the button cannot cover the plot. */
internal val FullscreenHeaderHeight = 48.dp

/**
 * Distance from the top of the chart canvas to the plot, before an HVAC lane.
 * The 8 kW label sits on this edge.
 */
internal val FullscreenPlotHeadroom = 28.dp

/** House-use up-triangles rise this far above the 8 kW plot edge. */
internal val HouseCapRise = 7.dp

/** Light scrim behind the fullscreen layer sheet. Within the 20–30% range. */
internal const val FullscreenLayerScrimAlpha = 0.25f

/** Narrower than the main drawer so a phone still has a strip to tap outside. Never above 280dp. */
internal val FullscreenLayerSheetMaxWidth = 240.dp

/** Minimum clear strip to the side of the sheet, used as the outside-to-dismiss target. */
internal val FullscreenLayerDismissGap = 56.dp

/** Paging and tap-to-exit stay off while the legend or the layer sheet is up. */
internal fun fullscreenChartGesturesEnabled(legendOpen: Boolean, layersOpen: Boolean): Boolean =
    !legendOpen && !layersOpen

/**
 * Extra top padding for the plot when the menu button would cover the 8 kW label,
 * the top of the series, or the house-use cap markers. The inset is the button
 * height, or zero when the header already holds the button clear of that region.
 */
internal fun fullscreenPlotInset(
    buttonHeight: Dp,
    chromeHeight: Dp,
    plotHeadroom: Dp,
    capRise: Dp = HouseCapRise
): Dp {
    if (buttonHeight <= 0.dp) return 0.dp
    val markerTop = chromeHeight.coerceAtLeast(0.dp) +
        (plotHeadroom.coerceAtLeast(0.dp) - capRise.coerceAtLeast(0.dp)).coerceAtLeast(0.dp)
    return if (buttonHeight > markerTop) buttonHeight else 0.dp
}

/** Sheet width: at most [maxWidth], and at most 280dp, leaving [dismissGap] to tap outside when it fits. */
internal fun fullscreenLayerSheetWidth(
    available: Dp,
    maxWidth: Dp = FullscreenLayerSheetMaxWidth,
    dismissGap: Dp = FullscreenLayerDismissGap
): Dp {
    if (available <= 0.dp) return 0.dp
    val cap = minOf(maxWidth, 280.dp)
    val room = available - dismissGap
    return if (room > 0.dp) minOf(cap, room) else minOf(cap, available)
}

internal data class SafePaddingPx(val start: Int, val top: Int, val end: Int, val bottom: Int)

/**
 * Cutout on the start edge (landscape camera) and the taller of cutout, gesture,
 * and mandatory insets on the top and bottom (portrait status and nav).
 */
internal fun fullscreenSafePaddingPx(
    cutoutStart: Int,
    cutoutTop: Int,
    cutoutEnd: Int,
    cutoutBottom: Int,
    gestureTop: Int,
    gestureBottom: Int,
    mandatoryTop: Int,
    mandatoryBottom: Int
): SafePaddingPx = SafePaddingPx(
    start = cutoutStart.coerceAtLeast(0),
    top = maxOf(cutoutTop, gestureTop, mandatoryTop).coerceAtLeast(0),
    end = cutoutEnd.coerceAtLeast(0),
    bottom = maxOf(cutoutBottom, gestureBottom, mandatoryBottom).coerceAtLeast(0)
)

/** A short unconsumed tap expands or restores. Swipes and long-presses do not. */
internal fun chartTapFires(
    consumed: Boolean,
    moved: Boolean,
    heldMillis: Long,
    longPressMillis: Long
): Boolean = !consumed && !moved && heldMillis >= 0 && heldMillis < longPressMillis

internal fun showChartExpandHint(seen: Boolean, expanded: Boolean): Boolean = !seen && !expanded

/** 1 moves toward the next chart, -1 toward the previous. A short drag stays put. */
internal fun pagerDelta(dx: Float, width: Float, canPrev: Boolean, canNext: Boolean): Int {
    if (width <= 0f) return 0
    val past = abs(dx) / width >= PAGER_POSITION_THRESHOLD - 0.005f
    return when {
        !past -> 0
        dx < 0f && canNext -> 1
        dx > 0f && canPrev -> -1
        else -> 0
    }
}

internal fun Modifier.chartTap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    val slop = viewConfiguration.touchSlop
    val longPress = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = false)
        if (down.isConsumed) return@awaitEachGesture
        val start = down.uptimeMillis
        var moved = false
        var consumed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (change.isConsumed) consumed = true
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            if (abs(dx) > slop || abs(dy) > slop) moved = true
            if (!change.pressed) {
                if (chartTapFires(consumed, moved, change.uptimeMillis - start, longPress)) {
                    change.consume()
                    onTap()
                }
                return@awaitEachGesture
            }
        }
    }
}

/**
 * A tap on the fullscreen chart exits. Listens on the initial pass and does not
 * consume the pointer, so a horizontal drag still reaches the pager.
 */
internal fun Modifier.fullscreenChartTap(enabled: Boolean, onTap: () -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(onTap) {
        val slop = viewConfiguration.touchSlop
        val longPress = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = down.uptimeMillis
            val origin = down.position
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) {
                    if (!moved && change.uptimeMillis - start < longPress) onTap()
                    return@awaitEachGesture
                }
                val dx = change.position.x - origin.x
                val dy = change.position.y - origin.y
                if (abs(dx) > slop || abs(dy) > slop) moved = true
            }
        }
    }
}

/** Grows the fullscreen chart out of the card it came from. At rest it is the identity. */
internal fun Modifier.scaleFromCard(progress: Float, card: Rect?, full: Rect?): Modifier {
    if (progress >= 0.999f || card == null || full == null || full.width <= 1f || full.height <= 1f) {
        return this
    }
    return graphicsLayer {
        val sx = (card.width / full.width).coerceIn(0.05f, 1f)
        val sy = (card.height / full.height).coerceIn(0.05f, 1f)
        scaleX = lerp(sx, 1f, progress)
        scaleY = lerp(sy, 1f, progress)
        translationX = lerp(card.center.x - full.center.x, 0f, progress)
        translationY = lerp(card.center.y - full.center.y, 0f, progress)
        transformOrigin = TransformOrigin.Center
    }
}
