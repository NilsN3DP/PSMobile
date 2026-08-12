package de.psmobile.diagnose

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import de.psmobile.BuildConfig
import de.psmobile.core.PsmCore
import de.psmobile.slicing.ResourceInstaller
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prueft die ganze Kette auf diesem Geraet und schreibt einen Bericht.
 *
 * Gegenstueck zu `Selbsttest.swift`. Er gehoert in die App und nicht in
 * einen Testlauf am Rechner: was auf diesem Geraet gilt - Speicher,
 * Dateisystem, Grafiktreiber, mitgelieferte Profile -, weiss nur dieses
 * Geraet. Ein gruener Lauf auf dem Emulator sagt nichts ueber ein
 * Tablet mit 2 GB RAM.
 *
 * Der Bericht wird waehrend des Laufs mitgeschrieben, nicht am Ende.
 * Stuerzt die App mitten in einem Schritt ab, gibt es kein Ende - dann
 * steht der Anfang des Schritts ohne Ergebnis in der Datei, und genau
 * das ist die gesuchte Stelle.
 */
class Selbsttest(private val context: Context) {

    enum class Ausgang(val zeichen: String) {
        OK("✓"),
        FEHLER("✗"),
        UEBERSPRUNGEN("–"),
        ANGABE("·"),
    }

    data class Schritt(
        val name: String,
        val ausgang: Ausgang,
        val detail: String,
        val sekunden: Double,
    )

    private val _schritte = MutableStateFlow<List<Schritt>>(emptyList())
    val schritte: StateFlow<List<Schritt>> = _schritte.asStateFlow()

    private val _aktuell = MutableStateFlow<String?>(null)
    val aktuell: StateFlow<String?> = _aktuell.asStateFlow()

    private val _laeuft = MutableStateFlow(false)
    val laeuft: StateFlow<Boolean> = _laeuft.asStateFlow()

    private val _bericht = MutableStateFlow<File?>(null)
    val bericht: StateFlow<File?> = _bericht.asStateFlow()

    /** Wie viele Koerper der Lasttest hoechstens auftuermt. */
    private val lastKoerperMax = 25

    private lateinit var datei: File

    // --- Der Durchlauf ----------------------------------------------------

