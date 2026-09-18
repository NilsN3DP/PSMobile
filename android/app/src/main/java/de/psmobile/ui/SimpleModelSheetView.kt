package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.shared.rules.SimpleModelSheetState
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay

/**
 * Das Modelle-Blatt am unteren Rand - Gegenstueck zu
 * `ios/PSMobile/Screens/SimpleModelSheetView.swift`.
 *
 * Es loest den einzelnen Knopf "Modell hinzufuegen" ab, sobald etwas
 * auf dem Bett liegt: anordnen, klonen, entfernen und auf ein anderes
 * Bett schieben. Welche Aktion wann etwas bewirkt, entscheidet
 * `SimpleModelSheetState` im gemeinsamen Modul.
 */
@Composable
fun SimpleModelSheetView(
    model: SlicerModel,
    onPickFile: () -> Unit,
    onArrange: () -> Unit = {},
) {
    // Die Vorschau laesst sich abschalten - sie kostet Platz, und auf
    // einem schmalen Geraet ist die Zeile ohnehin eng.
    val einstellungen = LocalAppSettingsStore.current
    val ps = LocalPsScale.current
    var ausgeklappt by remember { mutableStateOf(true) }
    var gewaehlt by remember { mutableStateOf<List<Int>>(emptyList()) }
    var zeigeBettwahl by remember { mutableStateOf(false) }

    val ids = model.objects.map { it.id }
    val aktionen = SimpleModelSheetState.enabledActions(
        objectsOnBed = model.objects.size,
        selected = gewaehlt,
        bedCount = maxOf(model.beds.size, 1),
        maxBeds = SlicerModel.maxBeds,
    )
    fun enthalten(a: SimpleModelSheetState.Action): Boolean = aktionen.contains(a)

    Column(
        Modifier
            .widthIn(max = ps.pt(380))
            .fillMaxWidth()
            .background(PrusaColors.panel)
            .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(2))),
    ) {
        // Kopfzeile
        Row(
            Modifier.fillMaxWidth().height(ps.touch(46)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(2)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Eine Zeile: mit sechs Knoepfen daneben brach "1 SELECTED" auf dem
            // Telefon in drei Zeilen um und wurde abgeschnitten (S23 FE, 16.09.2026).
            Text(
                SimpleModelSheetState.headline(selected = gewaehlt),
                fontSize = ps.font(12),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = ps.pt(10)),
            )

            if (gewaehlt.isEmpty()) {
                blattAktion("▣", st("Select all", "Alle wählen"),
                    an = enthalten(SimpleModelSheetState.Action.SELECT_ALL), kennung = "blatt.alle") {
                    gewaehlt = ids
                }
                blattAktion("＋", st("More", "Weitere"), an = true, kennung = "blatt.mehr",
                    aktion = onPickFile)
                blattAktion("▤", st("Arrange", "Anordnen"),
                    an = enthalten(SimpleModelSheetState.Action.ARRANGE), kennung = "blatt.anordnen") {
                    onArrange()
                }
            } else {
                blattAktion("✕", st("Cancel", "Abbrechen"), an = true, kennung = "blatt.abbrechen") {
                    gewaehlt = emptyList()
                }
                blattAktion("▤", st("Arrange", "Anordnen"),
                    an = enthalten(SimpleModelSheetState.Action.ARRANGE), kennung = "blatt.anordnen") {
                    onArrange()
                }
                blattAktion("➜", st("Move to", "Ziehen zu"),
                    an = enthalten(SimpleModelSheetState.Action.MOVE_TO_BED), kennung = "blatt.bett") {
                    zeigeBettwahl = true
                }
                blattAktion("⧉", st("Clone", "Klonen"),
                    an = enthalten(SimpleModelSheetState.Action.CLONE), kennung = "blatt.klonen") {
                    model.duplicate(gewaehlt)
                }
                blattAktion("✖", st("Remove", "Entfernen"),
                    an = enthalten(SimpleModelSheetState.Action.REMOVE), kennung = "blatt.entfernen") {
                    model.removeObjects(gewaehlt)
                    gewaehlt = emptyList()
                }
            }
            blattAktion(if (ausgeklappt) "⌄" else "⌃", st("Collapse", "Einklappen"),
                an = true, kennung = "blatt.klappen") {
                ausgeklappt = !ausgeklappt
            }
        }

        if (ausgeklappt) {
            HorizontalDivider(color = PrusaColors.divider)
            // Liste: drei Zeilen sind sichtbar, der Rest wird gescrollt.
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(ps.pt(58) * minOf(maxOf(model.objects.size, 1), SICHTBARE_ZEILEN))
                    .verticalScroll(rememberScrollState()),
            ) {
                model.objects.forEach { objekt ->
                    zeile(
                        model = model,
                        objekt = objekt,
                        gewaehlt = gewaehlt,
                        thumbnails = einstellungen.thumbnails,
                        onGewaehltChange = { gewaehlt = it },
                    )
                }
            }
            if (model.beds.size > 1) {
                HorizontalDivider(color = PrusaColors.divider)
                bettleiste(model)
            }
        }
    }

    // Geloeschte oder verschobene Objekte duerfen nicht als Geister
    // in der Auswahl bleiben - sonst nennt die Kopfzeile eine Zahl,
    // zu der es keine Zeilen mehr gibt.
    LaunchedEffect(model.objects.size) {
        val vorhanden = model.objects.map { it.id }
        gewaehlt = gewaehlt.filter { it in vorhanden }
    }

    if (zeigeBettwahl) {
        Dialog(
            onDismissRequest = { zeigeBettwahl = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(
                    Modifier
                        .padding(ps.pt(16))
                        .widthIn(max = ps.pt(520))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ps.pt(4)))
                        .background(PrusaColors.panel),
                ) {
                    bettwahl(
                        model = model,
                        gewaehlt = gewaehlt,
                        onDone = {
                            gewaehlt = emptyList()
                            zeigeBettwahl = false
                        },
                    )
                }
            }
        }
    }
}

