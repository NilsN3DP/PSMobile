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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.net.RemoteSliceClient
import de.psmobile.shared.rules.RemotePairing
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Zustand der Verbindungspruefung - `Pruefung` in RemoteSliceView.swift. */
private sealed interface Pruefung {
    data object Unbekannt : Pruefung
    data object Prueft : Pruefung
    data object Erreichbar : Pruefung
    data class Fehler(val meldung: String) : Pruefung
}

/**
 * Erklaerung und Einrichtung von Remote Slicing (siehe
 * docs/remote-slicing.md) - Gegenstueck zu
 * `ios/PSMobile/Screens/RemoteSliceView.swift`.
 *
 * Kein eigener Weg zum Schneiden - der laeuft ueber denselben
 * "Slice now"-Knopf wie immer, siehe SlicerModel.remoteSliceEnabled.
 * Dieser Bildschirm erklaert nur, was dahintersteckt, und nimmt
 * Serveradresse und Zugangs-Token entgegen.
 */
@Composable
fun RemoteSliceView(
    onHome: () -> Unit = {},
    model: SlicerModel = LocalSlicerModel.current,
) {
    val ps = LocalPsScale.current
    val scope = rememberCoroutineScope()

    // @AppStorage(remoteSliceHostKey): auf Android liegt die Adresse
    // im Dienst, jeder Tastendruck schreibt sie dorthin.
    var serverAdresse by remember { mutableStateOf(model.service.remoteSliceHost) }
    var token by remember { mutableStateOf("") }
    var tokenGeladen by remember { mutableStateOf(false) }
    var pruefung by remember { mutableStateOf<Pruefung>(Pruefung.Unbekannt) }
    var zeigeScanner by remember { mutableStateOf(false) }
    var zeigeEigenerCode by remember { mutableStateOf(false) }

    fun tokenLaden() {
        if (tokenGeladen) return
        tokenGeladen = true
        token = model.service.remoteSliceToken ?: ""
    }

    fun tokenSichern() {
        // Leer -> entfernen, sonst sichern: der Setter im Dienst tut genau das.
        model.service.remoteSliceToken = token.ifEmpty { null }
    }

    fun verbindungPruefen() {
        val basis = RemoteSliceClient.normalizedBaseURL(serverAdresse)
        if (basis == null) {
            pruefung = Pruefung.Fehler(st("Invalid server address.", "Ungültige Serveradresse."))
            return
        }
        pruefung = Pruefung.Prueft
        val aktuellesToken = token.ifEmpty { null }
        scope.launch {
            val erreichbar = withContext(Dispatchers.IO) {
                RemoteSliceClient.healthCheck(basis, aktuellesToken)
            }
            pruefung = if (erreichbar) Pruefung.Erreichbar
            else Pruefung.Fehler(st("Server not reachable.", "Server nicht erreichbar."))
        }
    }

    Box(Modifier.fillMaxSize().background(PrusaColors.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = ps.pt(560)).fillMaxWidth().padding(ps.pt(20)),
                verticalArrangement = Arrangement.spacedBy(ps.pt(20)),
                horizontalAlignment = Alignment.Start,
            ) {
                Kopf(onHome = onHome)
                Erklaerung()
                Einrichtung(
                    serverAdresse = serverAdresse,
                    onServerAdresseChange = {
                        serverAdresse = it
                        model.service.remoteSliceHost = it
                        pruefung = Pruefung.Unbekannt
                    },
                    token = token,
                    onTokenChange = {
                        token = it
                        tokenSichern()
                        pruefung = Pruefung.Unbekannt
                    },
                    pruefung = pruefung,
                    onVerbindungPruefen = { verbindungPruefen() },
                    onScannen = { zeigeScanner = true },
                    onEigenerCode = { zeigeEigenerCode = true },
                )
                if (serverAdresse.isNotEmpty()) {
                    PruefungsBereich(pruefung)
                }
                Sicherheitshinweis()
                Spacer(Modifier.height(ps.pt(20)))
            }
        }
        PSMarke(name = "remote")
    }

    // .fullScreenCover(isPresented: $zeigeScanner)
    if (zeigeScanner) {
        Dialog(
            onDismissRequest = { zeigeScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                QRScanSheet(
                    onCode = { code ->
                        zeigeScanner = false
                        val paar = RemotePairing.parse(code) ?: return@QRScanSheet
                        serverAdresse = paar.host
                        model.service.remoteSliceHost = paar.host
                        if (paar.token.isNotEmpty()) {
                            token = paar.token
                            tokenSichern()
                        }
                        pruefung = Pruefung.Unbekannt
                    },
                    onCancel = { zeigeScanner = false },
                )
            }
        }
    }

    // .sheet(isPresented: $zeigeEigenerCode)
    if (zeigeEigenerCode) {
        Dialog(
            onDismissRequest = { zeigeEigenerCode = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(Modifier.fillMaxSize().padding(ps.pt(24)), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .widthIn(max = ps.pt(480))
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ps.pt(12))),
                    ) {
                        EigenerCodeBlatt(
                            serverAdresse = serverAdresse,
                            token = token,
                            onFertig = { zeigeEigenerCode = false },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { tokenLaden() }
}

@Composable
private fun Kopf(onHome: () -> Unit) {
    val ps = LocalPsScale.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(ps.touch(40))
                .clickable(onClick = onHome)
                .testTag("remote.zurueck"),
            contentAlignment = Alignment.Center,
        ) {
            SfSymbol("chevron.left", Modifier.size(ps.pt(16)), tint = PrusaColors.textPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.Start) {
            Text(
                st("Remote Slicing", "Remote Slicing"),
                fontSize = ps.font(20),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textPrimary,
            )
            Text(
                st("Slice on your own server instead of this device.",
                    "Auf dem eigenen Server statt auf diesem Gerät slicen."),
                fontSize = ps.font(12),
                color = PrusaColors.textMuted,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Was es ist und wie es ablaeuft - bevor jemand eine Adresse
 * eintippt, sollte klar sein, wohin das Projekt dabei geht.
 */
@Composable
private fun Erklaerung() {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ps.pt(8)))
            .background(PrusaColors.panel)
            .padding(ps.pt(14)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(8)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            st("How it works", "So funktioniert es"),
            fontSize = ps.font(13),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textPrimary,
        )
        ErklaerungsZeile(
            "1",
            st("Your project (models, printer, filament and print settings) is sent to a server you run yourself.",
                "Dein Projekt (Modelle, Drucker-, Filament- und Druckeinstellungen) geht an einen Server, den du selbst betreibst."),
        )
        ErklaerungsZeile(
            "2",
            st("That server slices it - same core, same result as on this device, just with its own CPU instead of this device's.",
                "Der Server schneidet es - derselbe Kern, dasselbe Ergebnis wie auf diesem Gerät, nur mit eigener Rechenleistung statt der dieses Geräts."),
        )
        ErklaerungsZeile(
            "3",
            st("The finished G-code comes back and can be exported or sent to a printer, exactly like a local slice.",
                "Der fertige G-Code kommt zurück und lässt sich exportieren oder an einen Drucker senden - genau wie bei einem lokalen Schnitt."),
        )
    }
}

@Composable
private fun ErklaerungsZeile(nummer: String, text: String) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(10)),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            nummer,
            fontSize = ps.font(11),
            fontWeight = FontWeight.Bold,
            color = PrusaColors.orange,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(ps.pt(16)),
        )
        Text(
            text,
            fontSize = ps.font(12),
            color = PrusaColors.textMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Einrichtung(
    serverAdresse: String,
    onServerAdresseChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    pruefung: Pruefung,
    onVerbindungPruefen: () -> Unit,
    onScannen: () -> Unit,
    onEigenerCode: () -> Unit,
) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            st("Setup", "Einrichtung"),
            fontSize = ps.font(13),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(6)), horizontalAlignment = Alignment.Start) {
            Text(
                st("Server address", "Serveradresse"),
                fontSize = ps.font(11),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textMuted,
            )
            MonoFeld(
                text = serverAdresse,
                onTextChange = onServerAdresseChange,
                platzhalter = "192.168.1.50:8420",
                kennung = "remote.adresse",
                geheim = false,
                keyboardType = KeyboardType.Uri,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(ps.pt(6)), horizontalAlignment = Alignment.Start) {
            Text(
                st("Access token (if the server requires one)",
                    "Zugangs-Token (falls der Server eins verlangt)"),
                fontSize = ps.font(11),
                fontWeight = FontWeight.SemiBold,
                color = PrusaColors.textMuted,
            )
            MonoFeld(
                text = token,
                onTextChange = onTokenChange,
                platzhalter = st("Optional for a home-network server",
                    "Optional bei einem Server nur im Heimnetz"),
                kennung = "remote.token",
                geheim = true,
                keyboardType = KeyboardType.Password,
            )
        }

        OutlinedButton(
            onClick = onVerbindungPruefen,
            enabled = serverAdresse.isNotEmpty() && pruefung != Pruefung.Prueft,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PrusaColors.orange,
                disabledContentColor = PrusaColors.textMuted,
            ),
            modifier = Modifier.testTag("remote.verbinden"),
        ) {
            Text(st("Test connection", "Verbindung testen"), fontSize = ps.font(13))
        }

        // Paaren per QR-Code, statt Adresse und - vor allem - das
        // lange Token abzutippen. Ein Geraet zeigt seinen Code
        // (unten), ein zweites scannt ihn hier.
        Row(horizontalArrangement = Arrangement.spacedBy(ps.pt(10)), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onScannen,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrusaColors.orange),
                modifier = Modifier.testTag("remote.qr.scannen"),
            ) {
                SfSymbol("qrcode.viewfinder", Modifier.size(ps.pt(16)), tint = PrusaColors.orange)
                Spacer(Modifier.width(ps.pt(6)))
                Text(st("Scan QR code", "QR-Code scannen"), fontSize = ps.font(13))
            }

            if (serverAdresse.isNotEmpty()) {
                OutlinedButton(
                    onClick = onEigenerCode,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrusaColors.orange),
                    modifier = Modifier.testTag("remote.qr.zeigen"),
                ) {
                    SfSymbol("qrcode", Modifier.size(ps.pt(16)), tint = PrusaColors.orange)
                    Spacer(Modifier.width(ps.pt(6)))
                    Text(st("Show my QR code", "Meinen QR-Code zeigen"), fontSize = ps.font(13))
                }
            }
        }
    }
}

