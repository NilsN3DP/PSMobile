package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.shared.rules.AppSettings
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import java.util.Locale
import kotlin.math.floor

/**
 * Der Objektinspektor im Advanced Mode - Port von
 * `ios/PSMobile/Screens/AdvancedObjectInspectorView.swift`.
 *
 * Alles, was man mit einem einzelnen Objekt tut, an einer Stelle:
 * Groesse und Drehung in Zahlen, Ablegen, Einpassen, Spiegeln, Kopien.
 * Die Zahlenfelder sind bewusst da, wo die Gizmos schon sind: mit dem
 * Finger stellt man ungefaehr ein, mit einer Zahl genau.
 *
 * [gizmo]/[onGizmoChange] entsprechen `@Binding var gizmo` des Originals.
 * Die Griffe stehen inzwischen oben in der Werkzeugleiste; die Bindung
 * bleibt wie auf iOS Teil der Schnittstelle, wird hier aber nicht
 * gelesen.
 *
 * [onSichtbereichChange] ist das Gegenstueck zu
 * `AdvancedInspectorSichtbereichPreferenceKey`: der Rahmen des oberen
 * Aktionsblocks (iOS `.id("advanced.bearbeiten.aktionen")`) in
 * Fensterkoordinaten, wie `geo.frame(in: .global)`. Die Seitenleiste
 * entscheidet damit, ob sie dorthin scrollen muss.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AdvancedObjectInspectorView(
    model: SlicerModel = LocalSlicerModel.current,
    objekt: PsmCore.ObjectInfo,
    gizmo: PsmViewport.Gizmo,
    onGizmoChange: (PsmViewport.Gizmo) -> Unit,
    onSichtbereichChange: (Rect) -> Unit = {},
) {
    val ps = LocalPsScale.current
    var zeigeSchichten by remember { mutableStateOf(false) }
    /** Was das letzte Vereinfachen oder Zerlegen ergeben hat. */
    var vereinfachtText by remember { mutableStateOf("") }
    var zeigeTeilBlatt by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onSichtbereichChange(it.boundsInWindow()) },
            verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
            horizontalAlignment = Alignment.Start,
        ) {
            // Die Griffe stehen oben in der Werkzeugleiste, bei Ansicht
            // und Vorschau: sie bestimmen, was ein Finger im Viewport
            // tut, und das ist keine Zahleneinstellung.
            Groesse(model, objekt)
            HorizontalDivider(color = PrusaColors.divider)
            Drehung(model, objekt)
            HorizontalDivider(color = PrusaColors.divider)
            Handgriffe(model, objekt)
            Kopien(model, objekt)
            if (model.beds.size > 1) Bettwechsel(model, objekt)
            HorizontalDivider(color = PrusaColors.divider)
            SchichtenKnopf(onClick = { zeigeSchichten = true })
        }
        HorizontalDivider(color = PrusaColors.divider)
        Geometrie(
            model = model,
            objekt = objekt,
            vereinfachtText = vereinfachtText,
            onVereinfachtText = { vereinfachtText = it },
            onTeilHinzufuegen = { zeigeTeilBlatt = true },
        )
        HorizontalDivider(color = PrusaColors.divider)
        Teile(model, objekt)
        // Eine Marke, kein Bezeichner am Stapel - wie im Original.
        PSMarke(name = "advanced.objectTree")
    }

    if (zeigeSchichten) {
        Blatt(onDismiss = { zeigeSchichten = false }) {
            LayerProfileView(model = model, objekt = objekt, onClose = { zeigeSchichten = false })
        }
    }
    if (zeigeTeilBlatt) {
        Blatt(onDismiss = { zeigeTeilBlatt = false }) {
            TeilHinzufuegenView(model = model, objektId = objekt.id, onClose = { zeigeTeilBlatt = false })
        }
    }
}

@Composable
private fun SchichtenKnopf(onClick: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .clickable(onClick = onClick)
            .testTag("advanced.schichten"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            PsUiCatalog.tr("Variable layer height"),
            color = PrusaColors.orange,
            fontSize = ps.font(12),
        )
    }
}

// MARK: - Groesse

/**
 * Prozent und Millimeter nebeneinander: am Modell denkt man in Prozent,
 * beim Einpassen aufs Bett in Millimetern.
 */
