package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.psmobile.LocalSlicerModel
import de.psmobile.net.OctoPrintClient
import de.psmobile.net.PrinterStoreModel
import de.psmobile.net.PrusaLink
import de.psmobile.shared.net.PrusaLinkRules.Auth
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Der Drucker-Store fuer diesen Bildschirm - `PrinterStore()` auf iOS. */
@Composable
fun rememberPrinterStoreModel(): PrinterStoreModel {
    val context = LocalContext.current
    val schalter = LocalSlicerModel.current.startargumente
    return remember { PrinterStoreModel(context, schalter) }
}

private val PrusaLink.Printer.usesApiKey: Boolean get() = auth == Auth.API_KEY

/** `PrusaLinkClient.Printer()` - ein leerer Drucker mit frischer Kennung. */
private fun neuerDrucker(): PrusaLink.Printer =
    PrusaLink.Printer(id = UUID.randomUUID().toString(), name = "", host = "")

/** iOS haelt Geheimnisse getrennt; hier stecken sie im Drucker selbst. */
private fun mitGeheimnis(p: PrusaLink.Printer, secret: PrinterStoreModel.Secret): PrusaLink.Printer =
    p.copy(apiKey = secret.apiKey, password = secret.password)

/**
 * Ergebnis unabhaengig vom tatsaechlichen Client - beide melden
 * dieselbe Zweiheit (ok/fehler), nur mit eigenem Typ, weil
 * OctoPrintClient nichts von PrusaLink wissen muss.
 */
private fun pruefen(
    store: PrinterStoreModel,
    drucker: PrusaLink.Printer,
    secret: PrinterStoreModel.Secret,
): PrusaLink.Result = when (store.hostType(drucker)) {
    PrinterStoreModel.HostType.PRUSA_LINK -> PrusaLink.probe(mitGeheimnis(drucker, secret))
    PrinterStoreModel.HostType.OCTOPRINT -> when (val r = OctoPrintClient.probe(drucker, secret.apiKey)) {
        is OctoPrintClient.Result.Ok -> PrusaLink.Result.Ok(r.message)
        is OctoPrintClient.Result.Error -> PrusaLink.Result.Error(r.message)
    }
}

private fun hochladen(
    store: PrinterStoreModel,
    drucker: PrusaLink.Printer,
    secret: PrinterStoreModel.Secret,
    datei: File,
    name: String,
    printAfter: Boolean,
): PrusaLink.Result = when (store.hostType(drucker)) {
    PrinterStoreModel.HostType.PRUSA_LINK ->
        PrusaLink.upload(mitGeheimnis(drucker, secret), datei, name, printAfter)
    PrinterStoreModel.HostType.OCTOPRINT ->
        when (val r = OctoPrintClient.upload(drucker, secret.apiKey, datei, name, printAfter)) {
            is OctoPrintClient.Result.Ok -> PrusaLink.Result.Ok(r.message)
            is OctoPrintClient.Result.Error -> PrusaLink.Result.Error(r.message)
        }
}

/**
 * Drucker einrichten und den G-Code hinschicken.
 *
 * Gegenstueck zu `PrintersView` in `ios/PSMobile/Screens/PrintersView.swift`.
 *
 * @param senden Wenn gesetzt, wird nach dem Auswaehlen gesendet statt nur
 *   geprueft. So dient derselbe Bildschirm zum Einrichten und zum Senden.
 * @param passendesProfil Der Druckerprofilname des Projekts, aus dem der
 *   G-Code kommt - nur damit lassen sich passende von unpassenden
 *   Geraeten unterscheiden. Ohne Angabe stehen alle gleichrangig da.
 */
