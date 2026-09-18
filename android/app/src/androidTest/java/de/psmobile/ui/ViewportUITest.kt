package de.psmobile.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Zwilling zu `ios/PSMobileUITests/ViewportUITests.swift`.
 *
 * Prueft die Gesten im 3D-Arbeitsbereich. Ein Bildschirmfoto zeigt ein
 * Bett, nicht ob sich die Kamera drehen laesst - genau hier steckt der
 * Unterschied zwischen "uebersetzt" und "funktioniert". Der Vergleich
 * laeuft deshalb ueber Bilder: dieselbe Ansicht vor und nach der Geste.
 * Aendert sich nichts, hat die Geste nichts bewirkt.
 *
 * Ueber uiautomator statt ueber die Compose-Regel: der Viewport zeichnet
 * dauernd, und die Regel wartet auf Ruhe, die nie eintritt.
 */
@RunWith(AndroidJUnit4::class)
class ViewportUITest {

    private lateinit var geraet: UiDevice

    /**
     * Ohne die Compose-Regel: sie wartet auf Ruhe, und der Viewport
     * zeichnet dauernd. Gestartet wird deshalb ueber den Intent selbst,
     * bedient ueber uiautomator.
     */
    @Before
    fun setUp() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext
        context.startActivity(
            android.content.Intent(context, de.psmobile.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(
                    de.psmobile.Startargumente.EXTRA,
                    arrayOf("-psm-preset-printer", "-psm-start-advanced", "-psm-load-cube"),
                ),
        )
        geraet = UiDevice.getInstance(instr)
        // Erst der Arbeitsbereich, dann die Seite zu, dann der Viewport:
        // auf dem Telefon liegt die Schliessflaeche ueber dem Viewport,
        // und uiautomator meldet einen verdeckten Knoten nicht.
        assertTrue(
            "Der Arbeitsbereich ist nicht erschienen",
            geraet.wait(Until.hasObject(By.res("arbeitsbereich")), 90_000),
        )
        seiteZuAufDemTelefon()
        assertTrue(
            "Der Viewport ist nicht erschienen",
            geraet.wait(Until.hasObject(By.res("viewport")), 30_000),
        )
        // Das Bett wird beim ersten Bild noch aufgebaut.
        Thread.sleep(3_000)
    }

    /**
     * Auf dem Telefon liegt die Seite ueber dem Bett, und ein Zug im
     * Viewport traefe die Schliessflaeche statt der Szene - also vorher
     * zu, ueber denselben Knopf wie ein Finger. Gegenstueck in PsmUiTest.
     */
    private fun seiteZuAufDemTelefon() {
        // Woran man die offene Seite erkennt: an ihrer Liste - nicht am
        // ersten Reiter, der scrollt weg, sobald ein Objekt gewaehlt ist.
        if (geraet.displayWidth / dichte >= 760 || !geraet.hasObject(By.res("seitenleiste"))) return
        tippe("advanced.seite")
        geraet.wait(Until.gone(By.res("seitenleiste")), 5_000)
    }

    @Test
    fun drehenAendertDieAnsicht() {
        val flaeche = geraet.findObject(By.res("viewport")).visibleBounds
        val vorher = bild()

        // Eine freie Ecke: Orbit darf nicht davon abhaengen, ob ein
        // Objekt auf der Platte liegt.
        val x1 = flaeche.left + (flaeche.width() * 0.12).toInt()
        val x2 = flaeche.left + (flaeche.width() * 0.35).toInt()
        val y = flaeche.top + (flaeche.height() * 0.18).toInt()
        geraet.swipe(x1, y, x2, y, 20)
        Thread.sleep(1_500)

        assertNotEquals(
            "Nach dem Drehen sieht die Ansicht unveraendert aus",
            vorher,
            bild(),
        )
    }

