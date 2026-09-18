package de.psmobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.AlertDialog
import de.psmobile.ui.theme.PrusaColors
import java.util.Locale

/**
 * Eine Bettauswahl fuer beide Arbeitsmodi - Port von
 * `ios/PSMobile/Screens/BedSelector.swift`.
 *
 * Breit liegen die Betten als Kapseln in einer Reihe. Schmal bleibt das
 * aktive Bett direkt erreichbar; die vollstaendige Liste oeffnet sich
 * als Blatt ([BedSelectionOverlay]).
 */
@Composable
fun BedSelector(
    model: SlicerModel = LocalSlicerModel.current,
    onArrange: () -> Unit,
    onOpenSelection: () -> Unit,
) {
    val ps = LocalPsScale.current
    var umzubenennen by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }

    val schmal = ps.windowSize.width < 760.dp
    val aktiv = model.beds.firstOrNull { it.active }

    Box(Modifier.background(PrusaColors.background)) {
        if (schmal) {
            KompakteAuswahl(model, aktiv, onOpenSelection)
        } else {
            RasterAuswahl(model, onUmbenennen = { bett -> name = bett.name; umzubenennen = bett.index })
        }
        PSMarke(name = "bed.selector")
    }

    umzubenennen?.let { index ->
        BettUmbenennenDialog(
            name = name,
            onNameChange = { name = it },
            nachricht = st(
                "An empty name restores the bed number.",
                "Ein leerer Name stellt die Bettnummer wieder her.",
            ),
            onCancel = { umzubenennen = null },
            onApply = {
                model.renameBed(index, name)
                umzubenennen = null
            },
        )
    }
}

@Composable
private fun KompakteAuswahl(model: SlicerModel, aktiv: PsmCore.Bed?, onOpenSelection: () -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ps.pt(8), vertical = ps.pt(4)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
    ) {
        Row(
            Modifier
                .testTag("bed.selector.active")
                .weight(1f)
                .heightIn(min = ps.touch())
                .clip(RoundedCornerShape(ps.pt(7)))
                .background(PrusaColors.panelRaised)
                .clickable(onClick = onOpenSelection)
                .padding(horizontal = ps.pt(12)),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(7)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SfSymbol(
                if (aktiv?.locked == true) "lock.fill" else "square.stack.3d.up",
                Modifier.size(ps.font(17).value.dp),
                tint = PrusaColors.textPrimary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    aktiv?.let { model.bedLabel(it.index) } ?: st("Bed", "Bett"),
                    fontSize = ps.font(13),
                    fontWeight = FontWeight.SemiBold,
                    color = PrusaColors.textPrimary,
                )
                val anzahl = aktiv?.objectCount ?: 0
                Text(
                    objekteText(anzahl),
                    fontSize = ps.font(10),
                    color = PrusaColors.textMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            SfSymbol("chevron.down", Modifier.size(ps.font(17).value.dp), tint = PrusaColors.textPrimary)
        }
    }
}

/**
 * Eine waagerecht scrollende Reihe schmaler Kapseln in
 * Werkzeugleistenhoehe.
 */
