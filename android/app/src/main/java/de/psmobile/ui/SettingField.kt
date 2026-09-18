package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.shared.rules.AppSettings
import de.psmobile.shared.rules.TabsCatalog
import de.psmobile.ui.theme.ScaledOverlay
import java.util.Locale

/**
 * Ein einzelner Parameter - Gegenstueck zu
 * `ios/PSMobile/Screens/SettingField.swift`.
 *
 * Was gezeichnet wird, entscheidet der Typ aus dem Kern - Schalter,
 * Zahl, Auswahl oder Text. PrusaSlicer kennt zu jedem Parameter Typ,
 * Grenzen, Einheit und Auswahlwerte, und genau die kommen ueber das
 * ABI herueber.
 */
@Composable
fun SettingField(model: SlicerModel, option: TabsCatalog.Option, kompakt: Boolean) {
    val ps = LocalPsScale.current
    val zollEinheiten = rememberAppSetting(AppSettings.KEY_UNITS_IMPERIAL, false)
    var wert by remember(option.key) { mutableStateOf("") }
    /** Was laden() zuletzt angezeigt hat - unveraendert wird nichts geschrieben. */
    var angezeigt by remember(option.key) { mutableStateOf("") }
    var meta by remember(option.key) { mutableStateOf<PsmCore.ConfigMeta?>(null) }
    var auswahl by remember(option.key) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var gesperrt by remember(option.key) { mutableStateOf(PsmCore.Enablement(true, "")) }

    /// Nur echte Laengen (Einheit "mm") werden umgerechnet - eine
    /// Prozentzahl oder ein Grad-Wert hat mit Zoll nichts zu tun.
    val istLaenge = meta?.unit == "mm"

    fun laden() {
        val core = model.core ?: return
        val m = runCatching { core.configMeta(option.key) }.getOrNull()
        meta = m
        wert = runCatching { core[option.key] }.getOrNull() ?: ""
        // Der Kern liefert immer mm - fuers Anzeigen in Zoll umrechnen,
        // ohne den gespeicherten Wert anzufassen.
        if (zollEinheiten && m?.unit == "mm") {
            wert.toDoubleOrNull()?.let { mm -> wert = formatiert(mm / 25.4) }
        }
        angezeigt = wert
        gesperrt = runCatching { core.enablement(option.key) }.getOrDefault(PsmCore.Enablement(true, ""))
        if (m != null && m.type == PsmCore.ConfigType.ENUM && m.enumCount > 0) {
            auswahl = runCatching { core.configEnumValues(option.key, m.enumCount) }.getOrDefault(emptyList())
        }
    }

    fun schreiben(roh: String) {
        // Dezimalkomma wie Punkt: die deutsche Tastatur tippt "0,3", und der
        // Kern las daraus 0 - die erste Schicht stand danach auf 0 mm (S23 FE,
        // 16.09.2026). Nur bei Zahlenfeldern; ein Text darf sein Komma behalten.
        val zahlig = meta?.type == PsmCore.ConfigType.FLOAT || meta?.type == PsmCore.ConfigType.PERCENT ||
            meta?.type == PsmCore.ConfigType.INT
        val neu = if (zahlig) roh.replace(',', '.') else roh
        wert = neu
        // Unveraendert: nichts schreiben. In Zoll ist die Anzeige gerundet
        // (0.2 mm → 0.0079 in → 0.2007 mm), und bis zum 16.09.2026 galt das
        // Profil nach Antippen + Verlassen des Felds als geaendert (Emulator).
        if (neu == angezeigt) { laden(); return }
        // Umgekehrt beim Schreiben: was auf dem Bildschirm Zoll ist,
        // geht als mm an den Kern - der kennt keine Zoll-Einstellung.
        val zoll = neu.toDoubleOrNull()
        if (zollEinheiten && istLaenge && zoll != null) {
            model.setConfig(option.key, formatiert(zoll * 25.4))
        } else {
            model.setConfig(option.key, neu)
        }
        // Danach aus dem Kern zuruecklesen, nicht dem Bildschirm glauben:
        // ein abgelehnter Wert (Schichthoehe 0, Buchstaben) springt so
        // sichtbar auf den alten zurueck, und ein geaenderter kann andere
        // Felder sperren oder freigeben.
        laden()
    }

    LaunchedEffect(option.key) { laden() }

    Column(
        // Jedes Feld traegt seinen Parameternamen - siehe die
        // gleichlautende Stelle in SettingField.swift.
        Modifier
            .alpha(if (gesperrt.enabled) 1f else 0.55f)
            .testTag("feld.${option.key}"),
        verticalArrangement = Arrangement.spacedBy(ps.pt(4)),
        horizontalAlignment = Alignment.Start,
    ) {
        val m = meta
        if (!kompakt && m != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(6)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PsUiCatalog.tr(m.label),
                    fontSize = ps.font(13),
                    color = if (gesperrt.enabled) PrusaColors.textPrimary else PrusaColors.textMuted,
                )
                if (m.unit.isNotEmpty()) {
                    // Einheiten laufen durch den Katalog: "layers" heisst auf
                    // Deutsch "Schichten", "mm or %" "mm oder %" (16.09.2026).
                    Text(
                        if (istLaenge && zollEinheiten) "in" else PsUiCatalog.tr(m.unit),
                        fontSize = ps.font(11),
                        color = PrusaColors.textMuted,
                    )
                }
            }
        }

        // feld = gewoehnlichesFeld
        when (meta?.type) {
            PsmCore.ConfigType.BOOL -> {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (kompakt && m != null) {
                        Text(PsUiCatalog.tr(m.label), fontSize = ps.font(12), color = PrusaColors.textPrimary)
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = wert == "1",
                        onCheckedChange = { schreiben(if (it) "1" else "0") },
                        enabled = gesperrt.enabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrusaColors.orange,
                        ),
                    )
                }
            }

            PsmCore.ConfigType.ENUM -> {
                var offen by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ps.pt(4)))
                            .background(PrusaColors.panelRaised)
                            .clickable(enabled = gesperrt.enabled) { offen = true }
                            .height(ps.touch(40))
                            .padding(horizontal = ps.pt(10)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            beschriftung(auswahl, wert),
                            fontSize = ps.font(13),
                            color = PrusaColors.textPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        SfSymbol("chevron.down", Modifier.size(ps.pt(10)), tint = PrusaColors.textMuted)
                    }
                    DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                        ScaledOverlay {
                            auswahl.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(PsUiCatalog.tr(label), fontSize = ps.font(13)) },
                                    onClick = {
                                        offen = false
                                        schreiben(value)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // Zahl und Text gehen denselben Weg: PrusaSlicer haelt intern
                // ohnehin alles als Zeichenkette, und der Kern prueft beim
                // Setzen. Mehrzeilig nur, wo es als G-code markiert ist.
                if (option.code) {
                    var hatteFokus by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = wert,
                        onValueChange = { wert = it },
                        enabled = gesperrt.enabled,
                        textStyle = TextStyle(
                            fontSize = ps.font(12),
                            fontFamily = FontFamily.Monospace,
                            color = PrusaColors.textPrimary,
                        ),
                        cursorBrush = SolidColor(PrusaColors.orange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ps.pt(4)))
                            .background(PrusaColors.panelRaised)
                            .height(ps.pt(120))
                            .padding(ps.pt(8))
                            .onFocusChanged { zustand ->
                                // `onSubmit` eines TextEditor: uebernommen wird,
                                // wenn das Feld verlassen wird.
                                if (hatteFokus && !zustand.isFocused) schreiben(wert)
                                hatteFokus = zustand.isFocused
                            },
                    )
                } else {
                    BasicTextField(
                        value = wert,
                        onValueChange = { wert = it },
                        enabled = gesperrt.enabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { schreiben(wert) }),
                        textStyle = TextStyle(fontSize = ps.font(13), color = PrusaColors.textPrimary),
                        cursorBrush = SolidColor(PrusaColors.orange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ps.pt(4)))
                            .background(PrusaColors.panelRaised)
                            .height(ps.touch(40))
                            .padding(horizontal = ps.pt(10)),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
                        },
                    )
                }
            }
        }

        // Ist ein Feld ausgegraut, gehoert der Grund dazu - als Beschriftung
        // des sperrenden Parameters, nicht als Schluessel: bis zum
        // 16.09.2026 stand "perimeters" unter "Duenne Waende erkennen".
        if (!gesperrt.enabled && gesperrt.blockedBy.isNotEmpty()) {
            val sperrer = model.core?.let { c -> runCatching { c.configMeta(gesperrt.blockedBy)?.label }.getOrNull() }
                ?.takeIf { it.isNotBlank() }?.let { PsUiCatalog.tr(it) } ?: gesperrt.blockedBy
            Text(
                st("Depends on: ", "Abhängig von: ") + sperrer,
                fontSize = ps.font(11), color = PrusaColors.textMuted,
            )
        }
    }
}

private fun beschriftung(auswahl: List<Pair<String, String>>, wert: String): String =
    auswahl.firstOrNull { it.first == wert }?.let { PsUiCatalog.tr(it.second) } ?: wert

/** Bis zu vier Nachkommastellen, ueberfluessige Nullen weg. */
private fun formatiert(wert: Double): String {
    var t = String.format(Locale.US, "%.4f", wert)
    while (t.endsWith("0")) t = t.dropLast(1)
    if (t.endsWith(".")) t = t.dropLast(1)
    return t
}