@Composable
fun PrintersView(
    senden: File? = null,
    dateiname: String = "psmobile.gcode",
    passendesProfil: String? = null,
    onClose: () -> Unit,
    store: PrinterStoreModel = rememberPrinterStoreModel(),
) {
    val ps = LocalPsScale.current
    val scope = rememberCoroutineScope()
    var bearbeitet by remember { mutableStateOf<PrusaLink.Printer?>(null) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    var nachDemSendenDrucken by remember { mutableStateOf(false) }
    // Ob ein eingerichteter Drucker gerade erreichbar ist - automatisch
    // geprueft beim Oeffnen, nicht erst auf Tippen. Fehlt der Eintrag,
    // heisst das "wird noch geprueft", nicht "unbekannt fuer immer".
    val erreichbarkeit = remember { mutableStateMapOf<String, Boolean>() }

    val passende: List<PrusaLink.Printer> =
        if (senden == null) store.printers
        else PrinterCompatibilityPolicy.matching(
            printers = store.printers,
            selectedProfile = passendesProfil.orEmpty(),
            profileName = { it.presetName },
        )

    // Beim Senden darf ein fehlender Profiltreffer nicht stillschweigend
    // alle Drucker freigeben: genau dadurch konnte ein unpassendes
    // Modell versehentlich als Ziel angeboten werden.
    val angezeigt: List<PrusaLink.Printer> = if (senden == null) store.printers else passende

    fun handle(drucker: PrusaLink.Printer) {
        val secret = store.secret(drucker)
        if (!mitGeheimnis(drucker, secret).isComplete) {
            meldung = drucker.transportError
                ?: st("Credentials incomplete", "Anmeldedaten unvollständig")
            return
        }
        laeuft = true
        meldung = null
        scope.launch {
            val ergebnis = withContext(Dispatchers.IO) {
                val datei = senden
                if (datei != null) {
                    hochladen(store, drucker, secret, datei, dateiname, nachDemSendenDrucken)
                } else {
                    pruefen(store, drucker, secret)
                }
            }
            laeuft = false
            when (ergebnis) {
                is PrusaLink.Result.Ok -> {
                    meldung = ergebnis.message
                    if (senden == null) erreichbarkeit[drucker.id] = true
                }
                is PrusaLink.Result.Error -> {
                    meldung = ergebnis.message
                    if (senden == null) erreichbarkeit[drucker.id] = false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(PrusaColors.background)) {
        Column(Modifier.fillMaxSize()) {
            Kopfzeile(senden = senden, laeuft = laeuft, onClose = onClose)
            HorizontalDivider(color = PrusaColors.divider)
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = ps.pt(760)).fillMaxWidth().padding(ps.pt(16)),
                    verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    meldung?.let { text ->
                        Text(
                            text,
                            fontSize = ps.font(12),
                            color = PrusaColors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrusaColors.panelRaised)
                                .padding(ps.pt(12)),
                        )
                    }
                    if (senden != null) {
                        Row(
                            Modifier.fillMaxWidth().testTag("drucker.dann.drucken"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                st("Start printing after upload", "Nach dem Senden drucken"),
                                fontSize = ps.font(13),
                                color = PrusaColors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = nachDemSendenDrucken,
                                onCheckedChange = { nachDemSendenDrucken = it },
                                colors = orangeSwitch(),
                            )
                        }
                    }
                    if (store.printers.isEmpty()) {
                        Text(
                            st("No printer set up yet", "Noch kein Drucker eingerichtet"),
                            fontSize = ps.font(13),
                            color = PrusaColors.textMuted,
                        )
                    }
                    if (senden != null && passende.isEmpty() && store.printers.isNotEmpty()) {
                        Text(
                            st("No compatible printer is linked to this profile.",
                                "Kein kompatibler Drucker ist mit diesem Profil verknuepft."),
                            fontSize = ps.font(13),
                            color = PrusaColors.textMuted,
                            modifier = Modifier.fillMaxWidth().testTag("drucker.kein-kompatibler"),
                        )
                    }
                    angezeigt.forEach { drucker ->
                        Zeile(
                            drucker = drucker,
                            senden = senden,
                            laeuft = laeuft,
                            zustand = erreichbarkeit[drucker.id],
                            onBearbeiten = { bearbeitet = drucker },
                            onAktion = { handle(drucker) },
                        )
                    }
                    Knopf(st("Add printer", "Drucker hinzufügen"), kennung = "drucker.neu") {
                        bearbeitet = neuerDrucker()
                    }
                }
            }
        }
        PSMarke(name = "drucker")
    }

    bearbeitet?.let { drucker ->
        Dialog(
            onDismissRequest = { bearbeitet = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ScaledOverlay {
                Box(Modifier.fillMaxSize().padding(ps.pt(24)), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .widthIn(max = ps.pt(720))
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(ps.pt(12))),
                    ) {
                        PrinterEditView(
                            store = store,
                            printer = drucker,
                            profile = LocalSlicerModel.current.presetNames("printer"),
                            vorgabeProfil = passendesProfil,
                            // Eine alte Meldung ("nicht erreichbar") gehoert nicht zu
                            // einem geaenderten oder geloeschten Eintrag.
                            onClose = { bearbeitet = null; meldung = null },
                        )
                    }
                }
            }
        }
    }

    // Alle eingerichteten Drucker gleichzeitig anfragen - nur im
    // eigenen Netz schnell genug, um beim Oeffnen nicht aufzufallen;
    // ueber das offene Internet blockiert nichts, weil jede Anfrage
    // ihr eigenes Ergebnis unabhaengig eintraegt.
    LaunchedEffect(store.printers.map { it.id }) {
        for (drucker in store.printers) {
            val secret = store.secret(drucker)
            if (!mitGeheimnis(drucker, secret).isComplete) continue
            launch {
                val ok = withContext(Dispatchers.IO) {
                    pruefen(store, drucker, secret) is PrusaLink.Result.Ok
                }
                erreichbarkeit[drucker.id] = ok
            }
        }
    }
}