    fun durchlauf() {
        if (_laeuft.value) return
        _laeuft.value = true
        _schritte.value = emptyList()

        datei = berichtsdatei()
        anhaengen("# PSMobile Selbsttest")
        anhaengen("")
        anhaengen(umgebung())
        anhaengen("")

        var kern: PsmCore? = null
        var wuerfelId = 0

        try {
            schritt("Gerät und App") { Ausgang.ANGABE to umgebung() }

            schritt("Groß- und Kleinschreibung im Dateisystem") {
                Ausgang.ANGABE to schreibweisePruefen()
            }

            schritt("Kern starten") {
                val res = ResourceInstaller.ensureInstalled(context)
                val ordner = File(context.cacheDir, "psm-selbsttest").also {
                    it.deleteRecursively(); it.mkdirs()
                }
                kern = PsmCore.create(ordner.absolutePath, res.absolutePath)
                Ausgang.OK to ("Kern " + PsmCore.coreVersion())
            }

            schritt("Druckerprofil einrichten") {
                val k = kern ?: fehler("Kein Kern")
                k.loadBundledPresets()
                k.scanPrinterModels()
                k.installPresets(listOf("PrusaResearch:MK4S:0.4"))
                val drucker = k.presetNames(PsmCore.PresetType.PRINTER).size
                val druck = k.presetNames(PsmCore.PresetType.PRINT).size
                val filament = k.presetNames(PsmCore.PresetType.FILAMENT).size
                if (drucker == 0 || druck == 0 || filament == 0) {
                    fehler("Profile leer: $drucker/$druck/$filament")
                }
                Ausgang.OK to "$drucker Drucker · $druck Druckprofile · $filament Filamente"
            }

            // Aus dem Feld gemeldet: nach der Ersteinrichtung stand kein
            // sinnvolles Standardfilament, sondern ein x-beliebiger
            // Fremdhersteller - seit alle 36 Hersteller mitkommen statt
            // nur Prusa. Geprueft wird DIREKT nach der Einrichtung, vor
            // jeder eigenen Wahl; der Schritt weiter unten deckt das
            // nicht ab, weil er selbst schon aktiv waehlt.
            schritt("Standardfilament nach Einrichtung") {
                val k = kern ?: fehler("Kein Kern")
                val aktuell = k.selectedPreset(PsmCore.PresetType.FILAMENT)
                if (aktuell.isBlank()) fehler("Kein Filament vorausgewählt")
                if (!aktuell.contains("Prusament")) {
                    fehler("Vorausgewählt: \"$aktuell\" – kein Prusa-eigenes Filament")
                }
                Ausgang.OK to aktuell
            }

            schritt("Drucker auswählen") {
                val k = kern ?: fehler("Kein Kern")
                val namen = k.presetNames(PsmCore.PresetType.PRINTER)
                val name = namen.firstOrNull { it.contains("MK4S") }
                    ?: namen.firstOrNull()
                    ?: fehler("Keine Druckerprofile in der Liste")
                k.selectPreset(PsmCore.PresetType.PRINTER, name)
                val gewaehlt = k.selectedPreset(PsmCore.PresetType.PRINTER)
                if (gewaehlt != name) fehler("Ausgewählt \"$gewaehlt\" statt \"$name\"")
                Ausgang.OK to name
            }

            schritt("MK4S: Filament wählen") {
                val k = kern ?: fehler("Kein Kern")
                val namen = k.presetNames(PsmCore.PresetType.FILAMENT)
                if (namen.isEmpty()) fehler("Keine Filamentprofile für MK4S sichtbar")
                val ziel = namen.firstOrNull { it.contains("Prusament PLA") } ?: namen[0]
                k.setExtruderFilament(0, ziel)
                val gewaehlt = k.extruderFilament(0)
                if (gewaehlt != ziel) fehler("Gewählt \"$gewaehlt\" statt \"$ziel\"")
                Ausgang.OK to ziel
            }

            schritt("Modell laden") {
                val k = kern ?: fehler("Kein Kern")
                val ids = k.loadModel(Testkoerper.wuerfelDatei(context).absolutePath)
                wuerfelId = ids.firstOrNull() ?: fehler("Kein Objekt entstanden")
                val info = k.objectInfo(wuerfelId)
                Ausgang.OK to "${info?.triangles ?: 0} Dreiecke · " + String.format(
                    Locale.US, "%.0f×%.0f×%.0f mm",
                    info?.sizeMm?.first ?: 0f, info?.sizeMm?.second ?: 0f,
                    info?.sizeMm?.third ?: 0f,
                )
            }

            schritt("Liegt auf dem Bett") {
                val k = kern ?: fehler("Kein Kern")
                val info = k.objectInfo(wuerfelId) ?: fehler("Objekt verschwunden")
                if (info.outsideBed) fehler("Objekt ragt über das Bett hinaus")
                Ausgang.OK to "vollständig innerhalb"
            }

            schritt("Schneiden") {
                val k = kern ?: fehler("Kein Kern")
                val t0 = System.nanoTime()
                k.startSlice(null)
                val stand = k.awaitSlice()
                if (stand != PsmCore.SliceState.DONE) {
                    fehler("Ergebnis: $stand — " + k.lastError())
                }
                val dauer = (System.nanoTime() - t0) / 1_000_000_000.0
                val st = k.sliceStats() ?: fehler("Statistik leer")
                if (st.printTimeSeconds <= 0 || st.filamentGrams <= 0) {
                    fehler("Statistik leer: ${st.printTimeSeconds}s / ${st.filamentGrams}g")
                }
                Ausgang.OK to String.format(
                    Locale.US, "%.1f s · Druckzeit %.0f min · %.1f g",
                    dauer, st.printTimeSeconds / 60, st.filamentGrams,
                )
            }

            schritt("G-Code schreiben") {
                val k = kern ?: fehler("Kein Kern")
                val ziel = File(context.cacheDir, "psm-selbsttest/probe.gcode")
                k.exportGcode(ziel.absolutePath)
                if (!ziel.exists() || ziel.length() < 1024) {
                    fehler("Datei ${if (ziel.exists()) "${ziel.length()} Bytes" else "fehlt"}")
                }
                Ausgang.OK to "${ziel.length() / 1024} KB"
            }

            schritt("Projekt sichern und wieder laden") {
                val k = kern ?: fehler("Kein Kern")
                val ziel = File(context.cacheDir, "psm-selbsttest/probe.3mf")
                k.saveProject(ziel.absolutePath)
                if (!ziel.exists()) fehler("3MF nicht geschrieben")
                val vorher = k.listObjects().size
                k.clearBed()
                k.loadProject(ziel.absolutePath)
                val nachher = k.listObjects().size
                if (nachher != vorher) fehler("$vorher Objekte gesichert, $nachher zurückgelesen")
                wuerfelId = k.listObjects().firstOrNull() ?: wuerfelId
                Ausgang.OK to "${ziel.length() / 1024} KB · $nachher Objekt(e)"
            }

            schritt("Zurücknehmen und Wiederholen") {
                val k = kern ?: fehler("Kein Kern")
                val vorher = k.objectInfo(wuerfelId)?.position?.first ?: 0f
                k.beginHistory("Selbsttest")
                k.setPosition(wuerfelId, vorher + 10f, 0f, 0f)
                k.endHistory()
                k.undo()
                val nachUndo = k.objectInfo(wuerfelId)?.position?.first ?: 0f
                if (kotlin.math.abs(nachUndo - vorher) > 0.01f) {
                    fehler("Nach Rückgängig bei $nachUndo statt $vorher")
                }
                k.redo()
                val nachRedo = k.objectInfo(wuerfelId)?.position?.first ?: 0f
                if (kotlin.math.abs(nachRedo - (vorher + 10f)) > 0.01f) {
                    fehler("Nach Wiederholen bei $nachRedo statt ${vorher + 10f}")
                }
                k.undo()
                Ausgang.OK to "beide Richtungen"
            }

            schritt("Teil anlegen und entfernen") {
                val k = kern ?: fehler("Kein Kern")
                val vorher = k.volumes(wuerfelId).size
                k.addPrimitiveVolume(
                    wuerfelId, PsmCore.VolumeType.NEGATIVE,
                    PsmCore.PrimitiveShape.BOX, 5f, 5f, 5f,
                )
                val mit = k.volumes(wuerfelId).size
                if (mit != vorher + 1) fehler("$vorher → $mit statt ${vorher + 1}")
                k.removeVolume(wuerfelId, mit - 1)
                val ohne = k.volumes(wuerfelId).size
                if (ohne != vorher) fehler("Nach dem Entfernen $ohne statt $vorher")
                Ausgang.OK to "Aussparung angelegt und entfernt"
            }

            schritt("Bemalen") {
                val k = kern ?: fehler("Kein Kern")
                k.paintFacet(wuerfelId, 0, 0, PsmCore.PaintTool.SUPPORT, 1)
                k.clearPaint(wuerfelId, PsmCore.PaintTool.SUPPORT)
                Ausgang.OK to "Support gemalt und gelöscht"
            }

            // Nicht blind vervielfaeltigen: das Geraet hat eine Grenze,
            // die der Emulator nicht kennt. Nach jedem Koerper
            // nachrechnen und aufhoeren, bevor es eng wird - lieber ein
            // Lasttest mit acht Koerpern als eine App, die weggeht.
            schritt("Lasttest") {
                val k = kern ?: fehler("Kein Kern")
                var gesetzt = 1
                while (gesetzt < lastKoerperMax) {
                    if (freierSpeicherMb() < 250) break
                    k.duplicate(wuerfelId)
                    gesetzt++
                }
                k.arrange(2f)
                val t0 = System.nanoTime()
                k.startSlice(null)
                val stand = k.awaitSlice()
                val dauer = (System.nanoTime() - t0) / 1_000_000_000.0
                if (stand != PsmCore.SliceState.DONE) {
                    fehler("$gesetzt Körper: $stand — " + k.lastError())
                }
                Ausgang.OK to String.format(
                    Locale.US, "%d Körper · %.1f s · %d MB frei",
                    gesetzt, dauer, freierSpeicherMb(),
                )
            }
        } catch (t: Throwable) {
            anhaengen("")
            anhaengen("Abbruch: ${t.message}")
        } finally {
            runCatching { kern?.close() }
            anhaengen("")
            anhaengen(zusammenfassung())
            _aktuell.value = null
            _bericht.value = datei
            _laeuft.value = false
        }
    }