@Composable
private fun RasterAuswahl(model: SlicerModel, onUmbenennen: (PsmCore.Bed) -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ps.pt(8), vertical = ps.pt(4)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.beds.forEach { bett ->
                BettKapsel(model, bett, onUmbenennen)
            }
            Box(
                Modifier
                    .testTag("bed.add")
                    .size(width = ps.touch(40), height = ps.touch(38))
                    .clip(RoundedCornerShape(ps.pt(6)))
                    .background(PrusaColors.panelRaised)
                    .clickable { model.addBed() },
                contentAlignment = Alignment.Center,
            ) {
                SfSymbol("plus", Modifier.size(ps.font(14).value.dp), tint = PrusaColors.orange)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Name, Objektzahl und Schlossknopf in einer Kapsel. Umbenennen und
 * Entfernen liegen im Kontextmenue (langer Druck).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BettKapsel(model: SlicerModel, bett: PsmCore.Bed, onUmbenennen: (PsmCore.Bed) -> Unit) {
    val ps = LocalPsScale.current
    var zeigeMenue by remember { mutableStateOf(false) }
    val entfernbar = model.beds.size > 1 && bett.objectCount == 0

    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(ps.pt(6)))
                .background(PrusaColors.panelRaised)
                .border(
                    1.dp,
                    if (bett.active) PrusaColors.orange.copy(alpha = 0.6f) else Color.Transparent,
                    RoundedCornerShape(ps.pt(6)),
                ),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(2)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Dezenter als das volle Orange der uebrigen Aktionsknoepfe:
            // das aktive Bett ist ein Zustand, kein Befehl.
            Row(
                Modifier
                    .testTag("bed.card.${bett.index}")
                    .combinedClickable(
                        onClick = { model.selectBed(bett.index) },
                        onLongClick = { zeigeMenue = true },
                    )
                    .padding(start = ps.pt(10))
                    .heightIn(min = ps.touch(32)),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    model.bedLabel(bett.index),
                    fontSize = ps.font(11),
                    fontWeight = FontWeight.Medium,
                    color = if (bett.active) PrusaColors.orange else PrusaColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${bett.objectCount}",
                    fontSize = ps.font(9),
                    color = PrusaColors.textMuted,
                )
            }

            Box(
                Modifier
                    .testTag("bed.lock.${bett.index}")
                    .size(width = ps.touch(26), height = ps.touch(32))
                    .clickable { model.toggleBedLock(bett.index) },
                contentAlignment = Alignment.Center,
            ) {
                SfSymbol(
                    if (bett.locked) "lock.fill" else "lock.open",
                    Modifier.size(ps.font(9).value.dp),
                    tint = PrusaColors.textMuted,
                )
            }

            // Direkt sichtbar statt nur im Kontextmenue - nur bei einem
            // leeren Bett: ein volles darf nicht so verschwinden.
            if (entfernbar) {
                Box(
                    Modifier
                        .testTag("bed.remove.${bett.index}")
                        .padding(end = ps.pt(3))
                        .size(width = ps.touch(24), height = ps.touch(32))
                        .clickable { model.removeBed(bett.index) },
                    contentAlignment = Alignment.Center,
                ) {
                    SfSymbol("xmark", Modifier.size(ps.font(8).value.dp), tint = PrusaColors.textMuted)
                }
            } else {
                Spacer(Modifier.width(ps.pt(3)))
            }
        }

        DropdownMenu(expanded = zeigeMenue, onDismissRequest = { zeigeMenue = false }) {
            DropdownMenuItem(
                text = { Text(st("Rename", "Umbenennen")) },
                leadingIcon = { SfSymbol("pencil", Modifier.size(ps.font(16).value.dp)) },
                onClick = { zeigeMenue = false; onUmbenennen(bett) },
            )
            if (entfernbar) {
                DropdownMenuItem(
                    text = { Text(st("Remove", "Entfernen"), color = PrusaColors.danger) },
                    leadingIcon = { SfSymbol("trash", Modifier.size(ps.font(16).value.dp), tint = PrusaColors.danger) },
                    onClick = { zeigeMenue = false; model.removeBed(bett.index) },
                )
            }
        }
    }
}

/** Der Umbenennen-Dialog: Name, Abbrechen, Uebernehmen. */
@Composable
private fun BettUmbenennenDialog(
    name: String,
    onNameChange: (String) -> Unit,
    nachricht: String?,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val ps = LocalPsScale.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(st("Rename bed", "Bett umbenennen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ps.pt(8))) {
                if (nachricht != null) Text(nachricht)
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = ps.font(14), color = PrusaColors.textPrimary),
                    cursorBrush = SolidColor(PrusaColors.orange),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ps.touch(40))
                                .clip(RoundedCornerShape(ps.pt(6)))
                                .background(PrusaColors.panelRaised)
                                .padding(horizontal = ps.pt(10)),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (name.isEmpty()) {
                                Text(st("Name", "Name"), fontSize = ps.font(14), color = PrusaColors.textMuted)
                            }
                            inner()
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) { Text(st("Apply", "Übernehmen"), color = PrusaColors.orange) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted) }
        },
    )
}

/**
 * Das vom jeweiligen Arbeitsmodus praesentierte Blatt mit allen Betten.
 * [isPresented]/[onIsPresentedChange] entsprechen `@Binding var isPresented`.
 */
