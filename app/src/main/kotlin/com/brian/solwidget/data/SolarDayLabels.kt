package com.brian.solwidget.data

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Daily energy labels for the Solcast chart. A day is "whole" when the series
 * covers production from first power through last power (sunrise to sunset),
 * not midnight-to-midnight.
 */
object SolarDayLabels {
    const val PRODUCING_KW = 0.05
    const val RAMP_KW = 0.15
    val MAX_GAP: Duration = Duration.ofMinutes(45)

    fun summaries(points: List<PowerPoint>, zone: ZoneId): List<DayTotals> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.periodEnd }
        val dates = sorted.map { it.periodEnd.atZone(zone).toLocalDate() }.distinct()
        return dates.map { date -> summarizeDay(sorted, date, zone) }
    }

    fun summaryFor(
        points: List<PowerPoint>,
        date: LocalDate,
        zone: ZoneId
    ): DayTotals {
        if (points.isEmpty()) return emptyDay(date)
        val sorted = points.sortedBy { it.periodEnd }
        return summarizeDay(sorted, date, zone)
    }

    private fun emptyDay(date: LocalDate) = DayTotals(
        date = date,
        energyKwh = 0.0,
        peakKw = 0.0,
        liveEnergyKwh = 0.0,
        forecastEnergyKwh = 0.0,
        complete = false
    )

    private fun summarizeDay(
        sorted: List<PowerPoint>,
        date: LocalDate,
        zone: ZoneId
    ): DayTotals {
        val dayPoints = sorted.filter { it.periodEnd.atZone(zone).toLocalDate() == date }
        if (dayPoints.isEmpty()) return emptyDay(date)
        val producing = dayPoints.filter { it.kw > PRODUCING_KW }
        val peak = dayPoints.maxBy { it.kw }
        val base = DayTotals(
            date = date,
            energyKwh = dayPoints.sumOf { it.energyKwh },
            peakKw = peak.kw,
            liveEnergyKwh = dayPoints.filter { it.kind == SeriesKind.LIVE }.sumOf { it.energyKwh },
            forecastEnergyKwh = dayPoints.filter { it.kind == SeriesKind.FORECAST }.sumOf { it.energyKwh },
            complete = false
        )
        if (producing.isEmpty()) return base

        val firstProd = producing.first()
        val lastProd = producing.last()
        val firstIdx = sorted.indexOfFirst { it.periodEnd == firstProd.periodEnd }
        val lastIdx = sorted.indexOfFirst { it.periodEnd == lastProd.periodEnd }
        if (firstIdx < 0 || lastIdx < firstIdx) return base

        val complete = !hasInteriorGap(sorted, firstIdx, lastIdx) &&
            isBookendedStart(sorted, firstIdx, firstProd) &&
            isBookendedEnd(sorted, lastIdx, lastProd)
        return base.copy(complete = complete)
    }

    private fun hasInteriorGap(sorted: List<PowerPoint>, firstIdx: Int, lastIdx: Int): Boolean {
        for (i in firstIdx until lastIdx) {
            val gap = Duration.between(sorted[i].periodEnd, sorted[i + 1].periodEnd)
            if (gap > MAX_GAP) return true
        }
        return false
    }

    private fun isBookendedStart(
        sorted: List<PowerPoint>,
        firstIdx: Int,
        firstProd: PowerPoint
    ): Boolean {
        if (firstProd.kw <= RAMP_KW) return true
        if (firstIdx == 0) return false
        val prev = sorted[firstIdx - 1]
        val gap = Duration.between(prev.periodEnd, firstProd.periodEnd)
        return prev.kw <= PRODUCING_KW && !gap.isNegative && gap <= MAX_GAP
    }

    private fun isBookendedEnd(
        sorted: List<PowerPoint>,
        lastIdx: Int,
        lastProd: PowerPoint
    ): Boolean {
        if (lastProd.kw <= RAMP_KW) return true
        if (lastIdx >= sorted.lastIndex) return false
        val next = sorted[lastIdx + 1]
        val gap = Duration.between(lastProd.periodEnd, next.periodEnd)
        return next.kw <= PRODUCING_KW && !gap.isNegative && gap <= MAX_GAP
    }
}
