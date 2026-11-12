package com.brian.solwidget.data

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcobeeTest {
    private val t0: Instant = Instant.parse("2026-01-15T15:00:00Z")

    @Test
    fun hvacSummaryPricesAuxGasAndKeepsHeatPumpSeparate() {
        val cold = sample(t0, 65.0, 75.0, 66.0).copy(heatPumpSec = 300, auxHeatSec = 180, coolSec = 0, fanSec = 300)
        val mild = sample(t0.plusSeconds(300), 68.0, 75.0, 66.0).copy(heatPumpSec = 240, auxHeatSec = 0, coolSec = 0)
        val summary = hvacSummary(listOf(cold, mild), t0, t0.plusSeconds(3600), t0.plusSeconds(3600))
        assertEquals(540.0 / 3600.0, summary.heatPumpHours, 0.0001)
        assertEquals(180.0 / 3600.0, summary.auxHours, 0.0001)
        assertEquals(summary.auxHours * 0.60, summary.gasCostDollars, 0.0001)
        val coldRuns = hvacRuns(listOf(cold), t0, t0.plusSeconds(3600), t0.plusSeconds(3600))
        assertEquals(1, coldRuns.size)
        assertEquals(HvacSource.AUX, coldRuns.single().source)
        val mildRuns = hvacRuns(listOf(mild), t0, t0.plusSeconds(3600), t0.plusSeconds(3600))
        assertEquals(1, mildRuns.size)
        assertEquals(HvacSource.HEAT_PUMP, mildRuns.single().source)
        assertTrue(hvacSummaryLabel(summary).contains("Aux heat"))
        assertTrue(hvacSummaryLabel(summary).contains("Gas ≈"))
        assertTrue(hvacSummaryLabel(hvacSummary(listOf(mild), t0, t0.plusSeconds(3600))).startsWith("Heat pump"))
        assertTrue(!hvacSummaryLabel(hvacSummary(listOf(mild), t0, t0.plusSeconds(3600))).contains("Gas"))
    }

    @Test
    fun overlapSlotIsAuxAndContiguousAuxMerges() {
        val samples = listOf(
            sample(t0, 64.0, 75.0, 66.0).copy(heatPumpSec = 0, auxHeatSec = 300),
            sample(t0.plusSeconds(300), 64.0, 75.0, 66.0).copy(heatPumpSec = 120, auxHeatSec = 180),
            sample(t0.plusSeconds(600), 70.0, 75.0, 66.0).copy(heatPumpSec = 240, auxHeatSec = 0)
        )
        val runs = hvacRuns(samples, t0, t0.plusSeconds(3600), t0.plusSeconds(3600))
        assertEquals(2, runs.size)
        assertEquals(HvacSource.AUX, runs[0].source)
        assertEquals(t0, runs[0].start)
        assertEquals(t0.plusSeconds(600), runs[0].end)
        assertEquals(HvacSource.HEAT_PUMP, runs[1].source)
        assertEquals(t0.plusSeconds(600), runs[1].start)
        assertEquals(t0.plusSeconds(900), runs[1].end)
    }

    @Test
    fun aboveCoolSetpointIsACoolingGap() {
        val mark = sample(t0, indoor = 75.0, cool = 74.0, heat = 68.0).classify()
        assertEquals(EcobeeBand.COOLING, mark.band)
        assertEquals(74.0, mark.setF)
    }

    @Test
    fun belowHeatSetpointIsAHeatingGap() {
        val mark = sample(t0, indoor = 67.0, cool = 74.0, heat = 68.0).classify()
        assertEquals(EcobeeBand.HEATING, mark.band)
        assertEquals(68.0, mark.setF)
    }

    @Test
    fun deadbandDrawsTheCloserSetpointAndDoesNotFill() {
        val tie = sample(t0, indoor = 71.0, cool = 74.0, heat = 68.0).classify()
        assertEquals(EcobeeBand.DEADBAND, tie.band)
        assertEquals(74.0, tie.setF)
        val nearerHeat = sample(t0, indoor = 69.0, cool = 74.0, heat = 68.0).classify()
        assertEquals(EcobeeBand.DEADBAND, nearerHeat.band)
        assertEquals(68.0, nearerHeat.setF)
    }

    @Test
    fun sittingOnEitherSetpointIsDeadbandOnThatSetpoint() {
        val onCool = sample(t0, 74.0, cool = 74.0, heat = 68.0).classify()
        assertEquals(EcobeeBand.DEADBAND, onCool.band)
        assertEquals(74.0, onCool.setF)
        val onHeat = sample(t0, 68.0, cool = 74.0, heat = 68.0).classify()
        assertEquals(EcobeeBand.DEADBAND, onHeat.band)
        assertEquals(68.0, onHeat.setF)
    }

    @Test
    fun aMissingOrOffSetpointIsSkipped() {
        assertEquals(EcobeeBand.HEATING, sample(t0, 60.0, cool = null, heat = 68.0).classify().band)
        val onlyHeat = sample(t0, 80.0, cool = null, heat = 68.0).classify()
        assertEquals(EcobeeBand.DEADBAND, onlyHeat.band)
        assertEquals(68.0, onlyHeat.setF)
        assertEquals(EcobeeBand.COOLING, sample(t0, 80.0, cool = 74.0, heat = null).classify().band)
        val neither = sample(t0, 70.0, cool = null, heat = null).classify()
        assertEquals(EcobeeBand.DEADBAND, neither.band)
        assertNull(neither.setF)
        assertNull(parseEcobeeSetpoint("off"))
        assertNull(parseEcobeeSetpoint("  "))
        assertNull(parseEcobeeSetpoint(null))
        assertEquals(74.0, parseEcobeeSetpoint("74.0"))
    }

    @Test
    fun coolWinsWhenTheIndoorTemperatureClearsIt() {
        val mark = sample(t0, indoor = 75.0, cool = 74.0, heat = 80.0).classify()
        assertEquals(EcobeeBand.COOLING, mark.band)
        assertEquals(74.0, mark.setF)
    }

    @Test
    fun deadbandSplitsFillSegments() {
        val run = listOf(
            sample(t0, 75.0, 74.0, 68.0).classify(),
            sample(t0.plusSeconds(300), 76.0, 74.0, 68.0).classify(),
            sample(t0.plusSeconds(600), 71.0, 74.0, 68.0).classify(),
            sample(t0.plusSeconds(900), 66.0, 74.0, 68.0).classify(),
            sample(t0.plusSeconds(1200), 65.0, 74.0, 68.0).classify()
        )
        val fills = ecobeeFillSegments(run)
        assertEquals(2, fills.size)
        assertEquals(EcobeeBand.COOLING, fills[0].first().band)
        assertEquals(listOf(75.0, 76.0), fills[0].map { it.indoorF })
        assertEquals(EcobeeBand.HEATING, fills[1].first().band)
        assertEquals(listOf(66.0, 65.0), fills[1].map { it.indoorF })
        assertTrue(fills.flatten().none { it.band == EcobeeBand.DEADBAND })
    }

    @Test
    fun aSingleGapSampleDoesNotBecomeAFill() {
        val run = listOf(sample(t0, 80.0, 74.0, 68.0).classify())
        assertTrue(ecobeeFillSegments(run).isEmpty())
    }

    @Test
    fun gapOverFifteenMinutesBreaksTheRunAndFifteenMinutesDoesNot() {
        val from = t0
        val to = t0.plusSeconds(3600)
        val samples = listOf(
            sample(t0, 75.0, 74.0, 68.0),
            sample(t0.plus(ECOBEE_GAP), 75.2, 74.0, 68.0),
            sample(t0.plus(ECOBEE_GAP).plusSeconds(15 * 60 + 1), 66.0, 74.0, 68.0)
        )
        val runs = prepareEcobee(samples, from, to, plotWidthPx = 400)
        assertEquals(2, runs.size)
        assertEquals(2, runs[0].size)
        assertEquals(EcobeeBand.HEATING, runs[1].single().band)
    }

    @Test
    fun downsamplingNeverBridgesAGapEvenInOnePixel() {
        val from = t0
        val to = t0.plusSeconds(3600)
        val samples = listOf(
            sample(t0.plusSeconds(60), 75.0, 74.0, 68.0),
            sample(t0.plusSeconds(60 + 16 * 60), 66.0, 74.0, 68.0)
        )
        val runs = prepareEcobee(samples, from, to, plotWidthPx = 1)
        assertEquals(2, runs.size)
        assertEquals(listOf(t0.plusSeconds(60), t0.plusSeconds(60 + 16 * 60)), runs.map { it.single().at })
    }

    @Test
    fun pixelBucketsKeepTheLargerGapAndStayInsideTheSampleTimes() {
        val from = t0
        val to = t0.plusSeconds(500)
        val samples = (0 until 100).map { index ->
            val at = t0.plusSeconds(index * 5L)
            if (index == 40) sample(at, indoor = 80.0, cool = 74.0, heat = 68.0)
            else sample(at, indoor = 71.0, cool = 74.0, heat = 68.0)
        }
        val run = prepareEcobee(samples, from, to, plotWidthPx = 10).single()
        assertTrue(run.size <= 10)
        assertEquals(10, run.size)
        assertEquals(run.map { it.at }, run.map { it.at }.sorted())
        val originals = samples.map { it.at }.toSet()
        assertTrue(run.all { it.at in originals })
        assertTrue(run.any { it.band == EcobeeBand.COOLING && it.indoorF == 80.0 })
    }

    @Test
    fun aLaterDeadbandDoesNotEraseACoolingSampleInTheSamePixel() {
        val from = t0
        val to = t0.plusSeconds(100)
        val samples = listOf(
            sample(t0.plusSeconds(1), indoor = 80.0, cool = 74.0, heat = 68.0),
            sample(t0.plusSeconds(2), indoor = 71.0, cool = 74.0, heat = 68.0)
        )
        val mark = downsampleEcobee(samples, from, 100_000, plotWidthPx = 1).single()
        assertEquals(EcobeeBand.COOLING, mark.band)
        assertEquals(80.0, mark.indoorF, 0.0)
    }

    @Test
    fun sharedAxisUnionsOutdoorAndIndoorWhenEcobeeIsOn() {
        val indoor = listOf(sample(t0.plusSeconds(60), 72.0, cool = 74.0, heat = 68.0))
        val (lo, hi) = sharedFahrenheitRange(
            outdoorF = listOf(10.0, 40.0),
            indoor = indoor,
            from = t0,
            to = t0.plusSeconds(3600),
            scaleToData = true
        )
        assertTrue(lo < 10.0)
        assertTrue(hi > 74.0)
        val fixed = sharedFahrenheitRange(listOf(70.0), indoor, t0, t0.plusSeconds(60), scaleToData = false)
        assertEquals(ForecastSnapshot.TEMP_MIN_F, fixed.first, 0.0)
        assertEquals(ForecastSnapshot.TEMP_MAX_F, fixed.second, 0.0)
    }

    @Test
    fun temperatureGridUsesRoundStepsAndSkipsFreezing() {
        val wide = temperatureGrid(-24.0, 86.0, snapEnds = true)
        assertEquals(25.0, wide.step, 0.0)
        assertEquals(-25.0, wide.min, 0.0)
        assertEquals(100.0, wide.max, 0.0)
        assertTrue(wide.ticks.none { abs(it - 32.0) <= 3.0 })
        val covered = temperatureGrid(-16.0, 80.0, snapEnds = true)
        assertEquals(20.0, covered.step, 0.0)
        assertEquals(-20.0, covered.min, 0.0)
        assertEquals(80.0, covered.max, 0.0)
        val near = temperatureGrid(20.0, 45.0, snapEnds = true)
        assertTrue(near.ticks.none { abs(it - 32.0) <= 3.0 })
        val tight = temperatureGrid(68.0, 78.0, snapEnds = true)
        assertEquals(5.0, tight.step, 0.0)
        assertTrue(tight.ticks.contains(70.0))
        assertTrue(tight.min <= 68.0 && tight.max >= 78.0)
    }

    @Test
    fun samplesAfterNowAreNotDrawn() {
        val samples = listOf(
            sample(t0, 75.0, 74.0, 68.0),
            sample(t0.plusSeconds(300), 76.0, 74.0, 68.0),
            sample(t0.plusSeconds(900), 80.0, 74.0, 68.0)
        )
        val now = t0.plusSeconds(300)
        val run = prepareEcobee(samples, t0, t0.plusSeconds(3600), plotWidthPx = 80, now = now).single()
        assertEquals(listOf(t0, t0.plusSeconds(300)), run.map { it.at })
        assertTrue(run.all { !it.at.isAfter(now) })
    }

    @Test
    fun samplesOutsideTheWindowAreDropped() {
        val samples = listOf(
            sample(t0.minusSeconds(1), 80.0, 74.0, 68.0),
            sample(t0.plusSeconds(60), 66.0, 74.0, 68.0),
            sample(t0.plusSeconds(3600), 50.0, 74.0, 68.0)
        )
        val runs = prepareEcobee(samples, t0, t0.plusSeconds(3600), plotWidthPx = 50)
        assertEquals(66.0, runs.single().single().indoorF, 0.0)
    }

    private fun sample(at: Instant, indoor: Double, cool: Double?, heat: Double?) =
        EcobeeSample(at, indoor, cool, heat)
}
