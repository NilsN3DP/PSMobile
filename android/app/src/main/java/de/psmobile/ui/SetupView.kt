package de.psmobile.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.psmobile.core.PsmCore
import de.psmobile.shared.rules.PrinterGrouping
import de.psmobile.slicing.ResourceInstaller
import de.psmobile.ui.theme.PrusaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/// Ersteinrichtung - Gegenstueck zum Konfigurationsassistenten des
/// Desktops.
///
/// Der Zweck ist nicht Kosmetik: Werden alle Druckermodelle installiert,
/// dauert der Start 13,5 s und die Auswahllisten enthalten 5762
/// Filamente. Mit einem einzelnen gewaehlten Drucker sind es 1,9 s und
/// 189 Filamente. Die Gruppierung nach Familien kommt aus dem
/// gemeinsamen Modul.
@Composable
fun SetupView(
    models: List<PsmCore.PrinterModel>,
    busy: Boolean,
    /** Bisher installierte Modelle, Format "vendor:model:variant". */
    preselected: Set<String>,
    onConfirm: (List<String>) -> Unit,
    onLanguageChange: (String) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val ps = LocalPsScale.current
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var expandedVendors by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedFamilies by remember { mutableStateOf<Set<String>>(emptySet()) }

    /// Ist es eng? Dann faellt Beiwerk weg, statt alles zu schrumpfen.
    val tight = ps.factor <= 0.8f

    val shown = models.filter { m ->
        // Hart aus, nicht nur standardmaessig versteckt: PSMobile
        // schneidet nur FFF, und das SLA-Buendel ist zudem kaputt.
        !m.isSla &&
            (query.trim().isEmpty() || m.name.contains(query, ignoreCase = true))
    }

    /// Zwei Ebenen: Hersteller, darunter Familien - beide Einteilungen
    /// aus dem gemeinsamen Modul.
    val hersteller: List<Hersteller> = run {
        val liste = shown
        PrinterGrouping.groupByVendor(liste.map { it.key }).map { v ->
            val modelle = v.indices.map { liste[it] }
            val familien = PrinterGrouping.group(modelle.map { it.family }).map { g ->
                Familie(
                    name = g.family,
                    isLegacy = g.isLegacy,
                    modelle = g.indices.map { modelle[it] },
                )
            }
            Hersteller(name = v.family, familien = familien)
        }
    }

    // Beim Erscheinen und noch einmal, wenn die gespeicherte Wahl
    // spaeter hereinkommt - siehe die gleichlautende Stelle in
    // SetupView.swift. Nur solange nichts gewaehlt ist: eine getroffene
    // Wahl darf eine nachziehende Voreinstellung nicht ueberschreiben.
    //
    // Hier ist es Vorsorge, drueben war es ein Reparaturversuch: dort
    // hatte die Ansicht die alte Druckerwahl uebernommen, bevor
    // -psm-reset-setup sie leerte. Seit dem 11.09.2026 wirkt der
    // Schalter drueben in PSMobileApp.init(), vor dem ersten Bild -
    // hier tut er es seit jeher in SlicerService.ensureCore.
    LaunchedEffect(preselected) { if (selected.isEmpty()) selected = preselected }

    SchwebenderDialog(kennung = "dialog.einrichtung", maximaleBreite = ps.pt(760)) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(ps.pt(if (tight) 12 else 24)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(if (tight) 6 else 14)),
            horizontalAlignment = Alignment.Start,
        ) {
            Kopfzeile(tight, onClose)
            Suchfeld(query, onQuery = { query = it })
            Liste(
                modifier = Modifier.weight(1f),
                alle = hersteller,
                query = query,
                tight = tight,
                expandedVendors = expandedVendors,
                expandedFamilies = expandedFamilies,
                selected = selected,
                onVendor = { name ->
                    expandedVendors = if (name in expandedVendors) expandedVendors - name else expandedVendors + name
                },
                onFamilie = { name ->
                    expandedFamilies = if (name in expandedFamilies) expandedFamilies - name else expandedFamilies + name
                },
                onVariante = { key ->
                    selected = if (key in selected) selected - key else selected + key
                },
            )
            Abschluss(tight, busy, selected, onConfirm)
        }
    }
}

/// Eine Familie mit ihren Modellen.
private data class Familie(
    val name: String,
    val isLegacy: Boolean,
    val modelle: List<PsmCore.PrinterModel>,
)

/// Ein Hersteller mit seinen Familien.
private data class Hersteller(
    val name: String,
    val familien: List<Familie>,
) {
    val anzahl: Int get() = familien.sumOf { it.modelle.size }
}

// MARK: - Teile

