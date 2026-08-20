package de.psmobile.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.shared.ui.Corners
import de.psmobile.ui.theme.psTouch
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.shared.rules.AppSettings
import de.psmobile.shared.rules.SimpleModeState

private fun t(english: String, german: String) = SimpleModeState.text(english, german)

/**
 * Einstellungen der App - erreichbar vom Startbildschirm.
 *
 * Bewusst getrennt von den Druckeinstellungen: dort geht es um das
 * Werkstueck, hier um das Programm. Wer die Modellvorschau abschalten
 * will, sucht sie nicht zwischen Schichthoehe und Fuelldichte.
 *
 * Jeder Schalter nennt darunter den Grund, warum es ihn gibt. Eine
 * Einstellung, deren Wirkung man erraten muss, wird entweder nie
 * angefasst oder einmal falsch.
 */
/**
 * Schalter, die die gemeinsame Liste zwar kennt, die auf Android aber
 * (noch) niemand liest - siehe android-parity-plan.md.
 *
 * KEY_PORTRAIT_BOTTOM_BAR wertet nur
 * ios/Screens/AdvancedWorkspaceView.swift aus. Ihn trotzdem anzuzeigen
 * waere schlimmer als ihn wegzulassen: ein Schalter, der nichts tut,
 * kostet den Nutzer mehr Zeit als ein fehlender.
 *
 * KEY_UNITS_IMPERIAL stand hier ebenfalls - genau mit dem Befund, dass
 * sich "Laengen in Zoll anzeigen" umlegen liess und in den
 * Druckeinstellungen unveraendert 0.2 mm stand. Seit AP-17 f rechnet
 * SettingsScreen um, also darf der Schalter wieder erscheinen.
 *
 * KEY_MULTI_BED_RENDER stand hier ebenfalls, bis SlicerScreen ihn
 * auswertet.
 */
private val nurIOS = setOf(
    AppSettings.KEY_PORTRAIT_BOTTOM_BAR,
)

