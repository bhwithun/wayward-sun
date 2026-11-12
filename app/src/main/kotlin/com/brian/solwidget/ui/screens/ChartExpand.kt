package com.brian.solwidget.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.lerp
import kotlin.math.abs

internal const val CHART_EXPAND_MILLIS = 200

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
    val threshold = width * 0.25f
    return when {
        dx <= -threshold && canNext -> 1
        dx >= threshold && canPrev -> -1
        else -> 0
    }
}

internal fun resistedDrag(dx: Float, canPrev: Boolean, canNext: Boolean): Float = when {
    dx > 0f && !canPrev -> dx * 0.2f
    dx < 0f && !canNext -> dx * 0.2f
    else -> dx
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
