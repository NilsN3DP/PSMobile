package de.psmobile.shared.rules

object FavoriteSettingRules {

    fun sanitize(favorites: Set<String>, availableKeys: List<String>): List<String> =
        availableKeys.filter { it in favorites }.distinct()
}
