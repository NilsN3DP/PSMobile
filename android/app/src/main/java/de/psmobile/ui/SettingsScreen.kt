package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import de.psmobile.shared.ui.Corners
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.AlertDialog
import de.psmobile.ui.theme.psTouch
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay

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
    onSettingChanged: () -> Unit = {},
    onTabChange: (String) -> Unit = {},
    // Das geltende Profil und was daran offen ist. Vorgaben, damit
    // Vorschauen und Tests den Bildschirm weiterhin ohne Dienst bauen
    // koennen - dann bleibt der Kopf einfach leer.
    presetNames: List<String> = emptyList(),
    selectedPreset: String = "",
    changes: List<SlicerService.Profilaenderung> = emptyList(),
    onSelectPreset: (String) -> Unit = {},
    onDiscardChanges: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val compactNavigation = SettingsLayout.usesCompactNavigation(
        LocalConfiguration.current.screenWidthDp,
    )

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

    // Gemerkte Einstellungen liegen ueber alle Reiter in einem Satz; die
    // Seite zeigt nur die des aktuellen Reiters. FAVORITES_PAGE ist ein
    // eigener Index vor der ersten echten Seite.
    val prefs = remember(context) {
        context.getSharedPreferences("psmobile", android.content.Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet().orEmpty())
    }
    fun toggleFavorite(key: String) {
        favorites = FavoriteSettings.toggle(favorites, key)
        prefs.edit().putStringSet("favorites", favorites).apply()
    }
    val tabKeyOrder = remember(tab) { FavoriteSettings.keysOf(PsUi.tabs[tab].orEmpty()) }
    val favoriteKeys = remember(favorites, tabKeyOrder) {
        FavoriteSettings.orderedFor(favorites, tabKeyOrder)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(PrusaColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {

    // --- Reiter wie oben im Desktop-Fenster ---------------------------
    //
    // Vorher kam man hier nur ueber ein kleines Zahnrad neben dem
    // jeweiligen Auswahlfeld hinein und musste zum Bett zurueck, um den
    // Bereich zu wechseln. Am Desktop sind es Reiter; hier auch.
    Row(
        Modifier.fillMaxWidth().background(PrusaColors.Panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    // Zurueck und die Reiter scrollen fuer sich. Was rechts steht -
    // Profilname und Zaehler - darf nicht mitwandern: es ist die
    // Auskunft darueber, woran man gerade arbeitet.
    Row(
        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹  ${PsUi.tr("Back")}",
            color = PrusaColors.Orange,
            fontSize = 15.sp,
            modifier = Modifier
                .clickable(onClick = onClose)
                .height(psTouch(52))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        )
        listOf(
            "print"    to "Print settings",
            "filament" to "Filament settings",
            "printer"  to "Printer settings",
        ).forEach { (key, label) ->
            val active = key == tab
            Box(
                Modifier
                    .height(psTouch(52))
                    .clickable(enabled = !active) { onTabChange(key) }
                    .background(if (active) PrusaColors.Background else PrusaColors.Panel)
                    .padding(horizontal = if (compactNavigation) 10.dp else 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    PsUi.tr(label),
                    color = if (active) PrusaColors.Orange else PrusaColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
    ProfilKopf(
        profil = selectedPreset,
        profile = presetNames,
        aenderungen = changes,
        titel = PsUi.tr(
            when (tab) {
                "print" -> "Print settings"
                "filament" -> "Filament settings"
                else -> "Printer settings"
            },
        ),
        onSelect = onSelectPreset,
        onVerwerfen = onDiscardChanges,
    )
    }
    HorizontalDivider(color = PrusaColors.Divider)

    if (compactNavigation) {
        SettingsPageRail(
            pages = pages,
            pageIndex = pageIndex,
            onSelect = { pageIndex = it },
        )
        HorizontalDivider(color = PrusaColors.Divider)
    }

    Row(Modifier.fillMaxWidth().weight(1f)) {

        // --- Seitenliste, wie der Baum links im Desktop-Dialog ---------
        if (!compactNavigation) Column(
            Modifier.width(280.dp).fillMaxHeight()
                .background(PrusaColors.Panel)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsPageEntry(
                title = PsUi.appText("Favourites", "Favoriten") +
                    if (favoriteKeys.isEmpty()) "" else " · ${favoriteKeys.size}",
                active = pageIndex == FAVORITES_PAGE,
                leading = {
                    Text("★", color = PrusaColors.Orange, fontSize = 16.sp)
                },
                onClick = { pageIndex = FAVORITES_PAGE },
            )
            HorizontalDivider(color = PrusaColors.Divider)

            pages.forEachIndexed { i, page ->
                val active = i == pageIndex
                Row(
                    Modifier.fillMaxWidth().height(psTouch(52))
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
            val specialActive = pageIndex == pages.size
            Row(
                Modifier.fillMaxWidth().height(psTouch(56))
                    .background(
                        if (specialActive) PrusaColors.PanelRaised else PrusaColors.Panel
                    )
                    .clickable { pageIndex = pages.size }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(Corners.FIELD.dp))
                        .background(PrusaColors.Orange),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋯", color = PrusaColors.TextPrimary, fontSize = 14.sp)
                }
                Text(
                    PsUi.appText("Special dialogs", "Spezialdialoge"),
                    color = if (specialActive) PrusaColors.Orange else PrusaColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (specialActive) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        // --- Parameter der gewaehlten Seite ---------------------------
        Column(
            if (compactNavigation) Modifier.fillMaxSize()
            else Modifier.weight(1f).fillMaxHeight(),
        ) {

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
                        Modifier.height(psTouch(48))
                            .clip(RoundedCornerShape(Corners.FIELD.dp))
                            .background(if (active) PrusaColors.Orange else PrusaColors.PanelRaised)
                            .clickable { onModeChange(m) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(PsUi.tr(label), color = PrusaColors.TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            if (pageIndex == FAVORITES_PAGE) {
                FavoritesPanel(
                    core = core,
                    tab = tab,
                    keys = favoriteKeys,
                    mode = mode,
                    configRevision = configRevision,
                    onToggleFavorite = ::toggleFavorite,
                    onChanged = onSettingChanged,
                )
                return@Column
            }

            if (pageIndex == pages.size) {
                SpecialSettingsPanel(
                    core = core,
                    tab = tab,
                    extruderCount = extruderCount,
                    configRevision = configRevision,
                    onChanged = onSettingChanged,
                )
                return@Column
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
            val metaByGroup: Map<String, List<SettingEntry>> =
                remember(page.title, mode, configRevision) {
                    page.groups.associate { g ->
                        g.title to g.options
                            .mapNotNull { opt -> core.configMeta(opt.key)?.let { opt to it } }
                            .filter { (_, meta) -> meta.mode.ordinal <= mode.ordinal }
                            .map { (opt, meta) ->
                                // Ob der Wert gerade ueberhaupt wirkt. Einmal je
                                // Seite und Konfigurationsstand, nicht je Bild -
                                // dieselbe Ueberlegung wie bei den Metadaten.
                                SettingEntry(
                                    opt, meta,
                                    runCatching { core.enablement(opt.key) }.getOrNull(),
                                )
                            }
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
                    items(visible, key = { it.meta.key }) { entry ->
                        // Auch die Filamentwerte sind Vektoren mit einem
                        // Eintrag je Extruder, weil full_config() ueber alle
                        // Duesen zusammensetzt. Der Filamenttab bearbeitet
                        // aber genau ein Filament - ohne Index staende in
                        // "Diameter" beim XL-5T "1.75,1.75,1.75,1.75,1.75".
                        val index = when {
                            page.extruder >= 0 -> page.extruder
                            tab == "filament"  -> 0
                            else               -> -1
                        }
                        SettingRow(core, entry.meta, configRevision,
                                   extruder = index, multiline = entry.option.code,
                                   enabled = entry.enablement?.enabled ?: true,
                                   blockedBy = entry.enablement?.blockedBy.orEmpty(),
                                   isFavorite = entry.meta.key in favorites,
                                   onToggleFavorite = { toggleFavorite(entry.meta.key) },
                                   onChanged = onSettingChanged)
                    }
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
/**
 * Ein Parameter, wie er auf der Seite steht: was tabs.json sagt, was
 * PrintConfig dazu weiss, und ob er im aktuellen Zustand ueberhaupt
 * wirkt. Alles drei einmal je Seite ermittelt, nicht je Bild.
 */
private data class SettingEntry(
    val option: PsUi.Option,
    val meta: PsmCore.ConfigMeta,
    val enablement: PsmCore.Enablement?,
)

private data class RenderPage(val page: PsUi.Page, val extruder: Int = -1) {
    val title: String
        get() = if (extruder >= 0) page.title.replace("{n}", (extruder + 1).toString())
                else PsUi.tr(page.title)
    val icon   get() = page.icon
    val groups get() = page.groups
}

/** Phone navigation mirrors the desktop page tree without reserving 280 dp. */
@Composable
private fun SettingsPageRail(
    pages: List<RenderPage>,
    pageIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .background(PrusaColors.Panel).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val favouritesActive = pageIndex == FAVORITES_PAGE
        Box(
            Modifier.height(psTouch(48)).clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(if (favouritesActive) PrusaColors.Orange else PrusaColors.PanelRaised)
                .clickable { onSelect(FAVORITES_PAGE) }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "★",
                color = if (favouritesActive) PrusaColors.Background else PrusaColors.Orange,
                fontSize = 15.sp,
            )
        }
        pages.forEachIndexed { index, page ->
            val active = index == pageIndex
            Box(
                Modifier.height(psTouch(48)).clip(RoundedCornerShape(Corners.FIELD.dp))
                    .background(if (active) PrusaColors.Orange else PrusaColors.PanelRaised)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    page.title,
                    color = if (active) PrusaColors.Background else PrusaColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
        val special = pageIndex == pages.size
        Box(
            Modifier.height(psTouch(48)).clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(if (special) PrusaColors.Orange else PrusaColors.PanelRaised)
                .clickable { onSelect(pages.size) }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                PsUi.appText("Special", "Spezial"),
                color = if (special) PrusaColors.Background else PrusaColors.TextPrimary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
internal fun SettingRow(
    core: PsmCore,
    meta: PsmCore.ConfigMeta,
    configRevision: Int,
    extruder: Int = -1,
    multiline: Boolean = false,
    enabled: Boolean = true,
    blockedBy: String = "",
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onChanged: () -> Unit = {},
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
        }.onSuccess { onChanged() }
    }

    // Start- und End-G-code sind mehrzeilig. Ein einzeiliges Feld macht
    // sie unbedienbar, deshalb hier ein eigener Zweig.
    if (multiline) {
        GcodeField(meta, value, ::push)
        return
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            // Ausgegraut statt versteckt: der Wert steht weiter da, man
            // sieht nur, dass er gerade nichts bewirkt.
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Der Stern merkt die Zeile fuer die Favoritenseite. Er steht
            // vor der Beschriftung, weil er zur Zeile gehoert und nicht
            // zum Wert - rechts waere er ein weiteres Bedienelement in
            // einer Reihe, in der schon eines steht.
            onToggleFavorite?.let { toggle ->
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(Corners.FIELD.dp))
                        .clickable(onClick = toggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isFavorite) "★" else "☆",
                        color = if (isFavorite) PrusaColors.Orange else PrusaColors.TextMuted,
                        fontSize = 16.sp,
                    )
                }
            }
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

        // Warum gesperrt? Der Desktop graut aus und schweigt dazu. Auf
        // dem Tablet, wo kein Handbuch danebenliegt, ist das die
        // wichtigere Haelfte der Auskunft.
        if (!enabled && blockedBy.isNotBlank()) {
            val label = remember(blockedBy) {
                runCatching { core.configMeta(blockedBy)?.label }
                    .getOrNull().orEmpty().ifBlank { blockedBy }
            }
            Text(
                PsUi.tr("Ohne Wirkung, solange") + " “" + PsUi.tr(label) +
                    "” " + PsUi.tr("das nicht zulaesst"),
                color = PrusaColors.Orange,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
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
            Modifier.fillMaxWidth().height(psTouch(48))
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
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
            ScaledOverlay {
            options.forEach { (raw, text) ->
                DropdownMenuItem(
                    text = { Text(PsUi.tr(text), color = PrusaColors.TextPrimary, fontSize = 13.sp) },
                    onClick = { expanded = false; onSelect(raw) },
                )
            }
            }
        }
    }
}

@Composable
private fun ValueField(value: String, unit: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(psTouch(48))
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .background(PrusaColors.PanelRaised)
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
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
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
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
                    PsUi.appText("Apply", "Übernehmen"),
                    color = PrusaColors.Orange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onCommit(text) },
                )
            }
        }
    }
}

/** Eigener Seitenindex vor der ersten echten Seite. */
private const val FAVORITES_PAGE = -1

/** Ein Eintrag in der Seitenliste links - Symbol, Titel, Auswahlzustand. */
@Composable
private fun SettingsPageEntry(
    title: String,
    active: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(psTouch(56))
            .background(if (active) PrusaColors.PanelRaised else PrusaColors.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
        Text(
            title,
            color = if (active) PrusaColors.Orange else PrusaColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * Die selbst zusammengestellte Seite.
 *
 * Sie zeigt dieselben Zeilen wie die Fachseiten, nur eben die gemerkten.
 * Die Stufe Simple/Advanced/Expert gilt auch hier: ein Expertenwert
 * verschwindet, wenn man auf Simple zurueckschaltet. Er bleibt aber
 * gemerkt, sonst muesste man ihn nach jedem Stufenwechsel neu suchen -
 * deshalb steht die Zahl im Seitentitel und der Hinweis unten.
 */
@Composable
private fun FavoritesPanel(
    core: PsmCore,
    tab: String,
    keys: List<String>,
    mode: PsmCore.Mode,
    configRevision: Int,
    onToggleFavorite: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val entries = remember(keys, mode, configRevision) {
        keys.mapNotNull { key -> core.configMeta(key)?.let { key to it } }
            .filter { (_, meta) -> meta.mode.ordinal <= mode.ordinal }
    }
    val hiddenByMode = keys.size - entries.size

    if (keys.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("☆", color = PrusaColors.TextMuted, fontSize = 40.sp)
                Text(
                    PsUi.appText("No favourites yet", "Noch keine Favoriten"),
                    color = PrusaColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    PsUi.appText(
                        "Tap the star next to a setting to keep it here.",
                        "Den Stern neben einer Einstellung antippen, dann steht sie hier.",
                    ),
                    color = PrusaColors.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries, key = { it.first }) { (key, meta) ->
            val enablement = remember(key, configRevision) {
                runCatching { core.enablement(key) }.getOrNull()
            }
            SettingRow(
                core, meta, configRevision,
                extruder = if (tab == "filament") 0 else -1,
                enabled = enablement?.enabled ?: true,
                blockedBy = enablement?.blockedBy.orEmpty(),
                isFavorite = true,
                onToggleFavorite = { onToggleFavorite(key) },
                onChanged = onChanged,
            )
        }
        if (hiddenByMode > 0) {
            item(key = "hidden_by_mode") {
                Text(
                    PsUi.appText(
                        "$hiddenByMode more are hidden by the current mode.",
                        PsUi.appText("$hiddenByMode more are hidden at this level.", "$hiddenByMode weitere sind in dieser Stufe ausgeblendet."),
                    ),
                    color = PrusaColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * Rechts im Kopf: welches Profil gerade bearbeitet wird - und was daran
 * gegenueber dem gespeicherten Stand offen ist.
 *
 * Beides stand vorher nur im Advanced-Seitenband. Wer aus dem Simple
 * Mode hierher kam, sah drei Reiter und sonst nichts: nicht, welches
 * Profil er gerade veraendert, und keinen Weg zu einem anderen, ohne
 * den Bildschirm zu verlassen.
 *
 * Der Zaehler erscheint erst, wenn es etwas zurueckzusetzen gibt. Ein
 * Knopf ohne Wirkung ist eine Frage, die man sich stellt und auf die
 * man keine Antwort bekommt.
 */
@Composable
private fun ProfilKopf(
    profil: String,
    profile: List<String>,
    aenderungen: List<SlicerService.Profilaenderung>,
    titel: String,
    onSelect: (String) -> Unit,
    onVerwerfen: () -> Unit,
) {
    var sucheOffen by remember { mutableStateOf(false) }
    var rueckfrage by remember { mutableStateOf(false) }

    if (profil.isNotBlank()) {
        Row(
            Modifier
                .clickable(enabled = profile.isNotEmpty()) { sucheOffen = true }
                .heightIn(min = psTouch(44))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                profil,
                color = PrusaColors.TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            // PrusaSlicers Symbolsatz hat keine Lupe; der Trichter ist
            // dort das Zeichen fuers Filtern. Eigene Symbole kommen
            // nicht dazu - siehe E-12.
            PsIcon(
                "funnel.svg",
                Modifier.padding(start = 6.dp).size(14.dp),
                tint = ColorFilter.tint(PrusaColors.TextMuted),
            )
        }
    }

    if (aenderungen.isNotEmpty()) {
        Row(
            Modifier
                .padding(end = 10.dp)
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(PrusaColors.PanelRaised)
                .clickable { rueckfrage = true }
                .heightIn(min = psTouch(40))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PsIcon(
                "undo_toolbar.svg",
                Modifier.size(14.dp),
                tint = ColorFilter.tint(PrusaColors.Orange),
            )
            Text(
                "${aenderungen.size}",
                color = PrusaColors.Orange,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    if (sucheOffen) {
        ProfilsucheDialog(
            titel = titel,
            profile = profile,
            gewaehlt = profil,
            onClose = { sucheOffen = false },
            onSelect = {
                sucheOffen = false
                onSelect(it)
            },
        )
    }

    if (rueckfrage) {
        AlertDialog(
            onDismissRequest = { rueckfrage = false },
            containerColor = PrusaColors.Panel,
            titleContentColor = PrusaColors.TextPrimary,
            textContentColor = PrusaColors.TextPrimary,
            title = {
                Text(
                    PsUi.appText(
                        "Reset profile to its saved values?",
                        "Profil auf seine gespeicherten Werte zurücksetzen?",
                    ),
                    color = PrusaColors.TextPrimary,
                )
            },
            // Die Liste, nicht nur die Zahl: "sieben Werte" beantwortet
            // nicht die Frage, ob die eine Aenderung dabei ist, an der
            // einem liegt.
            text = {
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    aenderungen.forEach { a ->
                        Column {
                            Text(a.bezeichnung, color = PrusaColors.TextPrimary, fontSize = 13.sp)
                            Text(
                                a.vorher + "  →  " + a.jetzt,
                                color = PrusaColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    PsUi.appText(
                        "Reset ${aenderungen.size} values",
                        "${aenderungen.size} Werte zurücksetzen",
                    ),
                    color = PrusaColors.Danger,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            rueckfrage = false
                            onVerwerfen()
                        }
                        .heightIn(min = psTouch(44))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            },
            dismissButton = {
                Text(
                    PsUi.appText("Cancel", "Abbrechen"),
                    color = PrusaColors.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { rueckfrage = false }
                        .heightIn(min = psTouch(44))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            },
        )
    }
}

/**
 * Profilsuche - dieselbe Filterregel wie im Advanced-Seitenband.
 *
 * [filterPresetOptions] steht dort schon und wird hier nur genutzt:
 * zwei Suchen, die verschieden filtern, waeren zwei Antworten auf
 * dieselbe Frage.
 */
@Composable
private fun ProfilsucheDialog(
    titel: String,
    profile: List<String>,
    gewaehlt: String,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var suche by remember { mutableStateOf("") }
    val treffer = filterPresetOptions(profile, suche)

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = PrusaColors.Panel,
        titleContentColor = PrusaColors.TextPrimary,
        textContentColor = PrusaColors.TextPrimary,
        title = { Text(titel, color = PrusaColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Corners.FIELD.dp))
                        .background(PrusaColors.PanelRaised)
                        .heightIn(min = psTouch(44))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (suche.isEmpty()) {
                            Text(
                                PsUi.appText("Search", "Suchen"),
                                color = PrusaColors.TextMuted,
                                fontSize = 13.sp,
                            )
                        }
                        BasicTextField(
                            value = suche,
                            onValueChange = { suche = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = PrusaColors.TextPrimary,
                                fontSize = 13.sp,
                            ),
                            cursorBrush = SolidColor(PrusaColors.Orange),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                ) {
                    if (treffer.isEmpty()) {
                        Text(
                            PsUi.appText("No matching profiles", "Keine passenden Profile"),
                            color = PrusaColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                    treffer.forEach { name ->
                        val aktiv = name == gewaehlt
                        Text(
                            name,
                            color = if (aktiv) PrusaColors.Orange else PrusaColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(name) }
                                .heightIn(min = psTouch(44))
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Text(
                PsUi.appText("Close", "Schließen"),
                color = PrusaColors.TextMuted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .heightIn(min = psTouch(44))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
    )
}
