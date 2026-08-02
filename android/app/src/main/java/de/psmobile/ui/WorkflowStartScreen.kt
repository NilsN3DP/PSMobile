package de.psmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.psmobile.ui.theme.PrusaColors

@Composable
fun WorkflowStartScreen(
    onSimple: () -> Unit,
    onAdvanced: () -> Unit,
    onAdvancedWizard: () -> Unit,
    onAppSettings: () -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    val context = LocalContext.current
    // Auf niedrigen Fenstern - Querformat auf einem Telefon, geteilter
    // Bildschirm - passte bisher nur die erste Karte auf den Schirm; die
    // zweite Wahlmoeglichkeit war unsichtbar. Deshalb dieselbe
    // Dichteentscheidung wie ueberall sonst.
    val configuration = LocalConfiguration.current
    val tight = UiScale.density(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    ) == UiDensity.TIGHT
    var languageMenu by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(PsUi.language) }
    Box(
        Modifier.fillMaxSize().background(PrusaColors.Background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                // Die Activity zeichnet bewusst edge-to-edge. Der Einstieg
                // darf trotzdem nie unter Uhr, Kameraausschnitt oder
                // Benachrichtigungen beginnen.
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = if (tight) 12.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (tight) 8.dp else 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(if (tight) 26.dp else 32.dp)
                        .background(PrusaColors.Orange, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("S", color = PrusaColors.Background, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("PSMobile", color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text("3D PRINT WORKSPACE", color = PrusaColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { languageMenu = true },
                    modifier = Modifier,
                ) { Text(language.uppercase() + " ▾") }
                DropdownMenu(
                    expanded = languageMenu,
                    onDismissRequest = { languageMenu = false },
                ) {
                    PsUi.availableLanguages.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code.uppercase()) },
                            onClick = {
                                language = code
                                languageMenu = false
                                PsUi.setLanguage(context, code)
                                onLanguageChange(code)
                            },
                        )
                    }
                }
            }
            Surface(
                Modifier.fillMaxWidth()
                    .border(1.dp, PrusaColors.Divider, RoundedCornerShape(4.dp)),
                color = PrusaColors.Panel,
                shape = RoundedCornerShape(4.dp),
            ) {
                Column(
                    Modifier.padding(if (tight) 12.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(if (tight) 8.dp else 12.dp),
                ) {
                    Text(SimpleModeState.text("CHOOSE MODE", "MODUS WÄHLEN"), color = PrusaColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    Text(
                        SimpleModeState.text(
                            "Both modes use your configured printers and profiles.",
                            "Beide Modi verwenden deine eingerichteten Drucker und Profile.",
                        ),
                        color = PrusaColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    WorkflowModeRow(
                        index = "01",
                        title = "Simple Mode",
                        detail = SimpleModeState.text(
                            "Get to print quickly with a few clear decisions.",
                            "Schnell zum Druck mit wenigen, klaren Entscheidungen.",
                        ),
                        action = SimpleModeState.text("Start", "Starten"),
                        emphasized = true,
                        onClick = onSimple,
                    )
                    WorkflowModeRow(
                        index = "02",
                        title = "Advanced Mode",
                        detail = SimpleModeState.text(
                            "Complete slicer workspace for projects and details.",
                            "Vollständige Slicer-Arbeitsfläche für Projekte und Details.",
                        ),
                        action = SimpleModeState.text("Open", "Öffnen"),
                        emphasized = false,
                        onClick = onAdvanced,
                    )
                    TextButton(
                        onClick = onAdvancedWizard,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Text(
                            SimpleModeState.text("Set up profiles or printers", "Profile oder Drucker einrichten") + "  ›",
                            color = PrusaColors.TextMuted,
                        )
                    }
                    // Einstellungen der App, nicht des Drucks. Vom
                    // Startbildschirm aus, weil man sie vor der Arbeit
                    // setzt und danach selten wieder anfasst.
                    TextButton(
                        onClick = onAppSettings,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Text(
                            SimpleModeState.text("App settings", "App-Einstellungen") + "  ›",
                            color = PrusaColors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowModeRow(
    index: String,
    title: String,
    detail: String,
    action: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().heightIn(min = 82.dp)
        .background(if (emphasized) PrusaColors.PanelRaised else PrusaColors.Background, RoundedCornerShape(3.dp))
        .border(1.dp, if (emphasized) PrusaColors.Orange else PrusaColors.Divider, RoundedCornerShape(3.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(index, color = if (emphasized) PrusaColors.Orange else PrusaColors.TextMuted, style = MaterialTheme.typography.labelLarge)
    Column(Modifier.padding(start = 14.dp).weight(1f)) {
        Text(title, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = PrusaColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
    Text(action + "  ›", color = if (emphasized) PrusaColors.Orange else PrusaColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
}
