package de.psmobile.net

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import de.psmobile.BuildConfig
import de.psmobile.shared.rules.AppSettings
import de.psmobile.slicing.SlicerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Freiwilliger Testbericht nach dem Slicen - Android-Gegenstueck zu
 * `ios/PSMobile/Networking/DiagnosticsReporter.swift`. Geht an denselben
 * Server wie Remote Slicing (docker/remote-slice/server.py, `/diagnostics`),
 * derselbe Host, derselbe Token. Zwei getrennte Schalter (siehe
 * AppSettings.kt im gemeinsamen Modul):
 *
 *   KEY_DIAG_AUTO_UPLOAD  - fuer Testversionen: Geraet, Zeiten UND ein
 *                           Screenshot bei jedem Slice.
 *   KEY_TELEMETRY_ANON    - fuer spaetere Nutzer: dieselben Zahlen,
 *                           ausdruecklich ohne Bild und ohne Projektnamen.
 *
 * Beide sind standardmaessig aus. Ein Fehlschlag beim Senden darf nie
 * auffallen - das hier ist Beiwerk, kein Teil des Slice-Vorgangs.
 */
object DiagnosticsReporter {

    private const val TAG = "DiagnosticsReporter"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Ergebnis(
        val erfolgreich: Boolean,
        val sekunden: Double,
        val dreiecke: Int,
        val weg: String, // "local" oder "remote"
        val fehler: String?,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences("psmobile", Context.MODE_PRIVATE)

    /**
     * Wie nachSlice(), aber fuer einen Selbsttest-Lauf ohne Slice davor
     * oder danach - siehe Selbsttest.kt. Dieselben zwei Schalter,
     * dieselbe "aus heisst aus"-Regel; nur die Felder sind andere (kein
     * "weg"/"dreiecke", dafuer der Bericht direkt statt nachtraeglich
     * von der Platte gelesen).
     */
    fun nachSelbsttest(context: Context, bericht: String, fehlerZahl: Int, warnungZahl: Int) {
        val p = prefs(context)
        val autoTest = p.getBoolean(AppSettings.KEY_DIAG_AUTO_UPLOAD, false)
        val anonym = p.getBoolean(AppSettings.KEY_TELEMETRY_ANON, false)
        if (!autoTest && !anonym) return
        val sendeAnonym = anonym && !autoTest

        scope.launch {
            val eintrag = JSONObject().apply {
                put("app_version", appVersion())
                put("os_version", Build.VERSION.RELEASE)
                put("geraet", geraetekennung())
                put("ram_bytes", ramBytes(context))
                put("erfolgreich", fehlerZahl == 0)
                put("fehler_anzahl", fehlerZahl)
                put("warnung_anzahl", warnungZahl)
                put("weg", "selbsttest")
                put("anonym", sendeAnonym)
            }
            if (!sendeAnonym) {
                eintrag.put("selbsttest", bericht)
            }
            sendenRoh(context, eintrag)
        }
    }

    /** Screenshot-Erzeugung liegt beim Aufrufer (braucht ein Window/View,
     * siehe MainActivity - PixelCopy statt direktem Zugriff, da Android
     * keinen synchronen Weg wie UIGraphicsImageRenderer kennt). */
    fun nachSlice(
        context: Context,
        ergebnis: Ergebnis,
        projektname: String?,
        screenshotPngBase64: String?,
    ) {
        val p = prefs(context)
        val autoTest = p.getBoolean(AppSettings.KEY_DIAG_AUTO_UPLOAD, false)
        val anonym = p.getBoolean(AppSettings.KEY_TELEMETRY_ANON, false)
        if (!autoTest && !anonym) return
        val sendeAnonym = anonym && !autoTest

        scope.launch {
            // Kein Server eingetragen: gar nicht erst den (teuren, mit
            // Screenshot und Protokoll gefuellten) Eintrag zusammenbauen.
            val hostEinstellung = p.getString(SlicerService.KEY_REMOTE_SLICE_HOST, "") ?: ""
            if (RemoteSliceClient.normalizedBaseURL(hostEinstellung) == null) return@launch

            val eintrag = JSONObject().apply {
                put("app_version", appVersion())
                put("os_version", Build.VERSION.RELEASE)
                put("geraet", geraetekennung())
                put("ram_bytes", ramBytes(context))
                put("erfolgreich", ergebnis.erfolgreich)
                put("sekunden", ergebnis.sekunden)
                put("dreiecke", ergebnis.dreiecke)
                put("weg", ergebnis.weg)
                put("anonym", sendeAnonym)
            }
            if (!sendeAnonym) {
                if (!projektname.isNullOrBlank()) eintrag.put("projekt", projektname)
                ergebnis.fehler?.let { eintrag.put("fehler", it) }
                // Screenshot nur im Testmodus, nie bei der anonymen
                // Telemetrie - das ist der ganze Sinn der Trennung.
                if (autoTest && screenshotPngBase64 != null) {
                    eintrag.put("screenshot_png_base64", screenshotPngBase64)
                }
                // Der letzte Selbsttest laeuft nicht bei jedem Slice mit -
                // das waere Minuten pro Schnitt -, aber falls schon einer
                // auf der Platte liegt, kostet das Anhaengen nichts.
                letzterSelbsttest(context)?.let { eintrag.put("selbsttest", it) }
            }
            sendenRoh(context, eintrag)
        }
    }

    /**
     * Hier verschluckte bis vor kurzem ein stilles try/catch jeden
     * Fehlschlag spurlos - ein 401 wegen fehlendem Token sah von aussen
     * genauso aus wie "kein Server eingetragen, gar nicht erst versucht"
     * (dieselbe Falle wie in RemoteSliceClient.swift). Jede Stufe hier
     * einzeln geloggt, damit ein kuenftiger Fehlschlag sofort auffaellt.
     */
    private fun sendenRoh(context: Context, eintrag: JSONObject) {
        val p = prefs(context)
        val hostEinstellung = p.getString(SlicerService.KEY_REMOTE_SLICE_HOST, "") ?: ""
        val basis = RemoteSliceClient.normalizedBaseURL(hostEinstellung)
        if (basis == null) {
            Log.w(TAG, "Diagnose-Upload uebersprungen: keine gueltige Serveradresse (\"$hostEinstellung\")")
            return
        }
        val token = SecretStore.get(context, "remote-slice-token")
        val koerper = eintrag.toString().toByteArray(Charsets.UTF_8)

        val c = (URL(basis + "/diagnostics").openConnection() as HttpURLConnection)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (!token.isNullOrEmpty()) {
            c.setRequestProperty("Authorization", "Bearer $token")
        } else {
            Log.w(TAG, "Diagnose-Upload an $basis ohne Token - Server lehnt das vermutlich ab")
        }
        c.setFixedLengthStreamingMode(koerper.size)
        try {
            c.outputStream.use { it.write(koerper) }
            val code = c.responseCode
            if (code < 200 || code >= 300) {
                val text = (c.errorStream?.bufferedReader()?.use { it.readText() }) ?: "keine Antwortdaten"
                Log.w(TAG, "Diagnose-Upload an $basis: HTTP $code - $text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Diagnose-Upload an $basis fehlgeschlagen", e)
        } finally {
            c.disconnect()
        }
    }

    private fun appVersion(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    private fun ramBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    private fun geraetekennung(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    /**
     * Neuester Bericht unter filesDir/Selbsttest, falls vorhanden - nach
     * Dateiname sortiert, der traegt den Zeitstempel schon im Namen
     * (siehe Selbsttest.kt Berichtsdatei-Benennung).
     */
    private fun letzterSelbsttest(context: Context): String? {
        val ordner = File(context.filesDir, "Selbsttest")
        val neueste = ordner.listFiles { f -> f.extension == "md" }
            ?.maxByOrNull { it.name }
            ?: return null
        return runCatching { neueste.readText(Charsets.UTF_8) }.getOrNull()
    }
}
