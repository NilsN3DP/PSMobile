package de.psmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import de.psmobile.shared.rules.ColorMixCodec
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.psTouch
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.psmobile.slicing.SlicerService
import de.psmobile.shared.rules.ColorMixComponent
import de.psmobile.shared.rules.ColorMixRecipe
import de.psmobile.ui.theme.PrusaColors

/** Advanced-Editor für echte virtuelle ColorMix-Extruder. */
@Composable
fun ColorMixScreen(service: SlicerService, onClose: () -> Unit) {
    val state by service.colorMix.collectAsState()
    val presets by service.presets.collectAsState()
    val heads = presets.extruders
    var selectedHeads by remember(heads.size) { mutableStateOf(setOf(0, 1).filter { it < heads.size }.toSet()) }
    var ratio by remember { mutableStateOf(0.5f) }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ColorMix", color = PrusaColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(
            PsUi.appText("A virtual extruder alternates between two or three heads during printing. It creates a mixed colour appearance — it does not physically melt-mix filament.", "Ein virtueller Extruder wechselt während des Drucks zwischen zwei oder drei Köpfen. Das erzeugt einen gemischten Farbeindruck – kein physisches Schmelzmischen."),
            color = PrusaColors.TextMuted,
        )
        if (!state.available) {
            Text(
                PsUi.appText("The installed slicer core does not yet contain the ColorMix ABI. Recipes will activate after the next native-core build.", "Der installierte Slicer-Core enthält die ColorMix-ABI noch nicht. Das Rezept wird erst nach dem nächsten Native-Core-Build aktiviert."),
                color = PrusaColors.TextMuted,
            )
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(PsUi.appText("Back", "Zurück")) }
            return@Column
        }

        var fehlgeschlagen by remember { mutableStateOf(false) }
        Text(PsUi.appText("Choose heads (2–3)", "Köpfe auswählen (2–3)"), color = PrusaColors.TextPrimary)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            heads.forEach { head ->
                FilterChip(
                    selected = head.index in selectedHeads,
                    onClick = {
                        selectedHeads = if (head.index in selectedHeads) {
                            selectedHeads - head.index
                        } else if (selectedHeads.size < 3) {
                            selectedHeads + head.index
                        } else selectedHeads
                    },
                    label = { Text("T${head.index + 1}") },
                )
            }
        }
        if (selectedHeads.size == 2) {
            Text(PsUi.appText("First-head share: ${(ratio * 100).toInt()} %", "Anteil des ersten Kopfs: ${(ratio * 100).toInt()} %"), color = PrusaColors.TextMuted)
            Slider(value = ratio, onValueChange = { ratio = it }, valueRange = 0.1f..0.9f)
        }

        // Die Anteile, aus denen Vorschau und Rezept entstehen - beide
        // aus derselben Rechnung, sonst zeigt die Vorschau etwas
        // anderes, als gespeichert wird.
        val ordered = selectedHeads.sorted()
        val components = when (ordered.size) {
            2 -> listOf(
                ColorMixComponent(ordered[0], ratio.toDouble()),
                ColorMixComponent(ordered[1], 1.0 - ratio),
            )
            3 -> ordered.map { ColorMixComponent(it, 1.0 / ordered.size) }
            else -> emptyList()
        }
        // Welche Farbe dabei herauskommt, rechnet der gemeinsame Codec -
        // dieselbe Zahl wie drueben.
        val vorschau = if (components.isEmpty()) null else ColorMixCodec.previewColor(
            physicalColors = heads.map { it.color.ifBlank { "#808080" } },
            components = components,
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(psTouch(62))
                    .background(
                        androidx.compose.ui.graphics.Color(
                            android.graphics.Color.parseColor(vorschau ?: "#808080")
                        ),
                        RoundedCornerShape(Corners.FIELD.dp),
                    )
                    .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp)),
            )
            Column {
                Text(
                    PsUi.appText("Preview", "Vorschau"),
                    color = PrusaColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    vorschau ?: PsUi.appText("Select valid colors", "Gültige Farben wählen"),
                    color = PrusaColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Button(
            onClick = {
                if (components.isEmpty() || vorschau == null) return@Button
                val nextId = maxOf(
                    heads.size,
                    state.recipes.maxOfOrNull(ColorMixRecipe::id) ?: heads.size,
                ) + 1
                val vorher = state.recipes.size
                service.saveColorMix(
                    state.recipes + ColorMixRecipe(nextId, components, vorschau)
                )
                // Ein stiller Fehlschlag waere hier der schlimmste Fall:
                // man tippt, nichts passiert, und niemand sagt warum.
                fehlgeschlagen = service.colorMix.value.recipes.size <= vorher
            },
            enabled = selectedHeads.size in 2..3 && vorschau != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(PsUi.appText("Add mixed colour", "Mischfarbe hinzufügen")) }

        if (fehlgeschlagen) {
            Text(
                PsUi.appText(
                    "The mixed color could not be saved.",
                    "Die gemischte Farbe konnte nicht gespeichert werden.",
                ),
                color = PrusaColors.Danger,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        state.recipes.forEach { recipe ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(PsUi.appText("Virtual extruder ${recipe.id}", "Virtueller Extruder ${recipe.id}"), color = PrusaColors.TextPrimary)
                Text(
                    recipe.components.joinToString(" · ") { "T${it.head + 1}: ${(it.ratio * 100).toInt()} %" },
                    color = PrusaColors.TextMuted,
                )
                OutlinedButton(onClick = { service.saveColorMix(state.recipes.filterNot { it.id == recipe.id }) }) {
                    Text(PsUi.appText("Remove", "Entfernen"))
                }
            }
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(PsUi.appText("Done", "Fertig")) }
    }
}
