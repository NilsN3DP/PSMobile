package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.psmobile.LocalSlicerModel
import de.psmobile.SlicerModel
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors

/**
 * Ein Teil ins Objekt legen - Port von
 * `ios/PSMobile/Screens/TeilHinzufuegenView.swift`.
 *
 * Drei Entscheidungen, mehr braucht es nicht: wofuer der Koerper da
 * ist, welche Form er hat, wie gross er ist. Er entsteht in der Mitte
 * des Objekts; an seinen Platz schiebt man ihn danach mit den Griffen -
 * genau wie am Desktop.
 *
 * Der Modellkoerper selbst fehlt in der Auswahl. Einen Vollkoerper aus
 * einem Grundkoerper zu bauen ist ein Modellierschritt; die vier
 * anderen sind das, wofuer man sonst das Programm wechselt.
 */
@Composable
fun TeilHinzufuegenView(
    model: SlicerModel = LocalSlicerModel.current,
    objektId: Int,
    onClose: () -> Unit,
) {
    val ps = LocalPsScale.current
    var art by remember { mutableStateOf(PsmCore.VolumeType.NEGATIVE) }
    var form by remember { mutableStateOf(PsmCore.PrimitiveShape.BOX) }
    var groesse by remember { mutableFloatStateOf(10f) }

    fun hinzufuegen() {
        if (model.addPrimitiveVolume(objektId, art, form, groesse)) {
            onClose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(ps.pt(20)),
            verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    st("Add part", "Teil hinzufügen"),
                    color = PrusaColors.textPrimary,
                    fontSize = ps.font(19),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .heightIn(min = ps.touch(44))
                        .clickable(onClick = onClose)
                        .testTag("teil.abbrechen"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st("Cancel", "Abbrechen"), color = PrusaColors.textMuted)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ps.pt(14)),
                horizontalAlignment = Alignment.Start,
            ) {
                Artwahl(art = art, onArtChange = { art = it })
                Formwahl(form = form, onFormChange = { form = it })
                Groessenwahl(groesse = groesse, onGroesseChange = { groesse = it })
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ps.touch(52))
                    .clip(RoundedCornerShape(ps.pt(4)))
                    .background(PrusaColors.orange)
                    .clickable { hinzufuegen() }
                    .testTag("teil.hinzufuegen"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st("Add", "Hinzufügen"),
                    color = Color.White,
                    fontSize = ps.font(15),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        PSMarke(name = "teilhinzufuegen")
    }
}

/** Eine Art aus der Auswahl: Typ, englischer und deutscher Name. */
private class TeilArt(val typ: PsmCore.VolumeType, val en: String, val de: String)

private val teilArten: List<TeilArt> = listOf(
    TeilArt(PsmCore.VolumeType.NEGATIVE, "Negative volume", "Aussparung"),
    TeilArt(PsmCore.VolumeType.MODIFIER, "Modifier", "Modifier"),
    TeilArt(PsmCore.VolumeType.SUPPORT_BLOCKER, "Support blocker", "Stützen verhindern"),
    TeilArt(PsmCore.VolumeType.SUPPORT_ENFORCER, "Support enforcer", "Stützen erzwingen"),
)

/** Der Rohwert wie im C-ABI - so wie `rawValue` auf iOS. */
private fun rohwert(typ: PsmCore.VolumeType): Int = when (typ) {
    PsmCore.VolumeType.MODEL_PART -> 0
    PsmCore.VolumeType.NEGATIVE -> 1
    PsmCore.VolumeType.MODIFIER -> 2
    PsmCore.VolumeType.SUPPORT_BLOCKER -> 3
    PsmCore.VolumeType.SUPPORT_ENFORCER -> 4
    PsmCore.VolumeType.UNKNOWN -> -1
}

/**
 * Jede Art mit einem Satz dazu. Vier Woerter ohne Erklaerung waeren
 * vier Fragen - "Modifier" sagt niemandem etwas, der es nicht schon
 * weiss.
 */
