package com.johang.audiocinemateca.domain.model

/**
 * Constantes y utilidades para los canales de actualización de Audiocinemateca.
 */
object UpdateChannels {
    const val STABLE = "stable"
    const val BETA = "beta"

    fun normalize(channel: String?): String =
        if (channel?.trim()?.lowercase() == BETA) BETA else STABLE

    fun displayName(channel: String?): String =
        if (normalize(channel) == BETA) "Beta" else "Estable"
}

/**
 * Representación y comparador estricto SemVer 2.0.0 con soporte para pre-releases
 * (ej. `3.4.0-beta.1`, `3.4.0-rc.2`, `v3.3.2`).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList()
) : Comparable<SemVer> {

    val isPrerelease: Boolean
        get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // Según SemVer 2.0.0: una versión sin pre-release es MAYOR que una con pre-release.
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1

        val maxLen = minOf(preRelease.size, other.preRelease.size)
        for (i in 0 until maxLen) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            if (a == b) continue

            val aNum = a.toIntOrNull()
            val bNum = b.toIntOrNull()
            return when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1 // Numéricos siempre tienen menor precedencia que alfanuméricos
                bNum != null -> 1
                else -> a.compareTo(b)
            }
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    companion object {
        private val SEMVER_REGEX = Regex(
            """^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+.*)?$"""
        )

        fun parseOrNull(rawVersion: String?): SemVer? {
            val trimmed = rawVersion?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val match = SEMVER_REGEX.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: 0
            val patch = match.groupValues[3].toIntOrNull() ?: 0
            val preRaw = match.groupValues[4]
            val preParts = if (preRaw.isEmpty()) {
                emptyList()
            } else {
                preRaw.split(".").filter { it.isNotEmpty() }
            }
            return SemVer(major = major, minor = minor, patch = patch, preRelease = preParts)
        }

        fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
            val remote = parseOrNull(remoteVersion)
            val current = parseOrNull(currentVersion)
            return if (remote != null && current != null) {
                remote > current
            } else {
                remoteVersion.trim().removePrefix("v").removePrefix("V") >
                    currentVersion.trim().removePrefix("v").removePrefix("V")
            }
        }
    }
}
