package de.psmobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmViewport
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import kotlin.math.roundToInt

/*
 * Oberflaeche im PrusaSlicer-Zuschnitt, aber fuer Touch gebaut.
 *
 * Aufteilung wie am Desktop:
 *   links   schmale Werkzeugleiste
 *   mitte   Bett, vollflaechig
 *   rechts  Seitenleiste mit Profilen, Objekten, Einstellungen, Slicen
 *
 * Unterschiede zum Desktop, bewusst:
 *   - Zielgeraet ist das Tablet im Querformat. Auf schmalen Geraeten
 *     klappt die Seitenleiste weg statt zu schrumpfen.
 *   - Alle Ziele mindestens 48 dp. Am Desktop sind die Werkzeugsymbole
 *     32 px und die Kombifelder 22 px hoch - mit dem Finger unbedienbar.
 *   - Statt der dreispaltigen Parametertabelle nur die fuenf Regler, die
 *     den Alltag abdecken. Die vollstaendige Liste kommt spaeter als
 *     eigener Bildschirm, generiert aus PrintConfig.
 */

private val SIDEBAR_WIDTH = 340.dp
private val TOOL_SIZE = 56.dp

@Composable
fun SlicerScreen(
    service: SlicerService?,
    onPickFile: (android.net.Uri) -> Unit,
    onShare: (android.net.Uri) -> Unit,
) {
    if (service == null) {
        Box(
            Modifier.fillMaxSize().background(PrusaColors.Background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = PrusaColors.Orange) }
        return
    }
    SlicerContent(service, onPickFile, onShare)
}

@Composable
private fun SlicerContent(
    service: SlicerService,
    onPickFile: (android.net.Uri) -> Unit,
    onShare: (android.net.Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPickFile) }

    val objects by service.objects.collectAsState()
    val progress by service.progress.collectAsState()
    val presets by service.presets.collectAsState()
    val sceneRevision by service.sceneRevision.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val sceneController = remember { SceneController() }

    val selected = objects.firstOrNull { it.id == selectedId } ?: objects.firstOrNull()

    Row(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ToolStrip(
            hasSelection = selected != null,
            onAdd = { picker.launch(arrayOf("*/*")) },
            onDelete = { selected?.let { service.removeObject(it.id) } },
            onDeleteAll = { service.clearBed() },
            onArrange = { service.arrange() },
            onDropToBed = { selected?.let { service.dropToBed(it.id) } },
            onDuplicate = { selected?.let { service.duplicate(it.id) } },
        )

        // Echter GLES-Viewport auf Basis der Shader aus PrusaSlicer.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            SceneView(
                core = service.coreOrNull,
                shaderDir = remember(service) { service.shaderDir() },
                selectedId = selected?.id,
                onSelect = { id -> selectedId = if (id >= 0) id else null },
                invalidateKey = sceneRevision,
                controller = sceneController,
                modifier = Modifier.fillMaxSize(),
            )
            ViewBar(
                sceneController,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }

        Sidebar(
            service = service,
            presets = presets,
            objects = objects,
            selected = selected,
            progress = progress,
            onSelect = { selectedId = it },
            onShare = onShare,
            modifier = Modifier.width(SIDEBAR_WIDTH).fillMaxHeight(),
        )
    }
}

