package de.psmobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay

/**
 * Die senkrechte Werkzeugschiene links - Port von
 * `ios/PSMobile/Screens/WerkzeugSchiene.swift`.
 *
 * Reihenfolge und Namen kommen aus `toolbar.json`, das aus PrusaSlicers
 * eigener Werkzeugleiste stammt (GLCanvas3D). Auch welche Werkzeuge
 * ohne Auswahl sinnlos sind, ist von dort uebernommen - das sind die
 * `enabling_callbacks` des Originals.
 *
 * [kopiert]/[onKopiertChange] entsprechen `@Binding var kopiert`: die
 * Zwischenablage liegt beim Aufrufer, weil sie den Bildschirm ueberlebt.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WerkzeugSchiene(
    model: SlicerModel = LocalSlicerModel.current,
    kopiert: Int?,
    onKopiertChange: (Int?) -> Unit,
    onEinfuegen: () -> Unit,
    onSettings: () -> Unit,
    onArrange: () -> Unit = {},
    onMalwerkzeug: (PsmCore.PaintTool) -> Unit = {},
    onPrinters: () -> Unit = {},
    onAppSettings: () -> Unit = {},
) {
    val ps = LocalPsScale.current
    /** Nur fuer das Arrange-Popover - die anderen Werkzeuge oeffnen nichts Eigenes. */
    var zeigeArrangePanel by remember { mutableStateOf(false) }
    var zeigeTrennMenue by remember { mutableStateOf(false) }

    val auswahl: Int? = model.selectedId

    val werkzeuge = listOf(
        Werkzeug("add", "plus", st("Import", "Import")),
        Werkzeug("delete", "trash", st("Delete", "Löschen")),
        Werkzeug("deleteall", "trash.slash", st("Clear", "Leeren")),
        Werkzeug("arrange", "square.grid.2x2", PsUiCatalog.tr("Arrange")),
        Werkzeug("copy", "doc.on.doc", st("Copy", "Kopieren")),
        Werkzeug("paste", "doc.on.clipboard", st("Paste", "Einfügen")),
        Werkzeug("more", "plus.square.on.square", st("+ copy", "+ Kopie")),
        Werkzeug("fewer", "minus.square", st("− copy", "− Kopie")),
        // Ein Knopf statt zwei: am Desktop ist das ein Rechtsklick-Menue
        // mit "Split to objects" / "Split to parts".
        Werkzeug("split", "square.split.2x1", st("Separate", "Trennen")),
        // Die Pinsel gehoeren zu den Gizmos, nicht in einen Reiter am
        // rechten Rand: beide bestimmen, was ein Finger auf dem Modell tut.
        Werkzeug("paintsupport", "paintbrush.pointed", st("Supports", "Stützen")),
        Werkzeug("paintseam", "scribble", st("Seam", "Naht")),
        Werkzeug("paintmmu", "paintpalette", "MMU"),
    )

    fun erlaubt(name: String): Boolean {
        if (name in brauchtAuswahl) return auswahl != null
        return when (name) {
            "paste" -> kopiert != null
            "undo" -> model.undoLabel.isNotEmpty()
            "redo" -> model.redoLabel.isNotEmpty()
            "deleteall" -> model.objects.isNotEmpty()
            "arrange" -> model.objects.isNotEmpty()
            else -> true
        }
    }

    fun ausfuehren(name: String) {
        when (name) {
            "add" -> onEinfuegen()
            "undo" -> model.undo()
            "redo" -> model.redo()
            // Loeschen loescht, was markiert ist - das ist der Punkt, an
            // dem eine Mehrfachauswahl etwas bringt.
            "delete" -> model.removeObjects(model.selectedIds.toList())
            "deleteall" -> model.newProject()
            // "arrange" hat einen eigenen Zweig in knopf() - Tipp und
            // Halten unterscheiden sich.
            "copy" -> onKopiertChange(auswahl)
            "paste" -> kopiert?.let { model.duplicate(listOf(it)) }
            "more" -> {
                val id = auswahl
                val o = model.objects.firstOrNull { it.id == id }
                if (id != null && o != null) model.setInstances(id, o.instances + 1)
            }
            "fewer" -> {
                val id = auswahl
                val o = model.objects.firstOrNull { it.id == id }
                if (id != null && o != null) model.setInstances(id, maxOf(o.instances - 1, 1))
            }
            "splitobjects" -> auswahl?.let { model.split(it) }
            "splitvolumes" -> auswahl?.let { model.splitVolumes(it) }
            "paintsupport" -> onMalwerkzeug(PsmCore.PaintTool.SUPPORT)
            "paintseam" -> onMalwerkzeug(PsmCore.PaintTool.SEAM)
            "paintmmu" -> onMalwerkzeug(PsmCore.PaintTool.MMU)
            "settings" -> onSettings()
            else -> {}
        }
    }

    Column(
        Modifier
            .width(ps.pt(74))
            .fillMaxHeight()
            .background(PrusaColors.panel),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = ps.pt(10), horizontal = ps.pt(6)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        ) {
            werkzeuge.forEach { w ->
                val an = erlaubt(w.name)
                when (w.name) {
                    "arrange" -> Box {
                        // Tipp ordnet alle ungesperrten Betten sofort an,
                        // Halten oeffnet ein Popover direkt am Knopf mit
                        // Zielbett/Abstand.
                        //
                        // Nur der Tipp haengt an `an`: drueben sitzt die
                        // LongPressGesture als simultaneousGesture
                        // ausserhalb von .disabled(!an) und geht auch bei
                        // ausgegrautem Knopf - etwa auf einem frisch
                        // angelegten, leeren Bett, auf das man von den
                        // anderen Betten anordnen will. Bis 12.09.2026
                        // sperrte Android mit enabled = an beides; der
                        // Zwilling zu testArrangePanelOrdnetDasExplizite-
                        // Zielbett blieb genau daran liegen.
                        KnopfInhalt(
                            w, an,
                            Modifier
                                .testTag("schiene." + w.name)
                                .combinedClickable(
                                    onClick = { if (an) model.arrangeAll() },
                                    onLongClick = { zeigeArrangePanel = true },
                                ),
                        )
                        if (zeigeArrangePanel) {
                            val dichte = LocalDensity.current
                            val versatz = with(dichte) { IntOffset(ps.pt(74).roundToPx(), 0) }
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = versatz,
                                onDismissRequest = { zeigeArrangePanel = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                ScaledOverlay {
                                    Box(
                                        Modifier
                                            .shadow(ps.pt(12), RoundedCornerShape(ps.pt(12)))
                                            .clip(RoundedCornerShape(ps.pt(12))),
                                    ) {
                                        ArrangePanel(
                                            model = model,
                                            isPresented = zeigeArrangePanel,
                                            onIsPresentedChange = { zeigeArrangePanel = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "split" -> Box {
                        KnopfInhalt(
                            w, an,
                            Modifier
                                .testTag("schiene." + w.name)
                                .clickable(enabled = an) { zeigeTrennMenue = true },
                        )
                        DropdownMenu(
                            expanded = zeigeTrennMenue,
                            onDismissRequest = { zeigeTrennMenue = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(st("Split to objects", "Zu Objekten trennen")) },
                                onClick = { zeigeTrennMenue = false; ausfuehren("splitobjects") },
                            )
                            DropdownMenuItem(
                                text = { Text(st("Split to parts", "Zu Volumen trennen")) },
                                onClick = { zeigeTrennMenue = false; ausfuehren("splitvolumes") },
                            )
                        }
                    }
                    else -> KnopfInhalt(
                        w, an,
                        Modifier
                            .testTag("schiene." + w.name)
                            .clickable(enabled = an) { ausfuehren(w.name) },
                    )
                }
            }
        }
        Fusszeile(onPrinters = onPrinters, onAppSettings = onAppSettings)
    }
}

/** Ein Werkzeug: Kennung wie in toolbar.json, Zeichen, kurzer Text. */
private data class Werkzeug(val name: String, val symbol: String, val label: String)

/** Welche Werkzeuge ohne Auswahl sinnlos sind - die enabling_callbacks im Original. */
private val brauchtAuswahl: Set<String> = setOf(
    "delete", "copy", "more", "fewer", "split",
    // Ein Pinsel ohne Objekt hat nichts zu bemalen.
    "paintsupport", "paintseam", "paintmmu",
)

@Composable
private fun KnopfInhalt(w: Werkzeug, an: Boolean, modifier: Modifier) {
    val ps = LocalPsScale.current
    val farbe = if (an) PrusaColors.textPrimary else PrusaColors.textMuted.copy(alpha = 0.35f)
    Column(
        modifier
            .fillMaxWidth()
            .height(ps.touch(58))
            .clip(RoundedCornerShape(ps.pt(8)))
            .background(if (an) PrusaColors.panelRaised else Color.Transparent),
        verticalArrangement = Arrangement.spacedBy(ps.pt(3), Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SfSymbol(w.symbol, Modifier.size(ps.font(19).value.dp), tint = farbe, contentDescription = w.label)
        Text(
            w.label,
            fontSize = ps.font(9),
            color = farbe,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Drucker und App-Einstellungen, unten links. */
@Composable
private fun Fusszeile(onPrinters: () -> Unit, onAppSettings: () -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ps.pt(8), horizontal = ps.pt(6)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
    ) {
        HorizontalDivider(
            Modifier.padding(horizontal = ps.pt(8)),
            color = PrusaColors.divider,
        )
        FusszeilenKnopf("paperplane", st("Printers", "Drucker"), kennung = "drucker.oeffnen", aktion = onPrinters)
        FusszeilenKnopf("gearshape", st("App", "App"), kennung = "appeinstellungen.oeffnen", aktion = onAppSettings)
    }
}

@Composable
private fun FusszeilenKnopf(symbol: String, label: String, kennung: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .testTag(kennung)
            .fillMaxWidth()
            .height(ps.touch(50))
            .clickable(onClick = aktion),
        verticalArrangement = Arrangement.spacedBy(ps.pt(3), Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SfSymbol(symbol, Modifier.size(ps.font(17).value.dp), tint = PrusaColors.textMuted, contentDescription = label)
        Text(
            label,
            fontSize = ps.font(9),
            color = PrusaColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
