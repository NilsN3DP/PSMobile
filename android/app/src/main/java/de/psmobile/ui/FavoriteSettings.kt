package de.psmobile.ui

import de.psmobile.shared.rules.FavoriteSettingRules

/**
 * Selbst gewaehlte Einstellungen, die oben in der Seitenliste stehen.
 *
 * Die Einstellungen kommen unveraendert aus PrusaSlicers Seitenbaum -
 * 330 Parameter auf 23 Seiten. Wer an einem Drucker arbeitet, fasst
 * davon regelmaessig ein Dutzend an und sucht sie jedes Mal neu. Der
 * Desktop hat dafuer eine Volltextsuche; auf dem Tablet ist Tippen
 * teurer als Antippen, deshalb hier eine gemerkte Auswahl.
 *
 * Die Reihenfolge folgt bewusst der Seitenreihenfolge und nicht dem
 * Zeitpunkt des Merkens: so steht ein Wert immer an derselben Stelle,
 * auch wenn zwischendurch weitere dazukommen.
 */
object FavoriteSettings {

    fun toggle(current: Set<String>, key: String): Set<String> =
        if (key in current) current - key else current + key

    /**
     * Die gemerkten Schluessel dieses Reiters, in der Reihenfolge, in der
     * sie in den Seiten stehen. Schluessel aus anderen Reitern und
     * Schluessel, die es nicht mehr gibt, fallen weg - ein
     * Profilpaket-Update darf keine toten Eintraege hinterlassen.
     */
    fun orderedFor(favorites: Set<String>, keysInTabOrder: List<String>): List<String> =
        FavoriteSettingRules.sanitize(favorites, keysInTabOrder)

    /** Alle Schluessel eines Reiters in Seiten- und Gruppenreihenfolge. */
    fun keysOf(pages: List<PsUi.Page>): List<String> =
        pages.flatMap { page -> page.groups.flatMap { group -> group.options.map { it.key } } }
            .distinct()
}