/** Wie auf Android: drei Zeilen sind sichtbar, der Rest wird gescrollt. */
private const val SICHTBARE_ZEILEN = 3

@Composable
private fun zeile(
    model: SlicerModel,
    objekt: PsmCore.ObjectInfo,
    gewaehlt: List<Int>,
    thumbnails: Boolean,
    onGewaehltChange: (List<Int>) -> Unit,
) {
    val ps = LocalPsScale.current
    val angehakt = gewaehlt.contains(objekt.id)
    val hervor = objekt.id == model.selectedId && gewaehlt.isEmpty()
    Row(
        Modifier
            .testTag("blatt.zeile.${objekt.id}")
            .fillMaxWidth()
            .height(ps.pt(58))
            .background(if (hervor) PrusaColors.panelRaised else Color.Transparent)
            .clickable {
                // Sobald etwas angehakt ist, hakt ein Tippen an und ab.
                // Ohne Auswahl waehlt es das Objekt im Viewport aus.
                if (gewaehlt.isEmpty()) {
                    model.select(objekt.id)
                } else {
                    onGewaehltChange(SimpleModelSheetState.toggle(selected = gewaehlt, id = objekt.id))
                }
            }
            .padding(horizontal = ps.pt(8)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnails) ObjektMasse(objekt = objekt)
        Box(
            Modifier
                .size(ps.touch(40))
                .clickable {
                    onGewaehltChange(SimpleModelSheetState.toggle(selected = gewaehlt, id = objekt.id))
                },
            contentAlignment = Alignment.Center,
        ) {
            SfSymbol(
                if (angehakt) "checkmark.square.fill" else "square",
                Modifier.size(ps.font(16).value.dp),
                tint = if (angehakt) PrusaColors.orange else PrusaColors.textMuted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
            Text(
                if (objekt.name.isEmpty()) "Objekt ${objekt.id}" else objekt.name,
                fontSize = ps.font(13),
                color = PrusaColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                masseText(objekt.sizeMm.first, objekt.sizeMm.second, objekt.sizeMm.third),
                fontSize = ps.font(10),
                color = if (objekt.outsideBed) PrusaColors.danger else PrusaColors.textMuted,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun bettleiste(model: SlicerModel) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ps.pt(8), vertical = ps.pt(6)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        model.beds.forEach { bett ->
            Box(
                Modifier
                    .testTag("blatt.bett.${bett.index}")
                    .heightIn(min = ps.pt(36))
                    .clip(RoundedCornerShape(ps.pt(2)))
                    .background(if (bett.active) PrusaColors.orange else PrusaColors.panelRaised)
                    .clickable { model.selectBed(bett.index) }
                    .padding(horizontal = ps.pt(12)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st("Bed ", "Bett ") + "${bett.index + 1} · ${bett.objectCount}",
                    fontSize = ps.font(10),
                    color = if (bett.active) PrusaColors.background else PrusaColors.textPrimary,
                )
            }
        }
    }
}

/**
 * Zielbetten fuer "auf Bett ziehen". Das letzte Ziel legt bei Bedarf
 * ein neues Bett an - ohne diese Moeglichkeit waere ein volles Bett
 * eine Sackgasse.
 */
@Composable
private fun bettwahl(
    model: SlicerModel,
    gewaehlt: List<Int>,
    onDone: () -> Unit,
) {
    val ps = LocalPsScale.current
    val ziele = SimpleModelSheetState.moveTargets(
        bedCount = maxOf(model.beds.size, 1),
        activeBed = model.beds.indexOfFirst { it.active }.takeIf { it >= 0 } ?: 0,
        maxBeds = SlicerModel.maxBeds,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(PrusaColors.background)
            .padding(ps.pt(20)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(st("Move to bed", "Auf Bett ziehen"), fontSize = ps.font(18), color = PrusaColors.textPrimary)
        ziele.forEach { index ->
            Box(
                Modifier
                    .testTag("bettwahl.$index")
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(48))
                    .clip(RoundedCornerShape(ps.pt(3)))
                    .background(PrusaColors.panelRaised)
                    .clickable {
                        model.moveToBed(gewaehlt, target = index)
                        onDone()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (index < model.beds.size) st("Bed ", "Bett ") + "${index + 1}"
                    else st("New bed", "Neues Bett"),
                    fontSize = ps.font(14),
                    color = PrusaColors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun blattAktion(
    glyph: String,
    label: String,
    an: Boolean,
    kennung: String,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    val farbe = if (an) PrusaColors.textPrimary else PrusaColors.textMuted.copy(alpha = 0.4f)
    Column(
        Modifier
            .testTag(kennung)
            .size(ps.pt(52), ps.touch(44))
            .clickable(enabled = an, onClick = aktion),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = ps.font(15), color = farbe)
        Text(label, fontSize = ps.font(8), color = farbe, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Das Groessenverhaeltnis eines Objekts als Kaestchen. Bewusst kein
 * gerendertes Bild: das kostet je Objekt einen Durchgang durch den
 * Viewport. Um zwei Objekte zu unterscheiden, reichen die Proportionen.
 */
@Composable
fun ObjektMasse(objekt: PsmCore.ObjectInfo) {
    val ps = LocalPsScale.current
    val b = maxOf(objekt.sizeMm.first, 0.1f)
    val t = maxOf(objekt.sizeMm.second, 0.1f)
    val h = maxOf(objekt.sizeMm.third, 0.1f)
    val laengste = maxOf(b, maxOf(t, h))
    val breite = maxOf(26f * (maxOf(b, t) / laengste), 4f)
    val hoehe = maxOf(26f * (h / laengste), 4f)
    Box(
        Modifier
            .testTag("blatt.masse.${objekt.id}")
            .size(ps.pt(36))
            .clip(RoundedCornerShape(ps.pt(2)))
            .background(PrusaColors.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ps.pt(breite), ps.pt(hoehe))
                .clip(RoundedCornerShape(ps.pt(1)))
                .background(PrusaColors.orange),
        )
    }
}
