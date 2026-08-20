package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.shared.rules.ArrangeAvailability
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.psTouch
import de.psmobile.shared.ui.Corners
import kotlin.math.roundToInt

/**
 * Anordnen mit Optionen: Zielbett, Abstand, Drehen.
 *
 * Ein kleines, am *Anordnen*-Knopf verankertes Feld statt eines eigenen
 * Bildschirms - Anordnen ist eine schnelle Randentscheidung. Genau wie
 * drueben, wo dasselbe als Popover am Knopf haengt
 * (`BedSelector.swift`, `ArrangePanel`). Die Frage „geht das
 * ueberhaupt?" beantwortet der gemeinsame `BedStripContract`, nicht
 * diese Ansicht; die Meldung danach kommt vom Kern.
 */
@Composable
internal fun AnordnenPanel(service: SlicerService, onClose: () -> Unit) {
    val betten by service.beds.collectAsState()
    val fallback = SimpleModeState.text("Bed", "Bett")

    var alleBetten by remember { mutableStateOf(false) }
    var ziel by remember(betten.size) {
        mutableStateOf(betten.firstOrNull { it.active }?.index ?: 0)
    }
    var abstand by remember { mutableStateOf(6f) }
    var drehenErlauben by remember { mutableStateOf(true) }
    var ergebnis by remember { mutableStateOf("") }

    val schnappschuesse = betten.map {
        AndroidBedSnapshot(it.index, it.name, it.locked, it.objectCount,
            it.instanceCount, it.active)
    }
    val zustand = AndroidBedStripAdapter.state(schnappschuesse, fallback)
    fun verfuegbarkeit(index: Int): ArrangeAvailability =
        AndroidBedStripAdapter.arrangeFuer(schnappschuesse, fallback, index)

    Column(
        Modifier
            .width(300.dp)
            .background(PrusaColors.Background, RoundedCornerShape(Corners.SHEET.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                PsUi.appText("Arrange", "Anordnen"),
                color = PrusaColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                PsUi.appText("Done", "Fertig"),
                color = PrusaColors.Orange,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ZielKapsel(
                PsUi.appText("Current bed", "Aktuelles Bett"),
                aktiv = !alleBetten,
                modifier = Modifier.weight(1f),
            ) { alleBetten = false; ergebnis = "" }
            ZielKapsel(
                PsUi.appText("All beds", "Alle Betten"),
                aktiv = alleBetten,
                modifier = Modifier.weight(1f),
            ) { alleBetten = true; ergebnis = "" }
        }

        if (!alleBetten) {
            Text(
                PsUi.appText("Target bed", "Zielbett"),
                color = PrusaColors.TextMuted,
                fontSize = 11.sp,
            )
            Column(
                Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                zustand.items.forEach { bett ->
                    val gewaehlt = ziel == bett.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = psTouch(38))
                            .clip(RoundedCornerShape(Corners.FIELD.dp))
                            .background(
                                if (gewaehlt) PrusaColors.Orange
                                else PrusaColors.PanelRaised
                            )
                            .clickable { ziel = bett.id; ergebnis = "" }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val farbe = if (gewaehlt) PrusaColors.Background
                                    else PrusaColors.TextPrimary
                        if (bett.locked) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = farbe,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(bett.name, color = farbe, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${bett.objectCount}", color = farbe, fontSize = 13.sp)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PsUi.appText("Spacing", "Abstand"),
                    color = PrusaColors.TextPrimary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${"%.1f".format(abstand)} mm",
                    color = PrusaColors.TextPrimary,
                    fontSize = 13.sp,
                )
            }
            // 0 bis 50 mm in Halbschritten - dieselben Grenzen wie der
            // Stepper drueben, nur als Regler, weil ein Daumen hier
            // schneller ist als hundert Tipper.
            Slider(
                value = abstand,
                onValueChange = { abstand = (it * 2f).roundToInt() / 2f },
                valueRange = 0f..50f,
                steps = 99,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = psTouch(32))
                .clickable { drehenErlauben = !drehenErlauben },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                PsUi.appText("Allow rotation", "Drehen erlauben"),
                color = PrusaColors.TextPrimary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(18.dp)
                    .background(
                        if (drehenErlauben) PrusaColors.Orange else PrusaColors.PanelRaised,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }

        if (ergebnis.isNotEmpty()) {
            Text(
                ergebnis,
                color = PrusaColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Button(
            onClick = {
                ergebnis = anordnen(
                    service, zustand.items.size, alleBetten, ziel, abstand,
                    drehenErlauben, ::verfuegbarkeit,
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = psTouch(44)),
            shape = RoundedCornerShape(Corners.FIELD.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrusaColors.Orange,
                contentColor = PrusaColors.Background,
            ),
        ) { Text(PsUi.appText("Arrange", "Anordnen")) }
    }
}

/**
 * Der Lauf selbst. Getrennt gehalten, weil hier zwei Quellen
 * zusammenkommen: die Regel (darf ich?) aus dem gemeinsamen Vertrag und
 * die Auskunft (was ist passiert?) vom Kern. Beide Saetze stehen
 * wortgleich in `ArrangePanel.anordnen()`.
 */
private fun anordnen(
    service: SlicerService,
    bettAnzahl: Int,
    alleBetten: Boolean,
    ziel: Int,
    abstand: Float,
    drehenErlauben: Boolean,
    verfuegbarkeit: (Int) -> ArrangeAvailability,
): String {
    if (alleBetten) {
        val moeglich = (0 until bettAnzahl).any {
            verfuegbarkeit(it) == ArrangeAvailability.AVAILABLE
        }
        if (!moeglich) return PsUi.appText(
            "No unlocked bed contains instances to arrange.",
            "Kein entsperrtes Bett enthält Instanzen zum Anordnen.",
        )
        service.arrangeAlleBetten(abstand, drehenErlauben)
        return PsUi.appText("All beds arranged.", "Alle Betten angeordnet.")
    }
    when (verfuegbarkeit(ziel)) {
        ArrangeAvailability.LOCKED -> return PsUi.appText(
            "Bed ${ziel + 1} is locked. Unlock it in the bed selector.",
            "Bett ${ziel + 1} ist gesperrt. Entsperre es in der Bettauswahl.",
        )
        ArrangeAvailability.EMPTY -> return PsUi.appText(
            "Bed ${ziel + 1} is empty. There is nothing to arrange.",
            "Bett ${ziel + 1} ist leer. Es gibt nichts anzuordnen.",
        )
        ArrangeAvailability.AVAILABLE -> Unit
    }
    val info = service.arrangeBett(ziel, abstand, drehenErlauben)
        ?: return PsUi.appText("Arranging failed.", "Anordnen ist fehlgeschlagen.")
    return when {
        !info.ok && info.status == PsmCore.ArrangeStatus.LOCKED -> PsUi.appText(
            "Bed ${ziel + 1} is locked. Unlock it in the bed selector.",
            "Bett ${ziel + 1} ist gesperrt. Entsperre es in der Bettauswahl.",
        )
        !info.ok && info.status == PsmCore.ArrangeStatus.FULL -> PsUi.appText(
            "Bed ${ziel + 1} is full. Reduce copies or choose another bed.",
            "Bett ${ziel + 1} ist voll. Verringere die Kopien oder wähle ein anderes Bett.",
        )
        !info.ok -> PsUi.appText("Arranging failed.", "Anordnen ist fehlgeschlagen.")
        info.status == PsmCore.ArrangeStatus.EMPTY -> PsUi.appText(
            "Bed ${ziel + 1} is empty. There is nothing to arrange.",
            "Bett ${ziel + 1} ist leer. Es gibt nichts anzuordnen.",
        )
        else -> PsUi.appText(
            "Bed ${ziel + 1}: ${info.instanceCount} instances arranged.",
            "Bett ${ziel + 1}: ${info.instanceCount} Instanzen angeordnet.",
        )
    }
}

@Composable
private fun ZielKapsel(
    label: String,
    aktiv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = psTouch(34))
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .background(if (aktiv) PrusaColors.Orange else PrusaColors.PanelRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (aktiv) PrusaColors.Background else PrusaColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