@Composable
fun AppSettingsScreen(
    prefs: SharedPreferences,
    language: String,
    onLanguageChange: (String) -> Unit,
    onToggleChanged: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Der gemerkte Zustand haelt die Schalter waehrend der Sitzung; die
    // Wahrheit steht in den Preferences.
    val values = remember {
        mutableStateMapOf<String, Boolean>().apply {
            AppSettings.toggles.forEach { put(it.key, prefs.getBoolean(it.key, it.standard)) }
        }
    }
    var startMode by remember {
        mutableStateOf(prefs.getString(AppSettings.KEY_START_MODE, AppSettings.START_ASK)
            ?: AppSettings.START_ASK)
    }
    // svc.uiLanguage (der Ursprung von `language`) ist eine reine
    // SharedPreferences-Property, kein Compose State - ein Tap auf
    // "Deutsch" rief onLanguageChange zwar auf, aber ohne eigenen
    // State loeste das keine Neuzeichnung aus, und ohne
    // PsUi.setLanguage lud sich der Sprachkatalog erst beim naechsten
    // App-Start neu. Gegenstueck zum Sprachwaehler in
    // WorkflowStartScreen.kt, der beides schon richtig macht.
    val context = LocalContext.current
    var currentLanguage by remember { mutableStateOf(language) }
    var zeigeSelbsttest by remember { mutableStateOf(false) }

    if (zeigeSelbsttest) {
        SelbsttestScreen(onClose = { zeigeSelbsttest = false }, modifier = modifier)
        return
    }

    Box(
        modifier.fillMaxSize().background(PrusaColors.Background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(psTouch(56)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹  " + t("Back", "Zurück"),
                    color = PrusaColors.Orange,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onClose).padding(end = 16.dp),
                )
                Text(
                    t("App settings", "App-Einstellungen"),
                    color = PrusaColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            HorizontalDivider(color = PrusaColors.Divider)

            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                AppSettings.groupsInOrder.forEach { group ->
                    val items = AppSettings.group(group).filterNot { it.key in nurIOS }
                    val isAppearance = group == AppSettings.Group.APPEARANCE
                    if (items.isEmpty() && !isAppearance) return@forEach

                    SectionHeader(AppSettings.groupTitle(group))

                    // Sprache und Startmodus sind keine Ja/Nein-Fragen und
                    // stehen deshalb als Auswahl in der Darstellung.
                    if (isAppearance) {
                        ChoiceRow(
                            title = t("Language", "Sprache"),
                            why = t(
                                "Labels come from PrusaSlicer's own catalog.",
                                "Die Beschriftungen stammen aus PrusaSlicers eigenem Katalog.",
                            ),
                            options = listOf("en", "de"),
                            selected = currentLanguage,
                            label = { if (it == "de") "Deutsch" else "English" },
                            onSelect = {
                                currentLanguage = it
                                PsUi.setLanguage(context, it)
                                onLanguageChange(it)
                            },
                        )
                        ChoiceRow(
                            title = t("On start", "Beim Start"),
                            why = t(
                                "Skip the mode question if you always use the same one.",
                                "Die Modusfrage entfällt, wenn man ohnehin immer denselben nimmt.",
                            ),
                            options = AppSettings.startModes,
                            selected = startMode,
                            label = AppSettings::startModeLabel,
                            onSelect = {
                                startMode = it
                                prefs.edit().putString(AppSettings.KEY_START_MODE, it).apply()
                            },
                        )
                    }

                    items.forEach { toggle ->
                        ToggleRow(
                            title = t(toggle.title.english, toggle.title.german),
                            why = t(toggle.why.english, toggle.why.german),
                            checked = values[toggle.key] ?: toggle.standard,
                            onChange = {
                                values[toggle.key] = it
                                prefs.edit().putBoolean(toggle.key, it).apply()
                                onToggleChanged(toggle.key, it)
                            },
                        )
                    }
                }
                // Diagnose - Gegenstueck zum gleichnamigen Abschnitt in
                // AppSettingsView.swift. Kein Schalter, sondern Werkzeug,
                // deshalb ganz unten und nicht zwischen den Vorlieben.
                SectionHeader(t("Diagnostics", "Diagnose"))
                ActionRow(
                    title = t("Self-test", "Selbsttest"),
                    why = t(
                        "Runs loading, slicing, saving, painting and a load test on this " +
                            "device, and writes a report.",
                        "Prüft Laden, Schneiden, Sichern, Bemalen und eine Volllast auf " +
                            "diesem Gerät und schreibt einen Bericht.",
                    ),
                    onClick = { zeigeSelbsttest = true },
                )
                ActionRow(
                    title = t("Share log", "Protokoll teilen"),
                    why = t(
                        "The last messages from the core and the app - device and version, " +
                            "no account data and no network addresses.",
                        "Die letzten Meldungen von Kern und App - Gerät und Version, " +
                            "keine Kontodaten und keine Netzwerkadressen.",
                    ),
                    onClick = {
                        runCatching {
                            val ziel = de.psmobile.core.LogExport.uri(context)
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_STREAM, ziel)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    t("Share log", "Protokoll teilen"),
                                )
                            )
                        }
                    },
                )
                Text(
                    "PSMobile ${de.psmobile.BuildConfig.VERSION_NAME} (${de.psmobile.BuildConfig.VERSION_CODE})",
                    color = PrusaColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = PrusaColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    why: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Corners.CARD.dp))
            .background(PrusaColors.PanelRaised)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = PrusaColors.TextPrimary, fontSize = 14.sp)
            Text(why, color = PrusaColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = PrusaColors.Orange),
        )
    }
    Box(Modifier.height(6.dp))
}

/**
 * Ein Eintrag, der etwas tut, statt etwas umzuschalten.
 *
 * Gegenstueck zu den Knoepfen im iOS-Diagnosebereich: Titel, eine Zeile
 * Begruendung, ein Winkel nach rechts. Bewusst dieselbe Hoehe wie die
 * Schalterzeilen daneben - ein Bereich, der aussieht wie ein anderer,
 * verwirrt mehr als er ordnet.
 */
@Composable
private fun ActionRow(
    title: String,
    why: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(PrusaColors.PanelRaised, RoundedCornerShape(Corners.CARD.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .heightIn(min = psTouch(56)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = PrusaColors.TextPrimary, fontSize = 14.sp)
            Text(why, color = PrusaColors.TextMuted, fontSize = 11.sp)
        }
        Text("›", color = PrusaColors.Orange, fontSize = 18.sp)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    why: String,
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Corners.CARD.dp))
            .background(PrusaColors.PanelRaised)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, color = PrusaColors.TextPrimary, fontSize = 14.sp)
        Text(why, color = PrusaColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val on = option == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Corners.FIELD.dp))
                        .background(if (on) PrusaColors.Orange else PrusaColors.Panel)
                        .border(
                            1.dp,
                            if (on) PrusaColors.Orange else PrusaColors.Divider,
                            RoundedCornerShape(Corners.FIELD.dp),
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        label(option),
                        color = if (on) PrusaColors.Background else PrusaColors.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    Box(Modifier.height(6.dp))
}