@Composable
private fun orangeSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = PrusaColors.orange,
    uncheckedThumbColor = PrusaColors.textMuted,
    uncheckedTrackColor = PrusaColors.panelRaised,
    uncheckedBorderColor = PrusaColors.divider,
)

@Composable
private fun Kopfzeile(senden: File?, laeuft: Boolean, onClose: () -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier.fillMaxWidth().height(ps.touch(56)).padding(horizontal = ps.pt(16)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .clickable(onClick = onClose)
                .testTag("drucker.zurueck"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "‹  " + st("Back", "Zurück"),
                fontSize = ps.font(15),
                color = PrusaColors.orange,
            )
        }
        Text(
            if (senden == null) st("Printers", "Drucker") else st("Send to printer", "An Drucker senden"),
            fontSize = ps.font(20),
            color = PrusaColors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (laeuft) {
            CircularProgressIndicator(
                modifier = Modifier.size(ps.pt(20)),
                color = PrusaColors.orange,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun Zeile(
    drucker: PrusaLink.Printer,
    senden: File?,
    laeuft: Boolean,
    zustand: Boolean?,
    onBearbeiten: () -> Unit,
    onAktion: () -> Unit,
) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(PrusaColors.panelRaised)
            .padding(ps.pt(12)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ErreichbarkeitsPunkt(drucker = drucker, zustand = zustand)
        // Der Name bekommt den Rest der Breite und bricht um - wie der
        // Text in SwiftUIs HStack. Ohne weight nahm sich ein langer Name
        // alles, und die Knoepfe rechts schrumpften auf 0 x 0
        // (RandfaelleUITest, 12.09.2026).
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                if (drucker.name.isEmpty()) drucker.host else drucker.name,
                fontSize = ps.font(14),
                color = PrusaColors.textPrimary,
            )
            Text(
                drucker.baseUrl,
                fontSize = ps.font(11),
                color = PrusaColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .heightIn(min = ps.touch(44))
                .clickable(onClick = onBearbeiten)
                .testTag("drucker.bearbeiten." + drucker.id),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                st("Edit", "Bearbeiten"),
                fontSize = ps.font(12),
                color = PrusaColors.textMuted,
            )
        }
        Box(
            Modifier
                .height(ps.touch(44))
                .clip(RoundedCornerShape(ps.pt(3)))
                .background(PrusaColors.orange)
                .clickable(enabled = !laeuft, onClick = onAktion)
                .padding(horizontal = ps.pt(14))
                .testTag("drucker.aktion." + drucker.id),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (senden == null) st("Test", "Prüfen") else st("Send", "Senden"),
                fontSize = ps.font(13),
                color = Color.White,
            )
        }
    }
}