    // --- Ein Schritt ------------------------------------------------------

    /**
     * Ausfuehren, messen, melden.
     *
     * Wirft der Block, wird daraus ein Fehlschlag mit dem Grund. Der Name
     * steht VOR dem Lauf in der Datei und nicht danach: was dort ohne
     * Ergebnis steht, ist die Stelle, an der es geknallt hat.
     */
    private inline fun schritt(name: String, block: () -> Pair<Ausgang, String>) {
        _aktuell.value = name
        anhaengen("→ $name")
        val t0 = System.nanoTime()
        var ausgang = Ausgang.OK
        var detail = ""
        try {
            val ergebnis = block()
            ausgang = ergebnis.first
            detail = ergebnis.second
        } catch (t: Throwable) {
            ausgang = Ausgang.FEHLER
            detail = t.message ?: t.toString()
        }
        val dauer = (System.nanoTime() - t0) / 1_000_000_000.0
        anhaengen(String.format(Locale.US, "%s %s (%.1f s) %s",
            ausgang.zeichen, name, dauer, detail))
        _schritte.value = _schritte.value + Schritt(name, ausgang, detail, dauer)
    }

    private fun fehler(text: String): Nothing = throw IllegalStateException(text)

    // --- Bericht ----------------------------------------------------------

    private fun berichtsdatei(): File {
        val ordner = File(context.cacheDir, "selbsttest").also { it.mkdirs() }
        val zeit = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(ordner, "selbsttest-$zeit.md").also { it.writeText("") }
    }