/* ------------------------------------------------------------------ */
/* Werkzeugleiste                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun ToolStrip(
    hasSelection: Boolean,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onDeleteAll: () -> Unit,
    onArrange: () -> Unit,
    onDropToBed: () -> Unit,
    onDuplicate: () -> Unit,
) {
    Column(
        Modifier
            .width(TOOL_SIZE + 16.dp)
            .fillMaxHeight()
            .background(PrusaColors.Panel)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToolButton(Icons.Default.Add, "Modell hinzufügen", true, onAdd)
        ToolButton(Icons.Default.ContentCopy, "Duplizieren", hasSelection, onDuplicate)
        ToolButton(Icons.Default.Delete, "Löschen", hasSelection, onDelete)
        ToolButton(Icons.Default.DeleteSweep, "Bett leeren", true, onDeleteAll)

        HorizontalDivider(
            Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            color = PrusaColors.Divider,
        )

        ToolButton(Icons.Default.GridView, "Anordnen", true, onArrange)
        ToolButton(Icons.Default.VerticalAlignBottom, "Aufs Bett legen", hasSelection, onDropToBed)

        // Verschieben, Skalieren und Drehen brauchen den Viewport (M4) -
        // deshalb sichtbar, aber noch inaktiv, damit die Leiste schon
        // ihre endgueltige Form hat.
        ToolButton(Icons.Default.OpenWith, "Verschieben (M5)", false) {}
        ToolButton(Icons.Default.ZoomOutMap, "Skalieren (M5)", false) {}
        ToolButton(Icons.Default.Rotate90DegreesCcw, "Drehen (M5)", false) {}
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(TOOL_SIZE)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) PrusaColors.PanelRaised else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) PrusaColors.TextPrimary else PrusaColors.TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Ansichtsleiste ueber dem Bett - Gegenstueck zur Ansichts-Werkzeugleiste
 * unten im Slicer. Wichtig auf dem Tablet: nach ein paar Wischern ist man
 * schnell unter dem Bett, und ohne festen Blickwinkel findet man nicht
 * zurueck.
 */
@Composable
private fun ViewBar(controller: SceneController, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrusaColors.Panel.copy(alpha = 0.88f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val views = listOf(
            "Iso" to PsmViewport.View.ISO,
            "Oben" to PsmViewport.View.TOP,
            "Vorn" to PsmViewport.View.FRONT,
            "Hinten" to PsmViewport.View.BACK,
            "Links" to PsmViewport.View.LEFT,
            "Rechts" to PsmViewport.View.RIGHT,
        )
        views.forEach { (label, v) ->
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { controller.setView(v) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = PrusaColors.TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Seitenleiste                                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun Sidebar(
    service: SlicerService,
    presets: SlicerService.Presets,
    objects: List<PsmCore.ObjectInfo>,
    selected: PsmCore.ObjectInfo?,
    progress: SlicerService.Progress,
    onSelect: (Int) -> Unit,
    onShare: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = progress is SlicerService.Progress.Running
    val quick by service.quickSettings.collectAsState()

    Column(
        modifier
            .background(PrusaColors.Panel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Drucker")
            PresetCombo(presets.printers, presets.selectedPrinter) {
                service.selectPreset(PsmCore.PresetType.PRINTER, it)
            }

            SectionLabel("Druckeinstellungen")
            PresetCombo(presets.prints, presets.selectedPrint) {
                service.selectPreset(PsmCore.PresetType.PRINT, it)
            }

            SectionLabel("Filament")
            PresetCombo(presets.filaments, presets.selectedFilament) {
                service.selectPreset(PsmCore.PresetType.FILAMENT, it)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = PrusaColors.Divider)

            SectionLabel("Schnelleinstellungen")
            QuickSetting("Schichthöhe", quick.layerHeight, SlicerService.LAYER_HEIGHTS) {
                service.setQuick(SlicerService.QuickKey.LAYER_HEIGHT, it)
            }
            QuickSetting("Füllung", quick.fillDensity, SlicerService.FILL_DENSITIES) {
                service.setQuick(SlicerService.QuickKey.FILL_DENSITY, it)
            }
            QuickSetting("Stützen", quick.supports, SlicerService.SUPPORT_MODES) {
                service.setQuick(SlicerService.QuickKey.SUPPORTS, it)
            }
            QuickSetting("Rand", quick.brim, SlicerService.BRIM_MODES) {
                service.setQuick(SlicerService.QuickKey.BRIM, it)
            }

            if (objects.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = PrusaColors.Divider)
                SectionLabel("Objekte")
                objects.forEach { obj ->
                    ObjectRow(obj, obj.id == selected?.id, { onSelect(obj.id) }) {
                        service.removeObject(obj.id)
                    }
                }
            }
        }

        ProgressBlock(progress)

        Button(
            onClick = { if (isRunning) service.cancelSlice() else service.startSlice() },
            enabled = objects.isNotEmpty() || isRunning,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) PrusaColors.PanelRaised else PrusaColors.Orange,
                contentColor = PrusaColors.TextPrimary,
            ),
        ) {
            Text(
                if (isRunning) "Abbrechen" else "Jetzt slicen",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }

        if (progress is SlicerService.Progress.Done) {
            OutlinedButton(
                onClick = { service.shareableGcodeUri()?.let(onShare) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                Text("G-Code exportieren", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = PrusaColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Kombifeld im Slicer-Stil.
 *
 * Kein ExposedDropdownMenu von Material: das rendert ein Textfeld mit
 * Tastaturfokus, was hier nur stoert. Eine flache Flaeche mit Menue
 * trifft die Desktop-Optik besser und ist mit 48 dp gut treffbar.
 */
@Composable
private fun PresetCombo(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(4.dp))
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected.ifBlank { "—" },
                color = PrusaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = PrusaColors.TextMuted)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PrusaColors.PanelRaised),
        ) {
            options.forEach { name ->
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            color = if (name == selected) PrusaColors.Orange else PrusaColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                        )
                    },
                    onClick = { expanded = false; onSelect(name) },
                )
            }
        }
    }
}