/**
 * Gruen erreichbar, gedaempft nicht erreichbar, ein kleiner Kreis
 * waehrend der ersten Pruefung noch laeuft - dieselbe Sprache wie
 * die Fortschrittspunkte anderswo in der App.
 */
@Composable
private fun ErreichbarkeitsPunkt(drucker: PrusaLink.Printer, zustand: Boolean?) {
    val ps = LocalPsScale.current
    val wert = when (zustand) {
        true -> st("Reachable", "Erreichbar")
        false -> st("Not reachable", "Nicht erreichbar")
        null -> st("Checking", "Wird geprüft")
    }
    Box(
        Modifier
            .size(ps.pt(9))
            .clip(CircleShape)
            .background(
                when (zustand) {
                    true -> PrusaColors.orange
                    false -> PrusaColors.textMuted.copy(alpha = 0.3f)
                    null -> PrusaColors.textMuted.copy(alpha = 0.15f)
                },
            )
            .testTag("drucker.erreichbar." + drucker.id)
            .semantics { stateDescription = wert },
    )
}

@Composable
private fun Knopf(label: String, kennung: String, aktion: () -> Unit) {
    val ps = LocalPsScale.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ps.touch(48))
            .border(1.dp, PrusaColors.divider, RoundedCornerShape(ps.pt(3)))
            .clickable(onClick = aktion)
            .testTag(kennung),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = ps.font(13), color = PrusaColors.orange)
    }
}

/**
 * Einen Drucker anlegen oder aendern - Gegenstueck zu `PrinterEditView`.
 *
 * Das Passwort steht in einem verdeckten Feld und geht direkt in den
 * Keystore - es taucht weder in den Einstellungen noch in einem
 * Protokoll auf.
 */