@Composable
private fun Groesse(model: SlicerModel, objekt: PsmCore.ObjectInfo) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Beschriftung(PsUiCatalog.tr("Scale"))
        Zahlenfeld(
            wert = String.format(Locale.US, "%.1f", objekt.scale.first * 100),
            einheit = "%",
            kennung = "advanced.scale.prozent",
            modifier = Modifier.weight(1f),
        ) { text ->
            val p = text.replace(',', '.').toFloatOrNull()
            if (p != null && p > 0f) model.setUniformScale(objekt.id, p / 100)
        }
        // In der eingestellten Einheit anzeigen und lesen - der Kern rechnet
        // weiter in mm (siehe masseText in Texte.kt).
        val zoll = rememberAppSetting(AppSettings.KEY_UNITS_IMPERIAL, false)
        val groesste = maxOf(objekt.sizeMm.first, maxOf(objekt.sizeMm.second, objekt.sizeMm.third))
        Zahlenfeld(
            wert = if (zoll) String.format(Locale.US, "%.2f", groesste / 25.4f)
                   else String.format(Locale.US, "%.1f", groesste),
            einheit = if (zoll) "in" else "mm",
            kennung = "advanced.scale.mm",
            modifier = Modifier.weight(1f),
        ) { text ->
            val eingabe = text.replace(',', '.').toFloatOrNull()
            val mm = if (eingabe != null && zoll) eingabe * 25.4f else eingabe
            if (mm != null && mm > 0f) model.scaleToSize(objekt.id, mm)
        }
    }
}

// MARK: - Drehung

@Composable
private fun Drehung(model: SlicerModel, objekt: PsmCore.ObjectInfo) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Beschriftung(PsUiCatalog.tr("Rotation"))
            listOf("X", "Y", "Z").forEachIndexed { achse, name ->
                // Auf Android liefert der Kern-Wrapper bereits Grad -
                // niemand dreht ein Modell um 1,5708.
                val grad = when (achse) {
                    0 -> objekt.rotation.first
                    1 -> objekt.rotation.second
                    else -> objekt.rotation.third
                }
                Zahlenfeld(
                    wert = gerundet(grad).toString(),
                    einheit = name,
                    kennung = "advanced.rotate.$name",
                    modifier = Modifier.weight(1f),
                    vorzeichen = true,
                ) { text ->
                    val g = text.replace(',', '.').toFloatOrNull()
                    if (g != null) model.setRotationAxis(objekt.id, achse, g)
                }
            }
        }
        // Vierteldrehungen sind der haeufigste Fall und mit dem Finger
        // sonst nicht genau zu treffen.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(ps.pt(64)))
            Knopf("↺ 90°", kennung = "advanced.drehen.links") {
                model.rotateBy(objekt.id, 2, -90f)
            }
            Knopf("↻ 90°", kennung = "advanced.drehen.rechts") {
                model.rotateBy(objekt.id, 2, 90f)
            }
            Knopf("180°", kennung = "advanced.drehen.halb") {
                model.rotateBy(objekt.id, 2, 180f)
            }
        }
    }
}

// MARK: - Handgriffe

@Composable
private fun Handgriffe(model: SlicerModel, objekt: PsmCore.ObjectInfo) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Knopf(st("Place on bed", "Auf das Bett legen"), kennung = "advanced.ablegen") {
                model.dropToBed(objekt.id)
            }
            Knopf(st("Fit to bed", "Aufs Bett einpassen"), kennung = "advanced.einpassen") {
                model.fitToBed(objekt.id)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("X", "Y", "Z").forEachIndexed { achse, name ->
                Knopf(st("Mirror ", "Spiegeln ") + name, kennung = "advanced.spiegeln.$name") {
                    model.mirror(objekt.id, achse)
                }
            }
        }
    }
}

