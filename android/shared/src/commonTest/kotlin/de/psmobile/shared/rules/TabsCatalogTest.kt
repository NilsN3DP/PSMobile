package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TabsCatalogTest {

    private val beispiel = """
    {
      "print": [
        {
          "title": "Layers and perimeters",
          "icon": "layers",
          "groups": [
            {
              "title": "Layer height",
              "options": [{"key": "layer_height"}, {"key": "first_layer_height"}]
            },
            {
              "title": "Horizontal shells",
              "options": [
                {"key": "top_solid_layers", "line": "Solid layers"},
                {"key": "bottom_solid_layers", "line": "Solid layers"},
                {"key": "extra_perimeters"}
              ]
            }
          ]
        }
      ],
      "printer": [
        {
          "title": "Extruder {n}",
          "icon": "funnel",
          "per_extruder": true,
          "groups": [
            {"title": "Size", "options": [{"key": "nozzle_diameter"}]}
          ]
        },
        {
          "title": "Custom G-code",
          "icon": "cog",
          "groups": [
            {"title": "Start", "options": [{"key": "start_gcode", "code": true}]}
          ]
        }
      ]
    }
    """

    @Test
    fun `die Reihenfolge aus der Datei bleibt erhalten`() {
        // Sie stammt aus Tab.cpp und ist uebernommene Information, keine
        // Entwurfsentscheidung - Sortieren waere ein Fehler.
        val tabs = TabsCatalog.parse(beispiel)
        val seite = tabs.getValue("print").first()
        assertEquals("Layers and perimeters", seite.title)
        assertEquals(listOf("Layer height", "Horizontal shells"),
                     seite.groups.map { it.title })
        assertEquals(listOf("layer_height", "first_layer_height"),
                     seite.groups[0].options.map { it.key })
    }

    @Test
    fun `mehrzeilige G-code-Felder sind als solche erkennbar`() {
        val tabs = TabsCatalog.parse(beispiel)
        val option = tabs.getValue("printer")[1].groups[0].options[0]
        assertTrue(option.code, "start_gcode muss mehrzeilig sein")
        assertFalse(tabs.getValue("print")[0].groups[0].options[0].code)
    }

    @Test
    fun `eine Extruderseite wird auf die Duesenzahl vervielfacht`() {
        val seiten = TabsCatalog.parse(beispiel).getValue("printer")
        val fuenf = TabsCatalog.expand(seiten, 5)
        assertEquals(
            listOf("Extruder 1", "Extruder 2", "Extruder 3", "Extruder 4",
                   "Extruder 5", "Custom G-code"),
            fuenf.map { it.title },
        )
    }

    @Test
    fun `bei einer Duese entfaellt die Nummer`() {
        // "Extruder 1" bei einem einzigen Extruder stiftet nur Verwirrung.
        val seiten = TabsCatalog.parse(beispiel).getValue("printer")
        assertEquals(listOf("Extruder", "Custom G-code"),
                     TabsCatalog.expand(seiten, 1).map { it.title })
    }

    @Test
    fun `zusammengehoerende Parameter stehen in einer Zeile`() {
        val gruppe = TabsCatalog.parse(beispiel)
            .getValue("print")[0].groups[1]
        val zeilen = TabsCatalog.lines(gruppe)
        assertEquals(2, zeilen.size)
        assertEquals("Solid layers", zeilen[0].title)
        assertEquals(listOf("top_solid_layers", "bottom_solid_layers"),
                     zeilen[0].options.map { it.key })
        // Was keine Zeilenangabe hat, steht fuer sich.
        assertEquals(null, zeilen[1].title)
        assertEquals(listOf("extra_perimeters"), zeilen[1].options.map { it.key })
    }

    @Test
    fun `unbekannte Felder stoeren nicht`() {
        // extract-ui.py kann jederzeit mehr mitliefern, ohne dass die
        // Apps darueber stolpern.
        val tabs = TabsCatalog.parse(
            """{"print":[{"title":"A","icon":"i","neu":42,"groups":[]}]}""")
        assertEquals("A", tabs.getValue("print").first().title)
    }
}
