package de.psmobile.ui

import androidx.compose.runtime.Composable
import de.psmobile.shared.rules.AppSettings

/**
 * Masse eines Objekts in der eingestellten Einheit - Gegenstueck zu
 * `Masse.text` in `ios/PSMobile/UI/Masse.swift`. Bis zum 15.09.2026 galt
 * "Laengen in Zoll anzeigen" nur fuer die Einstellungsseiten; Objektliste
 * und Inspektor blieben bei mm (Emulator, Bug-Bounty).
 */
@Composable
fun masseText(x: Float, y: Float, z: Float): String {
    val zoll = rememberAppSetting(AppSettings.KEY_UNITS_IMPERIAL, false)
    return if (zoll) String.format(java.util.Locale.US, "%.2f × %.2f × %.2f in", x / 25.4f, y / 25.4f, z / 25.4f)
    else String.format(java.util.Locale.US, "%.1f × %.1f × %.1f mm", x, y, z)
}
