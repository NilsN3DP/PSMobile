package de.psmobile.slicing.profileupdate

import java.net.URI
import java.io.File
import java.net.HttpURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import de.psmobile.shared.rules.SimpleModeState

fun interface ProfileUpdateHttp {
    suspend fun get(uri: URI): ByteArray
}

class HttpUrlConnectionProfileUpdateHttp : ProfileUpdateHttp {
    override suspend fun get(uri: URI): ByteArray {
        require(uri.scheme.equals(SimpleModeState.text("Only HTTPS is allowed", "https"), ignoreCase = true)) { "Nur HTTPS ist erlaubt" }
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 3_000
            readTimeout = 5_000
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            require(connection.contentLengthLong <= 20L * 1024 * 1024) { SimpleModeState.text("Profile package is too large", "Profilpaket ist zu groß") }
            return connection.inputStream.use { input ->
                input.readBytes().also { bytes -> require(bytes.size <= 20 * 1024 * 1024) }
            }
        } finally {
            connection.disconnect()
        }
    }
}

class ProfileUpdateRepository(
    private val manifestUri: URI,
    private val allowedHosts: Set<String>,
    private val coreVersion: ProfileVersion,
    private val activeVersion: ProfileVersion,
    private val http: ProfileUpdateHttp,
    private val packageStore: ProfilePackageStore? = null,
    private val skippedVersion: () -> ProfileVersion? = { null },
    private val rememberSkippedVersion: (ProfileVersion) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val state: StateFlow<ProfileUpdateState> = mutableState.asStateFlow()

    fun checkOnLaunch(scope: CoroutineScope) {
        if (manifestUri.scheme != "https" || allowedHosts.isEmpty()) return
        mutableState.value = ProfileUpdateState.Checking
        scope.launch {
            val manifest = runCatching {
                ProfileManifestCodec.decode(
                    http.get(manifestUri).decodeToString(),
                    manifestUri,
                    allowedHosts,
                ).getOrThrow()
            }.getOrNull()
            mutableState.value = when {
                manifest == null -> ProfileUpdateState.Idle
                manifest.version <= activeVersion -> ProfileUpdateState.Idle
                manifest.minSlic3rVersion > coreVersion -> ProfileUpdateState.Idle
                !UpdateOffer.shouldOffer(manifest.version, skippedVersion(), shownThisRun = false) -> ProfileUpdateState.Idle
                else -> ProfileUpdateState.Offer(manifest)
            }
        }
    }

    fun later() { mutableState.value = ProfileUpdateState.Idle }
    fun skipUntilNewer() {
        (mutableState.value as? ProfileUpdateState.Offer)?.manifest?.version?.let(rememberSkippedVersion)
        mutableState.value = ProfileUpdateState.Idle
    }

    fun downloadOffered(scope: CoroutineScope) {
        val offer = mutableState.value as? ProfileUpdateState.Offer ?: return
        mutableState.value = ProfileUpdateState.Downloading(offer.manifest, null)
        scope.launch {
            val staged = runCatching {
                val store = requireNotNull(packageStore) { "Paketspeicher fehlt" }
                val file = File.createTempFile("profile-update-", ".zip", store.activeRoot().parentFile)
                file.writeBytes(http.get(offer.manifest.packageUrl))
                store.stage(file, offer.manifest).getOrThrow()
            }
            mutableState.value = staged.fold(
                onSuccess = { ProfileUpdateState.ReadyToApply(offer.manifest) },
                onFailure = { ProfileUpdateState.Failed(it.message ?: SimpleModeState.text("Profile update failed", "Profilupdate fehlgeschlagen")) },
            )
        }
    }
}
