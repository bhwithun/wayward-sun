package com.brian.solwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppStorage(private val context: Context) {

    object Keys {
        val CACHE_URL = stringPreferencesKey("solcast_cache_url")
        val RESOURCE_ID = stringPreferencesKey("solcast_resource_id")
        val CACHED_FORECASTS = stringPreferencesKey("cached_forecasts_json")
        val CACHED_ACTUALS = stringPreferencesKey("cached_actuals_json")
        val CACHED_LIVE_PAST = stringPreferencesKey("cached_live_past_json")
        val CACHED_SOURCE = stringPreferencesKey("cached_source")
        val LAST_FETCH_AT = longPreferencesKey("last_fetch_at")
        val REQUESTS_USED = intPreferencesKey("requests_used")
        val REQUESTS_DAY = stringPreferencesKey("requests_day_utc")
        val AUTO_FETCHES_USED = intPreferencesKey("auto_fetches_used")
        val PLACE_QUERY = stringPreferencesKey("place_query")
        val PLACE_LABEL = stringPreferencesKey("place_label")
        val PLACE_LAT = doublePreferencesKey("place_lat")
        val PLACE_LNG = doublePreferencesKey("place_lng")
        val CACHED_WEATHER = stringPreferencesKey("cached_weather_json")
        val WEATHER_FETCH_AT = longPreferencesKey("weather_fetch_at")
        val SHOW_TEMPERATURE = booleanPreferencesKey("show_temperature")
        val SHOW_DTE_RATES = booleanPreferencesKey("show_dte_rates")
        val SHOW_BUY = booleanPreferencesKey("show_buy_rate")
        val SHOW_SELL = booleanPreferencesKey("show_sell_rate")
        val SHOW_PRECIPITATION = booleanPreferencesKey("show_precipitation")
        val SHOW_SOLCAST = booleanPreferencesKey("show_solcast")
    }

    companion object {
        const val DEFAULT_RESOURCE_ID = "84d7-8b52-33f3-bd7b"
        const val DEFAULT_CACHE_URL = "https://wayward-sun-brianandkathi.vercel.app"
        private val LEGACY_CACHE_URLS = setOf(
            "https://sol-widget-brianandkathi.vercel.app",
            "https://sol-widget.vercel.app",
            "https://sol-cache.brian-952.workers.dev",
            "https://solcast-cache-worker.brian-952.workers.dev",
        )
    }

    fun getStringFlow(key: Preferences.Key<String>, default: String? = null): Flow<String?> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getStringOnce(key: Preferences.Key<String>, default: String? = null): String? =
        getStringFlow(key, default).first()

    suspend fun getIntOnce(key: Preferences.Key<Int>, default: Int = 0): Int =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun getLongOnce(key: Preferences.Key<Long>, default: Long = 0L): Long =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { it[key] = value }
    }

    val resourceId: Flow<String> = getStringFlow(Keys.RESOURCE_ID, DEFAULT_RESOURCE_ID)
        .map { it?.ifBlank { DEFAULT_RESOURCE_ID } ?: DEFAULT_RESOURCE_ID }

    suspend fun cacheUrlOnce(): String {
        val stored = getStringOnce(Keys.CACHE_URL)?.trim()?.trimEnd('/')
        val url = when {
            stored.isNullOrBlank() || isLegacyCacheUrl(stored) -> DEFAULT_CACHE_URL
            else -> stored
        }
        if (stored != url) setCacheUrl(url)
        return url
    }

    suspend fun resourceIdOnce(): String =
        getStringOnce(Keys.RESOURCE_ID, DEFAULT_RESOURCE_ID)
            ?.ifBlank { DEFAULT_RESOURCE_ID }
            ?: DEFAULT_RESOURCE_ID

    suspend fun setCacheUrl(value: String) {
        val trimmed = value.trim().trimEnd('/')
        val url = when {
            trimmed.isBlank() || isLegacyCacheUrl(trimmed) -> DEFAULT_CACHE_URL
            else -> trimmed
        }
        setString(Keys.CACHE_URL, url)
    }

    private fun isLegacyCacheUrl(url: String): Boolean =
        LEGACY_CACHE_URLS.any { it.equals(url, ignoreCase = true) }

    suspend fun setResourceId(value: String) = setString(Keys.RESOURCE_ID, value.trim())

    suspend fun cachedForecasts(): String? = getStringOnce(Keys.CACHED_FORECASTS)
    suspend fun cachedActuals(): String? = getStringOnce(Keys.CACHED_ACTUALS)
    suspend fun cachedLivePast(): String? = getStringOnce(Keys.CACHED_LIVE_PAST)
    suspend fun saveLivePast(json: String) = setString(Keys.CACHED_LIVE_PAST, json)
    suspend fun cachedSource(): String? = getStringOnce(Keys.CACHED_SOURCE)
    suspend fun lastFetchAt(): Long = getLongOnce(Keys.LAST_FETCH_AT)

    suspend fun saveCache(
        forecastsJson: String,
        actualsJson: String,
        fetchedAtMillis: Long,
        source: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_FORECASTS] = forecastsJson
            prefs[Keys.CACHED_ACTUALS] = actualsJson
            prefs[Keys.LAST_FETCH_AT] = fetchedAtMillis
            if (source.isNullOrBlank()) prefs.remove(Keys.CACHED_SOURCE)
            else prefs[Keys.CACHED_SOURCE] = source
        }
    }

    suspend fun saveWorkerQuota(utcDay: String, requestsUsed: Int, autoFetchesUsed: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REQUESTS_DAY] = utcDay
            prefs[Keys.REQUESTS_USED] = requestsUsed
            prefs[Keys.AUTO_FETCHES_USED] = autoFetchesUsed
        }
    }

    suspend fun requestsUsed(utcDay: String): Int {
        val day = getStringOnce(Keys.REQUESTS_DAY).orEmpty()
        return if (day == utcDay) getIntOnce(Keys.REQUESTS_USED) else 0
    }

    suspend fun autoFetchesUsed(utcDay: String): Int {
        val day = getStringOnce(Keys.REQUESTS_DAY).orEmpty()
        return if (day == utcDay) getIntOnce(Keys.AUTO_FETCHES_USED) else 0
    }

    suspend fun getDoubleOnce(key: Preferences.Key<Double>, default: Double? = null): Double? =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun placeQueryOnce(): String = getStringOnce(Keys.PLACE_QUERY).orEmpty()
    suspend fun placeLabelOnce(): String = getStringOnce(Keys.PLACE_LABEL).orEmpty()
    suspend fun placeLatOnce(): Double? = getDoubleOnce(Keys.PLACE_LAT)
    suspend fun placeLngOnce(): Double? = getDoubleOnce(Keys.PLACE_LNG)

    suspend fun savePlace(query: String, label: String, lat: Double, lng: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLACE_QUERY] = query.trim()
            prefs[Keys.PLACE_LABEL] = label
            prefs[Keys.PLACE_LAT] = lat
            prefs[Keys.PLACE_LNG] = lng
        }
    }

    suspend fun clearPlace() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PLACE_QUERY)
            prefs.remove(Keys.PLACE_LABEL)
            prefs.remove(Keys.PLACE_LAT)
            prefs.remove(Keys.PLACE_LNG)
            prefs.remove(Keys.CACHED_WEATHER)
            prefs.remove(Keys.WEATHER_FETCH_AT)
        }
    }

    suspend fun cachedWeather(): String? = getStringOnce(Keys.CACHED_WEATHER)
    suspend fun weatherFetchAt(): Long = getLongOnce(Keys.WEATHER_FETCH_AT)

    suspend fun saveWeatherCache(json: String, fetchedAtMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_WEATHER] = json
            prefs[Keys.WEATHER_FETCH_AT] = fetchedAtMillis
        }
    }

    suspend fun getBooleanOnce(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun chartLayersOnce(): ChartLayers {
        val prefs = context.dataStore.data.first()
        val legacyRates = prefs[Keys.SHOW_DTE_RATES]
        return ChartLayers(
            temperature = prefs[Keys.SHOW_TEMPERATURE] ?: true,
            buy = prefs[Keys.SHOW_BUY] ?: legacyRates ?: true,
            sell = prefs[Keys.SHOW_SELL] ?: legacyRates ?: true,
            precipitation = prefs[Keys.SHOW_PRECIPITATION] ?: true,
            solcast = prefs[Keys.SHOW_SOLCAST] ?: true
        )
    }

    suspend fun setChartLayer(layer: ChartLayer, visible: Boolean) {
        val key = when (layer) {
            ChartLayer.TEMPERATURE -> Keys.SHOW_TEMPERATURE
            ChartLayer.BUY -> Keys.SHOW_BUY
            ChartLayer.SELL -> Keys.SHOW_SELL
            ChartLayer.PRECIPITATION -> Keys.SHOW_PRECIPITATION
            ChartLayer.SOLCAST -> Keys.SHOW_SOLCAST
        }
        setBoolean(key, visible)
    }

    suspend fun recordRequests(utcDay: String, additional: Int, autoPull: Boolean = false) {
        context.dataStore.edit { prefs ->
            val currentDay = prefs[Keys.REQUESTS_DAY]
            val http = if (currentDay == utcDay) prefs[Keys.REQUESTS_USED] ?: 0 else 0
            val autos = if (currentDay == utcDay) prefs[Keys.AUTO_FETCHES_USED] ?: 0 else 0
            prefs[Keys.REQUESTS_DAY] = utcDay
            prefs[Keys.REQUESTS_USED] = http + additional
            prefs[Keys.AUTO_FETCHES_USED] = autos + if (autoPull) 1 else 0
        }
    }
}
