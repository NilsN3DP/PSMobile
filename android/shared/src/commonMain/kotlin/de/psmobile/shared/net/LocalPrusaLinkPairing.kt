package de.psmobile.shared.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

enum class NozzleMaterial { BRASS, HARDENED, UNKNOWN }

data class LocalNozzle(val diameter: Double, val material: NozzleMaterial)

data class LocalPrinterIdentity(
    val id: String,
    val model: String,
    val hosts: List<String>,
) {
    companion object {
        fun merge(existing: LocalPrinterIdentity, host: String): LocalPrinterIdentity =
            existing.copy(hosts = (existing.hosts + host).distinct())
    }
}

data class LocalPrusaLinkQrPayload(
    val type: String,
    val version: Int,
    val model: String,
    val host: String,
    val port: Int,
    val transport: String,
    val pairingToken: String,
    val capabilities: Set<String>,
    val nozzle: LocalNozzle,
    val expiresAtEpochSeconds: Long? = null,
)

data class LocalPrusaLinkCredentials(
    val username: String,
    val password: String,
    val model: String,
    val nozzleDiameter: Double,
    val nozzleHardened: Boolean,
    val host: String,
    val port: Int,
)

sealed interface LocalPairingExchangeResult {
    data class Success(val credentials: LocalPrusaLinkCredentials) : LocalPairingExchangeResult
    data class Rejected(val unauthorized: Boolean) : LocalPairingExchangeResult
    data object Malformed : LocalPairingExchangeResult
}

object LocalPrusaLinkCapabilities {
    const val STATUS = "status"
    const val FILES = "files"
    const val UPLOAD = "upload"
    const val PAUSE = "pause"

    fun supports(capabilities: Set<String>, capability: String): Boolean =
        capability.lowercase() in capabilities.map(String::lowercase).toSet()
}

sealed interface LocalPairingValidation {
    data class Valid(val payload: LocalPrusaLinkQrPayload, val endpoint: String) : LocalPairingValidation
    data class Invalid(val reason: Reason) : LocalPairingValidation

    enum class Reason {
        MALFORMED,
        WRONG_TYPE,
        UNSUPPORTED_VERSION,
        UNSUPPORTED_TRANSPORT,
        INVALID_MODEL,
        NON_LOCAL_HOST,
        INVALID_PORT,
        MISSING_TOKEN,
        EXPIRED,
    }
}

object LocalPrusaLinkPairing {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String, nowEpochSeconds: Long): LocalPairingValidation = try {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val nozzle = root["nozzle"]?.jsonObject ?: return LocalPairingValidation.Invalid(LocalPairingValidation.Reason.MALFORMED)
        val payload = LocalPrusaLinkQrPayload(
            type = root.string("type"),
            version = root.int("version"),
            model = root.string("model"),
            host = root.string("host"),
            port = root.int("port"),
            transport = root.string("transport"),
            pairingToken = root.string("pairing_token"),
            capabilities = root["capabilities"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty().toSet(),
            nozzle = LocalNozzle(
                diameter = nozzle["diameter"]?.jsonPrimitive?.doubleOrNull
                    ?: return LocalPairingValidation.Invalid(LocalPairingValidation.Reason.MALFORMED),
                // Die CFW-Gegenstelle (build_prusalink_payload in
                // local_prusalink_qr.cpp, pfw653-farm-mini) sendet ein
                // "hardened"-Bool, kein "material"-String - der String
                // bleibt als Weg fuer manuelle Eingabe/Tests erhalten,
                // die Boole'sche Form ist bei echten Firmware-QR-Codes
                // massgeblich.
                material = when {
                    nozzle["material"] != null -> when (nozzle["material"]?.jsonPrimitive?.content?.lowercase()) {
                        "brass" -> NozzleMaterial.BRASS
                        "hardened" -> NozzleMaterial.HARDENED
                        else -> NozzleMaterial.UNKNOWN
                    }
                    nozzle["hardened"]?.jsonPrimitive?.booleanOrNull == true -> NozzleMaterial.HARDENED
                    nozzle["hardened"]?.jsonPrimitive?.booleanOrNull == false -> NozzleMaterial.BRASS
                    else -> NozzleMaterial.UNKNOWN
                },
            ),
            expiresAtEpochSeconds = root["expires_at"]?.jsonPrimitive?.longOrNull,
        )
        validate(payload, nowEpochSeconds)
    } catch (_: Throwable) {
        LocalPairingValidation.Invalid(LocalPairingValidation.Reason.MALFORMED)
    }

