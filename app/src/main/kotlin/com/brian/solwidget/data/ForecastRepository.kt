package com.brian.solwidget.data

import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ForecastRepository private constructor(context: Context) {

    private val storage = AppStorage(context.applicationContext)
    private val api = SolcastApi()
    private val mutex = Mutex()

    val apiKeyFlow = storage.apiKey
    val resourceIdFlow = storage.resourceId

    suspend fun saveSettings(apiKey: String, resourceId: String) {
        storage.setApiKey(apiKey)
        storage.setResourceId(resourceId.ifBlank { AppStorage.DEFAULT_RESOURCE_ID })
    }

    suspend fun loadSnapshot(): ForecastSnapshot = mutex.withLock {
        buildSnapshot(
            forecastsJson = storage.cachedForecasts(),
            actualsJson = storage.cachedActuals(),
            fetchedAt = storage.lastFetchAt().takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) },
            resourceId = storage.resourceIdOnce(),
            requestsUsed = storage.requestsUsed(utcDay()),
            errorMessage = null
        )
    }

    suspend fun refresh(force: Boolean = false): ForecastSnapshot = mutex.withLock {
        val apiKey = storage.apiKeyOnce()
        val resourceId = storage.resourceIdOnce()
        val used = storage.requestsUsed(utcDay())
        val autoUsed = storage.autoFetchesUsed(utcDay())
        val lastFetch = storage.lastFetchAt().takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) }

        if (apiKey.isBlank()) {
            return@withLock buildSnapshot(
                forecastsJson = null,
                actualsJson = null,
                fetchedAt = null,
                resourceId = resourceId,
                requestsUsed = used,
                errorMessage = "Add your Solcast API key in Settings to load live data."
            )
        }

        if (!force && lastFetch != null && Duration.between(lastFetch, Instant.now()) < MIN_AUTO_AGE) {
            return@withLock buildSnapshot(
                forecastsJson = storage.cachedForecasts(),
                actualsJson = storage.cachedActuals(),
                fetchedAt = lastFetch,
                resourceId = resourceId,
                requestsUsed = used,
                errorMessage = null
            )
        }

        if (!force && autoUsed >= DAILY_AUTO_LIMIT) {
            return@withLock buildSnapshot(
                forecastsJson = storage.cachedForecasts(),
                actualsJson = storage.cachedActuals(),
                fetchedAt = lastFetch,
                resourceId = resourceId,
                requestsUsed = used,
                errorMessage = null
            )
        }

        if (used + REQUESTS_PER_REFRESH > DAILY_LIMIT) {
            return@withLock buildSnapshot(
                forecastsJson = storage.cachedForecasts(),
                actualsJson = storage.cachedActuals(),
                fetchedAt = lastFetch,
                resourceId = resourceId,
                requestsUsed = used,
                errorMessage = "Daily Solcast quota reached ($used/$DAILY_LIMIT). Cached data is shown."
            )
        }

        withContext(Dispatchers.IO) {
            var billed = 0
            try {
                val forecasts = api.fetchForecasts(resourceId, apiKey)
                billed += 1
                val actuals = api.fetchEstimatedActuals(resourceId, apiKey)
                billed += 1
                storage.recordRequests(utcDay(), billed, autoPull = !force)
                storage.saveCache(forecasts, actuals, Instant.now().toEpochMilli())
                buildSnapshot(
                    forecastsJson = forecasts,
                    actualsJson = actuals,
                    fetchedAt = Instant.now(),
                    resourceId = resourceId,
                    requestsUsed = used + billed,
                    errorMessage = null
                )
            } catch (error: Exception) {
                if (billed > 0) storage.recordRequests(utcDay(), billed, autoPull = false)
                val currentUsed = storage.requestsUsed(utcDay())
                buildSnapshot(
                    forecastsJson = storage.cachedForecasts(),
                    actualsJson = storage.cachedActuals(),
                    fetchedAt = storage.lastFetchAt().takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) },
                    resourceId = resourceId,
                    requestsUsed = currentUsed,
                    errorMessage = error.message ?: "Unable to reach Solcast."
                )
            }
        }
    }

    private fun buildSnapshot(
        forecastsJson: String?,
        actualsJson: String?,
        fetchedAt: Instant?,
        resourceId: String,
        requestsUsed: Int,
        errorMessage: String?
    ): ForecastSnapshot {
        val parsed = api.parseCombined(actualsJson, forecastsJson)
        return if (parsed.isEmpty()) {
            ForecastSnapshot(
                resourceId = resourceId,
                points = demoPoints(),
                fetchedAt = fetchedAt,
                requestsUsed = requestsUsed,
                requestLimit = DAILY_LIMIT,
                isDemo = true,
                errorMessage = errorMessage
            )
        } else {
            ForecastSnapshot(
                resourceId = resourceId,
                points = parsed,
                fetchedAt = fetchedAt,
                requestsUsed = requestsUsed,
                requestLimit = DAILY_LIMIT,
                isDemo = false,
                errorMessage = errorMessage
            )
        }
    }

    companion object {
        const val DAILY_LIMIT = 10
        const val DAILY_AUTO_LIMIT = 4
        const val REQUESTS_PER_REFRESH = 2
        val MIN_AUTO_AGE: Duration = Duration.ofHours(6)

        @Volatile
        private var instance: ForecastRepository? = null

        fun get(context: Context): ForecastRepository =
            instance ?: synchronized(this) {
                instance ?: ForecastRepository(context.applicationContext).also { instance = it }
            }

        private fun utcDay(): String = LocalDate.now(ZoneOffset.UTC).toString()

        fun demoPoints(now: Instant = Instant.now()): List<PowerPoint> {
            val zone = java.time.ZoneId.systemDefault()
            val start = LocalDate.now(zone)
                .minusDays(ForecastSnapshot.PAST_DAYS)
                .atStartOfDay(zone)
                .toInstant()
            val intervals = ((ForecastSnapshot.PAST_DAYS + ForecastSnapshot.FUTURE_DAYS + 1) * 48).toInt()
            return (0 until intervals).map { index ->
                val time = start.plus(30L * index, ChronoUnit.MINUTES)
                val local = time.atZone(zone)
                val hour = local.hour + local.minute / 60.0
                val sun = max(0.0, sin(((hour - 6.0) / 12.0) * Math.PI))
                val clouds = 0.85 + 0.15 * cos(index / 3.0)
                val kw = 6.4 * sun * sun * clouds
                val kind = if (time.isAfter(now)) SeriesKind.FORECAST else SeriesKind.LIVE
                PowerPoint(time, kw, 0.5, kind)
            }
        }
    }
}
