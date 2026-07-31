package de.psmobile.slicing.profileupdate

import java.net.URI

/** Strict, comparable profile-channel version; prereleases are not accepted. */
data class ProfileVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ProfileVersion> {
    override fun compareTo(other: ProfileVersion): Int = compareValuesBy(
        this,
        other,
        ProfileVersion::major,
        ProfileVersion::minor,
        ProfileVersion::patch,
    )

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")

        fun parse(raw: String): ProfileVersion? {
            val match = pattern.matchEntire(raw) ?: return null
            val (major, minor, patch) = match.destructured
            return ProfileVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}

data class ProfileManifest(
    val version: ProfileVersion,
    val packageUrl: URI,
    val sha256: String,
    val minSlic3rVersion: ProfileVersion,
    val releaseNotes: List<String>,
)

enum class OfferDecision { UpdateNow, Later, SkipUntilNewer }

sealed interface ProfileUpdateState {
    data object Idle : ProfileUpdateState
    data object Checking : ProfileUpdateState
    data class Offer(val manifest: ProfileManifest) : ProfileUpdateState
    data class Downloading(val manifest: ProfileManifest, val percent: Int?) : ProfileUpdateState
    data class ReadyToApply(val manifest: ProfileManifest) : ProfileUpdateState
    data class Deferred(val manifest: ProfileManifest, val reason: String) : ProfileUpdateState
    data class Failed(val message: String) : ProfileUpdateState
}

object UpdateOffer {
    /** A skipped version stays silent, while a later version is offered again. */
    fun shouldOffer(
        remote: ProfileVersion,
        skipped: ProfileVersion?,
        shownThisRun: Boolean,
    ): Boolean = !shownThisRun && (skipped == null || remote > skipped)
}
