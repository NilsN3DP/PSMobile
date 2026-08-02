package de.psmobile.ui

import de.psmobile.core.PsmCore
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryToolUiTest {
    @Test
    fun `split into objects explains the result before it is chosen`() {
        assertEquals(
            "Edit disconnected meshes separately.",
            geometryToolDescription("In Objekte teilen"),
        )
    }

    @Test
    fun `cut result keeps exactly the parts selected by the simple choice`() {
        assertEquals(true to true, retainedCutParts(CutResult.BOTH))
        assertEquals(true to false, retainedCutParts(CutResult.UPPER))
        assertEquals(false to true, retainedCutParts(CutResult.LOWER))
    }

    @Test
    fun `flatten gives a concrete next touch instruction`() {
        assertEquals(
            "Tap a model face that should rest on the print bed.",
            surfaceToolInstruction(SurfaceToolMode.Flatten),
        )
    }

    @Test
    fun `support paint describes brush interaction`() {
        assertEquals(
            "Paint faces where supports should be generated.",
            surfaceToolInstruction(
                SurfaceToolMode.Paint(PsmCore.PaintTool.SUPPORT, 1, 3f),
            ),
        )
    }
}