@Composable
fun PrinterEditView(
    store: PrinterStoreModel,
    printer: PrusaLink.Printer,
    onClose: () -> Unit,
    /** Die installierten Druckerprofile - eines davon gehoert zu diesem Geraet. */
    profile: List<String> = emptyList(),
    /** Das aktive Profil: Vorgabe fuer einen neuen Eintrag. */
    vorgabeProfil: String? = null,
) {
    val ps = LocalPsScale.current
    // Ein neuer Eintrag ist mit dem aktiven Profil verknuepft. Bis zum
    // 14.09.2026 setzte nichts presetName - und beim Senden hiess es fuer
    // jeden von Hand angelegten Drucker "kein kompatibler Drucker" (S23 FE
    // mit der CORE One L).
    var entwurf by remember {
        mutableStateOf(
            if (printer.presetName.isBlank() && !vorgabeProfil.isNullOrBlank()) printer.copy(presetName = vorgabeProfil)
            else printer,
        )
    }
    var profilMenue by remember { mutableStateOf(false) }
    var hostType by remember { mutableStateOf(store.hostType(printer)) }
    var apiKey by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = ps.pt(640)).fillMaxWidth().padding(ps.pt(20)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(st("Printer", "Drucker"), fontSize = ps.font(20), color = PrusaColors.textPrimary)

            Feld(st("Name", "Name"), text = entwurf.name, onTextChange = { entwurf = entwurf.copy(name = it) },
                kennung = "drucker.name")

            // Der Host-Typ entscheidet, welcher Client ueberhaupt
            // spricht (siehe pruefen/hochladen) - vor der Adresse, weil
            // er auch bestimmt, welche Felder danach ueberhaupt Sinn
            // ergeben.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("drucker.hosttyp")) {
                SegmentedButton(
                    selected = hostType == PrinterStoreModel.HostType.PRUSA_LINK,
                    onClick = { hostType = PrinterStoreModel.HostType.PRUSA_LINK },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = segmentFarben(),
                ) { Text("PrusaLink", fontSize = ps.font(13)) }
                SegmentedButton(
                    selected = hostType == PrinterStoreModel.HostType.OCTOPRINT,
                    onClick = {
                        hostType = PrinterStoreModel.HostType.OCTOPRINT
                        // OctoPrint kennt in dieser Anbindung nur den
                        // API-Schluessel, kein Digest-Verfahren.
                        entwurf = entwurf.copy(auth = Auth.API_KEY)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = segmentFarben(),
                ) { Text("OctoPrint", fontSize = ps.font(13)) }
            }

            Feld(st("Address", "Adresse"), text = entwurf.host, onTextChange = { entwurf = entwurf.copy(host = it) },
                kennung = "drucker.adresse")
            Text(
                st("Without a scheme, HTTPS applies.", "Ohne Schema gilt HTTPS."),
                fontSize = ps.font(11),
                color = PrusaColors.textMuted,
            )

            if (hostType == PrinterStoreModel.HostType.PRUSA_LINK) {
                // PrusaLink ab 0.7 nutzt Benutzername und Passwort ueber
                // HTTP-Digest; aeltere Firmware einen API-Schluessel.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("drucker.verfahren")) {
                    SegmentedButton(
                        selected = !entwurf.usesApiKey,
                        onClick = { entwurf = entwurf.copy(auth = Auth.USER_PASSWORD) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = segmentFarben(),
                    ) { Text(st("User + password", "Benutzer + Passwort"), fontSize = ps.font(13)) }
                    SegmentedButton(
                        selected = entwurf.usesApiKey,
                        onClick = { entwurf = entwurf.copy(auth = Auth.API_KEY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = segmentFarben(),
                    ) { Text(st("API key", "API-Schlüssel"), fontSize = ps.font(13)) }
                }
            }

            if (entwurf.usesApiKey) {
                GeheimFeld(st("API key", "API-Schlüssel"), text = apiKey, onTextChange = { apiKey = it },
                    kennung = "drucker.apikey")
            } else {
                Feld(st("Username", "Benutzername"), text = entwurf.username,
                    onTextChange = { entwurf = entwurf.copy(username = it) }, kennung = "drucker.benutzer")
                GeheimFeld(st("Password", "Passwort"), text = password, onTextChange = { password = it },
                    kennung = "drucker.passwort")
            }

            if (hostType == PrinterStoreModel.HostType.PRUSA_LINK) {
                Feld(st("Storage", "Speicher"), text = entwurf.storage,
                    onTextChange = { entwurf = entwurf.copy(storage = it) }, kennung = "drucker.speicher")
            }

            // Welches Druckerprofil zu diesem Geraet gehoert: nur so weiss
            // das Senden, welcher G-Code hierher passt.
            Column(verticalArrangement = Arrangement.spacedBy(ps.pt(4))) {
                Text(st("Printer profile", "Druckerprofil"), fontSize = ps.font(12), color = PrusaColors.textMuted)
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ps.touch(44))
                            .clip(RoundedCornerShape(ps.pt(3)))
                            .background(PrusaColors.panelRaised)
                            .clickable { profilMenue = true }
                            .padding(horizontal = ps.pt(10))
                            .testTag("drucker.profil"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entwurf.presetName.ifBlank { st("Not selected", "Nicht gewählt") },
                            fontSize = ps.font(14), color = PrusaColors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("▾", fontSize = ps.font(11), color = PrusaColors.textMuted)
                    }
                    DropdownMenu(expanded = profilMenue, onDismissRequest = { profilMenue = false }) {
                        profile.take(30).forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, fontSize = ps.font(13)) },
                                onClick = { profilMenue = false; entwurf = entwurf.copy(presetName = name) },
                                modifier = Modifier.testTag("drucker.profil.$name"),
                            )
                        }
                    }
                }
                Text(
                    st("When sending, only printers linked to the active profile are offered.",
                        "Beim Senden werden nur Drucker angeboten, die mit dem aktiven Profil verknüpft sind."),
                    fontSize = ps.font(11), color = PrusaColors.textMuted,
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        st("Allow plain HTTP", "Klartext-HTTP erlauben"),
                        fontSize = ps.font(13),
                        color = PrusaColors.textPrimary,
                    )
                    Text(
                        st("Credentials travel unencrypted. Only in a network you trust.",
                            "Die Zugangsdaten gehen unverschlüsselt über das Netz. Nur in einem Netz, dem du traust."),
                        fontSize = ps.font(11),
                        color = PrusaColors.textMuted,
                    )
                }
                Switch(
                    checked = entwurf.allowInsecureHttp,
                    onCheckedChange = { entwurf = entwurf.copy(allowInsecureHttp = it) },
                    colors = orangeSwitch(),
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Loeschen gibt es nur fuer einen gesicherten Eintrag: bis zum
                // 16.09.2026 stand der Knopf auch unter "Drucker hinzufuegen",
                // wo er nichts loeschen konnte (S23 FE, Bug-Bounty).
                if (store.printers.any { it.id == entwurf.id }) {
                    TextButton(
                        onClick = {
                            store.remove(entwurf)
                            onClose()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = PrusaColors.danger),
                        modifier = Modifier.testTag("drucker.loeschen"),
                    ) { Text(st("Delete", "Löschen"), fontSize = ps.font(14)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    colors = ButtonDefaults.textButtonColors(contentColor = PrusaColors.textMuted),
                ) { Text(st("Cancel", "Abbrechen"), fontSize = ps.font(14)) }
                // Ohne Adresse gibt es nichts zu sichern: der Eintrag
                // haette einen Aktionsknopf, der ins Leere zeigt.
                // Gefunden am 12.09.2026 durch RandfaelleUITest.
                val adresseFehlt = entwurf.host.isBlank()
                Box(
                    Modifier
                        .height(ps.touch(48))
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(if (adresseFehlt) PrusaColors.textMuted else PrusaColors.orange)
                        .clickable(enabled = !adresseFehlt) {
                            store.upsert(entwurf)
                            store.setHostType(hostType, entwurf)
                            store.setSecret(
                                PrinterStoreModel.Secret(apiKey = apiKey, password = password),
                                entwurf,
                            )
                            onClose()
                        }
                        .padding(horizontal = ps.pt(20))
                        .testTag("drucker.sichern"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Save", "Sichern"), fontSize = ps.font(14), color = Color.White)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val vorhanden = store.secret(printer)
        apiKey = vorhanden.apiKey
        password = vorhanden.password
    }
}

@Composable
private fun segmentFarben() = SegmentedButtonDefaults.colors(
    activeContainerColor = PrusaColors.orange,
    activeContentColor = Color.White,
    activeBorderColor = PrusaColors.divider,
    inactiveContainerColor = PrusaColors.panelRaised,
    inactiveContentColor = PrusaColors.textPrimary,
    inactiveBorderColor = PrusaColors.divider,
)

@Composable
private fun Feld(label: String, text: String, onTextChange: (String) -> Unit, kennung: String) {
    Eingabe(label, text, onTextChange, kennung, geheim = false)
}

@Composable
private fun GeheimFeld(label: String, text: String, onTextChange: (String) -> Unit, kennung: String) {
    Eingabe(label, text, onTextChange, kennung, geheim = true)
}

@Composable
private fun Eingabe(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    kennung: String,
    geheim: Boolean,
) {
    val ps = LocalPsScale.current
    Column(verticalArrangement = Arrangement.spacedBy(ps.pt(4)), horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = ps.font(12), color = PrusaColors.textMuted)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = ps.font(14), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (geheim) KeyboardType.Password else KeyboardType.Text,
            ),
            visualTransformation = if (geheim) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .height(ps.touch(44))
                .clip(RoundedCornerShape(ps.pt(3)))
                .background(PrusaColors.panelRaised)
                .padding(horizontal = ps.pt(10))
                .testTag(kennung),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
            },
        )
    }
}
