package de.psmobile.shared.profileupdate

/** A platform-neutral decision contract for profile package update prompts. */
enum class ProfileUpdateDecision { NOW, LATER, SKIP_UNTIL_NEWER }

object ProfileUpdatePolicy {
    fun shouldOffer(remote: String, current: String, skipped: String?): Boolean {
        val remoteVersion = parse(remote) ?: return false
        val currentVersion = parse(current) ?: return false
        if (remoteVersion <= currentVersion) return false
        return skipped != remote
    }

    fun skippedVersionAfter(decision: ProfileUpdateDecision, offered: String): String? = when (decision) {
        ProfileUpdateDecision.SKIP_UNTIL_NEWER -> offered.takeIf { parse(it) != null }
        ProfileUpdateDecision.NOW, ProfileUpdateDecision.LATER -> null
    }

    private fun parse(source: String): List<Int>? {
        val parts = source.split('.')
        if (parts.size != 3 || parts.any { it.isEmpty() || it.any { char -> !char.isDigit() } }) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        for (index in indices) {
            val comparison = this[index].compareTo(other[index])
            if (comparison != 0) return comparison
        }
        return 0
    }
}
