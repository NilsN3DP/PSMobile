package de.psmobile.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.core.PsmViewport
import de.psmobile.ui.theme.PrusaColors
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/*
 * Der 3D-Arbeitsbereich - Gegenstueck zu `ViewportView.swift`.
 *
 * Auf iOS haelt `PSMGLView` den GL-Kontext und SwiftUI bettet es per
 * `UIViewRepresentable` ein; `updateUIView` uebertraegt bei jeder
 * Neuzeichnung den kompletten Zustand in den Viewport. Hier uebernimmt
 * [SceneView] (GLSurfaceView + Gesten) die Rolle von `PSMGLView`, und
 * dieser Baustein legt dieselbe `updateUIView`-Logik darueber: gleiche
 * Parameter, gleiche Reihenfolge, gleiche Zaehler-Semantik
 * (resetViewKey, viewPresetKey, focusBedKey).
 *
 * Der Gestencode wird nicht dupliziert - das Aequivalent zu
 * `touchGestureAnchor` aus `GestureAnchor.swift` steht bereits als
 * [remainingPointerAnchor] oben in SceneView.kt.
 */

/*
 * Drossel fuer onObjectChanged, wie `meldeAenderung()` in PSMGLView:
 * bei 120 Hz jedes Mal die Objektliste aus dem Kern zu holen waere
 * Verschwendung; zwoelf Meldungen je Sekunde sehen fluessig aus.
 */
/** Rund ein Bild bei 60 Hz - der Takt, in dem iOS die Marker neu projiziert. */
private const val BILDTAKT_MS = 16L

private const val MELDE_ABSTAND_MS = 80L

/*
 * Wie lange auf den GL-Viewport gewartet wird, wenn ein Update vor
 * onSurfaceCreated ankommt. Danach wird das Update aufgegeben - die
 * naechste Neuzeichnung bringt ohnehin ein frisches.
 */
private const val WARTE_SCHRITT_MS = 16L
private const val WARTE_VERSUCHE = 600

/**
 * Was ein Durchlauf von `updateUIView` in den Viewport schreibt.
 *
 * Auf iOS laeuft der Block sofort (`perform`), hier auf dem GL-Thread.
 * Weil der Viewport zu Beginn - vor onSurfaceCreated - noch fehlen
 * kann, wird der Schnappschuss so lange erneut eingereiht, bis er
 * angewendet wurde. Ein neuer Schnappschuss erbt die noch nicht
 * angewendeten Einmal-Ereignisse des alten, sonst ginge ein
 * "Ansicht zuruecksetzen" beim Start verloren.
 */
private class UpdateSchnappschuss(
    val zuruecksetzen: Boolean,
    val blickwinkel: PsmViewport.View?,
    val fokusBett: Int?,
    val vorschauBetreten: Boolean,
) {
    /** Auf dem GL-Thread gesetzt, sobald ein Viewport da war. */
    @Volatile var angewendet = false
}

/** Die Merker aus PSMGLView: letzterResetKey, letzterViewKey usw. */
private class ViewportMerker {
    var letzterResetKey = 0
    var letzterViewKey = 0
    var letzterFokusKey = 0
    var letzterModus = PsmViewport.Mode.EDITOR
    var letzteMeldung = 0L
    /** Zuletzt gemeldete Schichthoehen-Darstellung - wie `letzteSchichtdarstellung` auf iOS. */
    var letzteSchichtdarstellung: Pair<Float, Float>? = null
    /** Der zuletzt eingereihte Schnappschuss, bis er angewendet wurde. */
    var ausstehend: UpdateSchnappschuss? = null
    var versuche = 0
}

