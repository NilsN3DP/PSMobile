package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import de.psmobile.shared.ui.Corners
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import de.psmobile.ui.theme.psTouch
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.ui.theme.ScaledOverlay
import kotlin.math.roundToInt

/**
 * Was man mit einem ausgewaehlten Objekt am haeufigsten tut.
 *
 * Am Desktop verteilt sich das auf die Gizmos links und das
 * Objektmanipulator-Feld rechts unten. Auf dem Tablet ist beides
 * zusammengelegt: die Zahlen zum genauen Setzen, die Knoepfe fuer die
 * haeufigen Handgriffe, und das Skalieren zusaetzlich mit zwei Fingern
 * direkt am Objekt.
 *
 * Die Werte kommen bei jedem Aufruf frisch aus dem Kern - nach einer
 * Spreizgeste oder einem Anordnen stimmt sonst die Anzeige nicht mehr.
 */
@Composable
fun ObjectPanel(
    service: SlicerService,
    obj: PsmCore.ObjectInfo,
    beds: List<PsmCore.Bed>,
    gizmo: de.psmobile.core.PsmViewport.Gizmo,
    onGizmoChange: (de.psmobile.core.PsmViewport.Gizmo) -> Unit,
    scaleToolActive: Boolean,
    onScaleToolChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabelPublic(PsUi.tr("Object manipulation"))

        // Welche Griffe am Objekt stehen. Wie am Desktop die Gizmo-Leiste
        // links, hier als Zeile - auf dem Tablet ist waagerecht billiger
        // als eine weitere senkrechte Leiste.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                de.psmobile.core.PsmViewport.Gizmo.NONE   to PsUi.tr("None"),
                de.psmobile.core.PsmViewport.Gizmo.MOVE   to PsUi.tr("Move"),
                de.psmobile.core.PsmViewport.Gizmo.ROTATE to PsUi.tr("Rotate"),
                de.psmobile.core.PsmViewport.Gizmo.SCALE  to PsUi.tr("Scale"),
            ).forEach { (g, label) ->
                val on = g == gizmo
                Box(
                    Modifier.weight(1f).height(psTouch(48))
                        .clip(RoundedCornerShape(Corners.FIELD.dp))
                        .background(if (on) PrusaColors.Orange else PrusaColors.PanelRaised)
                        .clickable { onGizmoChange(g) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) Color.White else PrusaColors.TextPrimary,
                         fontSize = 13.sp,
                         fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        // --- Groesse ---------------------------------------------------
        //
        // Prozent und Millimeter nebeneinander: am Modell denkt man in
        // Prozent, beim Einpassen aufs Bett in Millimetern.
        val scale = obj.scale.first
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PsUi.tr("Scale"), color = PrusaColors.TextMuted,
                 fontSize = 12.sp, modifier = Modifier.width(64.dp))
            NumberField(
                value = NumberCodec.oneDecimal(scale * 100f),
                unit = "%",
                onCommit = { v ->
                    NumberCodec.parseFloat(v)?.let { pct ->
                        if (pct > 0f) service.setUniformScale(obj.id, pct / 100f)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            NumberField(
                value = NumberCodec.oneDecimal(
                    maxOf(obj.sizeMm.first, obj.sizeMm.second, obj.sizeMm.third)
                ),
                unit = "mm",
                onCommit = { v ->
                    // Ueber die laengste Kante skalieren, wie "Auf Bett
                    // einpassen" - nur mit eigenem Zielmass.
                    NumberCodec.parseFloat(v)?.let { mm ->
                        if (mm > 0f) service.scaleToSize(obj.id, mm)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Zwei-Finger-Skalierung ein- und ausschalten. Solange sie an ist,
        // zoomt das Spreizen nicht mehr die Kamera - das muss sichtbar
        // sein, sonst wirkt der Viewport kaputt.
        Row(
            Modifier.fillMaxWidth()
                .height(psTouch(50))
                .clip(RoundedCornerShape(Corners.FIELD.dp))
                .background(if (scaleToolActive) PrusaColors.Orange else PrusaColors.PanelRaised)
                .clickable { onScaleToolChange(!scaleToolActive) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (scaleToolActive) PsUi.appText("Two-finger scale · on", "Mit zwei Fingern skalieren · an")
                else                 PsUi.appText("Two-finger scale", "Mit zwei Fingern skalieren"),
                color = if (scaleToolActive) Color.White else PrusaColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = if (scaleToolActive) FontWeight.SemiBold else FontWeight.Normal,
            )
        }

        HorizontalDivider(color = PrusaColors.Divider)

        // --- Drehung ---------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PsUi.tr("Rotation"), color = PrusaColors.TextMuted,
                 fontSize = 12.sp, modifier = Modifier.width(64.dp))
            listOf("X" to 0, "Y" to 1, "Z" to 2).forEach { (label, axis) ->
                val deg = when (axis) {
                    0    -> obj.rotation.first
                    1    -> obj.rotation.second
                    else -> obj.rotation.third
                }
                NumberField(
                    value = deg.roundToInt().toString(),
                    unit = label,
                    onCommit = { v ->
                        NumberCodec.parseFloat(v)?.let {
                            service.setRotationAxis(obj.id, axis, it)
                        }
                    },
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                )
            }
        }

        // Vierteldrehungen sind der haeufigste Fall und mit dem Finger
        // sonst nicht genau zu treffen.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(64.dp))
            listOf("↺ 90°" to -90f, "↻ 90°" to 90f, "180°" to 180f).forEach { (label, d) ->
                SmallButton(label, Modifier.weight(1f)) {
                    service.rotateBy(obj.id, 2, d)
                }
            }
        }

        HorizontalDivider(color = PrusaColors.Divider)

        // --- Handgriffe ------------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(PsUi.tr("Place on bed"), Modifier.weight(1f)) {
                service.dropToBed(obj.id)
            }
            SmallButton(PsUi.appText("Fit to bed", "Aufs Bett einpassen"), Modifier.weight(1f)) {
                service.scaleToBed(obj.id)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(PsUi.appText("Mirror X", "Spiegeln X") to PsmCore.Axis.X,
                   PsUi.appText("Mirror Y", "Spiegeln Y") to PsmCore.Axis.Y,
                   PsUi.appText("Mirror Z", "Spiegeln Z") to PsmCore.Axis.Z).forEach { (label, axis) ->
                SmallButton(label, Modifier.weight(1f)) { service.mirror(obj.id, axis) }
            }
        }

        if (beds.size > 1) {
            var moveMenu by remember { mutableStateOf(false) }
            val active = beds.firstOrNull { it.active }?.index ?: 0
            Box(Modifier.fillMaxWidth()) {
                SmallButton(PsUi.appText("Move to another bed", "Auf anderes Bett verschieben"), Modifier.fillMaxWidth()) {
                    moveMenu = true
                }
                DropdownMenu(
                    expanded = moveMenu,
                    onDismissRequest = { moveMenu = false },
                ) {
                    ScaledOverlay {
                    beds.filter { it.index != active }.forEach { bed ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${PsUi.appText("Bed", "Bett")} ${bed.index + 1} · ${bed.objectCount} ${PsUi.appText("objects", "Objekte")}",
                                    color = PrusaColors.TextPrimary,
                                )
                            },
                            onClick = {
                                moveMenu = false
                                service.moveObjectToBed(obj.id, bed.index)
                            },
                        )
                    }
                    }
                }
            }
        }

        // --- Kopien ----------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PsUi.appText("Copies", "Kopien"), color = PrusaColors.TextMuted,
                 fontSize = 12.sp, modifier = Modifier.width(64.dp))
            SmallButton("−", Modifier.width(52.dp)) {
                if (obj.instances > 1) service.setInstances(obj.id, obj.instances - 1)
            }
            Text(
                obj.instances.toString(),
                color = PrusaColors.TextPrimary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp),
            )
            SmallButton("+", Modifier.width(52.dp)) {
                service.setInstances(obj.id, obj.instances + 1)
            }
        }
    }
}