@Composable
fun BedSelectionSheet(
    model: SlicerModel = LocalSlicerModel.current,
    isPresented: Boolean,
    onIsPresentedChange: (Boolean) -> Unit,
) {
    val ps = LocalPsScale.current
    var umzubenennen by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }

    Box(Modifier.background(PrusaColors.background)) {
        Column(Modifier.fillMaxWidth()) {
            // Navigationstitel und "Fertig" der Toolbar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ps.pt(16), vertical = ps.pt(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    st("Beds", "Betten"),
                    fontSize = ps.font(17),
                    fontWeight = FontWeight.SemiBold,
                    color = PrusaColors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onIsPresentedChange(false) },
                    modifier = Modifier.testTag("bed.selector.close"),
                ) { Text(st("Done", "Fertig"), color = PrusaColors.orange) }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(ps.pt(16)),
                verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
            ) {
                model.beds.forEach { bett ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                        val vordergrund = if (bett.active) PrusaColors.background else PrusaColors.textPrimary
                        Row(
                            Modifier
                                .testTag("bed.card.${bett.index}")
                                .fillMaxWidth()
                                .heightIn(min = ps.touch(64))
                                .clip(RoundedCornerShape(ps.pt(8)))
                                .background(if (bett.active) PrusaColors.orange else PrusaColors.panelRaised)
                                .clickable {
                                    model.selectBed(bett.index)
                                    onIsPresentedChange(false)
                                }
                                .padding(horizontal = ps.pt(12)),
                            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SfSymbol(
                                if (bett.locked) "lock.fill" else "square.stack.3d.up",
                                Modifier.size(ps.font(17).value.dp),
                                tint = vordergrund,
                            )
                            Column {
                                Text(
                                    model.bedLabel(bett.index),
                                    fontSize = ps.font(17),
                                    fontWeight = FontWeight.SemiBold,
                                    color = vordergrund,
                                )
                                Text(
                                    objekteText(bett.objectCount),
                                    fontSize = ps.font(12),
                                    color = PrusaColors.textMuted,
                                )
                            }
                            Spacer(Modifier.width(ps.pt(90)).weight(1f))
                        }

                        Row(Modifier.padding(ps.pt(4))) {
                            MiniKnopf("pencil", id = "bed.rename.${bett.index}") {
                                name = bett.name
                                umzubenennen = bett.index
                            }
                            MiniKnopf(if (bett.locked) "lock.open" else "lock", id = "bed.lock.${bett.index}") {
                                model.toggleBedLock(bett.index)
                            }
                            if (model.beds.size > 1 && bett.objectCount == 0) {
                                MiniKnopf("trash", id = "bed.remove.${bett.index}") {
                                    model.removeBed(bett.index)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        model.addBed()
                        onIsPresentedChange(false)
                    },
                    modifier = Modifier
                        .testTag("bed.add")
                        .fillMaxWidth()
                        .heightIn(min = ps.touch()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrusaColors.orange,
                        contentColor = Color.White,
                    ),
                ) {
                    SfSymbol("plus", Modifier.size(ps.font(16).value.dp), tint = Color.White)
                    Spacer(Modifier.width(ps.pt(6)))
                    Text(st("Add bed", "Bett hinzufügen"), fontSize = ps.font(15))
                }
            }
        }
        PSMarke(name = "bed.selector.sheet")
    }

    umzubenennen?.let { index ->
        BettUmbenennenDialog(
            name = name,
            onNameChange = { name = it },
            nachricht = null,
            onCancel = { umzubenennen = null },
            onApply = {
                model.renameBed(index, name)
                umzubenennen = null
            },
        )
    }
}

@Composable
private fun MiniKnopf(symbol: String, id: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .testTag(id)
            .size(ps.touch(34))
            .clickable(onClick = aktion),
        contentAlignment = Alignment.Center,
    ) {
        SfSymbol(symbol, Modifier.size(ps.font(17).value.dp), tint = PrusaColors.textPrimary)
    }
}

/** Konfliktfreies Bottom-Sheet fuer Hosts mit mehreren Systemblaettern. */
@Composable
fun BedSelectionOverlay(
    model: SlicerModel = LocalSlicerModel.current,
    isPresented: Boolean,
    onIsPresentedChange: (Boolean) -> Unit,
) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onIsPresentedChange(false) },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = minOf(ps.windowSize.height * 0.82f, ps.pt(620)))
                .shadow(ps.pt(20), RoundedCornerShape(ps.pt(14)))
                .clip(RoundedCornerShape(ps.pt(14)))
                .background(PrusaColors.background),
        ) {
            BedSelectionSheet(
                model = model,
                isPresented = isPresented,
                onIsPresentedChange = onIsPresentedChange,
            )
        }
    }
}

