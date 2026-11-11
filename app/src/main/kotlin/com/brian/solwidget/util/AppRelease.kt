package com.brian.solwidget.util

data class ReleaseOffer(
    val versionName: String,
    val apkUrl: String,
)

const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

const val APP_PACKAGE = "com.brian.solwidget"

fun isNewerRelease(installed: String, published: String): Boolean {
    val current = releaseNumbers(installed)
    val latest = releaseNumbers(published)
    if (current.isEmpty() || latest.isEmpty()) return false
    val count = maxOf(current.size, latest.size)
    for (index in 0 until count) {
        val here = current.getOrElse(index) { 0 }
        val there = latest.getOrElse(index) { 0 }
        if (there != here) return there > here
    }
    return false
}

fun isReleaseCheckDue(checkedAtMillis: Long, nowMillis: Long): Boolean {
    return nowMillis - checkedAtMillis >= MILLIS_PER_DAY
}

fun shouldPromptForUpdate(
    versionName: String,
    dismissedVersion: String?,
    promptedVersion: String?,
): Boolean {
    val version = versionName.trim()
    if (version.isEmpty()) return false
    if (dismissedVersion == version) return false
    if (promptedVersion == version) return false
    return true
}

fun releaseOffer(published: String, downloadUrl: String?, installed: String): ReleaseOffer? {
    val versionName = published.trim().removePrefix("v").removePrefix("V")
    if (versionName.isEmpty()) return null
    if (downloadUrl == null || !downloadUrl.startsWith("https://")) return null
    if (!isNewerRelease(installed, versionName)) return null
    return ReleaseOffer(versionName, downloadUrl)
}

fun isInstallableUpgrade(
    archivedPackage: String?,
    archivedVersionCode: Long,
    installedVersionCode: Long,
): Boolean {
    return archivedPackage == APP_PACKAGE && archivedVersionCode > installedVersionCode
}

fun hasZipMagic(header: ByteArray): Boolean {
    return header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
}

private fun releaseNumbers(name: String): List<Int> {
    val core = name.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore("+")
        .substringBefore("-")
    if (core.isEmpty()) return emptyList()
    return core.split(".").map { part -> part.toIntOrNull() ?: return emptyList() }
}
