package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.roundToInt

/**
 * Die Handvoll Werte, die man am haeufigsten anfasst - direkt in der
 * Seitenleiste, ohne Umweg ueber die Einstellungsseiten.
 *
 * Vorbild ist FrequentlyChangedParameters.cpp: dort stehen am Desktop
 * genau Fuellung, Stuetzen und Brim. Die Zuordnung der Stuetzenauswahl
 * auf die drei Parameter ist von dort uebernommen, nicht erfunden:
 *
 *   Keine                    support_material = false
 *   Ueberall                 = true,  buildplate_only = false, auto = true
 *   Nur auf dem Bett         = true,  buildplate_only = true,  auto = true
 *   Nur mit Erzwingern       = true,  buildplate_only = false, auto = false
 *
 * Dazu die Schalenstaerke nach dem Vorbild von EasyPrint: Decke, Wand und
 * Boden als Schichtzahl, daneben was das in Millimetern ergibt. Am Tablet
 * ist das die Angabe, nach der man tatsaechlich entscheidet.
 */
@Composable
fun QuickSettings(
    service: SlicerService,
    core: PsmCore,
    configRevision: Int,
    modifier: Modifier = Modifier,
) {
    // Einmal je Revision lesen statt bei jeder Neuzeichnung - sonst laeuft
    // fuer jeden Wert ein JNI-Aufruf je Bild. Befund A2.
    val values = remember(configRevision) {
        listOf("fill_density", "fill_pattern", "support_material",
               "support_material_auto", "support_material_buildplate_only",
               "brim_width", "top_solid_layers", "bottom_solid_layers",
               "perimeters", "layer_height", "perimeter_extrusion_width")
            .associateWith { runCatching { core[it] }.getOrNull().orEmpty() }
    }

    fun set(key: String, v: String) {
        service.setConfig(key, v)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // --- Fuellung --------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PsUi.tr("Infill"), color = PrusaColors.TextMuted,
                 fontSize = 13.sp, modifier = Modifier.width(88.dp))
            Box(Modifier.weight(1f)) {
                // Dieselben Stufen wie im Kombifeld des Desktops.
                Picker(
                    options = listOf("0%", "5%", "10%", "15%", "20%", "25%",
                                     "30%", "40%", "50%", "60%", "70%", "80%",
                                     "90%", "100%"),
                    selected = values["fill_density"].orEmpty(),
                ) { set("fill_density", it) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PsUi.tr("Fill pattern"), color = PrusaColors.TextMuted,
                 fontSize = 13.sp, modifier = Modifier.width(88.dp))
            Box(Modifier.weight(1f)) {
                // Werte und Beschriftungen kommen aus PrintConfig, nicht
                // aus einer eigenen Liste.
                val patterns = remember {
                    val n = runCatching { core.configMeta("fill_pattern")?.enumCount }
                        .getOrNull() ?: 0
                    runCatching { core.configEnumValues("fill_pattern", n) }
                        .getOrDefault(emptyList())
                }
                Picker(
                    options = patterns.map { it.first },
                    selected = values["fill_pattern"].orEmpty(),
                    labels = patterns.map { PsUi.tr(it.second) },
                ) { set("fill_pattern", it) }
            }
        }

        // --- Stuetzen --------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PsUi.tr("Supports"), color = PrusaColors.TextMuted,
                 fontSize = 13.sp, modifier = Modifier.width(88.dp))
            Box(Modifier.weight(1f)) {
                val on = values["support_material"] == "1"
                val auto = values["support_material_auto"] == "1"
                val plateOnly = values["support_material_buildplate_only"] == "1"
                val current = when {
                    !on        -> "None"
                    !auto      -> "For support enforcers only"
                    plateOnly  -> "Support on build plate only"
                    else       -> "Everywhere"
                }
                Picker(
                    options = listOf("None", "Support on build plate only",
                                     "For support enforcers only", "Everywhere"),
                    selected = current,
                    labels = listOf("None", "Support on build plate only",
                                    "For support enforcers only", "Everywhere")
                        .map { PsUi.tr(it) },
                ) { choice ->
                    when (choice) {
                        "None" -> set("support_material", "0")
                        "Everywhere" -> {
                            set("support_material", "1")
                            set("support_material_buildplate_only", "0")
                            set("support_material_auto", "1")
                        }
                        "Support on build plate only" -> {
                            set("support_material", "1")
                            set("support_material_buildplate_only", "1")
                            set("support_material_auto", "1")
                        }
                        "For support enforcers only" -> {
                            set("support_material", "1")
                            set("support_material_buildplate_only", "0")
                            set("support_material_auto", "0")
                        }
                    }
                }
            }
        }

        // --- Brim ------------------------------------------------------
        //
        // Am Desktop ein Haken, der brim_width zwischen 0 und dem letzten
        // Wert umschaltet. Genauso hier - der alte Wert bleibt gemerkt.
        val brim = values["brim_width"]?.toFloatOrNull() ?: 0f
        var lastBrim by remember { mutableStateOf(if (brim > 0f) brim else 5f) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PsUi.tr("Brim"), color = PrusaColors.TextMuted,
                 fontSize = 13.sp, modifier = Modifier.width(88.dp))
            Toggle(brim > 0f) { on ->
                if (on) set("brim_width", lastBrim.toString())
                else { lastBrim = if (brim > 0f) brim else lastBrim; set("brim_width", "0") }
            }
        }

        // --- Schalenstaerke -------------------------------------------
        //
        // Nach dem Vorbild von EasyPrint: Schichten eingeben, Millimeter
        // danebenstellen. Der Zusammenhang ist Schichthoehe mal Zahl bei
        // Decke und Boden, Extrusionsbreite mal Zahl bei den Waenden.
        val layerH = values["layer_height"]?.toFloatOrNull() ?: 0.2f
        val width = values["perimeter_extrusion_width"]?.toFloatOrNull()
            ?.takeIf { it > 0f } ?: 0.45f

        Text(PsUi.tr("Shell thickness").uppercase(),
             color = PrusaColors.TextMuted, fontSize = 11.sp,
             fontWeight = FontWeight.SemiBold,
             modifier = Modifier.padding(top = 4.dp))

        listOf(
            Triple(PsUi.tr("Top"),    "top_solid_layers",    layerH),
            Triple(PsUi.tr("Walls"),  "perimeters",          width),
            Triple(PsUi.tr("Bottom"), "bottom_solid_layers", layerH),
        ).forEach { (label, key, factor) ->
            val n = values[key]?.toIntOrNull() ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = PrusaColors.TextPrimary,
                     fontSize = 13.sp, modifier = Modifier.width(88.dp))
                Stepper(n) { set(key, it.toString()) }
                Text(
                    "%.2f mm".format(n * factor),
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Picker(
    options: List<String>,
    selected: String,
    labels: List<String> = emptyList(),
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val shown = options.indexOf(selected).let { i ->
        if (i >= 0 && i < labels.size) labels[i] else selected
    }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown.ifBlank { "–" }, color = PrusaColors.TextPrimary,
                 fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("▾", color = PrusaColors.TextMuted, fontSize = 11.sp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (i < labels.size) labels[i] else opt,
                            color = if (opt == selected) PrusaColors.Orange
                                    else PrusaColors.TextPrimary,
                            fontSize = 14.sp,
                        )
                    },
                    onClick = { open = false; onSelect(opt) },
                )
            }
        }
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(60.dp).height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!on) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(56.dp).height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (on) PrusaColors.Orange else PrusaColors.PanelRaised),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.padding(horizontal = 3.dp).size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White),
            )
        }
    }
}

/** Zahl mit Minus und Plus - auf dem Tablet schneller als Tippen. */
@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−") { if (value > 0) onChange(value - 1) }
        Text(
            value.toString(),
            color = PrusaColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.width(44.dp),
        )
        StepButton("+") { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.width(48.dp).height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PrusaColors.PanelRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = PrusaColors.TextPrimary, fontSize = 16.sp)
    }
}