/**
 * Ziel, Abstand und Ergebnis eines Arrange-Laufs - ein kleines
 * Popover statt eines Vollbild-Blatts.
 */
@Composable
fun ArrangePanel(
    model: SlicerModel = LocalSlicerModel.current,
    isPresented: Boolean,
    onIsPresentedChange: (Boolean) -> Unit,
) {
    val ps = LocalPsScale.current
    var ziel by remember { mutableStateOf(0) }
    var abstand by remember { mutableStateOf(6.0) }
    var ergebnis by remember { mutableStateOf("") }
    /** Alle Betten statt eines einzelnen Zielbetts. */
    var alleBetten by remember { mutableStateOf(false) }
    /** Entspricht ArrangeSettings::set_rotation_enabled im Kern - Vorgabe aus. */
    var drehenErlauben by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ziel = model.beds.firstOrNull { it.active }?.index ?: 0
    }

    fun anordnen() {
        if (alleBetten) {
            model.arrangeAll(gapMm = abstand.toFloat(), allowRotation = drehenErlauben)
            ergebnis = st("All beds arranged.", "Alle Betten angeordnet.")
            return
        }
        try {
            val info = model.arrange(target = ziel, gapMm = abstand.toFloat(), allowRotation = drehenErlauben)
            ergebnis = when (info.status) {
                PsmCore.ArrangeStatus.EMPTY -> st(
                    "Bed ${ziel + 1} is empty. There is nothing to arrange.",
                    "Bett ${ziel + 1} ist leer. Es gibt nichts anzuordnen.",
                )
                PsmCore.ArrangeStatus.ARRANGED -> st(
                    "Bed ${ziel + 1}: ${info.instanceCount} instances arranged.",
                    "Bett ${ziel + 1}: ${info.instanceCount} Instanzen angeordnet.",
                )
                else -> ergebnis
            }
        } catch (e: PsmCore.PsmException) {
            // Das Modell wirft bei gesperrtem oder vollem Bett - die
            // Texte sind dieselben wie die iOS-Fehlerzweige.
            val meldung = e.message.orEmpty().lowercase()
            ergebnis = when {
                model.isBedLocked(ziel) || meldung.contains("locked") || meldung.contains("gesperrt") -> st(
                    "Bed ${ziel + 1} is locked. Unlock it in the bed selector.",
                    "Bett ${ziel + 1} ist gesperrt. Entsperre es in der Bettauswahl.",
                )
                meldung.contains("full") || meldung.contains("voll") -> st(
                    "Bed ${ziel + 1} is full. Reduce copies or choose another bed.",
                    "Bett ${ziel + 1} ist voll. Verringere die Kopien oder wähle ein anderes Bett.",
                )
                else -> e.message.orEmpty()
            }
        }
    }

    Box(
        Modifier
            .width(ps.pt(300))
            .background(PrusaColors.background),
    ) {
        Column(
            Modifier.padding(ps.pt(16)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(12)),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PsUiCatalog.tr("Arrange"),
                    fontSize = ps.font(15),
                    fontWeight = FontWeight.SemiBold,
                    color = PrusaColors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { onIsPresentedChange(false) },
                    modifier = Modifier.testTag("arrange.close"),
                ) { Text(st("Done", "Fertig"), fontSize = ps.font(13), color = PrusaColors.orange) }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("arrange.zielmodus"),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            ) {
                ZielKapsel(
                    st("Current bed", "Aktuelles Bett"), aktiv = !alleBetten,
                    kennung = "arrange.zielmodus.aktuell", modifier = Modifier.weight(1f),
                ) { alleBetten = false }
                ZielKapsel(
                    st("All beds", "Alle Betten"), aktiv = alleBetten,
                    kennung = "arrange.zielmodus.alle", modifier = Modifier.weight(1f),
                ) { alleBetten = true }
            }

            if (!alleBetten) {
                Text(
                    st("Target bed", "Zielbett"),
                    fontSize = ps.font(11),
                    color = PrusaColors.textMuted,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = ps.pt(140))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
                ) {
                    model.beds.forEach { bett ->
                        val gewaehlt = ziel == bett.index
                        val vordergrund = if (gewaehlt) PrusaColors.background else PrusaColors.textPrimary
                        Row(
                            Modifier
                                .testTag("arrange.target.${bett.index}")
                                .fillMaxWidth()
                                .heightIn(min = ps.touch(38))
                                .clip(RoundedCornerShape(ps.pt(6)))
                                .background(if (gewaehlt) PrusaColors.orange else PrusaColors.panelRaised)
                                .clickable {
                                    ziel = bett.index
                                    ergebnis = ""
                                }
                                .padding(horizontal = ps.pt(10)),
                            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (bett.locked) {
                                SfSymbol("lock.fill", Modifier.size(ps.font(15).value.dp), tint = vordergrund)
                            }
                            Text(model.bedLabel(bett.index), fontSize = ps.font(15), color = vordergrund)
                            Spacer(Modifier.weight(1f))
                            Text("${bett.objectCount}", fontSize = ps.font(15), color = vordergrund)
                        }
                    }
                }
            }

            // Stepper: Beschriftung, Wert und die beiden Schrittknoepfe.
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("arrange.gap"),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(st("Spacing", "Abstand"), fontSize = ps.font(15), color = PrusaColors.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    String.format(Locale.getDefault(), "%.1f", abstand) + " mm",
                    fontSize = ps.font(15),
                    color = PrusaColors.textPrimary,
                )
                SchrittKnopf("−", enabled = abstand > 0.0) { abstand = maxOf(0.0, abstand - 0.5) }
                SchrittKnopf("+", enabled = abstand < 50.0) { abstand = minOf(50.0, abstand + 0.5) }
            }

            Row(
                Modifier
                    .testTag("arrange.drehen")
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(32))
                    .clickable { drehenErlauben = !drehenErlauben },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(st("Allow rotation", "Drehen erlauben"), fontSize = ps.font(15), color = PrusaColors.textPrimary)
                Spacer(Modifier.weight(1f))
                SfSymbol(
                    if (drehenErlauben) "checkmark.square.fill" else "square",
                    Modifier.size(ps.font(17).value.dp),
                    tint = if (drehenErlauben) PrusaColors.orange else PrusaColors.textMuted,
                )
            }

            if (ergebnis.isNotEmpty()) {
                Text(
                    ergebnis,
                    fontSize = ps.font(12),
                    color = PrusaColors.textPrimary,
                    modifier = Modifier.testTag("arrange.result"),
                )
            }

            Button(
                onClick = { anordnen() },
                modifier = Modifier
                    .testTag("arrange.run")
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(44)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrusaColors.orange,
                    contentColor = Color.White,
                ),
            ) {
                SfSymbol("square.grid.2x2", Modifier.size(ps.font(16).value.dp), tint = Color.White)
                Spacer(Modifier.width(ps.pt(6)))
                Text(PsUiCatalog.tr("Arrange"), fontSize = ps.font(15))
            }
        }
        PSMarke(name = "arrange.panel")
    }
}

@Composable
private fun ZielKapsel(
    label: String,
    aktiv: Boolean,
    kennung: String,
    modifier: Modifier = Modifier,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    Box(
        modifier
            .testTag(kennung)
            .heightIn(min = ps.touch(34))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(if (aktiv) PrusaColors.orange else PrusaColors.panelRaised)
            .clickable(onClick = aktion),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = ps.font(12),
            fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
            color = if (aktiv) PrusaColors.background else PrusaColors.textPrimary,
        )
    }
}

@Composable
private fun SchrittKnopf(zeichen: String, enabled: Boolean, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .size(width = ps.touch(36), height = ps.touch(32))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .clickable(enabled = enabled, onClick = aktion),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            zeichen,
            fontSize = ps.font(16),
            color = if (enabled) PrusaColors.textPrimary else PrusaColors.textMuted.copy(alpha = 0.4f),
        )
    }
}

/** "1 Objekt", sonst "n Objekte" - bis zum 16.09.2026 stand "1 objects" auf der Bettkarte. */
internal fun objekteText(anzahl: Int): String =
    if (anzahl == 1) st("1 object", "1 Objekt") else st("$anzahl objects", "$anzahl Objekte")
