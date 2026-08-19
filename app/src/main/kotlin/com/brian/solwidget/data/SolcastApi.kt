package com.brian.solwidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

class SolcastApi {

    fun fetchForecasts(resourceId: String, apiKey: String): String =
        get("$BASE/rooftop_sites/${encode(resourceId)}/forecasts?format=json", apiKey)

    fun fetchEstimatedActuals(resourceId: String, apiKey: String): String =
        get("$BASE/rooftop_sites/${encode(resourceId)}/estimated_actuals?format=json", apiKey)

    fun parseCombined(actualsJson: String?, forecastsJson: String?): List<PowerPoint> {
        val live = parseArray(actualsJson, "estimated_actuals", SeriesKind.LIVE)
        val forecast = parseArray(forecastsJson, "forecasts", SeriesKind.FORECAST)
        val latestLive = live.maxOfOrNull { it.periodEnd }
        val futureForecast = if (latestLive == null) {
            forecast
        } else {
            forecast.filter { it.periodEnd.isAfter(latestLive) }
        }
        return (live + futureForecast).sortedBy { it.periodEnd }
    }

    private fun parseArray(json: String?, arrayKey: String, kind: SeriesKind): List<PowerPoint> {
        if (json.isNullOrBlank()) return emptyList()
        val root = JSONObject(json)
        val array: JSONArray = when {
            root.has(arrayKey) -> root.getJSONArray(arrayKey)
            root.has("forecasts") && kind == SeriesKind.FORECAST -> root.getJSONArray("forecasts")
            root.has("estimated_actuals") && kind == SeriesKind.LIVE -> root.getJSONArray("estimated_actuals")
            else -> return emptyList()
        }
        val points = ArrayList<PowerPoint>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val kw = readPowerKw(item) ?: continue
            val periodEnd = parseSolcastTime(item.optString("period_end")) ?: continue
            val hours = parsePeriodHours(item.optString("period", "PT30M"))
            points += PowerPoint(periodEnd, kw, hours, kind)
        }
        return points
    }

    private fun readPowerKw(item: JSONObject): Double? {
        val keys = listOf("pv_estimate", "pv_power_rooftop", "pv_power")
        for (key in keys) {
            if (item.has(key) && !item.isNull(key)) {
                return item.optDouble(key, Double.NaN).takeIf { !it.isNaN() }
            }
        }
        return null
    }

    private fun get(url: String, apiKey: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SolWidget/1.0 (Android)")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw SolcastException(code, humanError(code, body))
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun humanError(code: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?: JSONObject(body).optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return when (code) {
            401, 403 -> apiMessage ?: "Solcast rejected the API key."
            404 -> apiMessage ?: "Rooftop site not found. Check the resource ID."
            429 -> apiMessage ?: "Daily Solcast request limit reached (hobbyist accounts get 10/day)."
            else -> apiMessage ?: "Solcast request failed (HTTP $code)."
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val BASE = "https://api.solcast.com.au"

        fun parseSolcastTime(value: String): Instant? {
            if (value.isBlank()) return null
            val trimmed = value.trim()
            val normalized = if (trimmed.contains('.')) {
                trimmed.substringBefore('.') + "Z"
            } else if (!trimmed.endsWith("Z") && !trimmed.contains('+')) {
                trimmed + "Z"
            } else {
                trimmed
            }
            return runCatching { Instant.parse(normalized) }.getOrNull()
        }

        fun parsePeriodHours(period: String): Double {
            return runCatching { Duration.parse(period).toMinutes() / 60.0 }
                .getOrDefault(0.5)
                .takeIf { it > 0.0 } ?: 0.5
        }
    }
}
