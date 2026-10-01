package com.brian.solwidget.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

class OpenMeteoApi {

    fun geocode(query: String): GeoPlace {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val json = get("$GEOCODE?name=$encoded&count=8&language=en&format=json")
        val results = JSONObject(json).optJSONArray("results")
            ?: throw IllegalStateException("No matching place for “$query”.")
        if (results.length() == 0) {
            throw IllegalStateException("No matching place for “$query”.")
        }
        val chosen = pickUsFirst(results)
        val parts = listOfNotNull(
            chosen.optString("name").takeIf { it.isNotBlank() },
            chosen.optString("admin1").takeIf { it.isNotBlank() },
            chosen.optString("country").takeIf { it.isNotBlank() }
        )
        return GeoPlace(
            query = query.trim(),
            label = parts.joinToString(", "),
            latitude = chosen.getDouble("latitude"),
            longitude = chosen.getDouble("longitude")
        )
    }

    fun fetchHourly(lat: Double, lng: Double, zone: ZoneId = DteTou.ZONE): Pair<String, List<WeatherPoint>> {
        val url = "$FORECAST?latitude=$lat&longitude=$lng" +
            "&hourly=temperature_2m,precipitation_probability" +
            "&daily=sunrise,sunset" +
            "&temperature_unit=fahrenheit" +
            "&past_days=2&forecast_days=$FORECAST_DAYS" +
            "&timezone=${URLEncoder.encode(zone.id, Charsets.UTF_8.name())}"
        val body = get(url)
        return body to parseHourly(body, zone)
    }

    fun parseHourly(json: String, zone: ZoneId = DteTou.ZONE): List<WeatherPoint> {
        val hourly = JSONObject(json).optJSONObject("hourly") ?: return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m")
        val pops = hourly.optJSONArray("precipitation_probability")
        val points = ArrayList<WeatherPoint>(times.length())
        for (i in 0 until times.length()) {
            val time = parseLocalHour(times.optString(i), zone) ?: continue
            val temp = temps?.optDoubleOrNull(i) ?: continue
            val pop = pops?.optDoubleOrNull(i)
            points += WeatherPoint(time, temp, pop)
        }
        return points
    }

    fun parseDailySun(json: String, zone: ZoneId = DteTou.ZONE): List<SunTimes> {
        val daily = JSONObject(json).optJSONObject("daily") ?: return emptyList()
        val times = daily.optJSONArray("time") ?: return emptyList()
        val rise = daily.optJSONArray("sunrise") ?: return emptyList()
        val set = daily.optJSONArray("sunset") ?: return emptyList()
        val out = ArrayList<SunTimes>(times.length())
        for (i in 0 until times.length()) {
            val date = runCatching {
                LocalDate.parse(times.optString(i).take(10))
            }.getOrNull() ?: continue
            val sunrise = parseLocalHour(rise.optString(i), zone) ?: continue
            val sunset = parseLocalHour(set.optString(i), zone) ?: continue
            out += SunTimes(date, sunrise, sunset)
        }
        return out
    }

    private fun pickUsFirst(results: JSONArray): JSONObject {
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            if (item.optString("country_code").equals("US", ignoreCase = true)) return item
        }
        return results.getJSONObject(0)
    }

    private fun parseLocalHour(value: String, zone: ZoneId): Instant? {
        if (value.isBlank()) return null
        return runCatching {
            if (value.endsWith("Z") || value.contains('+')) {
                Instant.parse(value)
            } else {
                LocalDateTime.parse(value).atZone(zone).toInstant()
            }
        }.getOrNull()
    }

    private fun JSONArray.optDoubleOrNull(index: Int): Double? {
        if (isNull(index)) return null
        val value = optDouble(index, Double.NaN)
        return value.takeIf { !it.isNaN() }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WaywardSun/2.0 (Android)")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Weather request failed (HTTP $code).")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val FORECAST = "https://api.open-meteo.com/v1/forecast"

        /** Today plus 14 days, matching the 336-hour Solcast forecast. */
        private const val FORECAST_DAYS = 15
        private const val GEOCODE = "https://geocoding-api.open-meteo.com/v1/search"
    }
}
