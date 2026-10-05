package com.brian.solwidget.data

import com.brian.solwidget.widget.widgetChartRange
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySeriesTest {
    private val now: Instant = Instant.parse("2026-10-03T16:00:00Z")

    @Test
    fun widgetChartCoversThePrior48HoursAndTheNext72() {
        val (from, to) = widgetChartRange(now)
        assertEquals(now.minus(Duration.ofHours(48)), from)
        assertEquals(now.plus(Duration.ofHours(72)), to)
    }

    @Test
    fun pastActualIsLive() {
        val point = interval(now.minusSeconds(3600), actual = 1.5, forecast = 4.0).toPowerPoint(now)
        assertEquals(SeriesKind.LIVE, point?.kind)
        assertEquals(1.5, point?.kw)
    }

    @Test
    fun pastForecastStandsInUntilActualsArrive() {
        val point = interval(now.minusSeconds(1800), actual = null, forecast = 2.25).toPowerPoint(now)
        assertEquals(SeriesKind.LIVE, point?.kind)
        assertEquals(2.25, point?.kw)
    }

    @Test
    fun futureUsesForecast() {
        val point = interval(now.plusSeconds(1800), actual = 0.1, forecast = 3.0).toPowerPoint(now)
        assertEquals(SeriesKind.FORECAST, point?.kind)
        assertEquals(3.0, point?.kw)
    }

    @Test
    fun futureActualWithoutForecastIsLive() {
        val point = interval(now.plusSeconds(1800), actual = 0.4, forecast = null).toPowerPoint(now)
        assertEquals(SeriesKind.LIVE, point?.kind)
        assertEquals(0.4, point?.kw)
    }

    @Test
    fun liveChartUsesHistoryForThePastAndTheCacheForTheFuture() {
        val from = now.minusSeconds(7200)
        val to = now.plusSeconds(7200)
        val past = PowerPoint(now.minusSeconds(1800), 2.0, 0.5, SeriesKind.LIVE)
        val stalePast = PowerPoint(now.minusSeconds(1800), 9.0, 0.5, SeriesKind.FORECAST)
        val future = PowerPoint(now.plusSeconds(1800), 4.0, 0.5, SeriesKind.FORECAST)
        val merged = liveSolarPoints(listOf(past), listOf(stalePast, future), from, to, now)
        assertEquals(listOf(past, future), merged)
    }

    @Test
    fun liveChartKeepsTheSnapshotUntilHistoryArrives() {
        val from = now.minusSeconds(7200)
        val to = now.plusSeconds(7200)
        val forecast = PowerPoint(now.minusSeconds(1800), 1.0, 0.5, SeriesKind.FORECAST)
        assertEquals(listOf(forecast), liveSolarPoints(null, listOf(forecast), from, to, now))
    }

    @Test
    fun storedLivePastRoundTripsAndStaysFreshInTheSameWindow() {
        val zone = ZoneId.of("America/Detroit")
        val now = Instant.parse("2026-10-05T20:00:00Z")
        val (from, to) = ForecastSnapshot.range(now, zone)
        val stored = StoredLivePast(
            key = historyFetchKey(from, to),
            savedAt = now.minusSeconds(60),
            points = listOf(PowerPoint(now.minusSeconds(1800), 1.25, 0.5, SeriesKind.LIVE))
        )
        assertTrue(LivePastStore.isFresh(stored, now, zone))
        val decoded = LivePastStore.decode(LivePastStore.encode(stored))
        assertEquals(stored.key, decoded?.key)
        assertEquals(stored.savedAt, decoded?.savedAt)
        assertEquals(1.25, decoded?.points?.single()?.kw)
        assertEquals(SeriesKind.LIVE, decoded?.points?.single()?.kind)
    }

    @Test
    fun storedLivePastIsStaleWhenTheWindowMovesOrTheSaveIsOld() {
        val zone = ZoneId.of("America/Detroit")
        val now = Instant.parse("2026-10-05T20:00:00Z")
        val (from, to) = ForecastSnapshot.range(now, zone)
        val stored = StoredLivePast(
            key = historyFetchKey(from, to),
            savedAt = now.minusSeconds(60),
            points = listOf(PowerPoint(now.minusSeconds(1800), 1.0, 0.5, SeriesKind.LIVE))
        )
        assertFalse(LivePastStore.isFresh(stored, now.plus(1, ChronoUnit.DAYS), zone))
        val aged = stored.copy(savedAt = now.minus(LivePastStore.MAX_AGE).minusSeconds(1))
        assertFalse(LivePastStore.isFresh(aged, now, zone))
        assertFalse(LivePastStore.isFresh(stored.copy(points = emptyList()), now, zone))
    }

    @Test
    fun missingKwIsDropped() {
        assertNull(interval(now.minusSeconds(60), actual = null, forecast = null).toPowerPoint(now))
    }

    private fun interval(end: Instant, actual: Double?, forecast: Double?) = HistoryInterval(
        periodEnd = end,
        actualKw = actual,
        forecastKw = forecast,
        periodHours = 0.5,
        tempF = null,
        precipPct = null
    )
}
