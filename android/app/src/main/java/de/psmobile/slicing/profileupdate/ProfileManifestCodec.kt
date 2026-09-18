package de.psmobile.slicing.profileupdate

import java.net.URI
import de.psmobile.shared.rules.SimpleModeState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ProfileManifestCodec {
    private val sha256 = Regex("^[a-f0-9]{64}$")

    fun decode(
        json: String,
        manifestUri: URI,
        allowedHosts: Set<String>,
    ): Result<ProfileManifest> = runCatching {
        requireAllowedHttps(manifestUri, allowedHosts)
        val root = Json.parseToJsonElement(json) as? JsonObject
            ?: error("Profilmanifest muss ein JSON-Objekt sein")
        val version = ProfileVersion.parse(root.string("version"))
            ?: error(SimpleModeState.text("Invalid profile version", "Ungültige Profilversion"))
        val packageUri = URI(root.string("package_url"))
        requireAllowedHttps(packageUri, allowedHosts)
        val digest = root.string("sha256")
        require(sha256.matches(digest)) { SimpleModeState.text("Invalid SHA-256 checksum", "Ungültige SHA-256-Prüfsumme") }
        val minVersion = ProfileVersion.parse(root.string("min_slic3r_version"))
            ?: error(SimpleModeState.text("Invalid minimum core version", "Ungültige minimale Core-Version"))
        val notes = (root["release_notes"] as? JsonArray)
            ?.map { it.jsonPrimitive.content.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        require(notes.isNotEmpty()) { "Release Notes fehlen" }
        ProfileManifest(version, packageUri, digest, minVersion, notes)
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull
            ?: error("Manifestfeld '$key' fehlt")

    private fun requireAllowedHttps(uri: URI, allowedHosts: Set<String>) {
        val host = uri.host?.lowercase() ?: error("URL ohne Host")
        require(uri.scheme.equals("https", ignoreCase = true)) { "Nur HTTPS ist erlaubt" }
        require(host in allowedHosts.map(String::lowercase).toSet()) { "Nicht freigegebener Host" }
    }
}
