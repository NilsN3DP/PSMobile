package de.psmobile.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.psmobile.Startargumente
import de.psmobile.shared.rules.AppSettings

/**
 * Die App-Einstellungen - Gegenstueck zu `AppSettingsStore` in
 * `ios/PSMobile/Screens/AppSettingsView.swift`.
 *
 * Dieselben Schluessel aus dem gemeinsamen Modul (`AppSettings`), nur in
 * den SharedPreferences "psmobile", die auch der Dienst liest. Die
 * Sprache liegt unter "lang" - dem Schluessel, den Android schon immer
 * benutzt hat, damit bestehende Installationen ihre Wahl behalten.
 */
class AppSettingsStore(context: Context, schalter: Startargumente = Startargumente.LEER) {

    private val prefs = context.applicationContext.getSharedPreferences("psmobile", Context.MODE_PRIVATE)
    private val app = context.applicationContext

    init {
        // Der Bildschirmfoto-Rundgang vergleicht beide Plattformen Bild
        // fuer Bild. Gespeicherte Schalter aus frueheren Testlaeufen
        // machen daraus einen Vergleich zweier Vorgeschichten - siehe
        // die gleichlautende Stelle in `AppSettingsView.swift`.
        if (schalter.contains("-psm-reset-settings")) {
            prefs.edit().apply {
                AppSettings.toggles.forEach { remove(it.key) }
                remove(AppSettings.KEY_START_MODE)
                remove(LANGUAGE_KEY)
                // Sichtbarkeitsstufe der Einstellungen - siehe
                // AppSettingsView.swift.
                remove("psm.sichtbarkeit")
            }.apply()
        }
    }

    private val _language = mutableStateOf(prefs.getString(LANGUAGE_KEY, "en") ?: "en")
    var language: String
        get() = _language.value
        set(value) {
            _language.value = value
            prefs.edit().putString(LANGUAGE_KEY, value).apply()
            // Der Katalog haelt sowohl die Beschriftungen als auch
            // Lang.current fuer das gemeinsame Modul.
            PsUiCatalog.load(app, value)
            revision++
        }

    private val _startMode = mutableStateOf(
        prefs.getString(AppSettings.KEY_START_MODE, AppSettings.START_ASK) ?: AppSettings.START_ASK,
    )
    var startMode: String
        get() = _startMode.value
        set(value) {
            _startMode.value = value
            prefs.edit().putString(AppSettings.KEY_START_MODE, value).apply()
            revision++
        }

    /** Nur damit die Oberflaeche sich neu zeichnet - die Wahrheit steht in den Preferences. */
    private var revision by mutableStateOf(0)

    fun bool(key: String, standard: Boolean): Boolean {
        revision
        if (!prefs.contains(key)) return standard
        return prefs.getBoolean(key, standard)
    }

    fun set(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        revision++
    }

    val thumbnails: Boolean get() = bool(AppSettings.KEY_THUMBNAILS, true)
    val showIncompatible: Boolean get() = bool(AppSettings.KEY_SHOW_INCOMPATIBLE, false)
    val multiBedRender: Boolean get() = bool(AppSettings.KEY_MULTI_BED_RENDER, true)

    companion object {
        const val LANGUAGE_KEY = "lang"
    }
}

val LocalAppSettingsStore = compositionLocalOf<AppSettingsStore> {
    error("AppSettingsStore fehlt - PSMobileApp stellt ihn bereit")
}

/** Kurzform fuer `@AppStorage(key) var x = standard` - liest live aus dem Store. */
@Composable
fun rememberAppSetting(key: String, standard: Boolean): Boolean =
    LocalAppSettingsStore.current.bool(key, standard)
