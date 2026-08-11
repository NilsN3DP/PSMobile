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
                Modifier.fillMaxWidth().height(56.dp),
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
                    val items = AppSettings.group(group)
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
                // Selbsttest- und Protokoll-Export-Eintraege (siehe iOS'
                // "Diagnose"-Abschnitt) fehlen hier noch bewusst - beide
                // Ziele existieren auf Android noch nicht (Selbsttest.kt,
                // PsmLog-Portierung), ein Knopf ohne Ziel waere nur ein
                // neuer toter Knopf. Nachtragen sobald Feature 2
                // (android-parity-plan.md) so weit ist.
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
            .clip(RoundedCornerShape(10.dp))
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
            .clip(RoundedCornerShape(10.dp))
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) PrusaColors.Orange else PrusaColors.Panel)
                        .border(
                            1.dp,
                            if (on) PrusaColors.Orange else PrusaColors.Divider,
                            RoundedCornerShape(8.dp),
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