    /** Der Ansichtswuerfel: eine Flaeche antippen setzt die Blickrichtung. */
    @Test
    fun wuerfelFlaecheAendertDieAnsicht() {
        assertTrue("Der Ansichtswuerfel fehlt", geraet.wait(Until.hasObject(By.res("wuerfel")), 10_000))
        val vorher = bild()
        geraet.findObject(By.res("wuerfel.oben")).click()
        Thread.sleep(1_500)
        assertNotEquals("Nach dem Tipp auf Oben sieht die Ansicht unveraendert aus", vorher, bild())
    }

    @Test
    fun zweiFingerZoomen() {
        val flaeche = geraet.findObject(By.res("viewport"))
        val vorher = bild()

        flaeche.pinchOpen(0.75f, 30)
        Thread.sleep(1_500)

        assertNotEquals(
            "Nach dem Spreizen sieht die Ansicht unveraendert aus",
            vorher,
            bild(),
        )
    }

    @Test
    fun direkterFingerzugVerschiebtDasObjektOhneMoveGizmo() {
        objektWaehlen()
        tippe("advanced.gizmo.none")
        val flaeche = geraet.findObject(By.res("viewport")).visibleBounds

        val start = punkt(flaeche, 0.5f, 0.5f)
        val ziel = punkt(flaeche, 0.68f, 0.5f)
        geraet.drag(start.x, start.y, ziel.x, ziel.y, 40)
        Thread.sleep(1_000)

        // Der alte Mittelpunkt ist nun leer und hebt die Auswahl auf.
        // Ohne Auswahl gibt es die Objektleiste nicht mehr - also auf
        // Verschwinden oder Ausgrauen pruefen, wie drueben.
        geraet.click(start.x, start.y)
        assertTrue(
            "Der direkte Zug liess das Objekt am alten Ort",
            warte {
                val griff = geraet.findObject(By.res("advanced.gizmo.none"))
                griff == null || !griff.isEnabled
            },
        )

        // Am Ziel liegt das Objekt und kann wieder gewaehlt werden.
        geraet.click(ziel.x, ziel.y)
        assertTrue(
            "Das direkt gezogene Objekt ist am Ziel nicht treffbar",
            warte { geraet.findObject(By.res("advanced.gizmo.none"))?.isEnabled == true },
        )
    }

    @Test
    fun moveGizmoZiehtNurEntlangDerGepicktenAchse() {
        objektWaehlen()
        tippe("advanced.gizmo.move")

        assertTrue(
            "Der Viewport meldet keinen Gizmo-Ursprung",
            geraet.wait(Until.hasObject(By.res("viewport.gizmo.origin")), 10_000),
        )
        assertTrue(
            "Der Viewport meldet keinen X-Achsgriff",
            geraet.wait(Until.hasObject(By.res("viewport.gizmo.axis.0")), 10_000),
        )

        val vorher = mitte("viewport.gizmo.origin")
        val griff = mitte("viewport.gizmo.axis.0")
        val dx = (griff.x - vorher.x).toFloat()
        val dy = (griff.y - vorher.y).toFloat()
        val laenge = maxOf(Math.hypot(dx.toDouble(), dy.toDouble()).toFloat(), 1f)
        val ex = dx / laenge
        val ey = dy / laenge
        // 60 Punkte drueben - hier in Pixel, ueber die Dichte.
        val weite = 60 * dichte
        val ziel = Punkt((griff.x + ex * weite).toInt(), (griff.y + ey * weite).toInt())

        geraet.drag(griff.x, griff.y, ziel.x, ziel.y, 40)

        assertTrue(
            "Der gepickte X-Achsgriff hat das Objekt nicht bewegt",
            warte {
                val jetzt = mitte("viewport.gizmo.origin")
                Math.hypot((jetzt.x - vorher.x).toDouble(), (jetzt.y - vorher.y).toDouble()) > 5 * dichte
            },
        )

        val nachher = mitte("viewport.gizmo.origin")
        val bx = (nachher.x - vorher.x).toFloat()
        val by = (nachher.y - vorher.y).toFloat()
        val entlang = bx * ex + by * ey
        val quer = Math.abs(bx * -ey + by * ex)
        assertTrue("Der X-Achsgriff bewegt nicht in Achsrichtung", entlang > 5 * dichte)
        assertTrue("Der X-Achsgriff laesst unerlaubte Querbewegung zu", quer < 8 * dichte)
    }

