/*
 * Gemeinsame Deklaration fuer die aus PrusaSlicer erzeugten
 * Einstellungs-Abhaengigkeiten.
 *
 * Die Klasse muss in genau einer Form in beiden Uebersetzungseinheiten
 * sichtbar sein. Zwei getrennte Klassendefinitionen mit demselben Namen
 * verletzen die One Definition Rule, auch wenn ihre Daten zufaellig
 * gleich aussehen.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#ifndef PSMOBILE_TOGGLES_HPP
#define PSMOBILE_TOGGLES_HPP

#include <map>
#include <string>

#include "libslic3r/PrintConfig.hpp"

namespace psm {

struct ToggleCollector {
    std::map<std::string, bool> enabled;

    void toggle_field(const std::string &opt_key, bool on, int opt_index = -1)
    {
        (void) opt_index;
        enabled[opt_key] = on;
    }

    void collect_print_fff(Slic3r::DynamicPrintConfig *config);
};

} // namespace psm

#endif /* PSMOBILE_TOGGLES_HPP */
