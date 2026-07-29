package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors

/**
 * Vollstaendige Einstellungen - Seiten, Gruppen und Reihenfolge stammen
 * aus PrusaSlicers Tab.cpp, die Beschriftungen aus dessen Uebersetzungen,
 * Typ und Sichtbarkeitsstufe aus PrintConfig.
 *
 * Hier ist nichts handverlesen: Welche Einstellung wo und auf welcher
 * Stufe erscheint, ist uebernommene Information. Siehe E-12.
 *
 * Fuer Touch angepasst: Bedienelemente mindestens 44 dp hoch, Umschalter
 * statt Ankreuzfelder, Werteingabe mit grosszuegigem Feld. Die
 * dreispaltige Desktop-Tabelle entfaellt.
 */
@Composable
fun SettingsScreen(
    core: PsmCore,
    tab: String,                       // "print" | "filament" | "printer"
    mode: PsmCore.Mode,
    onModeChange: (PsmCore.Mode) -> Unit,
    configRevision: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // PrusaSlicer legt die Extruderseite zur Laufzeit einmal je Duese an -
    // "Extruder 1" bis "Extruder 5". Genauso hier: die Vorlage aus
    // tabs.json traegt {n} im Titel und wird hier vervielfaeltigt.
    val extruderCount = remember(configRevision) {
        runCatching { core.extruderCount() }.getOrDefault(1)
    }
    val pages = remember(tab, extruderCount) {
        PsUi.tabs[tab].orEmpty().flatMap { p ->
            if (p.perExtruder) (0 until extruderCount).map { RenderPage(p, it) }
            else listOf(RenderPage(p))
        }
    }
    var pageIndex by remember(tab) { mutableStateOf(0) }

    Row(modifier.fillMaxSize().background(PrusaColors.Background)) {

        // --- Seitenliste, wie der Baum links im Desktop-Dialog ---------
        Column(
            Modifier.width(260.dp).fillMaxHeight()
                .background(PrusaColors.Panel)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹  ${PsUi.tr("Back")}", color = PrusaColors.Orange, fontSize = 15.sp)
            }
            HorizontalDivider(color = PrusaColors.Divider)

            pages.forEachIndexed { i, page ->
                val active = i == pageIndex
                Row(
                    Modifier.fillMaxWidth().height(48.dp)
                        .background(if (active) PrusaColors.PanelRaised else PrusaColors.Panel)
                        .clickable { pageIndex = i }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = "${page.icon}.svg"
                    if (page.icon.isNotEmpty() && PsUi.iconExists(context, icon)) {
                        PsIcon(icon, Modifier.size(20.dp))
                    } else {
                        Box(Modifier.size(20.dp))
                    }
                    Text(
                        page.title,
                        color = if (active) PrusaColors.Orange else PrusaColors.TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }

        // --- Parameter der gewaehlten Seite ---------------------------
        Column(Modifier.weight(1f).fillMaxHeight()) {

            // Die Stufen entsprechen PrusaSlicers Einfach/Erweitert/Experte.
            Row(
                Modifier.fillMaxWidth().background(PrusaColors.Panel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(PsUi.tr("Mode"), color = PrusaColors.TextMuted, fontSize = 12.sp)
                listOf(
                    PsmCore.Mode.SIMPLE to "Simple",
                    PsmCore.Mode.ADVANCED to "Advanced",
                    PsmCore.Mode.EXPERT to "Expert",
                ).forEach { (m, label) ->
                    val active = m == mode
                    Box(
                        Modifier.height(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) PrusaColors.Orange else PrusaColors.PanelRaised)
                            .clickable { onModeChange(m) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(PsUi.tr(label), color = PrusaColors.TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            val page = pages.getOrNull(pageIndex)
            if (page == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(PsUi.tr("No presets loaded."), color = PrusaColors.TextMuted)
                }
                return@Column
            }

            // Metadaten einmal je Seite und Stufe holen. Vorher lief
            // configMeta() fuer jeden Parameter in der Komposition, also
            // bei jeder Neuzeichnung erneut - bei 40 sichtbaren Werten
            // 40 JNI-Aufrufe je Bild. Befund A2.
            val metaByGroup = remember(page.title, mode, configRevision) {
                page.groups.associate { g ->
                    g.title to g.options
                        .mapNotNull { opt -> core.configMeta(opt.key)?.let { opt to it } }
                        .filter { (_, meta) -> meta.mode.ordinal <= mode.ordinal }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                page.groups.forEach { group ->
                    val visible = metaByGroup[group.title].orEmpty()
                    if (visible.isEmpty()) return@forEach

                    item(key = "g_${page.title}_${group.title}") {
                        Text(
                            PsUi.tr(group.title).uppercase(),
                            color = PrusaColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(visible, key = { it.second.key }) { (opt, meta) ->
                        SettingRow(core, meta, configRevision,
                                   extruder = page.extruder, multiline = opt.code)
                    }
                }
            }
        }
    }
}

/**
 * Eine Einstellung.
 *
 * Aufbau bewusst zweizeilig statt nebeneinander: Beschriftung links,
 * Bedienelement rechtsbuendig auf fester Breite, Erklaerung darunter in
 * voller Zeilenbreite. Damit stehen alle Bedienelemente auf einer
 * senkrechten Linie, unabhaengig davon wie lang die Erklaerung ist -
 * vorher sprang die Spalte je nach Texthoehe.
 */
private val CONTROL_WIDTH = 220.dp

/**
 * Eine Seite, wie sie tatsaechlich gezeigt wird.
 *
 * Die Vorlage aus tabs.json kann eine Extruderseite sein; dann gibt es
 * sie einmal je Duese, und extruder sagt welche. Bei allen anderen
 * Seiten steht dort -1.
 */
private data class RenderPage(val page: PsUi.Page, val extruder: Int = -1) {
    val title: String
        get() = if (extruder >= 0) page.title.replace("{n}", (extruder + 1).toString())
                else PsUi.tr(page.title)
    val icon   get() = page.icon
    val groups get() = page.groups
}

@Composable
private fun SettingRow(
    core: PsmCore,
    meta: PsmCore.ConfigMeta,
    configRevision: Int,
    extruder: Int = -1,
    multiline: Boolean = false,
) {
    // Die Revision gehoert in den Schluessel: sonst zeigt die Zeile nach
    // einem Profilwechsel den alten Wert und schreibt ihn beim naechsten
    // Antippen in die neue Konfiguration zurueck. Befund A3.
    //
    // Auf einer Extruderseite ist der Wert ein Eintrag im Vektor, nicht
    // die ganze Reihe - sonst staende in "Retraction Length" bei fuenf
    // Duesen "0.8,0.8,0.8,0.8,0.8".
    var value by remember(meta.key, configRevision, extruder) {
        mutableStateOf(
            if (extruder >= 0) core.getAt(meta.key, extruder).orEmpty()
            else core[meta.key].orEmpty()
        )
    }
    var expanded by remember(meta.key) { mutableStateOf(false) }

    fun push(v: String) {
        value = v
        runCatching {
            if (extruder >= 0) core.setAt(meta.key, extruder, v) else core[meta.key] = v
        }
    }

    // Start- und End-G-code sind mehrzeilig. Ein einzeiliges Feld macht
    // sie unbedienbar, deshalb hier ein eigener Zweig.
    if (multiline) {
        GcodeField(meta, value, ::push)
        return
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                PsUi.tr(meta.label).ifBlank { meta.key },
                color = PrusaColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )

            Box(Modifier.width(CONTROL_WIDTH), contentAlignment = Alignment.CenterEnd) {
                when (meta.type) {
                    PsmCore.ConfigType.BOOL -> Switch(
                        checked = value == "1" || value.equals("true", true),
                        onCheckedChange = { push(if (it) "1" else "0") },
                        colors = SwitchDefaults.colors(checkedTrackColor = PrusaColors.Orange),
                    )
                    PsmCore.ConfigType.ENUM -> EnumField(core, meta, value) { push(it) }
                    else -> ValueField(value, meta.unit) { push(it) }
                }
            }
        }

        if (meta.tooltip.isNotBlank()) {
            // Lange Erklaerungen zeigen erst zwei Zeilen; antippen klappt
            // sie auf. Manche Tooltips im Original sind fuenf Zeilen lang.
            Text(
                PsUi.tr(meta.tooltip),
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, end = CONTROL_WIDTH + 16.dp)
                    .clickable { expanded = !expanded },
            )
        }

        HorizontalDivider(
            Modifier.padding(top = 8.dp),
            color = PrusaColors.Divider.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun EnumField(
    core: PsmCore,
    meta: PsmCore.ConfigMeta,
    value: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(meta.key) { core.configEnumValues(meta.key, meta.enumCount) }
    val label = options.firstOrNull { it.first == value }?.second ?: value

    Box {
        Row(
            Modifier.fillMaxWidth().height(44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                PsUi.tr(label), color = PrusaColors.TextPrimary, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = PrusaColors.TextMuted)
        }
        DropdownMenu(expanded, { expanded = false },
                     Modifier.background(PrusaColors.PanelRaised)) {
            options.forEach { (raw, text) ->
                DropdownMenuItem(
                    text = { Text(PsUi.tr(text), color = PrusaColors.TextPrimary, fontSize = 13.sp) },
                    onClick = { expanded = false; onSelect(raw) },
                )
            }
        }
    }
}

@Composable
private fun ValueField(value: String, unit: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PrusaColors.PanelRaised)
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = PrusaColors.TextPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(PrusaColors.Orange),
            modifier = Modifier.weight(1f),
        )
        if (unit.isNotBlank()) {
            Text(PsUi.tr(unit), color = PrusaColors.TextMuted, fontSize = 12.sp)
        }
    }
}

/**
 * Mehrzeiliges Feld fuer eigenen G-Code.
 *
 * Am Desktop ist das ein Textfeld ueber mehrere Zeilen mit fester
 * Schrittweite; option.opt.is_code markiert es. Hier dasselbe, nur
 * ueber die volle Breite statt in der Spalte rechts - eine
 * Startsequenz hat schnell zwanzig Zeilen und passt in kein 220 dp
 * breites Feld.
 *
 * Geschrieben wird erst beim Verlassen des Feldes: bei jedem Tastendruck
 * durch die Konfiguration zu gehen, kostet bei langen Sequenzen spuerbar.
 */
@Composable
private fun GcodeField(
    meta: PsmCore.ConfigMeta,
    value: String,
    onCommit: (String) -> Unit,
) {
    var text by remember(meta.key, value) { mutableStateOf(value) }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            PsUi.tr(meta.label).ifBlank { meta.key },
            color = PrusaColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        if (meta.tooltip.isNotBlank()) {
            Text(
                PsUi.tr(meta.tooltip),
                color = PrusaColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
        }

        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(4.dp))
                .onFocusChanged { if (!it.isFocused && text != value) onCommit(text) }
                .padding(10.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                // Nichtproportional: in G-Code stehen Spalten untereinander.
                textStyle = TextStyle(
                    color = PrusaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(PrusaColors.Orange),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                // Ohne Hinweis tippt man auf dem Tablet blind - die
                // Platzhalter stehen nirgends sonst.
                "Platzhalter in geschweiften Klammern, z. B. {first_layer_temperature[0]}",
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            if (text != value) {
                Text(
                    "Übernehmen",
                    color = PrusaColors.Orange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onCommit(text) },
                )
            }
        }
    }
}