    @Test
    fun rotateGizmoLaesstDieObjektflaecheDirektZiehen() {
        pruefeGizmoMitDirektzug("advanced.gizmo.rotate")
    }

    @Test
    fun scaleGizmoLaesstDieObjektflaecheDirektZiehen() {
        pruefeGizmoMitDirektzug("advanced.gizmo.scale")
    }

    @Test
    fun aufFlaecheLegtGezieltDieGetroffeneZweiteInstanz() {
        // Die Punktliste unten beschreibt, wo die zweite Kopie auf dem
        // Tablet liegt. Im schmalen Hochformat-Viewport liegen beide
        // Kopien enger, und ein Punkt trifft die falsche - das Werkzeug
        // selbst prueft der Lauf auf Emulator und iPad.
        org.junit.Assume.assumeTrue("Punktliste fuer Tablet-Geometrie", geraet.displayWidth / dichte >= 760)
        val flaeche = geraet.findObject(By.res("viewport")).visibleBounds
        objektWaehlen()

        blaettereZu("advanced.kopien.mehr")
        tippe("advanced.kopien.mehr")
        assertTrue(
            "Die zweite Instanz wurde nicht angelegt",
            warte { textVon("advanced.kopien.anzahl") == "2" },
        )

        val drehfelder = listOf("X", "Y", "Z").map { "advanced.rotate.$it" }
        blaettereZu(drehfelder[0])
        assertTrue(drehfelder.all { geraet.wait(Until.hasObject(By.res(it)), 5_000) })
        assertTrue(
            "Instanz 0 startet nicht unveraendert",
            drehfelder.all { textVon(it) == "0" },
        )

        blaettereZu("objekt.aufflaeche")
        assertTrue(
            "Das Flaechenwerkzeug ist nicht erreichbar",
            geraet.wait(Until.hasObject(By.res("objekt.aufflaeche")), 10_000),
        )
        tippe("objekt.aufflaeche")
        // Telefon: die Punkte unten treffen sonst die Seite statt des Betts.
        seiteZuAufDemTelefon()

        /*
         * Die zweite Kopie liegt rechts neben der ersten. Mehrere Punkte
         * machen den Test unabhaengig davon, welche sichtbare Dreiecks-
         * haelfte die aktuelle Projektion an dieser Stelle zeigt.
         *
         * Nach einem Treffer nimmt das erste Undo nur das Hinlegen
         * zurueck und beide Kopien bleiben da. Bei einem Fehltipp nimmt
         * es stattdessen das Anlegen der zweiten Kopie zurueck; dann
         * stellt Redo sie fuer den naechsten Punkt wieder her.
         */
        // Mehr Punkte als drueben: im Gesamtlauf lag die zweite Kopie
        // zweimal knapp neben den drei Punkten der Vorlage (12.09.2026).
        val punkte = listOf(
            0.58f to 0.48f, 0.62f to 0.50f, 0.58f to 0.54f,
            0.66f to 0.48f, 0.70f to 0.52f, 0.62f to 0.44f, 0.55f to 0.50f,
        )
        var zweiteGetroffen = false
        for ((fx, fy) in punkte) {
            val p = punkt(flaeche, fx, fy)
            geraet.click(p.x, p.y)
            Thread.sleep(500)
            assertTrue(
                "Der Treffer auf Kopie 1 hat faelschlich Kopie 0 gedreht",
                drehfelder.all { textVon(it) == "0" },
            )

            tippe("advanced.zurueck")
            objektzeileWaehlen()
            if (warte(3_000) { textVon("advanced.kopien.anzahl") == "2" }) {
                zweiteGetroffen = true
                break
            }
            assertTrue(
                "Undo zeigt weder Flaechentreffer noch Fehltipp",
                warte(3_000) { textVon("advanced.kopien.anzahl") == "1" },
            )
            tippe("advanced.wiederholen")
            objektzeileWaehlen()
            assertTrue(
                "Die zweite Kopie liess sich nicht wiederherstellen",
                warte(5_000) { textVon("advanced.kopien.anzahl") == "2" },
            )
        }
        assertTrue("Keiner der rechten Punkte traf die zweite Instanz", zweiteGetroffen)
    }