    fun validate(payload: LocalPrusaLinkQrPayload, nowEpochSeconds: Long): LocalPairingValidation {
        val reason = when {
            payload.type != "prusalink-local" -> LocalPairingValidation.Reason.WRONG_TYPE
            payload.version != 1 -> LocalPairingValidation.Reason.UNSUPPORTED_VERSION
            payload.transport.lowercase() != "http" -> LocalPairingValidation.Reason.UNSUPPORTED_TRANSPORT
            payload.model.isBlank() -> LocalPairingValidation.Reason.INVALID_MODEL
            !isLocalHost(payload.host) -> LocalPairingValidation.Reason.NON_LOCAL_HOST
            payload.port !in 1..65535 -> LocalPairingValidation.Reason.INVALID_PORT
            payload.pairingToken.isBlank() -> LocalPairingValidation.Reason.MISSING_TOKEN
            payload.expiresAtEpochSeconds?.let { nowEpochSeconds >= it } == true -> LocalPairingValidation.Reason.EXPIRED
            else -> null
        }
        return reason?.let { LocalPairingValidation.Invalid(it) }
            ?: LocalPairingValidation.Valid(payload, endpoint(payload.host, payload.port))
    }

    fun manual(host: String, port: Int, token: String, model: String, capabilities: Set<String> = emptySet()): LocalPairingValidation =
        validate(
            LocalPrusaLinkQrPayload(
                type = "prusalink-local",
                version = 1,
                model = model,
                host = host.trim(),
                port = port,
                transport = "http",
                pairingToken = token,
                capabilities = capabilities,
                nozzle = LocalNozzle(0.4, NozzleMaterial.UNKNOWN),
            ),
            nowEpochSeconds = 0,
        )

    private fun endpoint(host: String, port: Int): String = "http://${host.trim()}:$port"

    private fun isLocalHost(host: String): Boolean {
        val parts = host.trim().split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
        val octets = parts.map { it.toInt() }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.content ?: ""
    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: -1
}

object LocalPairingExchange {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Die CFW-Gegenstelle (`lib/WUI/link_content/local_pairing.cpp` in
     * pfw653-farm-mini) antwortet auf `/api/pair` ausschliesslich mit
     * `{"username":"...","password":"..."}` - Modell, Duese, Host und
     * Port kannte der Drucker zum Zeitpunkt der Antwort bereits aus dem
     * eigenen QR-Code, den diese App gerade gescannt und validiert hat.
     * Diese Felder hier zusaetzlich in der Antwort zu verlangen hiess:
     * jede echte Kopplung schlug als "Antwort ungueltig" fehl, obwohl
     * der Token-Austausch selbst erfolgreich war.
     */
    fun parseResponse(jsonText: String, payload: LocalPrusaLinkQrPayload): LocalPairingExchangeResult {
        return try {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val username = root["username"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val password = root["password"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (username.isBlank() || password.isBlank()) return LocalPairingExchangeResult.Malformed
        val hardened = payload.nozzle.material == NozzleMaterial.HARDENED
        LocalPairingExchangeResult.Success(
            LocalPrusaLinkCredentials(
                username, password, payload.model, payload.nozzle.diameter, hardened, payload.host, payload.port,
            )
        )
        } catch (_: Throwable) {
            LocalPairingExchangeResult.Malformed
        }
    }
}
