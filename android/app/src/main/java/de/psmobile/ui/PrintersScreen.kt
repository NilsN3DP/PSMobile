package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.net.BackupStore
import de.psmobile.net.PrinterStore
import de.psmobile.net.PrusaLink
import de.psmobile.ui.theme.PrusaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Drucker verwalten.
 *
 * Das sind die physischen Geraete im Netz, nicht die Druckerprofile des
 * Slicers. Beide werden ueber `presetName` verknuepft, damit die Option
 * "nur eingerichtete Drucker zeigen" die Profilliste filtern kann.
 */
@Composable
fun PrintersScreen(
    presetNames: List<String>,
    onClose: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onReopenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var printers by remember { mutableStateOf(PrinterStore.all(context)) }
    var onlyLinked by remember { mutableStateOf(PrinterStore.onlyLinked(context)) }
    var editing by remember { mutableStateOf<PrusaLink.Printer?>(null) }
    var status by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var backupName by remember { mutableStateOf(BackupStore.folderName(context)) }

    LaunchedEffect(Unit) { backupName = BackupStore.folderName(context) }

    Box(modifier.fillMaxSize().background(PrusaColors.Background), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxSize().padding(24.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹  ${PsUi.tr("Back")}",
                     color = PrusaColors.Orange, fontSize = 15.sp,
                     modifier = Modifier.clickable(onClick = onClose))
                Box(Modifier.weight(1f))
                OutlinedButton(onClick = {
                    editing = PrusaLink.Printer(UUID.randomUUID().toString(), "", "")
                }) { Text(PsUi.tr("Add printer")) }
            }

            Text("PrusaLink", color = PrusaColors.TextPrimary,
                 fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                 modifier = Modifier.padding(top = 12.dp))
            Text(
                "Geräte im Netzwerk. Benutzername und Passwort stehen auf dem Drucker unter Einstellungen › Netzwerk › PrusaLink.",
                color = PrusaColors.TextMuted, fontSize = 13.sp,
            )

            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = PrusaColors.Divider)

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(printers, key = { it.id }) { p ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrusaColors.PanelRaised)
                            .clickable { editing = p }
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name.ifBlank { p.host },
                                     color = PrusaColors.TextPrimary, fontSize = 15.sp)
                                Text(p.host, color = PrusaColors.TextMuted, fontSize = 12.sp)
                                if (p.presetName.isNotBlank())
                                    Text(p.presetName, color = PrusaColors.TextMuted, fontSize = 11.sp)
                            }
                            status[p.id]?.let {
                                Text(it, color = if (it.startsWith("!")) PrusaColors.Danger
                                                 else PrusaColors.Ok, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = PrusaColors.Divider)

            // Option: Profilliste auf eingerichtete Geraete eindampfen
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Nur eingerichtete Drucker zeigen",
                         color = PrusaColors.TextPrimary, fontSize = 14.sp)
                    Text("Blendet Druckerprofile aus, zu denen kein PrusaLink-Gerät eingerichtet ist.",
                         color = PrusaColors.TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = onlyLinked,
                    onCheckedChange = {
                        onlyLinked = it
                        PrinterStore.setOnlyLinked(context, it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = PrusaColors.Orange),
                )
            }

            // Zurueck in die Druckerauswahl. Ohne das kam man nach der
            // Ersteinrichtung nie wieder an sie heran - Befund B2.
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Druckermodelle ändern",
                         color = PrusaColors.TextPrimary, fontSize = 14.sp)
                    Text("Legt fest, welche Prusa-Modelle und Düsengrößen in den Profilen erscheinen.",
                         color = PrusaColors.TextMuted, fontSize = 11.sp)
                }
                OutlinedButton(onClick = onReopenSetup) { Text("Auswahl öffnen") }
            }

            // Sicherung gesendeter Dateien
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Gesendete Dateien sichern",
                         color = PrusaColors.TextPrimary, fontSize = 14.sp)
                    Text(
                        backupName?.let { "Ordner: $it" }
                            ?: "Kein Ordner gewählt. Funktioniert auch mit eingebundenen Netzlaufwerken.",
                        color = PrusaColors.TextMuted, fontSize = 11.sp,
                    )
                }
                OutlinedButton(onClick = onPickBackupFolder) {
                    Text(if (backupName == null) "Ordner wählen" else "Ändern")
                }
            }
        }
    }

    editing?.let { p ->
        PrinterEditor(
            printer = p,
            presetNames = presetNames,
            onTest = { candidate ->
                withContext(Dispatchers.IO) {
                    when (val r = PrusaLink.probe(candidate)) {
                        is PrusaLink.Result.Ok -> r.message
                        is PrusaLink.Result.Error -> "!" + r.message
                    }
                }
            },
            onSave = { updated ->
                val exists = printers.any { it.id == updated.id }
                if (exists) PrinterStore.update(context, updated)
                else PrinterStore.add(context, updated)
                printers = PrinterStore.all(context)
                editing = null
            },
            onDelete = {
                PrinterStore.remove(context, p.id)
                printers = PrinterStore.all(context)
                editing = null
            },
            onCancel = { editing = null },
        )
    }
}