/**
 * Der 3D-Viewport mit derselben Schnittstelle wie `ViewportView` auf iOS.
 *
 * @param controller Wer den Viewport von aussen anfassen will (etwa fuer
 *   [SceneController.captureThumbnail]) gibt seinen eigenen mit; sonst
 *   legt der Baustein einen an.
 * @param selectedId Kennung des Hauptobjekts, -1 fuer keins - wie
 *   `Int32` auf iOS.
 * @param invalidateKey Aendert sich, wenn das Modell angefasst wurde.
 *   Ohne diesen Wert zeichnet der Viewport weiter den alten Stand.
 * @param focusBedIndex Welches Bett die Kamera zeigen soll; wirkt,
 *   sobald sich [focusBedKey] aendert.
 * @param bedNamen Namensschilder in der raeumlichen Mehrbett-Darstellung,
 *   nach Bettindex geordnet - leer ausserhalb des Mehrbett-Modus.
 * @param layerRange Sichtbarer Schichtbereich in der Vorschau, null = alles.
 * @param moveRange Werkzeugweg-Bereich innerhalb der sichtbaren Schicht(en).
 * @param resetViewKey Steigt, wenn die Ansicht zurueckgesetzt werden soll.
 * @param viewPresetKey Steigt, wenn [viewPreset] angefahren werden soll -
 *   so laesst sich dieselbe Richtung zweimal hintereinander waehlen.
 * @param onMoveRangeBounds Grenzen des Werkzeugweg-Bereichs, gemeldet nach
 *   jeder Aenderung des Schichtbereichs.
 * @param onObjectChanged Eine Geste hat das Objekt im Kern veraendert.
 * @param onLayerVisualizationChanged (min, max) der aktiven
 *   Schichthoehen-Darstellung, gemeldet bei jeder Aenderung - wie
 *   PSMGLView.onLayerVisualizationChanged.
 */
