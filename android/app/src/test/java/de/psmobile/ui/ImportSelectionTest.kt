package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSelectionTest {

    @Test
    fun `a single file still gets the project question`() {
        assertTrue(ImportSelection.askAboutProject(1))
    }

    @Test
    fun `a multi selection never asks, because a project would displace the rest`() {
        assertFalse(ImportSelection.askAboutProject(2))
        assertFalse(ImportSelection.askAboutProject(9))
    }

    @Test
    fun `an empty selection is not treated as a single file`() {
        assertFalse(ImportSelection.askAboutProject(0))
    }

    @Test
    fun `the summary counts what was loaded`() {
        val text = ImportSelection.summary(loaded = 3, total = 4, projectsAsObjects = 0)
        assertTrue(text, text.contains("3"))
        assertTrue(text, text.contains("4"))
    }

    @Test
    fun `a 3MF brought in as objects is spelled out, not silently downgraded`() {
        val text = ImportSelection.summary(loaded = 3, total = 3, projectsAsObjects = 1)
        assertTrue(text, text.contains("3MF"))
        assertTrue(text, text.length > ImportSelection.summary(3, 3, 0).length)
    }

    @Test
    fun `without a downgraded 3MF the summary stays short`() {
        val text = ImportSelection.summary(loaded = 2, total = 2, projectsAsObjects = 0)
        assertFalse(text, text.contains("3MF"))
        assertEquals(1, text.count { it == '.' })
    }
}
