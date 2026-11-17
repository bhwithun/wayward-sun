package com.brian.solwidget.ui.screens

import kotlin.math.max

/**
 * Column widths for the rates table, in dp, used as row weights.
 * [mid] is "Mid-peak" when that word fits the mid column on one line, otherwise "Mid".
 * The strings are single tokens so a too-narrow column cannot break them mid-word.
 */
internal data class RateHeaderPlan(
    val labelWeight: Float,
    val offWeight: Float,
    val midWeight: Float,
    val peakWeight: Float,
    val mid: String
) {
    val rowInnerDp: Float get() = labelWeight + offWeight + midWeight + peakWeight
}

/** Drawer sheet width. The details block insets the table by 16dp on each side. */
internal const val RATE_DRAWER_WIDTH_DP = 280f
internal const val RATE_DETAILS_PADDING_DP = 32f
internal const val RATE_ROW_PADDING_DP = 16f
internal const val RATE_ROW_GAP_DP = 6f

/** Width of the rates table inside the drawer on a phone of [screenWidthDp]. */
internal fun rateTableWidthDp(screenWidthDp: Float): Float {
    val drawer = minOf(RATE_DRAWER_WIDTH_DP, screenWidthDp)
    return (drawer - RATE_DETAILS_PADDING_DP).coerceAtLeast(0f)
}

/**
 * Fits Off-peak, Mid-peak, and Peak on one line. The label column keeps whatever
 * is left, at least the widest single word in the row names. If Mid-peak cannot
 * fit beside those, the header is "Mid".
 */
internal fun rateHeaderPlan(tableWidthDp: Float, textWidthDp: (String) -> Float): RateHeaderPlan {
    val gaps = RATE_ROW_GAP_DP * 3f
    val inner = (tableWidthDp - RATE_ROW_PADDING_DP - gaps).coerceAtLeast(0f)
    fun needed(text: String) = textWidthDp(text) + 1f
    val off = needed("Off-peak")
    val peak = needed("Peak")
    val midFull = needed("Mid-peak")
    val midShort = needed("Mid")
    val labelMin = max(needed("Import"), max(needed("Export"), needed("effective")))
    val midText = if (off + midFull + peak + labelMin <= inner) "Mid-peak" else "Mid"
    val mid = if (midText == "Mid-peak") midFull else midShort
    val label = (inner - off - mid - peak).coerceAtLeast(0f)
    return RateHeaderPlan(label, off, mid, peak, midText)
}