@Composable
fun ViewportView(
    core: PsmCore?,
    shaderDir: String,
    selectedId: Int,
    selectedIds: List<Int>,
    invalidateKey: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    controller: SceneController? = null,
    inputEnabled: Boolean = true,
    gizmo: PsmViewport.Gizmo = PsmViewport.Gizmo.MOVE,
    paintOptions: PsmCore.PaintOptions? = null,
    viewportMode: PsmViewport.Mode = PsmViewport.Mode.EDITOR,
    multiBedRender: Boolean = false,
    focusBedIndex: Int = 0,
    focusBedKey: Int = 0,
    bedNamen: List<String> = emptyList(),
    layerRange: IntRange? = null,
    moveRange: IntRange? = null,
    previewView: PsmViewport.PreviewView = PsmViewport.PreviewView.FEATURE,
    previewRoles: List<Int> = emptyList(),
    hiddenPreviewRoles: Set<Int> = emptySet(),
    previewExtruders: List<Int> = emptyList(),
    hiddenPreviewExtruders: Set<Int> = emptySet(),
    resetViewKey: Int = 0,
    viewPreset: PsmViewport.View? = null,
    viewPresetKey: Int = 0,
    onPreviewLoaded: ((Int) -> Unit)? = null,
    onMoveRangeBounds: ((IntRange?) -> Unit)? = null,
    onObjectChanged: (() -> Unit)? = null,
    onSurfaceTap: ((PsmViewport.SurfaceHit) -> Unit)? = null,
    onSurfaceStroke: ((PsmViewport.SurfaceHit, PsmViewport.SurfaceHit?) -> Unit)? = null,
    onBlockedInput: (() -> Unit)? = null,
    onLayerVisualizationChanged: ((Pair<Float, Float>?) -> Unit)? = null,
    /**
     * Lage des Ansichtswuerfels: unten links, wo er nichts verdeckt (Nils,
     * 14.09.2026 - oben in der Ecke und rechts mittig waren beide im Weg).
     * [wuerfelHoch] hebt ihn an, wo unten links etwas liegt - im Simple
     * Mode die Leiste mit Import/Zurueck.
     */
    wuerfelHoch: Dp = 0.dp,
) {
    // Wie SceneView: ohne Session gibt es nichts zu zeichnen.
    if (core == null) return

    val eigener = remember { SceneController() }
    val ctrl = controller ?: eigener
    val merker = remember { ViewportMerker() }
    val objektGeaendert by rememberUpdatedState(onObjectChanged)
    val vorschauGeladen by rememberUpdatedState(onPreviewLoaded)
    val bereichsGrenzen by rememberUpdatedState(onMoveRangeBounds)

    /*
     * Auf iOS greift die Spreizgeste das Objekt, sobald das Gizmo auf
     * "scale" steht (`vp.gizmo == .scale`). SceneView entscheidet das
     * ueber controller.scaleTool - hier wird beides gleichgesetzt.
     * Das Zurueckmelden laeuft ueber onScaled, gedrosselt wie
     * `meldeAenderung()` in PSMGLView.
     */
    SideEffect {
        ctrl.scaleTool = gizmo == PsmViewport.Gizmo.SCALE
        ctrl.onScaled = {
            val jetzt = System.currentTimeMillis()
            if (jetzt - merker.letzteMeldung > MELDE_ABSTAND_MS) {
                merker.letzteMeldung = jetzt
                objektGeaendert?.invoke()
            }
        }
    }

    /*
     * Namensschilder der Betten. Auf iOS werden sie bei jedem Bild neu
     * projiziert, weil sie mit Kamera und Bettversatz wandern. Der
     * GL-Thread liefert die Punkte nur per Rueckruf - deshalb im Takt
     * des Bildschirms nachfragen, solange die Mehrbett-Darstellung an
     * ist. Ein Aufruf liest nur Matrizen, das faellt nicht ins Gewicht.
     *
     * Mit delay(), nicht mit withFrameMillis(): wer auf den naechsten
     * Frame wartet, zaehlt fuer den Recomposer als ausstehende Arbeit,
     * und die Compose-Testregel wartet dann bis zum Abbruch auf Ruhe
     * ("pending recompositions"). So blieb ein Fall in
     * MultiBedArrangeUITest liegen, sobald ein zweites Bett im Bild war.
     */
    var schildAnker by remember { mutableStateOf<List<Pair<Float, Float>?>>(emptyList()) }
    val schilderZeigen = multiBedRender && bedNamen.size > 1
    LaunchedEffect(schilderZeigen, bedNamen.size, ctrl) {
        if (!schilderZeigen) {
            schildAnker = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            delay(BILDTAKT_MS)
            ctrl.bedLabelAnchors(bedNamen.size) { schildAnker = it }
        }
    }

    /*
     * Die Malmarkierung. GL-Inhalt taucht nicht von selbst im Baum der
     * Bedienhilfen auf; iOS legt dafuer einen durchsichtigen UIView
     * "viewport.malmarkierung" ueber den Viewport, solange Pinselzeiger
     * oder Markierung im Bild sind. Hier dasselbe als Box ohne
     * Eingabe - im Takt des Bildschirms nachgefragt, solange gemalt wird.
     */
    var malmarkierung by remember { mutableStateOf<PsmViewport.PaintVisualization?>(null) }
    val malenAktiv = paintOptions?.tool != null
    LaunchedEffect(malenAktiv, ctrl) {
        if (!malenAktiv) {
            malmarkierung = null
            return@LaunchedEffect
        }
        while (true) {
            delay(BILDTAKT_MS)
            ctrl.activePaintVisualization { malmarkierung = it }
        }
    }

    /*
     * Die Gizmo-Marker. iOS legt fuer Ursprung und drei Achsgriffe je
     * einen durchsichtigen UIView an ("viewport.gizmo.origin",
     * "viewport.gizmo.axis.0..2"), damit VoiceOver und XCUITest die
     * echten, vom Viewport projizierten Positionen bekommen - nur im
     * Editor, mit Auswahl und Verschiebe-Gizmo. Hier dasselbe.
     */
    var gizmoAchsen by remember { mutableStateOf<List<FloatArray?>>(emptyList()) }
    val gizmoMarkerZeigen =
        viewportMode == PsmViewport.Mode.EDITOR && selectedId >= 0 && gizmo == PsmViewport.Gizmo.MOVE
    LaunchedEffect(gizmoMarkerZeigen, ctrl) {
        if (!gizmoMarkerZeigen) {
            gizmoAchsen = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            delay(BILDTAKT_MS)
            ctrl.gizmoScreenAxes { neu ->
                // Nur bei Aenderung schreiben: FloatArray vergleicht sich
                // nach Identitaet, und jeder Schreibzugriff waere sonst
                // eine Neuzusammensetzung pro Bild.
                if (!achsenGleich(gizmoAchsen, neu)) gizmoAchsen = neu
            }
        }
    }

    /*
     * Der Ansichtswuerfel dreht sich mit der Kamera: Blickrichtung im
     * Bildtakt abfragen, nur bei Aenderung schreiben (wie die Gizmo-Achsen).
     * Drueben erledigt das PSMGLView.zeichne() im selben Zug.
     */
    var kameraWinkel by remember { mutableStateOf(Pair(-0.6f, 0.55f)) }
    LaunchedEffect(ctrl) {
        while (true) {
            delay(BILDTAKT_MS)
            ctrl.cameraAngles { neu -> if (neu != null && neu != kameraWinkel) kameraWinkel = neu }
        }
    }

    /*
     * Die Schichthoehen-Darstellung. PSMGLView.zeichne() fragt nach jedem
     * Bild `vp.activeLayerVisualization` ab und meldet nur Aenderungen
     * (`letzteSchichtdarstellung`). Hier im selben Takt, solange der
     * Editor zu sehen ist - in der Vorschau gibt es die Darstellung nicht.
     */
    val schichtdarstellungGeaendert by rememberUpdatedState(onLayerVisualizationChanged)
    LaunchedEffect(viewportMode, ctrl) {
        if (viewportMode != PsmViewport.Mode.EDITOR) {
            if (merker.letzteSchichtdarstellung != null) {
                merker.letzteSchichtdarstellung = null
                schichtdarstellungGeaendert?.invoke(null)
            }
            return@LaunchedEffect
        }
        while (true) {
            delay(BILDTAKT_MS)
            ctrl.activeLayerVisualization { neu ->
                if (neu != merker.letzteSchichtdarstellung) {
                    merker.letzteSchichtdarstellung = neu
                    schichtdarstellungGeaendert?.invoke(neu)
                }
            }
        }
    }

    Box(modifier) {
        SceneView(
            core = core,
            shaderDir = shaderDir,
            selectedId = selectedId.takeIf { it >= 0 },
            selectedIds = selectedIds.toSet(),
            onSelect = onSelect,
            onSurfaceTap = onSurfaceTap,
            onSurfaceStroke = onSurfaceStroke,
            invalidateKey = invalidateKey,
            controller = ctrl,
            inputEnabled = inputEnabled,
            onBlockedInput = onBlockedInput,
            modifier = Modifier.fillMaxSize(),
        )
        if (schilderZeigen) {
            BettSchilder(
                namen = bedNamen,
                anker = schildAnker,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (gizmoMarkerZeigen && gizmoAchsen.any { it != null }) {
            GizmoMarker(achsen = gizmoAchsen, modifier = Modifier.fillMaxSize())
        }
        if (inputEnabled) {
            Ansichtswuerfel(
                yaw = kameraWinkel.first,
                pitch = kameraWinkel.second,
                onAnsicht = { ctrl.setView(it) },
                onOrbit = { dx, dy -> ctrl.orbit(dx, dy) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = LocalPsScale.current.pt(6), bottom = LocalPsScale.current.pt(6) + wuerfelHoch),
            )
        }
        malmarkierung?.let { info ->
            val modus = when (info.mode) {
                PsmCore.PaintMode.BRUSH -> "Pinsel"
                PsmCore.PaintMode.SMART_FILL -> "Smart Fill"
                PsmCore.PaintMode.BUCKET_FILL -> "Bucket Fill"
            }
            val form = if (info.shape == PsmCore.PaintShape.CIRCLE) "Kreis" else "Kugel"
            val wert = "$modus, $form, ${info.radiusMm.roundToInt()} mm, " +
                "${info.annotationFacets} Facetten"
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics {
                        testTag = "viewport.malmarkierung"
                        contentDescription = "Bemalung im 3D-Viewport"
                        stateDescription = wert
                    },
            )
        }
    }

    /*
     * Das Gegenstueck zu `updateUIView`: laeuft nach jeder erfolgreichen
     * Neuzusammensetzung - also genau dann, wenn sich einer der
     * Parameter geaendert hat, invalidateKey eingeschlossen.
     */
    SideEffect {
        @Suppress("UNUSED_EXPRESSION") invalidateKey

        val zuruecksetzen = merker.letzterResetKey != resetViewKey
        merker.letzterResetKey = resetViewKey
        val blickwinkel = if (merker.letzterViewKey != viewPresetKey) viewPreset else null
        merker.letzterViewKey = viewPresetKey
        val fokus = if (merker.letzterFokusKey != focusBedKey) focusBedIndex else null
        merker.letzterFokusKey = focusBedKey
        val vorschauBetreten =
            viewportMode == PsmViewport.Mode.PREVIEW &&
                merker.letzterModus != PsmViewport.Mode.PREVIEW
        merker.letzterModus = viewportMode

        // Noch nicht angewendete Einmal-Ereignisse des Vorgaengers erben.
        val alt = merker.ausstehend?.takeIf { !it.angewendet }
        val schnappschuss = UpdateSchnappschuss(
            zuruecksetzen = zuruecksetzen || (alt?.zuruecksetzen ?: false),
            blickwinkel = blickwinkel ?: alt?.blickwinkel,
            fokusBett = fokus ?: alt?.fokusBett,
            vorschauBetreten = vorschauBetreten || (alt?.vorschauBetreten ?: false),
        )
        merker.ausstehend = schnappschuss
        merker.versuche = 0

        val anwenden: (PsmViewport, GLSurfaceView) -> Unit = { vp, v ->
            vp.setSelections(selectedIds, selectedId.takeIf { it >= 0 })
            // tool == null schaltet ab - wie setPaintOptions(nil) auf iOS.
            vp.setPaintOptions(paintOptions ?: PsmCore.PaintOptions())
            if (vp.currentGizmo() != gizmo) vp.setGizmo(gizmo)
            // Der Kern verwirft einen gleichlautenden Wert selbst.
            vp.setMultiBedRender(multiBedRender)
            schnappschuss.fokusBett?.let { vp.focusBed(it) }
            // Nicht nur beim Uebergang laden: der Wechsel Editor -> Preview
            // kann verpasst werden, wenn diese Ansicht neu aufgebaut wird,
            // waehrend viewportMode schon PREVIEW ist. loadPreview ist bei
            // unveraendertem Ergebnis ein billiges Nein.
            if (viewportMode == PsmViewport.Mode.PREVIEW) {
                val erfolg = vp.loadPreview()
                if (schnappschuss.vorschauBetreten) {
                    val schichten = if (erfolg) vp.layerCount() else 0
                    v.post { vorschauGeladen?.invoke(schichten) }
                }
            }
            if (vp.mode != viewportMode) vp.mode = viewportMode
            if (viewportMode == PsmViewport.Mode.PREVIEW) {
                vp.setPreviewView(previewView)
                for (role in previewRoles) {
                    vp.setRoleVisible(role, role !in hiddenPreviewRoles)
                }
                for (extruder in previewExtruders) {
                    vp.setExtruderVisible(extruder, extruder !in hiddenPreviewExtruders)
                }
            }
            layerRange?.let { bereich ->
                vp.setLayerRange(bereich.first, bereich.last)
                val grenzen = runCatching { vp.moveRangeBounds() }.getOrNull()
                v.post { bereichsGrenzen?.invoke(grenzen) }
                moveRange?.let { vp.setMoveRange(it.first, it.last) }
            }
            if (schnappschuss.zuruecksetzen) vp.resetView()
            schnappschuss.blickwinkel?.let { vp.setView(it) }
            vp.invalidate()
        }

        ctrl.perform(merker, schnappschuss, anwenden)
    }

}

/**
 * Reiht einen Update-Schnappschuss auf dem GL-Thread ein - das
 * Gegenstueck zu `PSMGLView.perform`. Fehlt der Viewport noch, wird es
 * spaeter erneut versucht, solange der Schnappschuss der juengste ist.
 */
private fun SceneController.perform(
    merker: ViewportMerker,
    schnappschuss: UpdateSchnappschuss,
    anwenden: (PsmViewport, GLSurfaceView) -> Unit,
) {
    val v = view ?: return
    v.queueEvent {
        val vp = holder?.viewport
        if (vp != null) {
            schnappschuss.angewendet = true
            anwenden(vp, v)
            v.post { if (merker.ausstehend === schnappschuss) merker.ausstehend = null }
        } else {
            v.postDelayed({
                // Nur der juengste Schnappschuss darf nachlegen; ein
                // aelterer hat seine Einmal-Ereignisse schon vererbt.
                if (merker.ausstehend === schnappschuss &&
                    merker.versuche++ < WARTE_VERSUCHE
                ) perform(merker, schnappschuss, anwenden)
            }, WARTE_SCHRITT_MS)
        }
    }
    v.requestRender()
}

/**
 * Ein Schild je Bett an der vom Kern projizierten Ecke der Druckflaeche -
 * dieselbe Optik wie in `PSMGLView.aktualisiereBettSchilder`: kleine
 * weisse Halbfettschrift auf dunklem, rund geschliffenem Grund.
 *
 * Die Anker kommen in Bildpunkten des GL-Puffers, und der deckt sich mit
 * dem Pixelraster dieser Flaeche - deshalb legt [Layout] direkt in Pixeln
 * ab statt ueber dp zu gehen. iOS teilt an der Stelle durch
 * contentScaleFactor, weil UIKit in Punkten misst; Compose misst hier in
 * Pixeln, also entfaellt der Schritt. Wie auf iOS sitzt der Anker am
 * linken Rand, vertikal mittig.
 */
@Composable
private fun BettSchilder(
    namen: List<String>,
    anker: List<Pair<Float, Float>?>,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            namen.forEach { name ->
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        },
    ) { messbare, vorgaben ->
        val frei = vorgaben.copy(minWidth = 0, minHeight = 0)
        val platzierbare = messbare.map { it.measure(frei) }
        layout(vorgaben.maxWidth, vorgaben.maxHeight) {
            platzierbare.forEachIndexed { position, schild ->
                val punkt = anker.getOrNull(position) ?: return@forEachIndexed
                schild.place(
                    x = punkt.first.roundToInt(),
                    y = (punkt.second - schild.height / 2f).roundToInt(),
                )
            }
        }
    }
}

