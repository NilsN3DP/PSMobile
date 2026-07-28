package de.psmobile.net

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * PrusaLink-Anbindung.
 *
 * Bewusst nativ statt ueber libcurl: libslic3r enthaelt keine einzige
 * curl-Referenz, der Netzwerkcode sitzt beim Desktop nur in der
 * GUI-Schicht. Siehe docs/entscheidungen.md, E-09.
 *
 * Endpunkte aus PrusaLink selbst (buddy-prusalink-local-control):
 *   GET  /api/v1/status              Zustand, dient auch als Verbindungstest
 *   PUT  /api/v1/files/{storage}/{name}   Datei hochladen
 * Beides mit dem Kopf `X-Api-Key`.
 *
 * Absichtlich ohne Fremdbibliothek - HttpURLConnection reicht fuer zwei
 * Aufrufe und spart eine Abhaengigkeit.
 */
object PrusaLink {

    private const val TAG = "PrusaLink"
    private const val TIMEOUT_MS = 15_000

    data class Printer(
        val id: String,
        val name: String,
        val host: String,          // IP oder Hostname, ohne Schema
        val apiKey: String,
        /** Preset-Name in PSMobile, damit Profil und Geraet zusammenfinden. */
        val presetName: String = "",
        val storage: String = "usb",
    ) {
        val baseUrl: String
            get() = if (host.startsWith("http")) host.trimEnd('/')
                    else "http://${host.trimEnd('/')}"
    }

    sealed interface Result {
        data class Ok(val message: String) : Result
        data class Error(val message: String) : Result
    }

    /** Zustand abfragen. Dient zugleich als Test fuer Adresse und Schluessel. */
    fun probe(p: Printer): Result = try {
        val c = open(p, "/api/v1/status", "GET")
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()

        when (code) {
            in 200..299 -> Result.Ok(describe(body))
            401, 403 -> Result.Error("API-Schluessel abgelehnt (HTTP $code)")
            else -> Result.Error("HTTP $code")
        }
    } catch (t: Throwable) {
        Result.Error(t.message ?: "nicht erreichbar")
    }

    /**
     * Datei hochladen.
     *
     * @param printAfter Druck direkt starten. PrusaLink erwartet die
     *                   Kopfzeile im Format `?1` bzw. `?0`, nicht true/false.
     */
    fun upload(p: Printer, file: File, remoteName: String, printAfter: Boolean): Result = try {
        val safe = remoteName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val c = open(p, "/api/v1/files/${p.storage}/$safe", "PUT")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.setRequestProperty("Print-After-Upload", if (printAfter) "?1" else "?0")
        c.setRequestProperty("Overwrite", "?1")
        c.setFixedLengthStreamingMode(file.length())

        file.inputStream().use { input -> c.outputStream.use { out -> input.copyTo(out) } }

        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()

        when (code) {
            in 200..299 -> Result.Ok(if (printAfter) "Gesendet, Druck gestartet" else "Gesendet")
            409 -> Result.Error("Datei existiert bereits oder Drucker beschäftigt")
            401, 403 -> Result.Error("API-Schlüssel abgelehnt (HTTP $code)")
            else -> Result.Error("HTTP $code: ${body.take(200)}")
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Upload fehlgeschlagen", t)
        Result.Error(t.message ?: "Übertragung fehlgeschlagen")
    }

    private fun open(p: Printer, path: String, method: String): HttpURLConnection =
        (URL(p.baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("X-Api-Key", p.apiKey)
            setRequestProperty("Accept", "application/json")
        }

    /** Aus der Statusantwort etwas Lesbares machen. */
    private fun describe(body: String): String = runCatching {
        val o = JSONObject(body)
        val printer = o.optJSONObject("printer")
        val state = printer?.optString("state").orEmpty()
        val nozzle = printer?.optDouble("temp_nozzle", Double.NaN) ?: Double.NaN
        buildString {
            append(state.ifBlank { "verbunden" })
            if (!nozzle.isNaN()) append("  ·  Düse %.0f °C".format(nozzle))
        }
    }.getOrDefault("verbunden")
}
