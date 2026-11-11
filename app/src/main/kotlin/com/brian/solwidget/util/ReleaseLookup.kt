package com.brian.solwidget.util

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class PublishedRelease(
    val version: String,
    val downloadUrl: String?,
)

sealed interface LatestRelease {
    data class Found(val release: PublishedRelease) : LatestRelease
    data object Unavailable : LatestRelease
    data object Unreadable : LatestRelease
}

object ReleaseLookup {
    fun queryLatest(): LatestRelease {
        val connection = open(LATEST, "application/vnd.github+json", 15_000, followRedirects = true)
        try {
            if (connection.responseCode !in 200..299) return LatestRelease.Unavailable
            val body = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            val parsed = parse(body) ?: return LatestRelease.Unreadable
            return LatestRelease.Found(parsed)
        } finally {
            connection.disconnect()
        }
    }

    fun downloadApk(url: String, cacheRoot: File): File {
        if (!url.startsWith("https://")) error("download refused")
        val directory = File(cacheRoot, "updates").apply { mkdirs() }
        val destination = File(directory, "wayward-sun-update.apk")
        val partial = File(directory, "wayward-sun-update.apk.part")
        if (partial.exists() && !partial.delete()) error("replace failed")
        try {
            val connection = openFollowingRedirects(url)
            try {
                partial.outputStream().use { output ->
                    connection.inputStream.use { input -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
            val header = ByteArray(2)
            val read = partial.inputStream().use { input -> input.read(header) }
            if (read != 2 || !hasZipMagic(header)) {
                partial.delete()
                error("not an apk")
            }
            if (destination.exists() && !destination.delete()) error("replace failed")
            if (!partial.renameTo(destination)) error("rename failed")
            return destination
        } catch (exception: Exception) {
            partial.delete()
            throw exception
        }
    }

    internal fun parse(body: String): PublishedRelease? {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val version = json.optString("tag_name")
        if (version.isBlank()) return null
        return PublishedRelease(version, apkUrl(json))
    }

    private fun apkUrl(body: JSONObject): String? {
        val assets = body.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk")) continue
            val url = asset.optString("browser_download_url")
            if (url.isNotBlank()) return url
        }
        return null
    }

    private fun openFollowingRedirects(start: String): HttpURLConnection {
        var current = start
        repeat(MAX_REDIRECTS) {
            val connection = open(current, APK_TYPE, 60_000, followRedirects = false)
            val code = try {
                connection.responseCode
            } catch (exception: Exception) {
                connection.disconnect()
                throw exception
            }
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) error("redirect missing")
                val resolved = URL(URL(current), location).toString()
                if (!resolved.startsWith("https://")) error("redirect refused")
                current = resolved
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                error("download failed")
            }
            return connection
        }
        error("redirect refused")
    }

    private fun open(url: String, accept: String, readTimeout: Int, followRedirects: Boolean): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = followRedirects
        connection.useCaches = false
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("User-Agent", "WaywardSun")
        connection.connectTimeout = 20_000
        connection.readTimeout = readTimeout
        return connection
    }

    private const val LATEST = "https://api.github.com/repos/bhwithun/wayward-sun/releases/latest"
    private const val APK_TYPE = "application/vnd.android.package-archive"
    private const val MAX_REDIRECTS = 5
}