    /// Wie im Desktop-PrusaSlicer: die Griffe haben Vorrang, die
    /// Objektflaeche selbst laesst sich aber bei jedem Werkzeug ziehen.
    /// Bis zum 17.09.2026 war das Gegenteil verlangt - und im Simple Mode,
    /// der immer ein Werkzeug aktiv hat, drehte ein Zug auf dem Objekt
    /// deshalb nur die Kamera (Nils, iPad). Nicht fuer das Move-Gizmo: dessen
    /// Griffe liegen in der Objektmitte und haben Vorrang (eigener Test oben).
    /// Zwilling: pruefeGizmoMitDirektzug in ViewportUITests.swift.
    private fun pruefeGizmoMitDirektzug(kennung: String) {
        val flaeche = geraet.findObject(By.res("viewport")).visibleBounds
        objektWaehlen()
        tippe(kennung)

        val start = punkt(flaeche, 0.5f, 0.5f)
        val ziel = punkt(flaeche, 0.68f, 0.5f)
        geraet.drag(start.x, start.y, ziel.x, ziel.y, 40)
        Thread.sleep(1_000)

        // Der alte Mittelpunkt ist leer und hebt die Auswahl auf - ohne
        // Auswahl gibt es die Werkzeugknoepfe nicht mehr.
        geraet.click(start.x, start.y)
        assertTrue(
            "$kennung: der Zug auf der Objektflaeche liess das Objekt am alten Ort",
            warte {
                val griff = geraet.findObject(By.res(kennung))
                griff == null || !griff.isEnabled
            },
        )

        // Am Ziel liegt das Objekt und kann wieder gewaehlt werden.
        geraet.click(ziel.x, ziel.y)
        assertTrue(
            "$kennung: das direkt gezogene Objekt ist am Ziel nicht treffbar",
            warte { geraet.findObject(By.res(kennung))?.isEnabled == true },
        )
    }

    private data class Punkt(val x: Int, val y: Int)

    private fun punkt(rahmen: Rect, fx: Float, fy: Float) =
        Punkt((rahmen.left + rahmen.width() * fx).toInt(), (rahmen.top + rahmen.height() * fy).toInt())

    private fun mitte(kennung: String): Punkt {
        val r = geraet.findObject(By.res(kennung))?.visibleBounds
            ?: throw AssertionError("Nicht im Bild: $kennung")
        return Punkt(r.centerX(), r.centerY())
    }

    private val dichte: Float
        get() = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    private fun objektWaehlen() {
        // Ueber die Objektliste auswaehlen, damit die Geste selbst nicht
        // erst Auswahl und Bewegung miteinander vermischt.
        tippe("inspektor.objekte")
        objektzeileWaehlen()
    }

    private fun objektzeileWaehlen() {
        val zeile = geraet.wait(
            Until.findObject(By.res(Pattern.compile("advanced\\.objekt\\..*"))),
            10_000,
        )
        assertTrue("Keine Objektzeile", zeile != null)
        zeile.click()
        geraet.waitForIdle(1_000)
        // Die Zeile rueckt beim Aufklappen des Bereichs noch nach; ein
        // Tipp in die Bewegung hinein trifft daneben (Telefon, 14.09.2026).
        // Woran man die Auswahl erkennt: die Objektleiste erscheint.
        if (!geraet.wait(Until.hasObject(By.res("objektleiste")), 3_000)) {
            geraet.findObject(By.res(Pattern.compile("advanced\\.objekt\\..*")))?.click()
            geraet.wait(Until.hasObject(By.res("objektleiste")), 5_000)
        }
        seiteZuAufDemTelefon()
    }

