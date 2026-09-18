package de.psmobile.core

/** Decodes the compact bed snapshot emitted by the bundled JNI bridge. */
internal object BedSnapshotDecoder {
    data class Metadata(val name: String, val locked: Boolean)

    fun decode(
        values: IntArray,
        metadata: Map<Int, Metadata>,
    ): List<PsmCore.Bed> {
        if (values.isEmpty()) return emptyList()
        val active = values.first()
        return values.drop(1).mapIndexed { index, objectCount ->
            val fallback = metadata[index]
            PsmCore.Bed(
                index = index,
                objectCount = objectCount,
                instanceCount = objectCount,
                active = index == active,
                name = fallback?.name ?: "Bed ${index + 1}",
                locked = fallback?.locked == true,
            )
        }
    }
}
