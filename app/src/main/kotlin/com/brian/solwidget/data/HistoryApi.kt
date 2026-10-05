package com.brian.solwidget.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject

class HistoryApi {
    fun fetch(baseUrl: String, from: Instant, to: Instant): HistoryPage {
        val fromEncoded = URLEncoder.encode(from.toString(), Charsets.UTF_8.name())
        val toEncoded = URLEncoder.encode(to.toString(), Charsets.UTF_8.name())
        val root = JSONObject(get("${baseUrl.trimEnd('/')}/history?from=$fromEncoded&to=$toEncoded"))
        if (root.has("error") && !root.has("points")) {
            throw IllegalStateException(root.optString("error", "History request failed."))
        }
        val pointsJson = root.optJSONArray("points")
        val points = buildList {
            if (pointsJson != null) {
                for (index in 0 until pointsJson.length()) {
                    val row = pointsJson.optJSONObject(index) ?: continue
                    val periodEnd = row.optString("periodEnd").takeIf { it.isNotBlank() }
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: continue
                    add(
                        HistoryInterval(
                            periodEnd = periodEnd,
                            actualKw = row.optionalDouble("actualKw"),
                            forecastKw = row.optionalDouble("forecastKw"),
                            periodHours = row.optionalDouble("periodHours") ?: 0.5,
                            tempF = row.optionalDouble("tempF"),
                            precipPct = row.optionalDouble("precipPct")
                        )
                    )
                }
            }
        }
        val sunJson = root.optJSONArray("sun")
        val sun = buildList {
            if (sunJson != null) {
                for (index in 0 until sunJson.length()) {
                    val row = sunJson.optJSONObject(index) ?: continue
                    val date = row.optString("date").takeIf { it.isNotBlank() }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: continue
                    val sunrise = row.optString("sunrise").let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: continue
                    val sunset = row.optString("sunset").let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: continue
                    add(SunTimes(date, sunrise, sunset))
                }
            }
        }
        return HistoryPage(from = from, to = to, points = points, sun = sun)
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WaywardSun/2.0 (Android)")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optString("error").takeIf { it.isNotBlank() }
                }.getOrNull() ?: "History request failed (HTTP $code)."
                throw IllegalStateException(message)
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.optionalDouble(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    val value = optDouble(name, Double.NaN)
    return value.takeIf { !it.isNaN() }
}
