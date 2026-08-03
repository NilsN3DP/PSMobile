package de.psmobile.shared.rules

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class ColorMixCodecTest {
    @Test fun `two heads are normalized and serialized in desktop numbering`() {
        val source = ColorMixCodec.encode(
            listOf("#ff0000", "#0000ff"),
            listOf(ColorMixRecipe(3, listOf(ColorMixComponent(0, 2.0), ColorMixComponent(1, 1.0)))),
        )
        val recipe = ColorMixCodec.decode(source).single()
        assertEquals(3, recipe.id)
        assertEquals(2, recipe.components.size)
        assertEquals(2.0 / 3.0, recipe.components[0].ratio, 0.0001)
        assertEquals(1.0 / 3.0, recipe.components[1].ratio, 0.0001)
    }

    @Test fun `a blend requires two or three distinct physical heads`() {
        val invalid = runCatching { ColorMixCodec.normalize(listOf(ColorMixComponent(0, 1.0))) }
        assertTrue(invalid.isFailure)
    }

    @Test fun `physical positions are one based through eight`() {
        assertEquals((1..8).map(Int::toString), ExtruderPresentation.positions(8).map { it.label })
    }

    @Test fun `equal red and blue components preview as purple`() {
        assertEquals(
            "#800080",
            ColorMixCodec.previewColor(
                listOf("#FF0000", "#0000FF"),
                listOf(ColorMixComponent(0, 1.0), ColorMixComponent(1, 1.0)),
            ),
        )
    }

    @Test fun `invalid source colors have no deceptive mix preview`() {
        assertNull(
            ColorMixCodec.previewColor(
                listOf("#FF0000", "not-a-colour"),
                listOf(ColorMixComponent(0, 1.0), ColorMixComponent(1, 1.0)),
            ),
        )
    }

    @Test fun `malformed recipes do not hide other valid recipes`() {
        val decoded = ColorMixCodec.decode(
            """{
              "physical_extruders":[{"id":1,"color":"#f00"},{"id":2,"color":"#00f"}],
              "virtual_extruders":[
                {"id":2,"kind":"fullspectrum","components":[{"extruder":1,"ratio":.5},{"extruder":2,"ratio":.5}]},
                {"id":3,"kind":"fullspectrum","components":[{"extruder":1,"ratio":.25},{"extruder":2,"ratio":.75}]}
              ]
            }""",
        )

        assertEquals(listOf(3), decoded.map(ColorMixRecipe::id))
        assertEquals(0.75, decoded.single().components.last().ratio, 0.0001)
    }
}