@Composable
private fun Kopien(model: SlicerModel, objekt: PsmCore.ObjectInfo) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Beschriftung(st("Copies", "Kopien"))
        Knopf("−", kennung = "advanced.kopien.weniger") {
            if (objekt.instances > 1) {
                model.setInstances(objekt.id, objekt.instances - 1)
            }
        }
        Text(
            "${objekt.instances}",
            modifier = Modifier.width(ps.pt(44)).testTag("advanced.kopien.anzahl"),
            color = PrusaColors.textPrimary,
            fontSize = ps.font(14),
            textAlign = TextAlign.Center,
        )
        Knopf("+", kennung = "advanced.kopien.mehr") {
            model.setInstances(objekt.id, objekt.instances + 1)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Bettwechsel(model: SlicerModel, objekt: PsmCore.ObjectInfo) {
    val ps = LocalPsScale.current
    var offen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ps.touch(44))
                .clip(RoundedCornerShape(ps.pt(6)))
                .background(PrusaColors.panelRaised)
                .clickable { offen = true }
                .testTag("advanced.bettwechsel"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                st("Move to another bed", "Auf anderes Bett verschieben"),
                color = PrusaColors.textPrimary,
                fontSize = ps.font(13),
            )
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            ScaledOverlay {
                model.beds.filter { !it.active }.forEach { bett ->
                    DropdownMenuItem(
                        text = { Text(st("Bed ", "Bett ") + "${bett.index + 1} · ${bett.objectCount}") },
                        onClick = {
                            offen = false
                            model.moveToBed(listOf(objekt.id), bett.index)
                        },
                    )
                }
            }
        }
    }
}

// MARK: - Geometrie

/**
 * Was an der Geometrie selbst geaendert wird.
 *
 * Vereinfachen meldet zurueck, was es gebracht hat: ohne die beiden
 * Zahlen tippt man darauf und weiss nicht, ob etwas passiert ist.
 */
@Composable
private fun Geometrie(
    model: SlicerModel,
    objekt: PsmCore.ObjectInfo,
    vereinfachtText: String,
    onVereinfachtText: (String) -> Unit,
    onTeilHinzufuegen: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Abschnitt(st("Geometry", "Geometrie"))
        if (vereinfachtText.isNotEmpty()) {
            Text(vereinfachtText, color = PrusaColors.orange, fontSize = ps.font(10))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AktionsKnopf(
                st("Simplify by half", "Auf die Hälfte"),
                kennung = "advanced.vereinfachen",
                modifier = Modifier.weight(1f),
            ) {
                model.simplify(objekt.id, 0.5f)?.let { e ->
                    onVereinfachtText("${e.first} → ${e.second}")
                }
            }
            AktionsKnopf(
                st("Split into parts", "In Teile zerlegen"),
                kennung = "advanced.zerlegen",
                modifier = Modifier.weight(1f),
            ) {
                val n = model.splitVolumes(objekt.id)
                onVereinfachtText(st("Parts", "Teile") + ": $n")
            }
        }
        // Aussparung, Modifier, Stuetzenblocker: das, wofuer man sonst
        // das Programm wechselt.
        AktionsKnopf(
            st("Add part", "Teil hinzufügen"),
            kennung = "advanced.teilHinzufuegen",
            modifier = Modifier.fillMaxWidth(),
            aktion = onTeilHinzufuegen,
        )
    }
}

// MARK: - Teile

/**
 * Der Objektbaum: aus wie vielen Koerpern besteht das Objekt, und
 * welcher Extruder druckt welchen. Bei einem Extruder ist die
 * Zuweisung sinnlos und bleibt weg.
 */