    private fun tippe(kennung: String) {
        // Telefon: die Reiter liegen in der Seite, und die ist hier zu.
        if (kennung.startsWith("inspektor.") && !geraet.hasObject(By.res(kennung))) {
            geraet.findObject(By.res("advanced.seite"))?.click()
            geraet.waitForIdle(1_000)
        }
        // Die Objektleiste blaettert waagerecht - auf dem Telefon liegt
        // ihr hinterer Teil ausserhalb, und uiautomator meldet den nicht.
        if (inDerObjektleiste(kennung)) leisteBlaettern(kennung)
        val knopf = geraet.wait(Until.findObject(By.res(kennung)), 10_000)
        assertTrue("Nicht gefunden: $kennung", knopf != null)
        knopf.click()
        geraet.waitForIdle(1_000)
    }

    /**
     * Die Objektleiste von Hand blaettern: scrollUntil() von uiautomator
     * bewegte sie auf dem Galaxy S23 FE nicht (die Kennung sitzt auf dem
     * Rahmen, nicht auf der scrollenden Zeile) - ein Wisch ueber ihre
     * Flaeche tut es.
     */
    private fun leisteBlaettern(kennung: String) {
        repeat(3) {
            if (geraet.hasObject(By.res(kennung))) return
            val r = geraet.findObject(By.res("objektleiste"))?.visibleBounds ?: return
            val y = r.centerY()
            geraet.swipe(r.left + (r.width() * 0.9).toInt(), y, r.left + (r.width() * 0.1).toInt(), y, 15)
            geraet.waitForIdle(1_000)
        }
    }

    private fun inDerObjektleiste(kennung: String) =
        kennung.startsWith("advanced.gizmo.") || kennung.startsWith("objekt.")

    /** Die Seitenleiste blaettert - was unter dem Rand liegt, erst hochholen. */
    private fun blaettereZu(kennung: String) {
        if (geraet.hasObject(By.res(kennung))) return
        if (inDerObjektleiste(kennung)) {
            leisteBlaettern(kennung)
            return
        }
        // Telefon: die Seite ist nach objektWaehlen zu - erst wieder auf.
        if (!geraet.hasObject(By.res("seitenleiste"))) {
            geraet.findObject(By.res("advanced.seite"))?.click()
            geraet.wait(Until.hasObject(By.res("seitenleiste")), 5_000)
        }
        val leiste = geraet.findObject(By.res("seitenleiste")) ?: return
        leiste.scrollUntil(Direction.DOWN, Until.hasObject(By.res(kennung)))
    }

    /**
     * Ein uiautomator-Knoten kann zwischen Suchen und Lesen verfallen
     * (StaleObjectException), wenn Compose die Zeile gerade neu setzt -
     * im dritten Gesamtlauf am 12.09. genau einmal. Dann noch einmal
     * suchen; leer heisst hier "gerade nicht lesbar", und die Schleifen
     * darueber fragen ohnehin wiederholt.
     */
    private fun textVon(kennung: String): String {
        repeat(3) {
            try {
                return geraet.findObject(By.res(kennung))?.text.orEmpty()
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(100)
            }
        }
        return ""
    }

    private fun warte(millis: Long = 10_000, bedingung: () -> Boolean): Boolean {
        val ende = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < ende) {
            if (bedingung()) return true
            Thread.sleep(300)
        }
        return bedingung()
    }

    /**
     * Ein Bildschirmfoto als Zeichenkette aus Stichproben.
     *
     * Nicht das ganze Bitmap vergleichen: zwei Aufnahmen derselben Szene
     * unterscheiden sich in einzelnen Pixeln, weil der Viewport weiter
     * zeichnet. Ein Raster aus 16x16 Stichproben ist grob genug, um das
     * zu ueberstehen, und fein genug, um eine gedrehte Kamera zu sehen.
     */
    private fun bild(): String {
        val voll: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val b = StringBuilder()
        for (zeile in 0 until 16) {
            for (spalte in 0 until 16) {
                val x = voll.width * spalte / 16 + voll.width / 32
                val y = voll.height * zeile / 16 + voll.height / 32
                // Auf grobe Stufen runden - Rauschen faellt damit heraus.
                b.append((voll.getPixel(x, y) ushr 4) and 0x0F0F0F).append(',')
            }
        }
        voll.recycle()
        return b.toString()
    }
}
