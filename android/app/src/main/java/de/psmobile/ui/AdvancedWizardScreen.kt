package de.psmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService

@Composable
fun AdvancedWizardScreen(service: SlicerService, onClose: () -> Unit) {
    val presets by service.presets.collectAsState()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(PsUi.appText("Advanced wizard", "Advanced-Assistent"), style = MaterialTheme.typography.headlineMedium)
        Text(PsUi.appText("Load printers, filament and Print Settings, or choose them directly.", "Drucker, Filament und Print Settings nachladen oder direkt auswählen."))
        OutlinedButton(onClick = service::reopenSetup, modifier = Modifier.fillMaxWidth()) {
            Text(PsUi.appText("Add or remove printer models", "Druckermodelle hinzufügen oder entfernen"))
        }
        WizardChoice(PsUi.appText("Printer", "Drucker"), presets.selectedPrinter, presets.printers) {
            service.selectPreset(PsmCore.PresetType.PRINTER, it)
        }
        WizardChoice(PsUi.appText("Filament", "Filament"), presets.selectedFilament, presets.filaments) {
            service.selectPreset(PsmCore.PresetType.FILAMENT, it)
        }
        WizardChoice("Print Settings", presets.selectedPrint, presets.prints) {
            service.selectPreset(PsmCore.PresetType.PRINT, it)
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(PsUi.appText("Open workspace", "Arbeitsfläche öffnen")) }
    }
}

@Composable
private fun WizardChoice(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    val matches = options.filter { it.contains(query.trim(), ignoreCase = true) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(PsUi.appText("Search", "Suchen")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            matches.take(24).forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) })
            }
            if (matches.size > 24) Text(PsUi.appText("${matches.size - 24} more matches – refine search.", "${matches.size - 24} weitere Treffer – Suche verfeinern."))
        }
    }
}
