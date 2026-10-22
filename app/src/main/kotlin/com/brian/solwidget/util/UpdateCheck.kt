package com.brian.solwidget.util

import java.net.HttpURLConnection
import java.net.URL

data class RemoteRelease(val tag: String, val pageUrl: String, val apkUrl: String = "")

enum class CommitRelation { SAME, LOCAL_BEHIND, LOCAL_AHEAD, DIVERGED, UNKNOWN }

data class UpdateDecision(val message: String, val apkUrl: String? = null)

object UpdateSchedule {
    const val AUTO_INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun isDue(lastCheckAtMillis: Long, nowMillis: Long, intervalMs: Long = AUTO_INTERVAL_MS): Boolean {
        if (lastCheckAtMillis <= 0L) return true
        return nowMillis - lastCheckAtMillis >= intervalMs
    }
}

fun decideUpdate(
    local: BuildLabel,
    remote: RemoteRelease?,
    relation: CommitRelation
): UpdateDecision {
    if (remote == null) {
        return UpdateDecision("No release has been published.")
    }
    val version = Semver.parse(remote.tag)
        ?: return UpdateDecision("Latest release ${remote.tag} isn't a version number.")
    val localRelease = Semver.parse(local.release)
    if (localRelease != null) {
        return when {
            version > localRelease -> installOffer(version, remote.apkUrl)
            version == localRelease -> UpdateDecision("You're on release $localRelease.")
            else -> UpdateDecision("This is release $localRelease. Published release is $version.")
        }
    }
    return when (relation) {
        CommitRelation.LOCAL_BEHIND -> installOffer(version, remote.apkUrl)
        CommitRelation.SAME -> UpdateDecision("This build matches release $version.")
        CommitRelation.LOCAL_AHEAD -> UpdateDecision("This build is ahead of release $version.")
        CommitRelation.DIVERGED -> UpdateDecision("Release $version is on a different commit.")
        CommitRelation.UNKNOWN -> UpdateDecision("Couldn't compare this build. Latest release is $version.")
    }
}

private fun installOffer(version: Semver, apkUrl: String): UpdateDecision {
    val apk = apkUrl.takeIf { it.isNotBlank() }
    return if (apk == null) {
        UpdateDecision("Release $version has no APK to install.")
    } else {
        UpdateDecision("Installing release $version.", apk)
    }
}

interface ReleaseSource {
    fun latest(): RemoteRelease?
    fun relation(commit: String, tag: String): CommitRelation
}

object UpdateChecker {
    fun check(local: BuildLabel, source: ReleaseSource = GithubReleases()): UpdateDecision {
        val remote = try {
            source.latest()
        } catch (_: Exception) {
            return UpdateDecision("Couldn't check for updates.")
        }
        if (remote == null || local.commit.isBlank() || Semver.parse(local.release) != null) {
            return decideUpdate(local, remote, CommitRelation.UNKNOWN)
        }
        val relation = try {
            source.relation(local.commit, remote.tag)
        } catch (_: Exception) {
            CommitRelation.UNKNOWN
        }
        return decideUpdate(local, remote, relation)
    }
}

class GithubReleases : ReleaseSource {
    override fun latest(): RemoteRelease? {
        val response = get("$API/releases/latest")
        if (response.code == 404) return null
        if (response.code !in 200..299) {
            throw IllegalStateException("GitHub releases returned HTTP ${response.code}.")
        }
        val tag = jsonString(response.body, "tag_name") ?: return null
        val page = jsonString(response.body, "html_url").orEmpty()
        return RemoteRelease(tag, page, apkDownloadUrl(response.body).orEmpty())
    }

    override fun relation(commit: String, tag: String): CommitRelation {
        if (!commit.matches(SHA) || !tag.matches(TAG)) return CommitRelation.UNKNOWN
        val response = get("$API/compare/$commit...$tag")
        if (response.code !in 200..299) return CommitRelation.UNKNOWN
        return when (jsonString(response.body, "status")) {
            "identical" -> CommitRelation.SAME
            "ahead" -> CommitRelation.LOCAL_BEHIND
            "behind" -> CommitRelation.LOCAL_AHEAD
            "diverged" -> CommitRelation.DIVERGED
            else -> CommitRelation.UNKNOWN
        }
    }

    private fun get(url: String): HttpText {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WaywardSun")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpText(code, body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API = "https://api.github.com/repos/bhwithun/wayward-sun"
        private val SHA = Regex("[0-9a-fA-F]{40}")
        private val TAG = Regex("[A-Za-z0-9._\\-]+")
    }
}

internal data class HttpText(val code: Int, val body: String)

internal fun jsonString(body: String, key: String): String? {
    val match = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""").find(body) ?: return null
    return unescapeJson(match.groupValues[1])
}

internal fun apkDownloadUrl(body: String): String? {
    val pattern = Regex(""""browser_download_url"\s*:\s*"((?:\\.|[^"\\])*)"""")
    return pattern.findAll(body)
        .map { unescapeJson(it.groupValues[1]) }
        .firstOrNull { it.substringBefore('?').endsWith(".apk", ignoreCase = true) }
}

private fun unescapeJson(raw: String): String = buildString {
    var index = 0
    while (index < raw.length) {
        val ch = raw[index]
        if (ch != '\\' || index + 1 >= raw.length) {
            append(ch)
            index++
            continue
        }
        when (val escaped = raw[index + 1]) {
            '\\', '/' -> append(escaped)
            '"' -> append('"')
            'n' -> append('\n')
            't' -> append('\t')
            else -> append(escaped)
        }
        index += 2
    }
}
