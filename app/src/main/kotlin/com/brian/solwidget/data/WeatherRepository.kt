package com.brian.solwidget.data

import android.content.Context
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WeatherRepository private constructor(context: Context) {

    private val storage = AppStorage(context.applicationContext)
    private val api = OpenMeteoApi()
    private val mutex = Mutex()

    suspend fun loadSnapshot(): WeatherSnapshot = mutex.withLock { snapshotFromCache() }

    suspend fun resolveAndSavePlace(query: String): GeoPlace {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            storage.clearPlace()
            throw IllegalStateException("Enter a city or ZIP code.")
        }
        val place = withContext(Dispatchers.IO) { api.geocode(trimmed) }
        storage.savePlace(trimmed, place.label, place.latitude, place.longitude)
        return place
    }

    suspend fun refresh(force: Boolean = false): WeatherSnapshot = mutex.withLock {
        val lat = storage.placeLatOnce()
        val lng = storage.placeLngOnce()
        val label = storage.placeLabelOnce()
        val query = storage.placeQueryOnce()
        if (lat == null || lng == null || label.isBlank()) {
            return@withLock WeatherSnapshot(
                place = null,
                points = emptyList(),
                fetchedAt = null,
                errorMessage = "Set a city or ZIP in Settings to load local weather."
            )
        }
        val place = GeoPlace(query, label, lat, lng)
        val last = storage.weatherFetchAt().takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) }
        if (!force && last != null && Duration.between(last, Instant.now()) < MIN_AGE) {
            return@withLock snapshotFromCache()
        }
        withContext(Dispatchers.IO) {
            try {
                val (json, points) = api.fetchHourly(lat, lng)
                storage.saveWeatherCache(json, Instant.now().toEpochMilli())
                WeatherSnapshot(place, points, Instant.now(), errorMessage = null)
            } catch (error: Exception) {
                snapshotFromCache().copy(
                    errorMessage = error.message ?: "Unable to load weather."
                )
            }
        }
    }

    private suspend fun snapshotFromCache(): WeatherSnapshot {
        val lat = storage.placeLatOnce()
        val lng = storage.placeLngOnce()
        val label = storage.placeLabelOnce()
        val query = storage.placeQueryOnce()
        val place = if (lat != null && lng != null && label.isNotBlank()) {
            GeoPlace(query, label, lat, lng)
        } else {
            null
        }
        val fetched = storage.weatherFetchAt().takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) }
        val points = storage.cachedWeather()?.let { api.parseHourly(it) }.orEmpty()
        val missing = if (place == null) {
            "Set a city or ZIP in Settings to load local weather."
        } else {
            null
        }
        return WeatherSnapshot(place, points, fetched, errorMessage = missing)
    }

    companion object {
        val MIN_AGE: Duration = Duration.ofHours(1)

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
    }
}