@Composable
private fun Teile(model: SlicerModel, objekt: PsmCore.ObjectInfo) {
    val ps = LocalPsScale.current
    val anzahl = model.volumeCount(objekt.id)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Abschnitt(st("Parts", "Teile") + " ($anzahl)")
        for (i in 0 until anzahl) {
            val teil = model.volumeInfo(objekt.id, i) ?: continue
            key(i) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = ps.pt(2)),
                    horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(
                            if (teil.name.isEmpty()) "${i + 1}" else teil.name,
                            color = PrusaColors.textPrimary,
                            fontSize = ps.font(13),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            teilArt(teil.type) + " · ${teil.triangles}",
                            color = PrusaColors.textMuted,
                            fontSize = ps.font(10),
                        )
                    }
                    if (model.extruderCount > 1) {
                        ExtruderWahl(model, objekt, i, teil)
                    }
                    // Der Modellkoerper bleibt: nimmt man ihn weg, bleibt
                    // ein Objekt ohne Geometrie zurueck. Alles andere ist
                    // ein Zusatz und darf wieder weg.
                    if (teil.type != PsmCore.VolumeType.MODEL_PART) {
                        Box(
                            Modifier
                                .height(ps.touch(40))
                                .clickable { model.removeVolume(objekt.id, i) }
                                .padding(horizontal = ps.pt(10))
                                .testTag("advanced.teil.$i.entfernen"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                st("Remove", "Entfernen"),
                                color = PrusaColors.danger,
                                fontSize = ps.font(12),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Das `Menu` je Teil: geerbt oder eine feste Duese. */
@Composable
private fun ExtruderWahl(
    model: SlicerModel,
    objekt: PsmCore.ObjectInfo,
    index: Int,
    teil: PsmCore.VolumeInfo,
) {
    val ps = LocalPsScale.current
    var offen by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .height(ps.touch(40))
                .clip(RoundedCornerShape(ps.pt(4)))
                .background(PrusaColors.panelRaised)
                .clickable { offen = true }
                .padding(horizontal = ps.pt(10))
                .testTag("advanced.teil.$index.extruder"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (teil.explicitExtruder == 0) st("Inherited", "Geerbt") else "${teil.explicitExtruder}",
                color = PrusaColors.orange,
                fontSize = ps.font(12),
            )
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            ScaledOverlay {
                // Null heisst: der Extruder des Objekts.
                DropdownMenuItem(
                    text = { Text(st("Inherited", "Geerbt")) },
                    onClick = {
                        offen = false
                        model.setVolumeExtruder(objekt.id, index, 0)
                    },
                )
                for (nummer in 1..model.extruderCount) {
                    DropdownMenuItem(
                        text = { Text("$nummer") },
                        onClick = {
                            offen = false
                            model.setVolumeExtruder(objekt.id, index, nummer)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Die Arten aus dem C-ABI. Ein Modifikator sieht im Baum aus wie ein
 * Teil, druckt aber nichts - das muss dranstehen.
 */
private fun teilArt(typ: PsmCore.VolumeType): String = when (typ) {
    PsmCore.VolumeType.MODEL_PART -> st("Part", "Teil")
    PsmCore.VolumeType.NEGATIVE -> st("Negative", "Aussparung")
    PsmCore.VolumeType.MODIFIER -> st("Modifier", "Modifikator")
    PsmCore.VolumeType.SUPPORT_BLOCKER -> st("Support blocker", "Stützensperre")
    PsmCore.VolumeType.SUPPORT_ENFORCER -> st("Support enforcer", "Stützenzwang")
    PsmCore.VolumeType.UNKNOWN -> "?"
}

// MARK: - Bausteine

@Composable
private fun Abschnitt(text: String) {
    val ps = LocalPsScale.current
    Text(
        text.uppercase(),
        color = PrusaColors.textMuted,
        fontSize = ps.font(11),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Beschriftung(text: String) {
    val ps = LocalPsScale.current
    Text(
        text,
        modifier = Modifier.width(ps.pt(64)),
        color = PrusaColors.textMuted,
        fontSize = ps.font(12),
        textAlign = TextAlign.Start,
    )
}

/** Ein Knopf in einer Zeile - fuellt wie `.frame(maxWidth: .infinity)`. */
@Composable
private fun RowScope.Knopf(label: String, kennung: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .clickable(onClick = aktion)
            .testTag(kennung),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = PrusaColors.textPrimary, fontSize = ps.font(12))
    }
}

/** Die orangen Geometrie-Knoepfe mit Radius 4. */
@Composable
private fun AktionsKnopf(
    label: String,
    kennung: String,
    modifier: Modifier = Modifier,
    aktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    Box(
        modifier
            .heightIn(min = ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .clickable(onClick = aktion)
            .testTag(kennung),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = PrusaColors.orange, fontSize = ps.font(12))
    }
}

/** Swift `Int(x.rounded())`: halbe Werte weg von null. */
private fun gerundet(wert: Float): Int =
    (if (wert < 0f) -floor(-wert + 0.5f) else floor(wert + 0.5f)).toInt()

/**
 * Ein Zahlenfeld, das erst beim Verlassen uebernimmt - `ZahlenFeld`
 * im Original.
 *
 * Bei jedem Tastendruck zu uebernehmen waere hier falsch: aus "12"
 * wird beim Tippen von "125" kurz die 12, und das Modell springt
 * zweimal. Ausserdem stuende nach dem Loeschen des letzten Zeichens
 * eine Null da.
 */
/**
 * [vorzeichen]: Feld darf negativ werden (Drehwinkel). Samsungs Tastatur
 * zeigt im Dezimalfeld keine Minus-Taste (S23 FE, 14.09.2026), Gboard
 * schon; iOS hat sie ueber .numbersAndPunctuation immer. Deshalb hier ein
 * kleiner "±"-Knopf, der das Vorzeichen des Eingabetexts kippt - ein
 * Android-Zusatz ohne iOS-Zwilling, weil das Problem dort nicht besteht.
 */
@Composable
private fun Zahlenfeld(
    wert: String,
    einheit: String,
    kennung: String,
    modifier: Modifier = Modifier,
    vorzeichen: Boolean = false,
    uebernehmen: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    val fokus = LocalFocusManager.current
    var text by remember { mutableStateOf(wert) }
    var amZug by remember { mutableStateOf(false) }
    // Ob seit dem Fokus etwas getippt wurde. Solange nicht, darf ein neuer
    // Wert aus dem Modell hinein - sonst zeigte das mm-Feld nach dem Wechsel
    // aus dem %-Feld noch 20.0, waehrend das Objekt schon 400 mm mass
    // (Emulator, 15.09.2026): das Modell aendert sich erst einen Bildaufbau
    // nach dem Fokuswechsel, und da war das Feld schon "am Zug".
    var getippt by remember { mutableStateOf(false) }
    // Waehrend jemand tippt, nicht dazwischenfunken. Sobald der Fokus weg
    // ist, den gebundenen Wert zeigen - auch wenn er sich erst einen
    // Bildaufbau spaeter aendert (deshalb haengt der Effekt an beidem).
    //
    // Vorher stand nach "Fertig" der alte Wert im Feld, weil `wert` in dem
    // Moment noch der Stand vor dem Uebernehmen war, und beim naechsten
    // Fokuswechsel wurde genau dieser alte Wert erneut uebernommen: ein
    // getippter Drehwinkel 45 landete als 0 (S23 FE, 14.09.2026).
    LaunchedEffect(wert, amZug) { if (!amZug || !getippt) text = wert }

    Row(
        modifier
            .height(ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .padding(horizontal = ps.pt(8)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it; getippt = true },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { zustand ->
                    val hatte = amZug
                    amZug = zustand.isFocused
                    if (zustand.isFocused) getippt = false
                    // Nach dem Uebernehmen den gebundenen Wert zeigen,
                    // nicht die Eingabe: eine abgelehnte "0" stand sonst
                    // im Feld, waehrend das Modell bei 100 % blieb
                    // (RandfaelleUITest, 12.09.2026).
                    if (hatte && !zustand.isFocused && text != wert) uebernehmen(text)
                }
                // Hardware-Enter (Tablet mit Tastatur) hier abfangen - beide
                // Haelften: nach clearFocus() landete das Loslassen der Taste
                // auf dem ersten Knopf der Schiene, und der oeffnete den
                // Importer (Emulator, 15.09.2026). Drueben faengt onSubmit
                // die Taste ohne Nebenwirkung.
                .onPreviewKeyEvent { ereignis ->
                    if (ereignis.key != Key.Enter && ereignis.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                    if (ereignis.type == KeyEventType.KeyUp) { uebernehmen(text); fokus.clearFocus() }
                    true
                }
                .testTag(kennung),
            textStyle = TextStyle(color = PrusaColors.textPrimary, fontSize = ps.font(13)),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { uebernehmen(text); fokus.clearFocus() }),
            singleLine = true,
            cursorBrush = SolidColor(PrusaColors.orange),
        )
        if (vorzeichen) {
            Text(
                "±",
                color = PrusaColors.textPrimary,
                fontSize = ps.font(15),
                modifier = Modifier
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .clickable {
                        val t = text.trim()
                        text = if (t.startsWith("-")) t.removePrefix("-") else if (t.isEmpty()) "-" else "-$t"
                        if (!amZug) uebernehmen(text)
                    }
                    .padding(horizontal = ps.pt(6))
                    .testTag("$kennung.vorzeichen"),
            )
        }
        Text(einheit, color = PrusaColors.textMuted, fontSize = ps.font(11))
    }
}

/**
 * Ein `.sheet` des Originals: Dialog in voller Fensterbreite mit einer
 * Karte in Panelfarbe, in der Dichte der uebrigen App.
 */
@Composable
private fun Blatt(onDismiss: () -> Unit, inhalt: @Composable () -> Unit) {
    val ps = LocalPsScale.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ScaledOverlay {
            Box(
                Modifier.fillMaxSize().padding(ps.pt(16)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .widthIn(max = ps.pt(720))
                        .fillMaxWidth()
                        .heightIn(max = ps.pt(720))
                        .clip(RoundedCornerShape(ps.pt(12)))
                        .background(PrusaColors.panel),
                ) {
                    inhalt()
                }
            }
        }
    }
}
