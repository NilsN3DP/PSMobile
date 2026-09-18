package de.psmobile.shared.net

/** Zentrale, fail-closed Schalter fuer experimentelle Druckerfunktionen. */
object ExperimentalFeatureFlags {
    const val localPrusaLinkPairingDefault: Boolean = false

    fun localPrusaLinkPairingEnabled(userOptIn: Boolean): Boolean = userOptIn
}