@Composable
private fun MonoFeld(
    text: String,
    onTextChange: (String) -> Unit,
    platzhalter: String,
    kennung: String,
    geheim: Boolean,
    keyboardType: KeyboardType,
) {
    val ps = LocalPsScale.current
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = ps.font(14),
            fontFamily = FontFamily.Monospace,
            color = PrusaColors.textPrimary,
        ),
        cursorBrush = SolidColor(PrusaColors.orange),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
        ),
        visualTransformation = if (geheim) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ps.touch(40))
            .clip(RoundedCornerShape(ps.pt(5)))
            .background(PrusaColors.panelRaised)
            .padding(horizontal = ps.pt(10))
            .testTag(kennung),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = ps.touch(40)), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(
                        platzhalter,
                        fontSize = ps.font(14),
                        fontFamily = FontFamily.Monospace,
                        color = PrusaColors.textMuted,
                    )
                }
                inner()
            }
        },
    )
}

/** Der eigene Server als QR-Code, fuer ein zweites Geraet. */
@Composable
private fun EigenerCodeBlatt(serverAdresse: String, token: String, onFertig: () -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(PrusaColors.background)
            .padding(ps.pt(24)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(16)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            st("Scan this on your other device", "Auf dem anderen Gerät scannen"),
            fontSize = ps.font(16),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textPrimary,
        )
        val url = RemotePairing.url(host = serverAdresse, token = token)
        Box(
            Modifier
                .clip(RoundedCornerShape(ps.pt(8)))
                .background(Color.White)
                .padding(ps.pt(16)),
        ) {
            QRCodeView(inhalt = url, modifier = Modifier.size(ps.pt(240)))
        }
        Text(
            st("Anyone who can scan this can submit slice jobs to your server.",
                "Wer diesen Code scannen kann, kann Aufträge an deinen Server schicken."),
            fontSize = ps.font(11),
            color = PrusaColors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = ps.pt(24)),
        )
        Button(
            onClick = onFertig,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrusaColors.orange,
                contentColor = Color.White,
            ),
            modifier = Modifier.testTag("remote.qr.fertig"),
        ) {
            Text(st("Done", "Fertig"))
        }
    }
}

