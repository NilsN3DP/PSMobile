package de.psmobile.shared.rules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Ein Anteil referenziert einen physischen Kopf, 0-basiert für die App. */
data class ColorMixComponent(val head: Int, val ratio: Double)

/** Ein virtueller Extruder, den libslic3r beim Slicen in Köpfe auflöst. */
data class ColorMixRecipe(
    val id: Int,
    val components: List<ColorMixComponent>,
    val color: String? = null,
)

/**
 * Exakte 3MF-Darstellung für PrusaSlicers FullSpectrum/ColorMix.
 * Die UI arbeitet mit 0-basierten Kopfnummern, im gespeicherten JSON sind
 * sie – wie auch die Extruderzuordnung – 1-basiert.
 */
object ColorMixCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(source: String): List<ColorMixRecipe> = runCatching {
        val root = json.parseToJsonElement(source).jsonObject
        val physicalCount = root["physical_extruders"]?.jsonArray?.size ?: Int.MAX_VALUE
        root["virtual_extruders"]?.jsonArray.orEmpty().mapNotNull { item ->
            val entry = item.jsonObject
            if (entry["kind"]?.jsonPrimitive?.content != "fullspectrum") return@mapNotNull null
            val id = entry["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            runCatching {
                require(id > physicalCount) { "Virtuelle ID kollidiert mit einem physischen Kopf" }
                val components = entry["components"]?.jsonArray.orEmpty().map { raw ->
                    val part = raw.jsonObject
                    val physical = part["extruder"]?.jsonPrimitive?.intOrNull
                        ?: error("ColorMix-Komponente ohne Kopf")
                    val ratio = part["ratio"]?.jsonPrimitive?.doubleOrNull
                        ?: error("ColorMix-Komponente ohne Anteil")
                    require(physical in 1..physicalCount) { "ColorMix-Kopf existiert nicht" }
                    ColorMixComponent(physical - 1, ratio)
                }
                ColorMixRecipe(id, normalize(components), entry["color"]?.jsonPrimitive?.contentOrNull)
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun encode(
        physicalColors: List<String>,
        recipes: List<ColorMixRecipe>,
    ): String {
        val validated = recipes.map { recipe ->
            require(recipe.id > physicalColors.size) { "Virtuelle ID muss hinter den physischen Köpfen liegen" }
            recipe.copy(components = normalize(recipe.components).also { components ->
                require(components.all { it.head in physicalColors.indices }) { "ColorMix-Kopf existiert nicht" }
            })
        }
        require(validated.map(ColorMixRecipe::id).distinct().size == validated.size) { "Virtuelle IDs müssen eindeutig sein" }

        val root = buildJsonObject {
            put("version", 1)
            put("physical_extruders", buildJsonArray {
                physicalColors.forEachIndexed { index, color ->
                    add(buildJsonObject { put("id", index + 1); put("color", color) })
                }
            })
            put("virtual_extruders", buildJsonArray {
                validated.forEach { recipe ->
                    add(buildJsonObject {
                        put("id", recipe.id)
                        put("kind", "fullspectrum")
                        recipe.color?.takeIf(String::isNotBlank)?.let { put("color", it) }
                        put("components", buildJsonArray {
                            recipe.components.forEach { component ->
                                add(buildJsonObject {
                                    put("extruder", component.head + 1)
                                    put("ratio", component.ratio)
                                })
                            }
                        })
                    })
                }
            })
        }
        return root.toString()
    }

    /** 2–3 verschiedene physische Köpfe, immer auf Summe 1 normiert. */
    fun normalize(raw: List<ColorMixComponent>): List<ColorMixComponent> {
        val merged = raw.filter { it.head >= 0 && it.ratio > 0.0 }
            .groupBy(ColorMixComponent::head)
            .map { (head, values) -> ColorMixComponent(head, values.sumOf(ColorMixComponent::ratio)) }
            .sortedBy(ColorMixComponent::head)
        require(merged.size in 2..3) { "ColorMix benötigt zwei oder drei verschiedene Köpfe" }
        val total = merged.sumOf(ColorMixComponent::ratio)
        require(total > 0.0) { "ColorMix-Anteile müssen positiv sein" }
        return merged.map { it.copy(ratio = it.ratio / total) }
    }
}
