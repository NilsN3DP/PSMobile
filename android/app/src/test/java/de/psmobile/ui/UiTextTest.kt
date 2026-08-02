package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {
    @Test
    fun `english uses the English application copy`() {
        assertEquals("Workspace", applicationText("en", "Workspace", "Arbeitsbereich"))
    }

    @Test
    fun `german uses the German application copy`() {
        assertEquals("Arbeitsbereich", applicationText("de", "Workspace", "Arbeitsbereich"))
    }
}
