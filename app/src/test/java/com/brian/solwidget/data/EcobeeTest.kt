package com.brian.solwidget.data

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcobeeTest {
    private val t0: Instant = Instant.parse("2026-01-15T15:00:00Z")

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
    fun betweenSetpointsIsDeadbandWithNoDashedSetpoint() {
        val mark = sample(t0, indoor = 71.0, cool = 74.0, heat = 68.0).classify()
        assertEquals(EcobeeBand.DEADBAND, mark.band)
        assertNull(mark.setF)
    }

    @Test
    fun sittingOnEitherSetpointIsDeadband() {
        assertEquals(EcobeeBand.DEADBAND, sample(t0, 74.0, cool = 74.0, heat = 68.0).classify().band)
        assertEquals(EcobeeBand.DEADBAND, sample(t0, 68.0, cool = 74.0, heat = 68.0).classify().band)
    }

    @Test
    fun aMissingOrOffSetpointIsSkipped() {
        assertEquals(EcobeeBand.HEATING, sample(t0, 60.0, cool = null, heat = 68.0).classify().band)
        assertEquals(EcobeeBand.DEADBAND, sample(t0, 80.0, cool = null, heat = 68.0).classify().band)
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
