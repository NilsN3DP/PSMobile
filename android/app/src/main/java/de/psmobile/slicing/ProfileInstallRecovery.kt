package de.psmobile.slicing

/** Decides whether a persisted printer selection must be repaired after an app update. */
object ProfileInstallRecovery {
    fun needsRecovery(
        selectedKeys: Collection<String>,
        installSucceeded: Boolean,
        printerCount: Int,
        printCount: Int,
        filamentCount: Int,
    ): Boolean {
        if (!installSucceeded) return true
        if (selectedKeys.isEmpty()) return false
        return printerCount == 0 || printCount == 0 || filamentCount == 0
    }
}