@Composable
private fun PrinterEditor(
    printer: PrusaLink.Printer,
    presetNames: List<String>,
    onTest: suspend (PrusaLink.Printer) -> String,
    onSave: (PrusaLink.Printer) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(printer.name) }
    var host by remember { mutableStateOf(printer.host) }
    var key by remember { mutableStateOf(printer.apiKey) }
    var user by remember { mutableStateOf(printer.username) }
    var pass by remember { mutableStateOf(printer.password) }
    var auth by remember { mutableStateOf(printer.auth) }
    var preset by remember { mutableStateOf(printer.presetName) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(PrusaColors.Background.copy(alpha = 0.94f)),
        Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PrusaColors.Panel)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(8.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (printer.name.isBlank()) "Drucker hinzufügen" else "Drucker bearbeiten",
                 color = PrusaColors.TextPrimary, fontSize = 18.sp,
                 fontWeight = FontWeight.SemiBold)

            Field("Name", name) { name = it }
            Field("Adresse (IP oder Hostname)", host) { host = it }
            // PrusaLink ab 0.7 nutzt Benutzername und Passwort ueber
            // HTTP-Digest; aeltere Firmware einen API-Schluessel.
            Text("Anmeldung", color = PrusaColors.TextMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    PrusaLink.Auth.USER_PASSWORD to "Benutzer + Passwort",
                    PrusaLink.Auth.API_KEY to "API-Schlüssel",
                ).forEach { (mode, label) ->
                    val active = mode == auth
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) PrusaColors.Orange
                                        else PrusaColors.PanelRaised)
                            .clickable { auth = mode },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = PrusaColors.TextPrimary, fontSize = 13.sp) }
                }
            }

            if (auth == PrusaLink.Auth.USER_PASSWORD) {
                Field("Benutzername", user) { user = it }
                Field("Passwort", pass) { pass = it }
            } else {
                Field("API-Schlüssel", key) { key = it }
            }

            if (presetNames.isNotEmpty()) {
                Text("Zugehöriges Druckerprofil", color = PrusaColors.TextMuted, fontSize = 12.sp)
                PresetPickerCompact(presetNames, preset) { preset = it }
            }

            testResult?.let {
                Text(it.removePrefix("!"),
                     color = if (it.startsWith("!")) PrusaColors.Danger else PrusaColors.Ok,
                     fontSize = 13.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !testing && host.isNotBlank() && (
                        if (auth == PrusaLink.Auth.API_KEY) key.isNotBlank()
                        else user.isNotBlank() && pass.isNotBlank()),
                    onClick = {
                        testing = true
                        scope.launch {
                            testResult = onTest(printer.copy(
                            host = host, auth = auth, apiKey = key,
                            username = user, password = pass))
                            testing = false
                        }
                    },
                ) { Text(if (testing) "Prüfe…" else "Verbindung testen") }

                Box(Modifier.weight(1f))

                if (printer.name.isNotBlank())
                    OutlinedButton(onClick = onDelete) { Text("Löschen") }
                OutlinedButton(onClick = onCancel) { Text("Abbrechen") }
                Button(
                    enabled = host.isNotBlank() && (
                        if (auth == PrusaLink.Auth.API_KEY) key.isNotBlank()
                        else user.isNotBlank() && pass.isNotBlank()),
                    onClick = {
                        onSave(printer.copy(
                            name = name.ifBlank { host },
                            host = host, auth = auth, apiKey = key,
                            username = user, password = pass,
                            presetName = preset,
                        ))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrusaColors.Orange,
                        contentColor = PrusaColors.TextPrimary,
                    ),
                ) { Text("Speichern") }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = PrusaColors.TextMuted, fontSize = 12.sp)
        Box(
            Modifier.fillMaxWidth().height(44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PrusaColors.PanelRaised)
                .border(1.dp, PrusaColors.Divider, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = PrusaColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(PrusaColors.Orange),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PresetPickerCompact(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
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
            Text(selected.ifBlank { "—" }, color = PrusaColors.TextPrimary,
                 fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("▾", color = PrusaColors.TextMuted)
        }
        androidx.compose.material3.DropdownMenu(
            expanded, { expanded = false },
            Modifier.background(PrusaColors.PanelRaised),
        ) {
            options.forEach { o ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(o, color = PrusaColors.TextPrimary, fontSize = 13.sp) },
                    onClick = { expanded = false; onSelect(o) },
                )
            }
        }
    }
}
