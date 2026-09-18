package de.psmobile.ui

/** Selects only printer links explicitly associated with the active profile. */
object PrinterCompatibilityPolicy {
    fun <T> matching(
        printers: List<T>,
        selectedProfile: String,
        profileName: (T) -> String,
    ): List<T> {
        if (selectedProfile.isBlank()) return emptyList()
        return printers.filter {
            profileName(it).isNotBlank() && profileName(it) == selectedProfile
        }
    }
}
