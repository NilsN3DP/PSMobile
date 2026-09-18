package de.psmobile.net

import android.util.Log
import de.psmobile.shared.rules.SimpleModeState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * OctoPrint-Anbindung - Gegenstueck zu
 * `ios/PSMobile/Networking/OctoPrintClient.swift`.
 *
 * Protokoll aus external/PrusaSlicer/src/slic3r/Utils/OctoPrint.cpp
 * abgelesen:
 *   - Test:      GET  api/version         mit X-Api-Key
 *   - Hochladen: POST api/files/local     multipart/form-data:
 *                "print"=true/false, "path"=<Zielordner>, "file"=<Datei>
 *
 * Anders als PrusaLink (PUT + optional Digest-Auth) kennt OctoPrint nur
 * den API-Schluessel - einfacher, aber ein eigener Client statt eines
 * Zweigs in [PrusaLink], weil Methode (POST statt PUT) und Kodierung
 * (multipart statt octet-stream) sich unterscheiden, nicht nur die
 * Kopfzeile.
 */
object OctoPrintClient {

    private const val TAG = "OctoPrintClient"
    private const val TIMEOUT_MS = 15_000

    sealed interface Result {
        data class Ok(val message: String) : Result
        data class Error(val message: String) : Result
    }

    private fun baseUrl(p: PrusaLink.Printer): String {
        val h = p.host.trim()
        val mitSchema = if (h.contains("://")) h
        else if (p.allowInsecureHttp) "http://$h" else "https://$h"
        return if (mitSchema.endsWith("/")) mitSchema.dropLast(1) else mitSchema
    }

    fun probe(p: PrusaLink.Printer, apiKey: String): Result {
        val url = runCatching { URL(baseUrl(p) + "/api/version") }.getOrNull()
            ?: return Result.Error(st("Invalid address.", "Ungültige Adresse."))
        return try {
            val c = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("X-Api-Key", apiKey)
            }
            try {
                val code = c.responseCode
                if (code == 401 || code == 403) {
                    return Result.Error(st("API key rejected.", "API-Schlüssel abgelehnt."))
                }
                if (code != 200) {
                    return Result.Error(st("Server answered with", "Server antwortete mit") + " $code")
                }
                val text = c.inputStream.bufferedReader().use { it.readText() }
                Result.Ok(if (text.length > 120) text.take(120) + "…" else text)
            } finally {
                c.disconnect()
            }
        } catch (t: Throwable) {
            // Wie PrusaLink: eine uebersetzte Zeile statt "Failed to connect to
            // /192.168.1.77:443" (S23 FE, 16.09.2026); das Detail steht im Protokoll.
            Log.w(TAG, "probe fehlgeschlagen", t)
            Result.Error(st("Printer not reachable", "Drucker nicht erreichbar"))
        }
    }

    fun upload(
        p: PrusaLink.Printer,
        apiKey: String,
        datei: File,
        name: String,
        printAfter: Boolean,
    ): Result {
        val url = runCatching { URL(baseUrl(p) + "/api/files/local") }.getOrNull()
            ?: return Result.Error(st("Invalid address.", "Ungültige Adresse."))
        // Ganz im Speicher aufgebaut, anders als PrusaLink.put(), das
        // direkt von der Platte streamt. OctoPrints multipart-Rahmen um
        // die Datei herum macht reines Datei-Streaming unbequemer als
        // beim einfachen PUT - fuer v1 hingenommen, bei sehr grossen
        // G-Codes ein spaeterer Verbesserungspunkt.
        val inhalt = runCatching { datei.readBytes() }.getOrNull()
            ?: return Result.Error(st("Could not read the file.", "Datei ließ sich nicht lesen."))

        val grenze = "psmobile-" + UUID.randomUUID().toString()
        val koerper = java.io.ByteArrayOutputStream()
        fun schreibe(s: String) = koerper.write(s.toByteArray(Charsets.UTF_8))
        fun feld(feldName: String, wert: String) {
            schreibe("--$grenze\r\n")
            schreibe("Content-Disposition: form-data; name=\"$feldName\"\r\n\r\n")
            schreibe(wert)
            schreibe("\r\n")
        }
        feld("print", if (printAfter) "true" else "false")
        feld("path", "")
        schreibe("--$grenze\r\n")
        schreibe("Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n")
        schreibe("Content-Type: application/octet-stream\r\n\r\n")
        koerper.write(inhalt)
        schreibe("\r\n--$grenze--\r\n")
        val bytes = koerper.toByteArray()

        return try {
            val c = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("X-Api-Key", apiKey)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$grenze")
                setFixedLengthStreamingMode(bytes.size.toLong())
            }
            try {
                c.outputStream.use { it.write(bytes) }
                val code = c.responseCode
                if (code == 401 || code == 403) {
                    return Result.Error(st("API key rejected.", "API-Schlüssel abgelehnt."))
                }
                if (code != 200 && code != 201) {
                    val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    return Result.Error(
                        st("Server answered with", "Server antwortete mit") + " $code: " + body.take(200),
                    )
                }
                Result.Ok(
                    if (printAfter) st("Uploaded, printing starts.", "Übertragen, Druck startet.")
                    else st("Uploaded.", "Übertragen."),
                )
            } finally {
                c.disconnect()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "upload fehlgeschlagen", t)
            Result.Error(st("Transfer failed", "Übertragung fehlgeschlagen"))
        }
    }

    private fun st(english: String, german: String): String =
        SimpleModeState.text(english, german)
}