/** Ein Parameter als Reihe grosser Schaltflaechen statt als Zahleneingabe. */
@Composable
private fun QuickSetting(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = PrusaColors.TextPrimary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (raw, caption) ->
                val active = raw == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) PrusaColors.Orange else PrusaColors.PanelRaised)
                        .clickable { onSelect(raw) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        caption,
                        color = PrusaColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectRow(
    obj: PsmCore.ObjectInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) PrusaColors.Orange.copy(alpha = 0.18f) else PrusaColors.PanelRaised)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                obj.name.ifBlank { "Objekt ${obj.id}" },
                color = PrusaColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "%.0f × %.0f × %.0f mm".format(obj.sizeMm.first, obj.sizeMm.second, obj.sizeMm.third),
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
            )
            if (obj.outsideBed) {
                Text("außerhalb des Bettes", color = PrusaColors.Danger, fontSize = 11.sp)
            }
        }
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Delete, "Entfernen", tint = PrusaColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProgressBlock(progress: SlicerService.Progress) {
    when (progress) {
        is SlicerService.Progress.Idle -> Unit

        is SlicerService.Progress.Running -> Column(Modifier.fillMaxWidth()) {
            // Phase mitzeigen, nicht nur Prozent - auf dem Tablet kann ein
            // Slice mehrere Minuten dauern.
            Text(progress.stage, color = PrusaColors.TextPrimary, fontSize = 12.sp,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = PrusaColors.Orange,
                trackColor = PrusaColors.PanelRaised,
            )
        }

        is SlicerService.Progress.Done -> {
            val st = progress.stats
            Column(Modifier.fillMaxWidth()) {
                Text("Fertig in %.1f s".format(progress.seconds),
                     color = PrusaColors.Ok, fontSize = 12.sp)
                if (st != null) {
                    Text(
                        buildString {
                            append("%d Layer · %d min · %.2f m".format(
                                st.layers, (st.printTimeSeconds / 60).roundToInt(),
                                st.filamentMm / 1000.0))
                            if (st.filamentGrams > 0.0) append(" · %.1f g".format(st.filamentGrams))
                        },
                        color = PrusaColors.TextMuted, fontSize = 12.sp,
                    )
                }
            }
        }

        is SlicerService.Progress.Failed -> Text(
            progress.message, color = PrusaColors.Danger, fontSize = 12.sp, maxLines = 3,
        )

        is SlicerService.Progress.Cancelled -> Text(
            "abgebrochen", color = PrusaColors.TextMuted, fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(2.dp))
}
