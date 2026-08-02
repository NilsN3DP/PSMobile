package de.psmobile.ui

/**
 * Layout state for multi-toolhead printers.
 *
 * Keeping this outside Compose makes the important edge cases testable: a
 * printer change can reduce the number of heads while the inspector stays
 * open, and a compact 2×4 selector must not point at a missing head.
 */
internal fun extruderHeadGroups(count: Int, columns: Int = 4): List<List<Int>> =
    (0 until count.coerceAtLeast(0)).toList().chunked(columns.coerceAtLeast(1))

internal fun normalizedExtruderIndex(index: Int, count: Int): Int =
    if (count <= 0) 0 else index.coerceIn(0, count - 1)

/** Scroll target that exposes the selected-head editor below the compact bank. */
internal fun extruderEditorScrollTarget(extruderCount: Int): Int =
    if (extruderCount > 1) 260 else 0