@Composable
private fun PruefungsBereich(pruefung: Pruefung) {
    val ps = LocalPsScale.current
    when (pruefung) {
        Pruefung.Unbekannt -> Unit
        Pruefung.Prueft -> Row(
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(ps.pt(16)),
                color = PrusaColors.orange,
                strokeWidth = 2.dp,
            )
            Text(
                st("Connecting…", "Verbinde…"),
                fontSize = ps.font(13),
                color = PrusaColors.textPrimary,
            )
        }
        Pruefung.Erreichbar -> Row(
            Modifier.testTag("remote.verbunden"),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SfSymbol("checkmark.circle.fill", Modifier.size(ps.pt(17)), tint = PrusaColors.orange)
            Text(
                st("Connected. \"Slice now\" now offers remote slicing.",
                    "Verbunden. „Slice now“ bietet jetzt Remote Slicing an."),
                fontSize = ps.font(13),
                color = PrusaColors.textPrimary,
            )
        }
        is Pruefung.Fehler -> Text(
            pruefung.meldung,
            fontSize = ps.font(12),
            color = PrusaColors.danger,
            modifier = Modifier.fillMaxWidth().testTag("remote.fehler"),
        )
    }
}

@Composable
private fun Sicherheitshinweis() {
    val ps = LocalPsScale.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ps.pt(8)))
            .background(PrusaColors.panel.copy(alpha = 0.6f))
            .padding(ps.pt(14)),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            st("Security", "Sicherheit"),
            fontSize = ps.font(13),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.textPrimary,
        )
        Text(
            st(
                "The address goes over the plain network unless it starts with https://. If the server is only reachable inside your home network, that's fine. If you expose it to the open internet, put it behind HTTPS (a reverse proxy or tunnel) and set an access token - otherwise anyone who finds the address can submit slice jobs.",
                "Die Adresse geht unverschlüsselt raus, außer sie beginnt mit https://. Läuft der Server nur im Heimnetz, ist das unproblematisch. Hängst du ihn ins offene Internet, gehört HTTPS davor (Reverse Proxy oder Tunnel) und ein Zugangs-Token gesetzt - sonst kann jeder, der die Adresse findet, Aufträge einreichen.",
            ),
            fontSize = ps.font(11),
            color = PrusaColors.textMuted,
        )
    }
}
