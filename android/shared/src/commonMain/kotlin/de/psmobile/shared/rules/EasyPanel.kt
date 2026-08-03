package de.psmobile.shared.rules

/**
 * Die Bereiche des Easy Mode.
 *
 * Steht bei den Regeln und nicht beim Bildschirm: welcher Bereich auf
 * welchen folgt und welcher zurueck nach HOME fuehrt, ist eine
 * Entscheidung ueber den Ablauf, keine ueber die Darstellung.
 * EasyModeState rechnet damit, und die iOS-Seite wird es genauso tun.
 */
enum class EasyPanel { HOME, PROJECTS, PRINTER, FILAMENT, SUPPORTS, ADHESION, PRINT_SETTINGS }
