package de.psmobile.core

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import de.psmobile.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ein teilbares Protokoll - Gegenstueck zu `LogExport.swift`.
 *
 * Auf iOS schreibt `PsmLog` jede Warnung zusaetzlich in eine eigene
 * Datei, weil `OSLogStore` nach einem Absturz nur noch das *neue*
 * Verfahren sieht. Auf Android stellt sich die Frage nicht: der
 * Logcat-Ringpuffer gehoert dem System, nicht dem Prozess, und ueberlebt
 * dessen Tod. Was der abgestuerzte Lauf zuletzt geschrieben hat, steht
 * also noch da - eine eigene Datei mitzuschreiben waere hier doppelte
 * Buchfuehrung.
 *
 * Gefiltert wird nach den Kennungen der App. `psmobile` ist die des
 * Kerns (siehe LOG_TAG in android/jni/psm_jni.cpp), die uebrigen sind
 * die der Kotlin-Seite. Ohne Filter kaeme das halbe Geraeteprotokoll
 * mit, samt fremder Apps - das gehoert niemandem in die Hand, der nur
 * einen Fehlerbericht wollte.
 */
object LogExport {

    /** Wessen Zeilen ins Protokoll gehoeren. */
    private val kennungen = listOf(
        "psmobile",        // der Kern, ueber __android_log_write
        "PsmCore",
        "SlicerService",
        "PsmViewport",
        "PsUi",
        "MainActivity",
        "ResourceInstaller",
    )

    /**
     * Wie viele Zeilen hoechstens. Ein Protokoll ist ein Blick zurueck,
     * kein Archiv - und eine Datei, die niemand mehr verschicken kann,
     * hilft auch nicht.
     */
    private const val MAX_ZEILEN = 4000

    /**
     * Kopf mit App- und Geraeteangaben. Bewusst ohne Seriennummer,
     * Kontodaten oder Netzwerkadressen: nur so ist "anonym senden"
     * zutreffend und nicht bloss behauptet.
     */
    private fun kopf(): String = buildString {
        appendLine("PSMobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}, ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
        appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        appendLine()
    }

    /**
     * Die letzten Protokollzeilen der App.
     *
     * `logcat -d` liefert seit Android 4.1 nur noch, was die eigene App
     * geschrieben hat - genau das ist hier gewollt, und deshalb braucht
     * es auch keine Berechtigung.
     */
    private fun zeilen(): List<String> = runCatching {
        val befehl = mutableListOf("logcat", "-d", "-v", "time")
        // Nur die eigenen Kennungen, alles andere stumm.
        kennungen.forEach { befehl += "$it:V" }
        befehl += "*:S"
        val prozess = ProcessBuilder(befehl).redirectErrorStream(true).start()
        val gelesen = prozess.inputStream.bufferedReader().use { it.readLines() }
        prozess.waitFor()
        gelesen.takeLast(MAX_ZEILEN)
    }.getOrElse { listOf("(Protokoll nicht lesbar: ${it.message})") }

    /** Der Text, wie er in der Datei steht. */
    fun text(): String = kopf() + zeilen().joinToString("\n") + "\n"

    /**
     * Eine teilbare Datei. Liegt im Cache, weil sie ein Auszug ist und
     * kein Besitz - beim naechsten Export wird sie ohnehin neu
     * geschrieben.
     */
    fun datei(context: Context): File {
        val ordner = File(context.cacheDir, "diagnose").also { it.mkdirs() }
        return File(ordner, "psmobile-protokoll.txt").also { it.writeText(text()) }
    }

    /** Die Adresse, unter der andere Apps die Datei lesen duerfen. */
    fun uri(context: Context) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        datei(context),
    )
}

/**
 * Grobe Absturzerkennung ohne eigenes SDK - Gegenstueck zu
 * `CrashHeuristic` auf iOS.
 *
 * Beim Start wird ein Merker gesetzt, beim geordneten Wechsel in den
 * Hintergrund wieder geloescht. Steht er beim naechsten Start noch, ist
 * der vorige Lauf nicht ueber den normalen Weg zu Ende gegangen. Das
 * schliesst ein hartes Wegwischen durch den Nutzer mit ein und nicht nur
 * einen echten Absturz - fuer die Frage "Protokoll ansehen?" macht das
 * keinen Unterschied.
 */
object CrashHeuristic {
    private const val DATEI = "psmobile"
    private const val SCHLUESSEL = "diag.unclean-session"

    private fun prefs(context: Context) =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)

    fun letzteSitzungUnsauberBeendet(context: Context): Boolean =
        prefs(context).getBoolean(SCHLUESSEL, false)

    fun sitzungBeginnt(context: Context) {
        prefs(context).edit().putBoolean(SCHLUESSEL, true).apply()
    }

    fun sitzungSauberBeendet(context: Context) {
        prefs(context).edit().putBoolean(SCHLUESSEL, false).apply()
    }
}
