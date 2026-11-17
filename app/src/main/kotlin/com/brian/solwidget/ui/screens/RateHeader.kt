package com.brian.solwidget.ui.screens

import com.brian.solwidget.data.DteTou
import com.brian.solwidget.util.Formatters
import kotlin.math.max

/**
 * One rates table that fits the drawer. Column widths are the widest cell in
 * that column, so a header cannot shrink the numbers and a number cannot
 * shrink a header. Every string is drawn on one line.
 */
internal data class RateTablePlan(
    val labelWidth: Float,
    val offWidth: Float,
    val midWidth: Float,
    val peakWidth: Float,
    val gap: Float,
    val labels: List<String>,
    val offHeader: String,
    val midHeader: String,
    val peakHeader: String,
    val offValues: List<String>,
    val midValues: List<String>,
    val peakValues: List<String>,
    /** Shown above the table when the cent sign is not on each value. */
    val unitNote: String?
) {
    fun fits(tableWidthDp: Float): Boolean {
        val used = labelWidth + offWidth + midWidth + peakWidth + gap * 3f + RATE_ROW_PADDING_DP
        return used <= tableWidthDp + 0.5f
    }
}

/** Drawer sheet width. The details block insets the table by 16dp on each side. */
internal const val RATE_DRAWER_WIDTH_DP = 280f
internal const val RATE_DETAILS_PADDING_DP = 32f
internal const val RATE_ROW_PADDING_DP = 16f

/** Width of the rates table inside the drawer on a phone of [screenWidthDp]. */
internal fun rateTableWidthDp(screenWidthDp: Float): Float {
    val drawer = minOf(RATE_DRAWER_WIDTH_DP, screenWidthDp)
    return (drawer - RATE_DETAILS_PADDING_DP).coerceAtLeast(0f)
}

/**
 * Picks the fullest labels that still fit [tableWidthDp]. Headers and values
 * share a column, and no column is narrower than its text.
 */
internal fun rateTablePlan(
    tableWidthDp: Float,
    headerWidth: (String) -> Float,
    bodyWidth: (String) -> Float
): RateTablePlan {
    val withCent = rateRows(withCent = true)
    val plain = rateRows(withCent = false)
    val options = listOf(
        RateOption(FULL_LABELS, "Mid-peak", withCent, unitNote = null),
        RateOption(FULL_LABELS, "Mid", withCent, unitNote = null),
        RateOption(SHORT_LABELS, "Mid-peak", withCent, unitNote = null),
        RateOption(SHORT_LABELS, "Mid", withCent, unitNote = null),
        RateOption(SHORT_LABELS, "Mid", plain, unitNote = "¢/kWh"),
        RateOption(SHORTER_LABELS, "Mid", plain, unitNote = "¢/kWh")
    )
    return options
        .map { it.plan(headerWidth, bodyWidth) }
        .firstOrNull { it.fits(tableWidthDp) }
        ?: options.last().plan(headerWidth, bodyWidth)
}

private val FULL_LABELS = listOf("Import base", "Import effective", "Export (R18)", "Export + PSCR")
private val SHORT_LABELS = listOf("Base", "Effective", "R18", "PSCR")
private val SHORTER_LABELS = listOf("Base", "Eff.", "R18", "PSCR")

private data class RateOption(
    val labels: List<String>,
    val midHeader: String,
    val rows: List<Triple<String, String, String>>,
    val unitNote: String?
) {
    fun plan(headerWidth: (String) -> Float, bodyWidth: (String) -> Float): RateTablePlan {
        val gap = 4f
        fun cell(header: String, values: List<String>): Float {
            val headerDp = headerWidth(header) + 1f
            val valueDp = values.maxOf { bodyWidth(it) + 1f }
            return max(headerDp, valueDp)
        }
        val offValues = rows.map { it.first }
        val midValues = rows.map { it.second }
        val peakValues = rows.map { it.third }
        return RateTablePlan(
            labelWidth = labels.maxOf { bodyWidth(it) + 1f },
            offWidth = cell("Off-peak", offValues),
            midWidth = cell(midHeader, midValues),
            peakWidth = cell("Peak", peakValues),
            gap = gap,
            labels = labels,
            offHeader = "Off-peak",
            midHeader = midHeader,
            peakHeader = "Peak",
            offValues = offValues,
            midValues = midValues,
            peakValues = peakValues,
            unitNote = unitNote
        )
    }
}

private fun rateRows(withCent: Boolean): List<Triple<String, String, String>> {
    fun text(value: Double) = if (withCent) Formatters.cents(value) else String.format(java.util.Locale.US, "%.2f", value)
    return listOf(
        Triple(text(DteTou.BASE_OFF_PEAK_CENTS), text(DteTou.BASE_MID_PEAK_CENTS), text(DteTou.BASE_PEAK_CENTS)),
        Triple(text(DteTou.OFF_PEAK_CENTS), text(DteTou.MID_PEAK_CENTS), text(DteTou.PEAK_CENTS)),
        Triple(text(DteTou.OUTFLOW_OFF_PEAK_CENTS), text(DteTou.OUTFLOW_MID_PEAK_CENTS), text(DteTou.OUTFLOW_PEAK_CENTS)),
        Triple(
            text(DteTou.OUTFLOW_OFF_PEAK_WITH_PSCR_CENTS),
            text(DteTou.OUTFLOW_MID_PEAK_WITH_PSCR_CENTS),
            text(DteTou.OUTFLOW_PEAK_WITH_PSCR_CENTS)
        )
    )
}
