package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.shared.rules.ColorMixCodec
import de.psmobile.shared.rules.ColorMixComponent
import de.psmobile.shared.rules.ColorMixRecipe
import de.psmobile.shared.rules.ExtruderPosition
import de.psmobile.shared.rules.ExtruderPresentation
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.roundToInt

/**
 * Erstellt virtuelle Farben aus zwei oder drei physischen Positionen -
 * Port von `ios/PSMobile/Screens/ColorMixView.swift`. Die Rezepte
 * werden als normale 3MF-Projektinformation gespeichert.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorMixView(
    onClose: () -> Unit,
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    var selected by remember { mutableStateOf(listOf(0, 1)) }
    var firstShare by remember { mutableStateOf(0.5) }
    var recipes by remember { mutableStateOf<List<ColorMixRecipe>>(emptyList()) }
    var saveFailed by remember { mutableStateOf(false) }

    val positions: List<ExtruderPosition> = ExtruderPresentation.positions(model.extruderCount)

    val components: List<ColorMixComponent> = when {
        selected.isEmpty() -> emptyList()
        selected.size == 2 -> listOf(
            ColorMixComponent(head = selected[0], ratio = firstShare),
            ColorMixComponent(head = selected[1], ratio = 1 - firstShare),
        )
        else -> {
            val rest = (1 - firstShare) / (selected.size - 1).toDouble()
            listOf(ColorMixComponent(head = selected[0], ratio = firstShare)) +
                selected.drop(1).map { ColorMixComponent(head = it, ratio = rest) }
        }
    }

    val physicalColors: List<String> = (0 until model.extruderCount).map { index ->
        val color = model.extruderColor(index)
        color.ifEmpty { "#808080" }
    }

    val preview: String? = ColorMixCodec.previewColor(physicalColors = physicalColors, components = components)

    fun toggle(index: Int) {
        val existing = selected.indexOf(index)
        if (existing >= 0) {
            if (selected.size <= 2) return
            selected = selected.toMutableList().also { it.removeAt(existing) }
        } else if (selected.size < 3) {
            selected = selected + index
        } else {
            selected = selected.dropLast(1) + index
        }
    }

    fun saveRecipe() {
        val farbe = preview ?: return
        // IDs der virtuellen Extruder beginnen strikt hinter den physischen
        // Positionen: bei acht Positionen ist die erste Mischung also 9.
        val nextID = (recipes.maxOfOrNull { it.id } ?: model.extruderCount) + 1
        val recipe = ColorMixRecipe(id = nextID, components = components, color = farbe)
        val neu = recipes + recipe
        recipes = neu
        if (!model.saveColorMix(neu)) {
            recipes = neu.dropLast(1)
            saveFailed = true
        } else {
            saveFailed = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
    ) {
        // Titel der NavigationStack-Leiste.
        Text(
            st("ColorMix", "ColorMix"),
            fontSize = ps.font(17),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ps.pt(20), vertical = ps.pt(12)),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ps.pt(18)),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(ps.pt(20)),
        ) {
            Text(
                st("Create a mixed color", "Gemischte Farbe erstellen"),
                fontSize = ps.font(20),
                fontWeight = FontWeight.Bold,
                color = PrusaColors.textPrimary,
            )
            Text(
                st(
                    "Choose two or three physical positions. Their existing materials stay assigned.",
                    "Wähle zwei oder drei physische Positionen. Die vorhandenen Materialien bleiben zugeordnet.",
                ),
                fontSize = ps.font(13),
                color = PrusaColors.textMuted,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                positions.forEach { position ->
                    val isSelected = selected.contains(position.index)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(ps.touch(46))
                            .clip(RoundedCornerShape(ps.pt(6)))
                            .background(if (isSelected) PrusaColors.orange else PrusaColors.panelRaised)
                            .clickable { toggle(position.index) }
                            .testTag("colormix.position.${position.index}"),
                    ) {
                        Text(
                            position.label,
                            fontSize = ps.font(15),
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else PrusaColors.textPrimary,
                        )
                    }
                }
            }

            if (selected.size >= 2) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    val prozent = (firstShare * 100).roundToInt()
                    Text(
                        st(
                            "Share of position ${selected[0] + 1}: $prozent%",
                            "Anteil von Position ${selected[0] + 1}: $prozent%",
                        ),
                        fontSize = ps.font(13),
                        color = PrusaColors.textPrimary,
                    )
                    Slider(
                        value = firstShare.toFloat(),
                        onValueChange = { firstShare = ((it * 10).roundToInt() / 10.0).coerceIn(0.1, 0.9) },
                        valueRange = 0.1f..0.9f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = PrusaColors.orange,
                            activeTrackColor = PrusaColors.orange,
                            inactiveTrackColor = PrusaColors.panelRaised,
                            activeTickColor = PrusaColors.orange,
                            inactiveTickColor = PrusaColors.divider,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(ps.touch(62))
                        .clip(RoundedCornerShape(ps.pt(8)))
                        .background(colorFromHex(preview ?: "#808080") ?: PrusaColors.panelRaised)
                        .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(8)))
                        .testTag("colormix.preview")
                        .semantics { stateDescription = preview ?: "" },
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        st("Preview", "Vorschau"),
                        fontSize = ps.font(13),
                        fontWeight = FontWeight.SemiBold,
                        color = PrusaColors.textPrimary,
                    )
                    Text(
                        preview ?: st("Select valid colors", "Gültige Farben wählen"),
                        fontSize = ps.font(12),
                        color = PrusaColors.textMuted,
                    )
                }
            }

            // `.buttonStyle(.borderedProminent).tint(orange)`
            val speichernAktiv = preview != null && selected.size >= 2
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(46))
                    .clip(RoundedCornerShape(ps.pt(8)))
                    .background(PrusaColors.orange)
                    .alpha(if (speichernAktiv) 1f else 0.5f)
                    .clickable(enabled = speichernAktiv) { saveRecipe() }
                    .testTag("colormix.save"),
            ) {
                Text(
                    st("Save mixed color", "Gemischte Farbe speichern"),
                    fontSize = ps.font(14),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            if (saveFailed) {
                Text(
                    st("The mixed color could not be saved.", "Die gemischte Farbe konnte nicht gespeichert werden."),
                    fontSize = ps.font(12),
                    color = PrusaColors.danger,
                )
            }

            if (recipes.isNotEmpty()) {
                Text(
                    st("Saved mixed colors", "Gespeicherte Mischfarben"),
                    fontSize = ps.font(15),
                    fontWeight = FontWeight.SemiBold,
                    color = PrusaColors.textPrimary,
                )
                recipes.forEach { recipe ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ps.pt(6)))
                            .background(PrusaColors.panelRaised)
                            .padding(ps.pt(10))
                            .testTag("colormix.recipe.${recipe.id}"),
                    ) {
                        Box(
                            Modifier
                                .size(ps.touch(28))
                                .clip(RoundedCornerShape(ps.pt(3)))
                                .background(colorFromHex(recipe.color ?: "#808080") ?: PrusaColors.panelRaised),
                        )
                        Spacer(Modifier.size(ps.pt(8)))
                        Text(
                            recipe.components.joinToString(" + ") { "${it.head + 1}" },
                            fontSize = ps.font(13),
                            color = PrusaColors.textPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(ps.touch(44))
                                .clickable {
                                    val neu = recipes.filterNot { it.id == recipe.id }
                                    recipes = neu
                                    model.saveColorMix(neu)
                                }
                                .semantics { contentDescription = st("Delete mixed color", "Mischfarbe löschen") },
                        ) {
                            SfSymbol(
                                "trash",
                                Modifier.size(ps.font(18).value.dp),
                                tint = PrusaColors.danger,
                            )
                        }
                    }
                }
            }

            // `Button("Done").buttonStyle(.bordered).tint(orange)`
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(46))
                    .clip(RoundedCornerShape(ps.pt(8)))
                    .background(PrusaColors.orange.copy(alpha = 0.18f))
                    .clickable(onClick = onClose)
                    .testTag("colormix.done"),
            ) {
                Text(
                    st("Done", "Fertig"),
                    fontSize = ps.font(14),
                    fontWeight = FontWeight.SemiBold,
                    color = PrusaColors.orange,
                )
            }
        }
    }

    LaunchedEffect(Unit) { recipes = model.colorMixRecipes() }
}
