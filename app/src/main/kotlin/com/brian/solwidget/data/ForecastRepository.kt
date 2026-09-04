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
    private val parser = SolcastApi()
    private val cacheApi = CacheApi()
    private val mutex = Mutex()

    val resourceIdFlow = storage.resourceId

    suspend fun saveSettings(cacheUrl: String, resourceId: String) {
        storage.setCacheUrl(cacheUrl)
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
        val resourceId = storage.resourceIdOnce()
        val used = storage.requestsUsed(utcDay())
        val lastFetch = storage.lastFetchAt().takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) }

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

        withContext(Dispatchers.IO) {
            try {
                val payload = cacheApi.fetchSnapshot(storage.cacheUrlOnce())
                val fetchedAt = payload.fetchedAt ?: Instant.now()
                storage.saveCache(payload.forecastsJson, payload.actualsJson, fetchedAt.toEpochMilli())
                storage.saveWorkerQuota(
                    payload.requestsDay ?: utcDay(),
                    payload.requestsUsed,
                    payload.autoFetchesUsed
                )
                if (payload.resourceId.isNotBlank()) {
                    storage.setResourceId(payload.resourceId)
                }
                val skip = payload.message?.takeIf {
                    it.contains("skipped", ignoreCase = true) ||
                        it.contains("quota", ignoreCase = true) ||
                        it.contains("limit reached", ignoreCase = true)
                }
                buildSnapshot(
                    forecastsJson = payload.forecastsJson,
                    actualsJson = payload.actualsJson,
                    fetchedAt = fetchedAt,
                    resourceId = payload.resourceId.ifBlank { resourceId },
                    requestsUsed = payload.requestsUsed,
                    errorMessage = skip
                )
            } catch (error: Exception) {
                buildSnapshot(
                    forecastsJson = storage.cachedForecasts(),
                    actualsJson = storage.cachedActuals(),
                    fetchedAt = storage.lastFetchAt().takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) },
                    resourceId = resourceId,
                    requestsUsed = storage.requestsUsed(utcDay()),
                    errorMessage = error.message ?: "Unable to reach the shared cache."
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
        val parsed = parser.parseCombined(actualsJson, forecastsJson)
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
