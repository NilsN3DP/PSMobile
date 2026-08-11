package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import de.psmobile.shared.rules.PrinterGrouping

/**
 * Ersteinrichtung - Gegenstueck zum Konfigurationsassistenten des Desktops.
 *
 * Zweck ist nicht Kosmetik: Werden alle 37 Druckermodelle installiert,
 * dauert der Start 13,5 s und die Auswahllisten enthalten 5762 Filamente.
 * Mit einem einzelnen gewaehlten Drucker sind es 1,9 s und 189 Filamente.
 */
@Composable
fun SetupScreen(
    models: List<PsmCore.PrinterModel>,
    busy: Boolean,
    onConfirm: (List<String>) -> Unit,
    onLanguageChange: (String) -> Unit,
    /** Bisher installierte Modelle, Format "vendor:model:variant". */
    preselected: Set<String> = emptySet(),
    /**
     * Nur gesetzt, wenn der Assistent erneut geoeffnet wurde (z. B. aus
     * "Drucker verwalten"), nicht beim allerersten Start - dort gibt es
     * noch nichts, wohin man abbrechen koennte. Gegenstueck zu iOS'
     * SetupView.onClose.
     */
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Frueher stand hier
    //     val compact = screenWidthDp > screenHeightDp
    // also "compact" = Querformat. Auf einem kleinen Fenster im
    // Hochformat war das false, und der Assistent nahm die GROSSEN
    // Masse - von der Druckerliste blieben anderthalb Zeilen uebrig.
    //
    // Massgeblich ist nicht die Ausrichtung, sondern ob eine der Kanten
    // knapp ist. Die Entscheidung liegt jetzt in UiScale, damit nicht
    // jeder Bildschirm seine eigene trifft.
    val tight = UiScale.density(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    ) == UiDensity.TIGHT

    val pagePadding = if (tight) 12.dp else 24.dp
    val itemVerticalPadding = if (tight) 6.dp else 12.dp
    val nozzleHeight = if (tight) 38.dp else 48.dp
    val finishHeight = if (tight) 46.dp else 58.dp
    // Ueberschrift und Zeilen duerfen mitschrumpfen; die Zielflaechen
    // bleiben ueber die Hoehen oben trotzdem fingergerecht.
    val titleSize = if (tight) 18.sp else 22.sp
    val rowTitleSize = if (tight) 14.sp else 15.sp
    val sectionGap = if (tight) 6.dp else 14.dp
    // Mit der bisherigen Wahl starten. Sonst muesste man beim blossen
    // Ergaenzen einer Duesengroesse alles aus dem Gedaechtnis neu
    // zusammenklicken - Befund B9.
    var selected by remember(preselected) { mutableStateOf(preselected) }
    var showSla by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var langMenu by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(PsUi.language) }

    val shown = models.filter {
        (showSla || !it.isSla) && it.name.contains(query.trim(), ignoreCase = true)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(PrusaColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = if (tight) 1040.dp else 760.dp)
                .fillMaxSize()
                .padding(pagePadding),
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                onClose?.let { close ->
                    Text(
                        "‹  ${PsUi.tr("Back")}",
                        color = PrusaColors.Orange,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable(onClick = close)
                            .padding(end = 12.dp, top = 15.dp, bottom = 15.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        PsUi.tr("Configuration Assistant"),
                        color = PrusaColors.TextPrimary,
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // Auf engen Schirmen faellt der Untertitel weg. Er
                    // erklaert nichts, was die Liste darunter nicht selbst
                    // zeigt, kostet aber eine Zeile, die dann der Liste
                    // fehlt.
                    if (!tight) {
                        Text(
                            PsUi.tr("Select all printers, you want to use."),
                            color = PrusaColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Eng: SLA-Schalter und Zaehler wandern in die Titelzeile,
                // statt eine eigene zu belegen.
                if (tight) {
                    Checkbox(
                        checked = showSla,
                        onCheckedChange = { showSla = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrusaColors.Orange),
                    )
                    Text(PsUi.tr("SLA materials"), color = PrusaColors.TextMuted, fontSize = 12.sp)
                    Text(
                        "  ${selected.map { it.substringBeforeLast(':') }.distinct().size} / ${shown.size}  ",
                        color = PrusaColors.TextMuted, fontSize = 12.sp,
                    )
                }

                // Sprache: Englisch ist Standard, uebersetzt wird aus den
                // mitgelieferten PrusaSlicer-Katalogen.
                Box {
                    Row(
                        Modifier.height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrusaColors.PanelRaised)
                            .clickable { langMenu = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lang.uppercase(), color = PrusaColors.TextPrimary, fontSize = 13.sp)
                        Text("  ▾", color = PrusaColors.TextMuted)
                    }
                    DropdownMenu(langMenu, { langMenu = false },
                                 Modifier.background(PrusaColors.PanelRaised)) {
                        ScaledOverlay {
                        PsUi.availableLanguages.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code.uppercase(),
                                              color = PrusaColors.TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    langMenu = false
                                    lang = code
                                    PsUi.setLanguage(context, code)
                                    // Ohne das steht nach dem Neustart
                                    // wieder Englisch. Befund A4.
                                    onLanguageChange(code)
                                },
                            )
                        }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = sectionGap), color = PrusaColors.Divider)

            if (!tight) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)) {
                    Checkbox(
                        checked = showSla,
                        onCheckedChange = { showSla = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrusaColors.Orange),
                    )
                    Text(PsUi.tr("SLA materials"), color = PrusaColors.TextMuted, fontSize = 13.sp)
                    Box(Modifier.weight(1f))
                    Text(
                        "${selected.map { it.substringBeforeLast(':') }.distinct().size} / ${shown.size}",
                        color = PrusaColors.TextMuted, fontSize = 13.sp,
                    )
                }
            }

            if (tight) {
                // OutlinedTextField bringt sein Label und rund 56 dp
                // Mindesthoehe mit. Bei 400 dp Gesamthoehe ist das ein
                // Siebtel des Schirms fuer ein Suchfeld - hier deshalb
                // ein flaches Feld mit Platzhalter statt Label.
                Row(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrusaColors.PanelRaised)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(PsUi.tr("Search printer model"),
                                 color = PrusaColors.TextMuted, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = PrusaColors.TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(PrusaColors.Orange),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(PsUi.tr("Search printer model")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Nach Familien geordnet wie am Desktop, Altgeraete am
                // Ende. Waehrend einer Suche entfaellt die Gliederung: wer
                // tippt, will Treffer sehen und keine Zwischenueberschriften.
                // Die Regel liefert Positionen, keine Modelle - so
                // ueberlebt sie die Bruecke nach Swift, wo Kotlins
                // Typparameter verlorengehen. Hier zurueck zu den
                // eigenen Objekten.
                val groups: List<Pair<String, List<PsmCore.PrinterModel>>> =
                    if (query.isBlank()) {
                        PrinterGrouping.group(shown.map { it.family })
                            .map { g -> g.family to g.indices.map { shown[it] } }
                    } else {
                        listOf("" to shown)
                    }

                groups.forEach { (family, modelle) ->
                if (family.isNotBlank()) {
                    item(key = "h_$family") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                family.uppercase(),
                                color = if (PrinterGrouping.isLegacy(family))
                                            PrusaColors.TextMuted
                                        else PrusaColors.Orange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "  ${modelle.size}",
                                color = PrusaColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                items(modelle, key = { it.key }) { m ->
                    val on = selected.any { it.startsWith("${m.key}:") }
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) PrusaColors.Orange.copy(alpha = 0.16f)
                                        else PrusaColors.PanelRaised)
                            .border(1.dp,
                                    if (on) PrusaColors.Orange else PrusaColors.Divider,
                                    RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = itemVerticalPadding),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                // Drucker an- oder abwaehlen. Beim Anwaehlen
                                // wird die uebliche 0.4er Duese vorbelegt.
                                selected = if (on) selected.filterNot {
                                    it.startsWith("${m.key}:")
                                }.toSet() else selected + "${m.key}:${
                                    m.variants.firstOrNull { v -> v == "0.4" }
                                        ?: m.variants.firstOrNull().orEmpty()
                                }"
                            }) {
                            Checkbox(
                                checked = on,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = PrusaColors.Orange),
                            )
                            // Die Familie steht in der Gruppenueberschrift.
                            // Sie zusaetzlich in jede Zeile zu schreiben,
                            // wiederholt nur, was zwei Zeilen darueber
                            // schon steht. Beim Suchen entfaellt die
                            // Gliederung - dann gehoert sie wieder dazu.
                            val technik = buildString {
                                append(if (m.isSla) "SLA" else "FFF")
                                if (family.isBlank() && m.family.isNotBlank()) {
                                    append("  ·  ${m.family}")
                                }
                            }
                            if (tight) {
                                // Eine Zeile statt zwei: das halbiert die
                                // Zeilenhoehe und damit die Zahl der
                                // sichtbaren Drucker. Technik und Familie
                                // stehen gedimmt hinter dem Namen.
                                Row(
                                    Modifier.weight(1f).padding(start = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        m.name,
                                        color = PrusaColors.TextPrimary,
                                        fontSize = rowTitleSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Text(
                                        "   $technik",
                                        color = PrusaColors.TextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            } else {
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(m.name, color = PrusaColors.TextPrimary,
                                         fontSize = rowTitleSize)
                                    Text(technik, color = PrusaColors.TextMuted, fontSize = 12.sp)
                                }
                            }
                        }

                        // Duesengroessen erst zeigen, wenn der Drucker
                        // gewaehlt ist - sonst 37 x 10 Knoepfe auf einmal.
                        if (on && m.variants.isNotEmpty()) {
                            Text(
                                PsUi.tr("Nozzle diameter"),
                                color = PrusaColors.TextMuted, fontSize = 11.sp,
                                modifier = Modifier.padding(start = 40.dp, top = 6.dp),
                            )
                            Row(
                                Modifier.padding(start = 40.dp, top = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                m.variants.forEach { v ->
                                    val vkey = "${m.key}:$v"
                                    val vOn = vkey in selected
                                    Box(
                                        Modifier.height(nozzleHeight)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (vOn) PrusaColors.Orange
                                                        else PrusaColors.Panel)
                                            .clickable {
                                                selected = if (vOn) selected - vkey
                                                           else selected + vkey
                                            }
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(v, color = PrusaColors.TextPrimary, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            Button(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(finishHeight),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrusaColors.Orange,
                    contentColor = PrusaColors.TextPrimary,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = PrusaColors.TextPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(PsUi.tr("Finish"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
