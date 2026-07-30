package de.psmobile.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Kleine, explizite Keystore-Ablage fuer Drucker-Credentials.
 *
 * Der AES-Schluessel ist nicht exportierbar. In den Preferences stehen
 * nur IV und Ciphertext; diese Datei wird zusaetzlich vom Android-Backup
 * ausgeschlossen, weil der Keystore-Schluessel nicht mitwandert.
 */
internal object SecretStore {
    private const val PREFS = "psmobile_secrets"
    private const val KEY_ALIAS = "psmobile-printer-credentials-v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun put(context: Context, ref: String, plaintext: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(1 + cipher.iv.size + ciphertext.size)
        packed[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(packed, 1)
        ciphertext.copyInto(packed, 1 + cipher.iv.size)
        prefs(context).edit(commit = true) {
            putString(ref, Base64.encodeToString(packed, Base64.NO_WRAP))
        }
    }

    /**
     * Nach Restore oder Keystore-Reset ist der Ciphertext absichtlich
     * nicht mehr lesbar. Dann null liefern und die UI neue Zugangsdaten
     * verlangen lassen, statt beim App-Start abzustuerzen.
     */
    fun get(context: Context, ref: String): String? = runCatching {
        val encoded = prefs(context).getString(ref, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = packed.firstOrNull()?.toInt()?.and(0xff) ?: return null
        if (ivSize !in 12..32 || packed.size <= 1 + ivSize) return null
        val iv = packed.copyOfRange(1, 1 + ivSize)
        val ciphertext = packed.copyOfRange(1 + ivSize, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    fun remove(context: Context, ref: String) {
        prefs(context).edit(commit = true) { remove(ref) }
    }
}
