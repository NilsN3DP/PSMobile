package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleWorkspaceStateTest {
    @Test
    fun simpleWorkspaceUsesTheSceneRevisionFromTheSharedService() {
        assertEquals(42, SimpleWorkspaceState.invalidateKey(42))
    }

    @Test
    fun emptyWorkspaceStillExposesModelImport() {
        assertTrue(SimpleWorkspaceState.showModelAddAction(0))
    }
}
