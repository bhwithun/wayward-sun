package com.brian.solwidget.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class CachePayload(
    val resourceId: String,
    val forecastsJson: String,
    val actualsJson: String,
    val fetchedAt: Instant?,
    val source: String?,
    val requestsUsed: Int,
    val autoFetchesUsed: Int,
    val requestsDay: String?,
    val message: String?
)

class CacheApi {
    fun fetchSnapshot(baseUrl: String): CachePayload {
        val root = JSONObject(get("$baseUrl/cache"))
        if (root.has("error") && !root.has("forecasts")) {
            throw IllegalStateException(root.optString("error", "Cache request failed."))
        }
        val fetchedAt = root.optString("fetchedAt").takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return CachePayload(
            resourceId = root.optString("resourceId").ifBlank { AppStorage.DEFAULT_RESOURCE_ID },
            forecastsJson = root.optJSONObject("forecasts")?.toString().orEmpty(),
            actualsJson = root.optJSONObject("actuals")?.toString().orEmpty(),
            fetchedAt = fetchedAt,
            source = root.optString("source").takeIf { it.isNotBlank() },
            requestsUsed = root.optInt("requestsUsed", 0),
            autoFetchesUsed = root.optInt("autoFetchesUsed", 0),
            requestsDay = root.optString("requestsDay").takeIf { it.isNotBlank() },
            message = root.optString("message").takeIf { it.isNotBlank() }
        )
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SolWidget/1.0 (Android)")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optString("error").takeIf { it.isNotBlank() }
                }.getOrNull() ?: "Cache request failed (HTTP $code)."
                throw IllegalStateException(message)
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
