package com.clouddroidide.android.updater

/**
 * Semantic version parser and comparator supporting tags like "v1.2.3", "1.2.3-beta1", etc.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String = ""
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
        if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
        return preRelease.compareTo(other.preRelease)
    }

    companion object {
        fun parse(raw: String): SemVer? {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            if (cleaned.isEmpty()) return null
            val parts = cleaned.split("-", limit = 2)
            val nums = parts[0].split(".")
            if (nums.isEmpty()) return null
            val major = nums.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = nums.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = nums.getOrNull(2)?.toIntOrNull() ?: 0
            val pre = parts.getOrNull(1) ?: ""
            return SemVer(major, minor, patch, pre)
        }

        fun isNewer(remoteTag: String, localVersion: String): Boolean {
            val remote = parse(remoteTag) ?: return false
            val local = parse(localVersion) ?: return false
            return remote > local
        }
    }
}
