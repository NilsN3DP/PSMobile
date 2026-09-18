package de.psmobile.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import de.psmobile.MainActivity
import de.psmobile.Startargumente
import org.junit.After
import org.junit.Rule

/**
 * Die gemeinsame Huelle der Android-Oberflaechentests - Gegenstueck zu
 * dem, was auf iOS `XCUIApplication` mitbringt.
 *
 * Warum es sie gibt: die iOS-Referenz wird von 27 Testdateien
 * abgeklopft, Android hatte eine einzige. Die Uebersetzung war damit
 * zwar zeilennah, aber nur auf einer Seite nachgerechnet - und "gleich"
 * heisst wenig, wenn nur eine Haelfte geprueft wird.
 *
 * Moeglich wird die Uebersetzung dadurch, dass beide Seiten dieselben
 * Kennungen tragen: was drueben `app.buttons["simple.karte.SUPPORTS"]`
 * heisst, heisst hier `tippe("simple.karte.SUPPORTS")`. Steht ein Test
 * hier anders als drueben, ist das ein Befund, kein Stilunterschied.
 *
 * [createEmptyComposeRule] statt `createAndroidComposeRule`: die
 * Aktivitaet muss mit Startargumenten anfangen (Drucker vorgeben, Modus
 * waehlen), und die kommen als Intent-Extra herein. Eine Regel, die
 * selbst startet, laesst dafuer keinen Platz.
 */
