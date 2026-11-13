package com.brian.solwidget.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseEnergyTest {
    private val zone: ZoneId = ZoneId.of("America/Detroit")
    private val from: Instant = Instant.parse("2026-11-11T05:00:00Z")
    private val to: Instant = Instant.parse("2026-11-12T05:00:00Z")
    private val noon: Instant = Instant.parse("2026-11-11T17:00:00Z")

    @Test
    fun wattHoursBecomeAverageKwForTheHalfHour() {
        assertEquals(1.0, homeKwFromWh(500.0), 0.0)
        assertEquals(5.0, homeKwFromWh(2500.0), 0.0)
        assertEquals(18.4, kwhFromWh(18400.0), 0.001)
    }

    @Test
    fun adjacentBucketsStepAndStopAtTheLastReading() {
        val first = from.plusSeconds(12 * 60 * 60)
        val second = first.plus(HOUSE_BUCKET)
        val runs = houseStepRuns(
            listOf(
                HouseSample(second, 3.0),
                HouseSample(first, 2.0)
            )
        )
        assertEquals(1, runs.size)
        assertEquals(
            listOf(first.minus(HOUSE_BUCKET), first, first, second),
            runs[0].map { it.at }
        )
        assertEquals(listOf(2.0, 2.0, 3.0, 3.0), runs[0].map { it.kw })
        assertEquals(second, runs[0].maxOf { it.at })
        assertFalse(runs[0].any { it.at.isAfter(second) })
    }

    @Test
    fun aGapBreaksTheLineAndASingleReadingDoesNotExtend() {
        val first = from.plusSeconds(12 * 60 * 60)
        val afterGap = first.plusSeconds(90 * 60)
        val runs = houseStepRuns(
            listOf(
                HouseSample(first, 2.0),
                HouseSample(afterGap, 4.0)
            )
        )
        assertEquals(2, runs.size)
        assertEquals(listOf(first.minus(HOUSE_BUCKET), first), runs[0].map { it.at })
        assertEquals(2.0, runs[0].last().kw, 0.0)
        assertEquals(afterGap, runs[1].last().at)
        assertEquals(afterGap.minus(HOUSE_BUCKET), runs[1].first().at)
        assertTrue(runs[0].none { vertex -> runs[1].any { it.at == vertex.at && it.kw == vertex.kw && vertex.at == afterGap.minus(HOUSE_BUCKET) } })
        assertTrue(runs.flatten().none { it.at.isAfter(afterGap) })
    }

    @Test
    fun missingHalfHoursKeepTheDailyTotalAndHideCoverage() {
        val summary = houseSummary(
            emptyList(),
            listOf(day(hasHalfHour = false)),
            from,
            to,
            zone,
            noon,
            solarVisible = true
        )
        assertEquals(18.4, summary?.homeKwh ?: 0.0, 0.001)
        assertNull(summary?.coveredPercent)
        assertEquals(true, summary?.hourlyDetailUnavailable)
        assertEquals("House 18.4 kWh", houseSummaryLabel(summary!!))
        assertTrue(houseStepRuns(emptyList()).isEmpty())
        assertTrue(houseChipVisible(emptyList(), listOf(day(hasHalfHour = false)), from, to, zone))
    }

    @Test
    fun solarCoveredUsesFromSolarOverHome() {
        val summary = houseSummary(
            emptyList(),
            listOf(day(hasHalfHour = true)),
            from,
            to,
            zone,
            noon,
            solarVisible = true
        )
        assertEquals(62, summary?.coveredPercent)
        assertEquals("House 18.4 kWh · Solar covered 62%", houseSummaryLabel(summary!!))
        assertFalse(summary.hourlyDetailUnavailable)
    }

    @Test
    fun coverageRequiresSolarAndHalfHoursOnEveryStartedDay() {
        val hidden = houseSummary(emptyList(), listOf(day(true)), from, to, zone, noon, solarVisible = false)
        assertNull(hidden?.coveredPercent)

        val days = listOf(
            day(true, home = 10.0, solar = 6.0),
            HouseDay(LocalDate.of(2026, 11, 12), 8.0, 4.0, false)
        )
        val later = Instant.parse("2026-11-12T17:00:00Z")
        val throughTomorrow = Instant.parse("2026-11-13T05:00:00Z")
        assertNull(
            houseSummary(emptyList(), days, from, throughTomorrow, zone, later, solarVisible = true)?.coveredPercent
        )

        val missingYesterday = houseSummary(
            emptyList(),
            listOf(day(true)),
            Instant.parse("2026-11-10T05:00:00Z"),
            to,
            zone,
            noon,
            solarVisible = true
        )
        assertNull(missingYesterday?.coveredPercent)
    }

    @Test
    fun futureDaysDoNotBlockCoverage() {
        val summary = houseSummary(
            emptyList(),
            listOf(day(true)),
            from,
            Instant.parse("2026-11-14T05:00:00Z"),
            zone,
            noon,
            solarVisible = true
        )
        assertEquals(62, summary?.coveredPercent)
    }

    @Test
    fun theChipHidesWhenTheRangeHasNoHouseData() {
        val sample = listOf(HouseSample(noon, 2.0))
        assertFalse(houseChipVisible(emptyList(), emptyList(), from, to, zone))
        assertFalse(
            houseChipVisible(
                sample,
                emptyList(),
                Instant.parse("2026-11-12T05:00:00Z"),
                Instant.parse("2026-11-13T05:00:00Z"),
                zone
            )
        )
        assertTrue(houseChipVisible(sample, emptyList(), from, to, zone))
    }

    @Test
    fun storedLivePastKeepsHouseSamples() {
        val stored = StoredLivePast(
            key = "range",
            savedAt = noon,
            points = listOf(com.brian.solwidget.data.PowerPoint(noon, 1.0, 0.5, SeriesKind.LIVE)),
            house = listOf(HouseSample(noon, 2.5)),
            houseDays = listOf(day(true))
        )
        val decoded = LivePastStore.decode(LivePastStore.encode(stored))
        assertEquals(2.5, decoded?.house?.single()?.homeKw ?: 0.0, 0.0)
        assertEquals(noon, decoded?.house?.single()?.periodEnd)
        assertEquals(LocalDate.of(2026, 11, 11), decoded?.houseDays?.single()?.day)
        assertEquals(1, decoded?.points?.size)
    }

    private fun day(hasHalfHour: Boolean, home: Double = 18.4, solar: Double = 11.408) = HouseDay(
        day = LocalDate.of(2026, 11, 11),
        homeKwh = home,
        fromSolarKwh = solar,
        hasHalfHour = hasHalfHour
    )
}