private fun achsenGleich(a: List<FloatArray?>, b: List<FloatArray?>): Boolean =
    a.size == b.size && a.indices.all { i ->
        val x = a[i]; val y = b[i]
        (x == null && y == null) || (x != null && y != null && x.contentEquals(y))
    }

/**
 * Unsichtbare Marker fuer Ursprung und Achsgriffe des Verschiebe-Gizmos -
 * Gegenstueck zu `gizmoMarkers` in ViewportView.swift. Sie nehmen keine
 * Beruehrung entgegen, sie stehen nur im Baum der Bedienhilfen, damit
 * ein Test weiss, wo der Viewport die Griffe hingezeichnet hat.
 */
@Composable
private fun GizmoMarker(
    achsen: List<FloatArray?>,
    modifier: Modifier = Modifier,
) {
    val punktgroesse = 12.dp
    val ursprung = achsen.firstOrNull { it != null }
    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier.size(punktgroesse).semantics {
                    testTag = "viewport.gizmo.origin"
                    contentDescription = "Ursprung des Verschiebewerkzeugs"
                },
            )
            // Nur sichtbare Achsen bekommen einen Marker - drueben sind
            // die anderen isHidden, hier gibt es sie gar nicht erst.
            listOf("X-Achse", "Y-Achse", "Z-Achse").forEachIndexed { i, name ->
                if (achsen.getOrNull(i) == null) return@forEachIndexed
                Box(
                    Modifier.size(punktgroesse).semantics {
                        testTag = "viewport.gizmo.axis.$i"
                        contentDescription = name
                    },
                )
            }
        },
    ) { messbare, vorgaben ->
        val frei = vorgaben.copy(minWidth = 0, minHeight = 0)
        val platzierbare = messbare.map { it.measure(frei) }
        layout(vorgaben.maxWidth, vorgaben.maxHeight) {
            // Ursprung: das "von" der ersten sichtbaren Achse.
            ursprung?.let { u ->
                val m = platzierbare[0]
                m.place((u[0] - m.width / 2f).roundToInt(), (u[1] - m.height / 2f).roundToInt())
            }
            var naechster = 1
            for (i in 0 until 3) {
                val a = achsen.getOrNull(i) ?: continue
                val m = platzierbare[naechster++]
                m.place((a[2] - m.width / 2f).roundToInt(), (a[3] - m.height / 2f).roundToInt())
            }
        }
    }
}