@Composable
private fun SectionLabelPublic(text: String) {
    Text(
        text.uppercase(),
        color = PrusaColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Zahleneingabe, die erst beim Verlassen schreibt.
 *
 * Bei jedem Tastendruck zu uebernehmen ergibt Zwischenzustaende wie "1."
 * oder "-", die der Kern zu Recht ablehnt - und einen Wert, der beim
 * Tippen springt.
 */
@Composable
private fun NumberField(
    value: String,
    unit: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value) }

    Row(
        modifier.height(psTouch(48))
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .background(PrusaColors.PanelRaised)
            .border(1.dp, PrusaColors.Divider, RoundedCornerShape(Corners.FIELD.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = PrusaColors.TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(PrusaColors.Orange),
            modifier = Modifier.weight(1f).onFocusChanged { state ->
                // Beim Verlassen uebernehmen. Waehrend des Tippens gibt es
                // Zwischenstaende wie "1." oder "-", die keine Zahl sind -
                // und ein Wert, der bei jedem Anschlag springt.
                if (!state.isFocused && text != value &&
                    NumberCodec.parseFloat(text) != null)
                    onCommit(text)
            },
        )
        Text(unit, color = PrusaColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SmallButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(psTouch(48))
            .clip(RoundedCornerShape(Corners.FIELD.dp))
            .background(PrusaColors.PanelRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = PrusaColors.TextPrimary, fontSize = 13.sp,
             textAlign = TextAlign.Center)
    }
}