abstract class PsmUiTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    /**
     * Startet die App mit denselben Schaltern wie die iOS-Tests.
     *
     * Die vorige Aktivitaet wird zuerst zu Ende gebracht. Ohne das
     * findet die Compose-Regel noch deren Baum: sie sieht *alle*
     * Kompositionen im Prozess, auch die einer sterbenden Aktivitaet.
     * Am 11.09.2026 tippte ein Test dadurch auf den Slice-Knopf des
     * vorigen Falls - mit dessen Wuerfel auf dem Bett - und bekam einen
     * laufenden Schnitt statt der erwarteten Begruendung. Auf iOS
     * stellt sich die Frage nicht: dort ist jeder Start ein eigener
     * Prozess.
     */
    protected fun starte(vararg argumente: String) {
        schliesseLeise()
        // Und warten, bis der alte Baum wirklich weg ist. close() gibt
        // zurueck, sobald die Aktivitaet zerstoert ist; ihre Komposition
        // haengt einen Wimpernschlag laenger im Baum, und genau in
        // diesem Wimpernschlag tippte der Test daneben.
        runCatching {
            compose.waitUntil(timeoutMillis = 10_000) {
                !existiert("simple.arbeitsbereich") && !existiert("arbeitsbereich")
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Das Benachrichtigungsrecht vorab erteilen: die App fragt es vor dem
        // ersten Slice an (MainActivity.benachrichtigungsrechtErfragen), und
        // der Systemdialog deckt die Aktivitaet ab - Compose meldet dann
        // "No compose hierarchies found" (so am 13.09.2026 in sieben Faellen).
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(Startargumente.EXTRA, arrayOf(*argumente))
        scenario = ActivityScenario.launch(intent)
    }

    /**
     * Dreht die Aktivitaet ins Querformat - Gegenstueck zu
     * `XCUIDevice.shared.orientation = .landscapeLeft`. Beim Schliessen
     * wird die Vorgabe wieder freigegeben.
     */
    protected fun querformat() {
        scenario?.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
    }

    @After
    fun schliesseApp() {
        runCatching {
            scenario?.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        schliesseLeise()
    }

    /**
     * Zumachen, ohne daran zu scheitern.
     *
     * `ActivityScenario.close()` wartet auf DESTROYED. Nach Stunden
     * GL-Arbeit braucht der Emulator dafuer manchmal zu lange und meldet
     * "Activity never becomes requested state [DESTROYED]" oder einen
     * haengenden HardwareRenderer - beides beim Aufraeumen, nicht beim
     * Pruefen. Ein Test soll nicht daran durchfallen, dass das Zumachen
     * langsam war.
     */
    private fun schliesseLeise() {
        runCatching { scenario?.close() }
        scenario = null
    }

    /**
     * Wartet, bis die Kennung da ist - Gegenstueck zu
     * `waitForExistence(timeout:)`.
     *
     * Der erste Start entpackt Profile und richtet einen Drucker ein;
     * drueben stehen dafuer 60 Sekunden, hier dieselben.
     */
    protected fun warteAuf(kennung: String, sekunden: Long = 60) {
        if (kennung.startsWith("bed.") && !kennung.startsWith("bed.selector")) oeffneKompakteBettauswahl(kennung)
        else schliesseKompakteBettauswahl(kennung)
        compose.waitUntil(timeoutMillis = sekunden * 1000) { existiert(kennung) }
    }

    /**
     * Auf schmalen Fenstern (Telefon) liegen Bettkarten, Plus, Schloss und
     * Papierkorb nicht in der Leiste, sondern in einem Blatt hinter der
     * Bettkarte `bed.selector.active`. Die Tests sind auf dem Tablet
     * geschrieben; hier geht das Blatt vorher auf, wenn die Kennung
     * fehlt - genau das, was ein Finger auch tut. Galaxy S23 FE, 14.09.2026.
     */
    /** Und wieder zu, sobald etwas ausserhalb des Blatts gebraucht wird - es ist modal. */
    private fun schliesseKompakteBettauswahl(kennung: String) {
        if (existiert(kennung) || !existiert("bed.selector.close")) return
        compose.onNodeWithTag("bed.selector.close", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // Das Blatt faehrt animiert weg - ein Tipp in die Bewegung hinein
        // traefe noch seinen Vorhang.
        compose.waitUntil(5_000) { !existiert("bed.selector.sheet") }
        // Das Fenster des Blatts lebt noch einen Moment nach seinem
        // Inhalt - ein Popup, das jetzt aufgeht, verliert an es den
        // Fokus und schliesst sich gleich wieder (S23 FE, Arrange-Panel).
        Thread.sleep(600)
    }

    private fun oeffneKompakteBettauswahl(kennung: String) {
        if (existiert(kennung) || !existiert("bed.selector.active")) return
        compose.onNodeWithTag("bed.selector.active", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { existiert("bed.selector.sheet") }
    }

    protected fun existiert(kennung: String): Boolean =
        compose.onAllNodesWithTag(kennung, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    protected fun tippe(kennung: String, sekunden: Long = 15) {
        warteAuf(kennung, sekunden)
        // Erst ins Bild holen: ein Knoten in einer waagerechten
        // Bildlaufzeile (Bettleiste, Objektleiste) liegt nach ein paar
        // Eintraegen rechts ausserhalb, und ein performClick dort landet
        // im Leeren - so blieb am 12.09.2026 das zehnte Bett aus. Ohne
        // blaetterbaren Vorfahren wirft performScrollTo, das ist dann
        // nichts.
        runCatching { compose.onNodeWithTag(kennung, useUnmergedTree = true).performScrollTo() }
        compose.onNodeWithTag(kennung, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    /**
     * Blaettert in einer Liste zu einer Kennung.
     *
     * Compose zeichnet in einer LazyColumn nur, was gerade sichtbar ist -
     * was darunter liegt, gibt es fuer einen Test nicht. XCUITest sieht
     * drueben auch Zeilen ausserhalb des Fensters; dieser Unterschied
     * liegt in den Testwerkzeugen, nicht in den Apps.
     */
    protected fun blaettereZu(liste: String, kennung: String) {
        compose.onNodeWithTag(liste, useUnmergedTree = true)
            .performScrollToNode(hasTestTag(kennung))
        compose.waitForIdle()
    }

    /**
     * Dasselbe fuer Kennungen, die zur Laufzeit entstehen -
     * `variante.PrusaResearch:MK4S:0.4` etwa. Drueben steht dafuer
     * `NSPredicate(format: "identifier BEGINSWITH ...")`.
     */
    protected fun mitPraefix(praefix: String) = compose.onAllNodes(
        SemanticsMatcher("Kennung faengt mit \"$praefix\" an") { knoten ->
            knoten.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(praefix) == true
        },
        useUnmergedTree = true,
    )

    protected fun existiertMitPraefix(praefix: String): Boolean =
        mitPraefix(praefix).fetchSemanticsNodes().isNotEmpty()

    protected fun warteAufPraefix(praefix: String, sekunden: Long = 15) {
        compose.waitUntil(timeoutMillis = sekunden * 1000) { existiertMitPraefix(praefix) }
    }

    protected fun tippeErstesMit(praefix: String, sekunden: Long = 15) {
        warteAufPraefix(praefix, sekunden)
        mitPraefix(praefix).onFirst().performClick()
        compose.waitForIdle()
    }

    /**
     * Tippt, falls die Kennung da und bedienbar ist - sonst nicht.
     *
     * Drueben steht dafuer `if reiter.waitForExistence(...),
     * reiter.isEnabled { reiter.tap() }`. Ein Reiter, den es auf dieser
     * Fensterbreite nicht gibt, soll den Test nicht scheitern lassen.
     */
    protected fun tippeFallsDa(kennung: String, sekunden: Long = 5) {
        runCatching { compose.waitUntil(timeoutMillis = sekunden * 1000) { existiert(kennung) } }
        if (!existiert(kennung)) return
        runCatching {
            compose.onNodeWithTag(kennung, useUnmergedTree = true).performClick()
            compose.waitForIdle()
        }
    }

    /**
     * Klappt einen Bereich der Expertenmodus-Seitenleiste auf.
     *
     * Drei Schritte, und jeder einzelne ist schon einmal der Grund
     * gewesen, warum ein Test "nicht erreichbar" meldete, obwohl die App
     * in Ordnung war:
     *
     * 1. Hinblaettern. Bei acht Extrudern liegt OBJECTS unter dem
     *    Fensterrand, und `performClick` auf einem Knoten ausserhalb des
     *    Bildes wirft.
     * 2. Nur tippen, wenn der Bereich noch zu ist - die Zeile ist ein
     *    Aufklapper, kein Umschalter. Ein zweites Tippen macht ihn wieder
     *    zu.
     * 3. Auf den Inhalt warten, nicht auf die Zeile.
     */
    protected fun oeffneInspektorBereich(reiter: String, inhalt: String) {
        if (existiertMitPraefix(inhalt)) return
        // Vielleicht ist der Bereich schon auf und sein Inhalt liegt nur
        // unter dem Rand (Telefon) - dann wuerde das Tippen ihn zumachen.
        runCatching { blaettereZuPraefix(inhalt) }
        if (existiertMitPraefix(inhalt)) return
        warteAuf(reiter, 15)
        blaettereZu("seitenleiste", reiter)
        compose.onNodeWithTag(reiter, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // 4. Auf dem Telefon ist die Seite kurz: der aufgeklappte Inhalt
        //    liegt unter dem Rand und ist in der LazyColumn noch nicht
        //    gebaut - erst hinblaettern, dann warten.
        runCatching { blaettereZuPraefix(inhalt) }
        if (!existiertMitPraefix(inhalt)) {
            runCatching { compose.waitUntil(3_000) { existiertMitPraefix(inhalt) } }
        }
        if (!existiertMitPraefix(inhalt)) {
            // Der Tipp ist nicht angekommen (Telefon: das Bettblatt fuhr
            // gerade weg) - noch einmal, wie ein Finger auch.
            compose.onNodeWithTag(reiter, useUnmergedTree = true).performClick()
            compose.waitForIdle()
            runCatching { blaettereZuPraefix(inhalt) }
        }
        warteAufPraefix(inhalt, 10)
    }

    private fun blaettereZuPraefix(praefix: String) {
        compose.onNodeWithTag("seitenleiste", useUnmergedTree = true).performScrollToNode(
            SemanticsMatcher("Kennung faengt mit \"$praefix\" an") { knoten ->
                knoten.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(praefix) == true
            },
        )
        compose.waitForIdle()
    }

    /**
     * Auf dem Telefon liegt die Expertenmodus-Seite ueber dem Bett, und ein
     * Zug im Viewport traefe die Schliessflaeche statt der Szene. Wer die
     * Szene anfassen will, macht die Seite vorher zu - ueber denselben Knopf
     * wie ein Finger. Auf dem Tablet steht die Seite daneben, da tut das
     * nichts.
     */
    protected fun seiteZuAufDemTelefon() {
        // Woran man die offene Seite erkennt: an ihrer Liste - nicht am
        // ersten Reiter, der scrollt weg, sobald ein Objekt gewaehlt ist.
        if (!telefon() || !existiert("seitenleiste")) return
        tippe("advanced.seite", 5)
        compose.waitUntil(5_000) { !existiert("seitenleiste") }
    }

    /** Das Gegenstueck: die Seite wieder auf, wenn ihr Inhalt gebraucht wird. */
    protected fun seiteAufAufDemTelefon() {
        if (!telefon() || existiert("seitenleiste") || !existiert("advanced.seite")) return
        tippe("advanced.seite", 5)
        compose.waitUntil(5_000) { existiert("seitenleiste") }
    }

    /** Schmales Fenster - dieselbe Grenze wie `schmal` in AdvancedWorkspaceView. */
    protected fun telefon(): Boolean {
        val metrik = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        return metrik.widthPixels / metrik.density < 760
    }

    /**
     * Wartet, bis so viele Modelle auf dem Bett liegen wie erwartet.
     *
     * Ein Test, der nur auf seinen Bildschirm wartet, kann trotzdem auf
     * dem Stand des vorigen Falls landen: der Dienst haelt den Kern
     * ueber das Ende einer Aktivitaet hinaus, und bis seine
     * Zuruecksetzung durch ist, zeigt die Oberflaeche noch das alte
     * Bett. Auf iOS stellt sich die Frage nicht - dort ist jeder Start
     * ein eigener Prozess. Also wartet der Test hier nicht auf ein Bild,
     * sondern auf den Zustand, den er braucht.
     */
    protected fun warteAufModelle(anzahl: Int, sekunden: Long = 30) {
        compose.waitUntil(timeoutMillis = sekunden * 1000) {
            mitPraefix("blatt.zeile.").fetchSemanticsNodes().size == anzahl
        }
    }

    /** Alle Texte auf dem Bildschirm - fuer Zusicherungen ohne Kennung. */
    protected fun alleTexte(): List<String> =
        compose.onAllNodes(SemanticsMatcher("hat Text") { it.config.contains(SemanticsProperties.Text) },
                           useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { knoten ->
                knoten.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
            }

    /**
     * Ob unter einer Kennung ein Text mit diesem Stueck steht.
     *
     * Damit prueft ein Test, ob ein Wert wirklich im Kern angekommen
     * ist: die Karte liest ihren Text aus der Konfiguration, nicht aus
     * dem Bildschirmzustand.
     */
    protected fun zeigtText(kennung: String, teil: String): Boolean =
        compose.onAllNodes(
            hasText(teil, substring = true, ignoreCase = true)
                and hasAnyAncestor(hasTestTag(kennung)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()
}
