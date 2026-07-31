package de.psmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.psmobile.ui.theme.PrusaColors

@Composable
fun WorkflowStartScreen(
    onSimple: () -> Unit,
    onAdvanced: () -> Unit,
    onAdvancedWizard: () -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    val context = LocalContext.current
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Text("PSMobile", color = PrusaColors.Orange, style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = { languageMenu = true },
                    modifier = Modifier.align(Alignment.CenterEnd),
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
            Text(
                "Wie möchtest du drucken?",
                color = PrusaColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Wähle einen Arbeitsbereich. Beide verwenden dieselbe Sitzung und deine vorhandenen Profile.",
                color = PrusaColors.TextMuted,
            )
            WorkflowCard(
                title = "Simple Mode",
                detail = "Der schnelle, touchfreundliche Weg vom Modell zur Druckvorschau.",
                action = "Simple starten",
                emphasized = true,
                onClick = onSimple,
            )
            WorkflowCard(
                title = "Advanced Mode",
                detail = "Die vollständige Slicer-Arbeitsfläche für Projekte, Profile und Details.",
                action = "Advanced öffnen",
                emphasized = false,
                onClick = onAdvanced,
            )
            OutlinedButton(
                onClick = onAdvancedWizard,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Advanced-Assistent starten")
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    title: String,
    detail: String,
    action: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) PrusaColors.PanelRaised else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = PrusaColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = PrusaColors.TextMuted)
            if (emphasized) {
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(action)
                }
            } else {
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(action)
                }
            }
        }
    }
}
