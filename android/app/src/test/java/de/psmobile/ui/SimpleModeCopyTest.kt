package de.psmobile.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Simple Mode hatte lange 26 rohe englische Zeichenketten neben 34
 * uebersetzten Aufrufen; eine Zeile mischte sogar beide Sprachen in
 * einem Panel. Der Fehler faellt beim Lesen nicht auf, weil jede
 * einzelne Zeile fuer sich harmlos aussieht. Deshalb prueft dieser Test
 * die Quelldatei selbst.
 *
 * Erlaubt bleiben Zeichen ohne Sprache - Pfeile, Symbole, Werte mit
 * Zeichenketteneinbettung wie "T${'$'}{index}" - und alles, was durch
 * st() oder PsUi laeuft.
 */
class SimpleModeCopyTest {

    private val source: File = sequenceOf(
        // Je nach Aufrufort liegt das Arbeitsverzeichnis auf dem Modul
        // oder auf dem Projektstamm.
        File("src/main/java/de/psmobile/ui/SimpleModeScreen.kt"),
        File("android/app/src/main/java/de/psmobile/ui/SimpleModeScreen.kt"),
    ).first { it.isFile }

    /**
     * Zeichenketten, die keine Sprache tragen und daher nicht uebersetzt
     * werden: Pfeile und Symbole, das einzelne Logo-S, sowie alles mit
     * eingebettetem Wert. Escape-Folgen wie \n fallen vorher weg, sonst
     * zaehlte ihr Buchstabe als Wort.
     */
    private fun carriesLanguage(literal: String): Boolean {
        if (literal.contains('$')) return false
        val plain = literal.replace(Regex("""\\."""), "")
        return Regex("""[A-Za-zÄÖÜäöüß]{2,}""").containsMatchIn(plain)
    }

    private val call = Regex("""Text\(\s*"((?:[^"\\]|\\.)*)"""")

    @Test
    fun `every visible sentence in Simple Mode goes through a translation`() {
        val untranslated = source.readLines().withIndex().flatMap { (index, line) ->
            call.findAll(line)
                .map { it.groupValues[1] }
                .filter(::carriesLanguage)
                .map { "${index + 1}: $it" }
                .toList()
        }
        assertTrue(
            "Nicht uebersetzte Texte im Simple Mode:\n" + untranslated.joinToString("\n"),
            untranslated.isEmpty(),
        )
    }
}
