package de.psmobile.net

import android.util.Log
import de.psmobile.shared.rules.SimpleModeState
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Anbindung an den selbstgehosteten Remote-Slice-Server (Docker, siehe
 * docs/remote-slicing.md) - das Android-Gegenstueck zu
 * `ios/PSMobile/Networking/RemoteSliceClient.swift`, derselbe Server,
 * dasselbe Protokoll.
 *
 * Bewusst `HttpURLConnection` statt einer Zusatzbibliothek - genau wie
 * [PrusaLink], derselbe Grund (siehe dort).
 *
 * Endpunkte:
 *   GET  /health              Erreichbarkeitspruefung
 *   POST /jobs                Projekt (.3mf, roher Koerper) hochladen -> {"id": "..."}
 *   GET  /jobs/{id}           Status/Fortschritt (JSON)
 *   GET  /jobs/{id}/gcode     Ergebnis herunterladen
 */
object RemoteSliceClient {

    private const val TAG = "RemoteSliceClient"

    // 15s war fuer den Health-Check gedacht, traf aber auch Hoch- und
    // Herunterladen: ein groesseres Projekt oder ein groesserer G-Code
    // auf einer normalen Verbindung braucht oft laenger, ganz ohne dass
    // der Server je langsam waere (siehe RemoteSliceClient.swift, gleiche
    // Begruendung dort nach Nils' Timeout-Meldung).
    private const val REQUEST_TIMEOUT_MS = 30_000
    private const val TRANSFER_TIMEOUT_MS = 180_000

    data class JobStats(
        val seconds: Double? = null,
        val layerCount: Int? = null,
        val maxZ: Double? = null,
        val printTimeSeconds: Double? = null,
        val filamentMm: Double? = null,
        val filamentG: Double? = null,
    )

    data class JobState(
        val id: String,
        val status: String,
        val percent: Int? = null,
        val stage: String? = null,
        val error: String? = null,
        val stats: JobStats? = null,
        val objectCount: Int? = null,
        val bedCount: Int? = null,
        val printer: String? = null,
    ) {
        val isDone: Boolean get() = status == "done"
        val isFailed: Boolean get() = status == "failed"
    }

    class RemoteSliceException(message: String) : Exception(message)

    private fun invalidAddress() = RemoteSliceException(
        SimpleModeState.text("Invalid server address.", "Ungültige Serveradresse."))

    private fun serverResponded(code: Int) = RemoteSliceException(
        if (code == 401 || code == 403)
            SimpleModeState.text("Server rejected the access token.",
                "Server hat das Zugangs-Token abgelehnt.")
        else
            SimpleModeState.text("Server responded with $code.", "Server antwortete mit $code."))

    private fun unexpectedResponse() = RemoteSliceException(
        SimpleModeState.text("Unexpected server response.", "Unerwartete Antwort vom Server."))

    /**
     * Aus einer Nutzereingabe wie "192.168.1.50:8420" oder
     * "https://slicer.example.com" eine Basis-URL machen. Ohne Schema
     * wird HTTP angenommen - fuers Heimnetz.
     */
    fun normalizedBaseURL(from: String): String? {
        val trimmed = from.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        return try {
            val url = URL(withScheme)
            if (url.host.isNullOrEmpty()) null
            else "${url.protocol}://${url.authority}"
        } catch (e: Exception) {
            null
        }
    }

    private fun open(baseUrl: String, path: String, method: String, token: String?): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = TRANSFER_TIMEOUT_MS
            if (!token.isNullOrEmpty()) setRequestProperty("Authorization", "Bearer $token")
        }

    /** Erreichbarkeit pruefen, bevor ein ganzes Projekt hochgeladen wird. */
    fun healthCheck(baseUrl: String, token: String?): Boolean = try {
        val c = open(baseUrl, "/health", "GET", token)
        val code = c.responseCode
        c.disconnect()
        if (code != 200) {
            val withoutToken = token.isNullOrEmpty()
            Log.w(TAG, "healthCheck: HTTP $code von $baseUrl" +
                if (withoutToken) " (kein Token gesetzt)" else "")
        }
        code == 200
    } catch (e: Exception) {
        Log.w(TAG, "healthCheck fehlgeschlagen ($baseUrl)", e)
        false
    }

    /** Laedt das Projekt hoch und liefert die Job-Kennung. */
    fun submitJob(projectFile: File, baseUrl: String, token: String?): String {
        val c = open(baseUrl, "/jobs", "POST", token)
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.setFixedLengthStreamingMode(projectFile.length())
        try {
            projectFile.inputStream().use { input -> c.outputStream.use { out -> input.copyTo(out) } }
            val code = c.responseCode
            if (code != 202) {
                val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "submitJob: HTTP $code von $baseUrl - $body")
                throw serverResponded(code)
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).getString("id")
        } catch (e: RemoteSliceException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "submitJob fehlgeschlagen ($baseUrl)", e)
            throw e
        } finally {
            c.disconnect()
        }
    }

    /**
     * Einmaliger Statusabruf - der Aufrufer entscheidet ueber das
     * Poll-Intervall, nicht der Client selbst.
     */
    fun fetchStatus(jobId: String, baseUrl: String, token: String?): JobState {
        val c = open(baseUrl, "/jobs/$jobId", "GET", token)
        try {
            val code = c.responseCode
            if (code != 200) {
                Log.w(TAG, "fetchStatus($jobId): HTTP $code")
                throw serverResponded(code)
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return parseJobState(body)
        } catch (e: RemoteSliceException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchStatus($jobId) fehlgeschlagen", e)
            throw e
        } finally {
            c.disconnect()
        }
    }

    /** Laedt den fertigen G-Code in eine lokale Datei. */
    fun downloadGcode(jobId: String, baseUrl: String, token: String?, to: File) {
        val c = open(baseUrl, "/jobs/$jobId/gcode", "GET", token)
        try {
            val code = c.responseCode
            if (code != 200) {
                Log.w(TAG, "downloadGcode($jobId): HTTP $code")
                throw serverResponded(code)
            }
            to.delete()
            c.inputStream.use { input -> to.outputStream().use { out -> input.copyTo(out) } }
        } catch (e: RemoteSliceException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "downloadGcode($jobId) fehlgeschlagen", e)
            throw e
        } finally {
            c.disconnect()
        }
    }

    private fun parseJobState(body: String): JobState {
        val o = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw unexpectedResponse()
        }
        val stats = o.optJSONObject("stats")?.let {
            JobStats(
                seconds = it.optDoubleOrNull("seconds"),
                layerCount = it.optIntOrNull("layer_count"),
                maxZ = it.optDoubleOrNull("max_z"),
                printTimeSeconds = it.optDoubleOrNull("print_time_seconds"),
                filamentMm = it.optDoubleOrNull("filament_mm"),
                filamentG = it.optDoubleOrNull("filament_g"),
            )
        }
        return JobState(
            id = o.getString("id"),
            status = o.getString("status"),
            percent = o.optIntOrNull("percent"),
            stage = o.optStringOrNull("stage"),
            error = o.optStringOrNull("error"),
            stats = stats,
            objectCount = o.optIntOrNull("object_count"),
            bedCount = o.optIntOrNull("bed_count"),
            printer = o.optStringOrNull("printer"),
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
