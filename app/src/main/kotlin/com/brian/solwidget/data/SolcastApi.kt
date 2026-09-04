package com.brian.solwidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

class SolcastApi {

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

    companion object {
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