/**
 * Erklaert die vom C++-Viewport gezeichnete Schichthoehenfarbe -
 * Gegenstueck zu `LayerProfileViewportLegend` auf iOS.
 *
 * Bewusst nur eine Legende, keine Compose-Ersatzzeichnung des Modells.
 * Die eigentliche Z-Farbe entsteht im PrusaSlicer-Shader; hier bleiben
 * lediglich die beiden gespeicherten Grenzwerte les- und fuer TalkBack
 * pruefbar.
 */
@Composable
fun LayerProfileViewportLegend(
    minHeight: Double,
    maxHeight: Double,
    modifier: Modifier = Modifier,
) {
    val ps = LocalPsScale.current
    val fein = st("Fine", "Fein") + " " + mm(minHeight)
    val grob = st("Coarse", "Grob") + " " + mm(maxHeight)
    val ecke = RoundedCornerShape(ps.pt(5))

    Row(
        horizontalArrangement = Arrangement.spacedBy(ps.pt(7)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = ps.touch(44))
            .clip(ecke)
            .background(PrusaColors.Panel.copy(alpha = 0.94f))
            .border(1.dp, PrusaColors.Orange.copy(alpha = 0.65f), ecke)
            .padding(horizontal = ps.pt(10))
            .clearAndSetSemantics {
                contentDescription = st(
                    "Layer height visualization",
                    "Schichthöhen-Visualisierung",
                )
                stateDescription = st(
                    "Fine ${mm(minHeight)}, coarse ${mm(maxHeight)}",
                    "Fein ${mm(minHeight)}, grob ${mm(maxHeight)}",
                )
                testTag = "viewport.schichthoehen"
            },
    ) {
        SfSymbol(
            "square.3.layers.3d",
            tint = PrusaColors.Orange,
            modifier = Modifier.size(ps.pt(14)),
        )
        Text(
            fein,
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.TextPrimary,
        )
        SfSymbol(
            "arrow.left.and.right",
            tint = PrusaColors.TextMuted,
            modifier = Modifier.size(ps.pt(14)),
        )
        Text(
            grob,
            fontSize = ps.font(11),
            fontWeight = FontWeight.SemiBold,
            color = PrusaColors.TextPrimary,
        )
    }
}

/** `String(format: "%.2f mm")` - mit Punkt als Dezimaltrenner wie auf iOS. */
private fun mm(value: Double): String = String.format(Locale.ROOT, "%.2f mm", value)
