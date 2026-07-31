package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppModeTest {
    @Test
    fun modeValuesExposeTheTwoWorkflows() {
        assertEquals(listOf(AppMode.SIMPLE, AppMode.ADVANCED), AppMode.entries)
    }
}