@Composable
private fun Artwahl(art: PsmCore.VolumeType, onArtChange: (PsmCore.VolumeType) -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Ueberschrift(st("Purpose", "Wofür"))
        teilArten.forEach { eintrag ->
            val aktiv = art == eintrag.typ
            val form = RoundedCornerShape(ps.pt(4))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ps.touch(52))
                    .clip(form)
                    .background(if (aktiv) PrusaColors.panelRaised else PrusaColors.panel)
                    .border(1.dp, if (aktiv) PrusaColors.orange else PrusaColors.divider, form)
                    .clickable { onArtChange(eintrag.typ) }
                    .padding(horizontal = ps.pt(12), vertical = ps.pt(8))
                    .testTag("teil.art.${rohwert(eintrag.typ)}"),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        st(eintrag.en, eintrag.de),
                        color = if (aktiv) PrusaColors.orange else PrusaColors.textPrimary,
                        fontSize = ps.font(13),
                        fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        erklaerung(eintrag.typ),
                        color = PrusaColors.textMuted,
                        fontSize = ps.font(10),
                    )
                }
            }
        }
    }
}

@Composable
private fun Formwahl(form: PsmCore.PrimitiveShape, onFormChange: (PsmCore.PrimitiveShape) -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Ueberschrift(st("Shape", "Form"))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormKnopf(st("Box", "Quader"), PsmCore.PrimitiveShape.BOX, form, onFormChange)
            FormKnopf(st("Cylinder", "Zylinder"), PsmCore.PrimitiveShape.CYLINDER, form, onFormChange)
            FormKnopf(st("Sphere", "Kugel"), PsmCore.PrimitiveShape.SPHERE, form, onFormChange)
        }
    }
}

@Composable
private fun Groessenwahl(groesse: Float, onGroesseChange: (Float) -> Unit) {
    val ps = LocalPsScale.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ps.pt(6)),
        horizontalAlignment = Alignment.Start,
    ) {
        Ueberschrift(st("Size", "Größe") + " · ${groesse.toInt()} mm")
        Slider(
            value = groesse,
            onValueChange = onGroesseChange,
            valueRange = 2f..60f,
            // Schrittweite 1 zwischen 2 und 60: 57 Zwischenstufen.
            steps = 57,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ps.touch(44))
                .testTag("teil.groesse"),
            colors = SliderDefaults.colors(
                thumbColor = PrusaColors.orange,
                activeTrackColor = PrusaColors.orange,
                activeTickColor = PrusaColors.orange,
                inactiveTickColor = PrusaColors.panelRaised,
            ),
        )
        Text(
            st("The part appears at the centre of the object. Move it into place with the handles.",
                "Das Teil entsteht in der Mitte des Objekts. An seinen Platz schiebt man es mit den Griffen."),
            color = PrusaColors.textMuted,
            fontSize = ps.font(10),
        )
    }
}

@Composable
private fun RowScope.FormKnopf(
    label: String,
    wert: PsmCore.PrimitiveShape,
    form: PsmCore.PrimitiveShape,
    onFormChange: (PsmCore.PrimitiveShape) -> Unit,
) {
    val ps = LocalPsScale.current
    val aktiv = form == wert
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = ps.touch(44))
            .clip(RoundedCornerShape(ps.pt(4)))
            .background(if (aktiv) PrusaColors.orange else PrusaColors.panelRaised)
            .clickable { onFormChange(wert) }
            .testTag("teil.form.${wert.raw}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (aktiv) PrusaColors.background else PrusaColors.textPrimary,
            fontSize = ps.font(12),
        )
    }
}

@Composable
private fun Ueberschrift(text: String) {
    val ps = LocalPsScale.current
    Text(
        text.uppercase(),
        color = PrusaColors.textMuted,
        fontSize = ps.font(11),
        fontWeight = FontWeight.SemiBold,
    )
}

private fun erklaerung(wert: PsmCore.VolumeType): String = when (wert) {
    PsmCore.VolumeType.NEGATIVE ->
        st("A hole in the model — the part is subtracted.",
            "Ein Loch im Modell — der Körper wird abgezogen.")
    PsmCore.VolumeType.MODIFIER ->
        st("Different settings apply inside this region.",
            "In diesem Bereich gelten andere Einstellungen.")
    PsmCore.VolumeType.SUPPORT_BLOCKER ->
        st("No supports here, whatever the automatic says.",
            "Hier setzt der Slicer keine Stützen, auch wenn die Automatik es will.")
    PsmCore.VolumeType.SUPPORT_ENFORCER ->
        st("Supports here, even without an overhang.",
            "Hier setzt er Stützen, auch ohne Überhang.")
    PsmCore.VolumeType.MODEL_PART, PsmCore.VolumeType.UNKNOWN -> ""
}
