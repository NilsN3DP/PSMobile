package de.psmobile.shared.net

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    bytes.usePinned { fest ->
        SecRandomCopyBytes(kSecRandomDefault, count.toULong(), fest.addressOf(0))
    }
    return bytes
}
