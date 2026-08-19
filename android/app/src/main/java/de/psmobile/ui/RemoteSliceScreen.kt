package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.shared.ui.Corners
import de.psmobile.net.RemoteSliceClient
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.shared.rules.RemotePairing
import de.psmobile.shared.rules.SimpleModeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun t(english: String, german: String) = SimpleModeState.text(english, german)

private enum class Pruefung { UNBEKANNT, PRUEFT, ERREICHBAR, FEHLER }

/**
 * Erklaerung und Einrichtung von Remote Slicing (siehe
 * docs/remote-slicing.md) - Android-Gegenstueck zu
 * `ios/PSMobile/Screens/RemoteSliceView.swift`, einschliesslich der
 * Kopplung per QR-Code: ein Geraet zeigt seinen Code, ein zweites
 * scannt ihn. Das Format steht als RemotePairing im gemeinsamen Modul,
 * damit ein iPhone den Code eines Android-Tablets lesen kann.
 *
 * Kein eigener Slice-Weg - laeuft ueber denselben "Slice now"-Knopf wie
 * immer, siehe SlicerService.remoteSliceEnabled. Dieser Bildschirm
 * erklaert nur, was dahintersteckt, und nimmt Serveradresse und
 * Zugangs-Token entgegen.
 */
@Composable
fun RemoteSliceScreen(
    service: SlicerService,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverAdresse by remember { mutableStateOf(service.remoteSliceHost) }
    var token by remember { mutableStateOf(service.remoteSliceToken ?: "") }
    var zeigeScanner by remember { mutableStateOf(false) }
    var zeigeEigenenCode by remember { mutableStateOf(false) }
    var pruefung by remember { mutableStateOf(Pruefung.UNBEKANNT) }
    var fehlerText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        modifier.fillMaxSize().background(PrusaColors.Background).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = t("Back", "Zurück"),
                    tint = PrusaColors.TextPrimary,
                    modifier = Modifier.size(28.dp).clickable(onClick = onHome),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Remote Slicing",
                        color = PrusaColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        t("Slice on your own server instead of this device.",
                            "Auf dem eigenen Server statt auf diesem Gerät slicen."),
                        color = PrusaColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().background(PrusaColors.Panel, RoundedCornerShape(Corners.FIELD.dp)).padding(14.dp),
            ) {
                Text(t("How it works", "So funktioniert es"), color = PrusaColors.TextPrimary,
                    fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                erklaerungsZeile("1", t(
                    "Your project (models, printer, filament and print settings) is sent to a server you run yourself.",
                    "Dein Projekt (Modelle, Drucker-, Filament- und Druckeinstellungen) geht an einen Server, den du selbst betreibst."))
                erklaerungsZeile("2", t(
                    "That server slices it - same core, same result as on this device, just with its own CPU instead of this device's.",
                    "Der Server schneidet es - derselbe Kern, dasselbe Ergebnis wie auf diesem Gerät, nur mit eigener Rechenleistung statt der dieses Geräts."))
                erklaerungsZeile("3", t(
                    "The finished G-code comes back and can be exported or sent to a printer, exactly like a local slice.",
                    "Der fertige G-Code kommt zurück und lässt sich exportieren oder an einen Drucker senden - genau wie bei einem lokalen Schnitt."))
            }

            Spacer(Modifier.height(20.dp))
            Text(t("Setup", "Einrichtung"), color = PrusaColors.TextPrimary,
                fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(t("Server address", "Serveradresse"), color = PrusaColors.TextMuted, fontSize = 11.sp)
            OutlinedTextField(
                value = serverAdresse,
                onValueChange = {
                    serverAdresse = it
                    service.remoteSliceHost = it
                    pruefung = Pruefung.UNBEKANNT
                },
                placeholder = { Text("192.168.1.50:8420") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrusaColors.TextPrimary,
                    unfocusedTextColor = PrusaColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Text(t("Access token (if the server requires one)", "Zugangs-Token (falls der Server eins verlangt)"),
                color = PrusaColors.TextMuted, fontSize = 11.sp)
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    service.remoteSliceToken = it
                    pruefung = Pruefung.UNBEKANNT
                },
                placeholder = { Text(t("Optional for a home-network server", "Optional bei einem Server nur im Heimnetz")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrusaColors.TextPrimary,
                    unfocusedTextColor = PrusaColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    service.remoteSliceHost = serverAdresse
                    val basis = RemoteSliceClient.normalizedBaseURL(serverAdresse)
                    if (basis == null) {
                        pruefung = Pruefung.FEHLER
                        fehlerText = t("Invalid server address.", "Ungültige Serveradresse.")
                        return@Button
                    }
                    pruefung = Pruefung.PRUEFT
                    val aktuellesToken = token.ifEmpty { null }
                    scope.launch {
                        val erreichbar = withContext(Dispatchers.IO) {
                            RemoteSliceClient.healthCheck(basis, aktuellesToken)
                        }
                        pruefung = if (erreichbar) Pruefung.ERREICHBAR else Pruefung.FEHLER
                        fehlerText = t("Server not reachable.", "Server nicht erreichbar.")
                    }
                },
                enabled = serverAdresse.isNotEmpty() && pruefung != Pruefung.PRUEFT,
                colors = ButtonDefaults.buttonColors(containerColor = PrusaColors.Orange),
            ) { Text(t("Test connection", "Verbindung testen"), color = PrusaColors.Background) }

            // Koppeln per QR-Code, statt Adresse und - vor allem - das
            // lange Token abzutippen. Ein Gerät zeigt seinen Code, ein
            // zweites scannt ihn hier. Dasselbe Format wie auf iOS, die
            // Regel dazu steht im gemeinsamen Modul (RemotePairing).
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { zeigeScanner = true }) {
                    Text(t("Scan QR code", "QR-Code scannen"), fontSize = 13.sp)
                }
                if (serverAdresse.isNotEmpty()) {
                    OutlinedButton(onClick = { zeigeEigenenCode = true }) {
                        Text(t("Show my QR code", "Meinen QR-Code zeigen"), fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            when (pruefung) {
                Pruefung.PRUEFT -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrusaColors.Orange)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Connecting…", "Verbinde…"), color = PrusaColors.TextPrimary, fontSize = 13.sp)
                }
                Pruefung.ERREICHBAR -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrusaColors.Orange,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        t("Connected. \"Slice now\" now offers remote slicing.",
                            "Verbunden. „Slice now“ bietet jetzt Remote Slicing an."),
                        color = PrusaColors.TextPrimary, fontSize = 13.sp,
                    )
                }
                Pruefung.FEHLER -> Text(fehlerText, color = PrusaColors.Danger, fontSize = 12.sp)
                Pruefung.UNBEKANNT -> Unit
            }

            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth().background(PrusaColors.Panel.copy(alpha = 0.6f), RoundedCornerShape(Corners.FIELD.dp))
                    .padding(14.dp),
            ) {
                Text(t("Security", "Sicherheit"), color = PrusaColors.TextPrimary,
                    fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    t(
                        "The address goes over the plain network unless it starts with https://. If the server is only reachable inside your home network, that's fine. If you expose it to the open internet, put it behind HTTPS (a reverse proxy or tunnel) and set an access token - otherwise anyone who finds the address can submit slice jobs.",
                        "Die Adresse geht unverschlüsselt raus, außer sie beginnt mit https://. Läuft der Server nur im Heimnetz, ist das unproblematisch. Hängst du ihn ins offene Internet, gehört HTTPS davor (Reverse Proxy oder Tunnel) und ein Zugangs-Token gesetzt - sonst kann jeder, der die Adresse findet, Aufträge einreichen."),
                    color = PrusaColors.TextMuted, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (zeigeScanner) {
        QrScanSheet(
            onCode = { code ->
                zeigeScanner = false
                // Ein Scanner liest jeden Code, den man ihm hinhaelt.
                // Passt er nicht, bleibt alles wie es war - stillschweigend
                // eine fremde Adresse zu uebernehmen waere schlimmer als
                // gar nichts zu tun.
                RemotePairing.parse(code)?.let { paar ->
                    serverAdresse = paar.host
                    service.remoteSliceHost = paar.host
                    if (paar.token.isNotEmpty()) {
                        token = paar.token
                        service.remoteSliceToken = paar.token
                    }
                    pruefung = Pruefung.UNBEKANNT
                }
            },
            onCancel = { zeigeScanner = false },
        )
    }

    if (zeigeEigenenCode) {
        EigenerKopplungscode(
            host = serverAdresse,
            token = token,
            onClose = { zeigeEigenenCode = false },
        )
    }
}

/**
 * Der eigene Server als QR-Code, fuer ein zweites Geraet.
 *
 * Der Hinweis zum Token steht bewusst darunter: wer den Code
 * herumzeigt, gibt damit auch das Zugangs-Token weiter, und das ist
 * nicht jedem klar, solange man nur ein Muster aus Quadraten sieht.
 */
@Composable
private fun EigenerKopplungscode(host: String, token: String, onClose: () -> Unit) {
    de.psmobile.ui.theme.AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Text(
                t("Close", "Schließen"),
                color = PrusaColors.Orange,
                modifier = Modifier.clickable(onClick = onClose).padding(12.dp),
            )
        },
        title = { Text(t("Scan this on your other device", "Auf dem anderen Gerät scannen")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QrCodeBild(
                    inhalt = RemotePairing.url(host, token),
                    modifier = Modifier.size(240.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (token.isEmpty()) {
                        t("Contains the server address.", "Enthält die Serveradresse.")
                    } else {
                        t(
                            "Contains the server address and the access token - only show it to devices that should have both.",
                            "Enthält Serveradresse und Zugangs-Token - nur Geräten zeigen, die beides haben sollen.",
                        )
                    },
                    color = PrusaColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        },
    )
}

@Composable
private fun erklaerungsZeile(nummer: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(nummer, color = PrusaColors.Orange, fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.widthIn(min = 16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = PrusaColors.TextMuted, fontSize = 12.sp)
    }
}
