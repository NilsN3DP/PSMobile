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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pages = PsUi.tabs[tab].orEmpty()
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
                        PsUi.tr(page.title),
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

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                page.groups.forEach { group ->
                    // Metadaten einmal je Gruppe holen und nach Stufe filtern.
                    val visible = group.options.mapNotNull { core.configMeta(it) }
                        .filter { it.mode.ordinal <= mode.ordinal }
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
                    items(visible, key = { it.key }) { meta ->
                        SettingRow(core, meta)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(core: PsmCore, meta: PsmCore.ConfigMeta) {
    var value by remember(meta.key) { mutableStateOf(core[meta.key].orEmpty()) }

    fun push(v: String) {
        value = v
        runCatching { core[meta.key] = v }
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                PsUi.tr(meta.label).ifBlank { meta.key },
                color = PrusaColors.TextPrimary,
                fontSize = 14.sp,
            )
            if (meta.tooltip.isNotBlank()) {
                Text(
                    PsUi.tr(meta.tooltip),
                    color = PrusaColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

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
            Modifier.width(200.dp).height(44.dp)
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
        Modifier.width(200.dp).height(44.dp)
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
