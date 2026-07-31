package de.psmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import de.psmobile.slicing.colormix.ColorMixComponent
import de.psmobile.slicing.colormix.ColorMixRecipe
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ColorMix", color = PrusaColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ein virtueller Extruder wechselt während des Drucks zwischen zwei oder drei Köpfen. Das erzeugt einen gemischten Farbeindruck – kein physisches Schmelzmischen.",
            color = PrusaColors.TextMuted,
        )
        if (!state.available) {
            Text(
                "Der installierte Slicer-Core enthält die ColorMix-ABI noch nicht. Das Rezept wird erst nach dem nächsten Native-Core-Build aktiviert.",
                color = PrusaColors.TextMuted,
            )
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Zurück") }
            return@Column
        }

        Text("Köpfe auswählen (2–3)", color = PrusaColors.TextPrimary)
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
                    label = { Text("Kopf ${head.index + 1}") },
                )
            }
        }
        if (selectedHeads.size == 2) {
            Text("Anteil des ersten Kopfs: ${(ratio * 100).toInt()} %", color = PrusaColors.TextMuted)
            Slider(value = ratio, onValueChange = { ratio = it }, valueRange = 0.1f..0.9f)
        }
        Button(
            onClick = {
                val ordered = selectedHeads.sorted()
                if (ordered.size !in 2..3) return@Button
                val components = if (ordered.size == 2) {
                    listOf(ColorMixComponent(ordered[0], ratio.toDouble()), ColorMixComponent(ordered[1], 1.0 - ratio))
                } else ordered.map { ColorMixComponent(it, 1.0 / ordered.size) }
                val nextId = maxOf(
                    heads.size,
                    state.recipes.maxOfOrNull(ColorMixRecipe::id) ?: heads.size,
                ) + 1
                service.saveColorMix(state.recipes + ColorMixRecipe(nextId, components))
            },
            enabled = selectedHeads.size in 2..3,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mischfarbe hinzufügen") }

        state.recipes.forEach { recipe ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Virtueller Extruder ${recipe.id}", color = PrusaColors.TextPrimary)
                Text(
                    recipe.components.joinToString(" · ") { "Kopf ${it.head + 1}: ${(it.ratio * 100).toInt()} %" },
                    color = PrusaColors.TextMuted,
                )
                OutlinedButton(onClick = { service.saveColorMix(state.recipes.filterNot { it.id == recipe.id }) }) {
                    Text("Entfernen")
                }
            }
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
    }
}
