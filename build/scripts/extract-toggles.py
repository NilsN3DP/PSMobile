#!/usr/bin/env python3
"""
extract-toggles.py - Abhaengigkeiten zwischen Einstellungen uebernehmen.

PrusaSlicer graut Parameter aus, die im aktuellen Zustand wirkungslos
sind: alle Stuetzenwerte bei ausgeschalteten Stuetzen, Ironing-Werte ohne
Ironing, Wipe-Tower-Werte bei einem Extruder. Die Regeln stehen in
ConfigManipulation::toggle_print_fff_options - rund 150 Zeilen reine
Konfigurationslogik ohne einen einzigen wx-Aufruf.

Statt sie abzutippen wird der Rumpf hier woertlich herausgeschnitten und
in eine Sammelklasse gepackt, deren toggle_field die Ergebnisse in eine
Karte schreibt, statt Widgets anzufassen. Damit bleibt die Logik
byte-genau die des Originals und wandert bei einem Versionswechsel
einfach mit - E-12.

Aufruf:  build/scripts/extract-toggles.py [prusaslicer-src] [ziel.cpp]

SPDX-License-Identifier: AGPL-3.0-or-later
"""

import os
import re
import sys

PS = sys.argv[1] if len(sys.argv) > 1 else "external/PrusaSlicer"
OUT = sys.argv[2] if len(sys.argv) > 2 else "core/src/psmobile_toggles_generated.cpp"

SRC = os.path.join(PS, "src/slic3r/GUI/ConfigManipulation.cpp")

HEADER = '''/*
 * psm_toggles.cpp - ERZEUGT, NICHT VON HAND BEARBEITEN.
 *
 * Erzeugt von build/scripts/extract-toggles.py aus
 * src/slic3r/GUI/ConfigManipulation.cpp.
 *
 * Der Rumpf unten ist woertlich der von
 * ConfigManipulation::toggle_print_fff_options. Er benutzt nur
 * toggle_field() und den uebergebenen config-Zeiger, keinen wx-Code und
 * keine Klassenmitglieder - deshalb laesst er sich unveraendert in eine
 * andere Huelle setzen. Aendert Prusa die Regeln, kommen sie beim
 * naechsten Lauf des Skripts mit.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_toggles.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Print.hpp"

using namespace Slic3r;

namespace psm {

/* --- ab hier woertlich aus dem Original ---------------------------- */

void ToggleCollector::collect_print_fff(DynamicPrintConfig *config)
'''

FOOTER = '''
} // namespace psm
'''


def main():
    if not os.path.exists(SRC):
        print("FEHLT: %s" % SRC, file=sys.stderr)
        return 1

    with open(SRC, encoding="utf-8", errors="replace") as f:
        lines = f.readlines()

    start = None
    for i, line in enumerate(lines):
        if line.startswith("void ConfigManipulation::toggle_print_fff_options"):
            start = i
            break
    if start is None:
        print("toggle_print_fff_options nicht gefunden", file=sys.stderr)
        return 1

    # Rumpf ueber Klammerzaehlung abgrenzen.
    depth, end = 0, None
    for i in range(start, len(lines)):
        depth += lines[i].count("{") - lines[i].count("}")
        if depth == 0 and i > start:
            end = i
            break
    if end is None:
        print("Rumpf nicht abgrenzbar", file=sys.stderr)
        return 1

    body = "".join(lines[start + 1:end + 1])

    # Sicherheitsnetz: taucht doch wx oder ein Klassenmitglied auf, ist
    # die Annahme gebrochen und wir brechen ab, statt stillschweigend
    # etwas Kaputtes zu erzeugen.
    for bad in ("wx", "m_", "GUI::"):
        hit = re.search(r"\b%s" % re.escape(bad), body)
        if hit:
            ctx = body[max(0, hit.start() - 40):hit.start() + 40].replace("\n", " ")
            print("ABBRUCH: '%s' im Rumpf gefunden - Annahme gebrochen.\n  %s"
                  % (bad, ctx), file=sys.stderr)
            return 1

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(HEADER)
        f.write(body)
        f.write(FOOTER)

    n = body.count("toggle_field")
    print("%s erzeugt: %d Zeilen, %d Regeln" % (OUT, end - start, n))
    return 0


if __name__ == "__main__":
    sys.exit(main())