    private fun anhaengen(zeile: String) {
        runCatching { datei.appendText(zeile + "\n") }
    }

    private fun zusammenfassung(): String {
        val alle = _schritte.value
        val fehler = alle.count { it.ausgang == Ausgang.FEHLER }
        val ok = alle.count { it.ausgang == Ausgang.OK }
        return "## Ergebnis\n\n$ok bestanden, $fehler fehlgeschlagen, " +
            "${alle.size} Schritte insgesamt."
    }

    // --- Angaben ----------------------------------------------------------

    private fun umgebung(): String = buildString {
        append("PSMobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ")
        append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ")
        append("${Build.MANUFACTURER} ${Build.MODEL}, ")
        append("${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}, ")
        append("${freierSpeicherMb()} MB frei")
    }

    private fun freierSpeicherMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    /**
     * Unterscheidet das Dateisystem Gross- und Kleinschreibung?
     *
     * Auf Android ja, auf iOS im Normalfall nein - und PrusaSlicers
     * Profildateien verlassen sich darauf, dass "PLA" und "pla" zwei
     * verschiedene Dinge sein duerfen. Die Antwort gehoert in den
     * Bericht, weil sie erklaert, warum ein Profilfehler auf einem
     * Geraet auftritt und auf dem anderen nicht.
     */
    private fun schreibweisePruefen(): String {
        val ordner = File(context.cacheDir, "psm-schreibweise").also {
            it.deleteRecursively(); it.mkdirs()
        }
        return runCatching {
            File(ordner, "Probe.txt").writeText("a")
            val andersHerum = File(ordner, "probe.txt")
            if (andersHerum.exists()) "unterscheidet NICHT (Probe.txt = probe.txt)"
            else "unterscheidet Groß- und Kleinschreibung"
        }.getOrElse { "nicht feststellbar: ${it.message}" }
    }
}
