package com.brian.solwidget.util

data class BuildLabel(
    val text: String,
    val release: String,
    val commit: String
) {
    val isRelease: Boolean get() = release.isNotEmpty()

    companion object {
        fun resolve(tagText: String, dirty: Boolean, shortHash: String, commit: String): BuildLabel {
            val release = if (dirty) null else highestRelease(tagText)
            val hash = shortHash.trim()
            return BuildLabel(
                text = release?.toString() ?: hash,
                release = release?.toString().orEmpty(),
                commit = commit.trim()
            )
        }

        fun highestRelease(tagText: String): Semver? {
            return tagText.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { Semver.parse(it) }
                .maxOrNull()
        }
    }
}

data class Semver(val major: Int, val minor: Int, val patch: Int) : Comparable<Semver> {
    override fun compareTo(other: Semver): Int {
        return compareValuesBy(this, other, Semver::major, Semver::minor, Semver::patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun parse(raw: String): Semver? {
            val match = pattern.matchEntire(raw.trim()) ?: return null
            return Semver(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt()
            )
        }
    }
}
