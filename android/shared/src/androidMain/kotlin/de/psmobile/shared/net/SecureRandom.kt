package de.psmobile.shared.net

import java.security.SecureRandom

private val zufall = SecureRandom()

internal actual fun secureRandomBytes(count: Int): ByteArray =
    ByteArray(count).also { zufall.nextBytes(it) }