@Composable
private fun Kopfzeile(tight: Boolean, onClose: (() -> Unit)?) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onClose != null) {
            Text(
                "‹  " + st("Back", "Zurück"),
                Modifier
                    .clickable(onClick = onClose)
                    .testTag("einrichtung.schliessen"),
                fontSize = ps.font(15), color = PrusaColors.orange,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(2)), horizontalAlignment = Alignment.Start) {
            Text(
                st("Configuration Assistant", "Ersteinrichtung"),
                fontSize = ps.font(if (tight) 18 else 22), fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textPrimary,
            )
            // Auf engen Schirmen faellt der Untertitel weg.
            if (!tight) {
                Text(
                    st("Select all printers you want to use.", "Wähle alle Drucker, die du nutzen willst."),
                    fontSize = ps.font(13), color = PrusaColors.textMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Suchfeld(query: String, onQuery: (String) -> Unit) {
    val ps = LocalPsScale.current
    // Der Platzhalter wird selbst gezeichnet, in einer Farbe, die auf
    // dem dunklen Feld auch zu sehen ist.
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(
                st("Search printer model", "Druckermodell suchen"),
                Modifier.padding(horizontal = ps.pt(12)),
                fontSize = ps.font(15), color = PrusaColors.textMuted,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(ps.pt(12))
                // Bis zum 11.09.2026 namenlos: die Tests griffen es als
                // "das erste Textfeld". Das haelt genau so lange, bis
                // ein zweites dazukommt.
                .testTag("einrichtung.suche"),
            textStyle = TextStyle(fontSize = ps.font(15), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
            singleLine = true,
        )
    }
}

@Composable
private fun Liste(
    modifier: Modifier,
    alle: List<Hersteller>,
    query: String,
    tight: Boolean,
    expandedVendors: Set<String>,
    expandedFamilies: Set<String>,
    selected: Set<String>,
    onVendor: (String) -> Unit,
    onFamilie: (String) -> Unit,
    onVariante: (String) -> Unit,
) {
    // Die Liste traegt einen Namen, damit ein Test zu einem Hersteller
    // blaettern kann. Ohne das sieht er nur, was gerade gezeichnet ist -
    // und PrusaResearch steht alphabetisch weit unten.
    LazyColumn(modifier.fillMaxWidth().testTag("einrichtung.liste")) {
        alle.forEach { marke ->
            // Bei nur einem Hersteller waere die oberste Ebene eine
            // Zeile, die man immer erst aufklappen muss, ohne dass sie
            // etwas unterscheidet.
            val offen = alle.size == 1 ||
                expandedVendors.contains(marke.name) ||
                query.isNotEmpty()
            if (alle.size > 1) {
                item(key = "hersteller:${marke.name}") {
                    HerstellerKopf(marke, offen, onVendor)
                }
            }
            if (offen) {
                marke.familien.forEach { gruppe ->
                    item(key = "familie:${marke.name}/${gruppe.name}") {
                        FamilienKopf(gruppe, expandedFamilies.contains(gruppe.name), onFamilie)
                    }
                    if (expandedFamilies.contains(gruppe.name) || query.isNotEmpty()) {
                        items(gruppe.modelle, key = { "modell:${it.key}" }) { modell ->
                            ModellZeile(modell, tight, selected, onVariante)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HerstellerKopf(marke: Hersteller, offen: Boolean, onVendor: (String) -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onVendor(marke.name) }
            .testTag("hersteller.${marke.name}")
            .padding(top = ps.pt(8))
            .heightIn(min = ps.touch(52)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(marke.name, fontSize = ps.font(17), fontWeight = FontWeight.Bold, color = PrusaColors.textPrimary)
        Text("${marke.anzahl}", fontSize = ps.font(13), color = PrusaColors.textMuted)
        Spacer(Modifier.weight(1f))
        SfSymbol(
            if (offen) "chevron.down" else "chevron.right",
            Modifier.size(ps.pt(14)),
            tint = PrusaColors.textMuted,
        )
    }
}

@Composable
private fun FamilienKopf(gruppe: Familie, offen: Boolean, onFamilie: (String) -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onFamilie(gruppe.name) }
            // Kennungen, damit der UI-Test die Zeilen findet.
            .testTag("familie.${gruppe.name}")
            .heightIn(min = ps.touch(52)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            gruppe.name.uppercase(),
            Modifier.padding(start = ps.pt(12)),
            fontSize = ps.font(14), fontWeight = FontWeight.SemiBold,
            color = if (gruppe.isLegacy) PrusaColors.textMuted else PrusaColors.orange,
        )
        Text("${gruppe.modelle.size}", fontSize = ps.font(13), color = PrusaColors.textMuted)
        Spacer(Modifier.weight(1f))
        SfSymbol(
            if (offen) "chevron.down" else "chevron.right",
            Modifier.size(ps.pt(14)),
            tint = PrusaColors.textMuted,
        )
    }
}

@Composable
private fun ModellZeile(
    modell: PsmCore.PrinterModel,
    tight: Boolean,
    selected: Set<String>,
    onVariante: (String) -> Unit,
) {
    val ps = LocalPsScale.current
    // Jede Duesengroesse ist eine eigene Wahl - PrusaSlicer fuehrt sie
    // als getrennte Profile.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ps.pt(if (tight) 10 else 14)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.Top,
    ) {
        DruckerBild(modell, tight)

        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(4)), horizontalAlignment = Alignment.Start) {
            Text(modell.name, fontSize = ps.font(if (tight) 16 else 17), color = PrusaColors.textPrimary)

            Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(6))) {
                modell.variants.forEach { variante ->
                    val key = "${modell.key}:$variante"
                    val gewaehlt = selected.contains(key)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(ps.pt(4)))
                            .background(if (gewaehlt) PrusaColors.orange else PrusaColors.panelRaised)
                            .clickable { onVariante(key) }
                            .testTag("variante.$key")
                            .widthIn(min = ps.touch(64))
                            .heightIn(min = ps.touch(if (tight) 48 else 56))
                            .padding(horizontal = ps.pt(16)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            variante,
                            fontSize = ps.font(15),
                            color = if (gewaehlt) Color.White else PrusaColors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/// Vorschaubild je Druckermodell, wie in der Desktop-App - liegt als
/// loses PNG in den entpackten Ressourcen (profiles/<vendor>/
/// <model_id>_thumbnail.png). Fehlt die Datei, bleibt ein
/// Platzhalter-Symbol statt einer leeren Luecke.
@Composable
private fun DruckerBild(modell: PsmCore.PrinterModel, tight: Boolean) {
    val ps = LocalPsScale.current
    val context = LocalContext.current
    val seite = ps.pt(if (tight) 40 else 48)
    val bild by produceState<ImageBitmap?>(initialValue = null, key1 = modell.key) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                thumbnailPfad(context, modell)?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(seite)
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised),
        contentAlignment = Alignment.Center,
    ) {
        val b = bild
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            SfSymbol("printer", Modifier.size(ps.pt(18)), tint = PrusaColors.textMuted)
        }
    }
}

/** Einmal nachgesehen, dann gemerkt - ein Aufruf je Zeile waere Laerm im Protokoll. */
@Volatile private var ressourcenWurzel: File? = null

private fun thumbnailPfad(context: Context, modell: PsmCore.PrinterModel): String? {
    // modell.key hat die Form "vendor:model_id" - der Dateiname der
    // Vorschau folgt bei den meisten Herstellern genau der model_id.
    // Voron ist die Ausnahme ("Voron_v2_250_thumbnail.png" statt
    // "V2_250_thumbnail.png"). Deshalb erst den genauen Namen
    // versuchen, sonst im Vendor-Ordner nach einer "*_thumbnail.png"
    // suchen, die die model_id enthaelt.
    val teile = modell.key.split(":", limit = 2)
    if (teile.size != 2) return null
    val resDir = ressourcenWurzel
        ?: runCatching { ResourceInstaller.ensureInstalled(context) }.getOrNull()?.also { ressourcenWurzel = it }
        ?: return null
    val vendorDir = File(File(resDir, "profiles"), teile[0])
    val genau = File(vendorDir, "${teile[1]}_thumbnail.png")
    if (genau.isFile) return genau.path
    val modelId = teile[1].lowercase()
    return vendorDir.listFiles()?.firstOrNull {
        val n = it.name.lowercase()
        n.endsWith("_thumbnail.png") && n.contains(modelId)
    }?.path
}

@Composable
private fun Abschluss(
    tight: Boolean,
    busy: Boolean,
    selected: Set<String>,
    onConfirm: (List<String>) -> Unit,
) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(ps.touch(if (tight) 46 else 58))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(if (selected.isEmpty()) PrusaColors.panelRaised else PrusaColors.orange)
            .clickable(enabled = !(selected.isEmpty() || busy)) { onConfirm(selected.toList()) }
            .testTag("fertig"),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(ps.pt(22)), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(
                st("Finish", "Fertig"),
                fontSize = ps.font(16), fontWeight = FontWeight.SemiBold,
                color = if (selected.isEmpty()) PrusaColors.textMuted else Color.White,
            )
        }
    }
}
