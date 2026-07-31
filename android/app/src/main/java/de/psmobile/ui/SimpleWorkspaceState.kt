package de.psmobile.ui

/** Keeps the Simple canvas tied to the same scene revision as Advanced. */
object SimpleWorkspaceState {
    fun invalidateKey(sceneRevision: Int): Int = sceneRevision
    fun showModelAddAction(modelCount: Int): Boolean = modelCount >= 0
}
