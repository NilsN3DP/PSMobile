#!/usr/bin/env python3
"""
gap-report.py - Was hat PrusaSlicer, was PSMobile nicht?

Kein Augenmass, sondern ein Abgleich am Quelltext. Der Bericht laeuft
nach jeder Runde und ist die Grundlage der Aufgabenliste.

Vier Vergleiche:

  1. Parameter - alle FFF-Optionen aus PrintConfig.cpp gegen die
     Schluessel in tabs.json. Fehlende werden NICHT nur gezaehlt, sondern
     eingeordnet: Messartefakt, interne Buchfuehrung, veraltet,
     PSMobile-Spezialdialog, offene Dialogluecke oder Objektwerkzeug.
  2. Werkzeuge am Modell - das EType-Enum aus GLGizmosManager.hpp.
  3. Menuebefehle - append_menu_item aus MainFrame.cpp, gegen die Liste
     dessen gehalten, was die App anbietet.
  4. Eigene Dialoge - die wxDialog-Klassen, die Parameter bearbeiten.

Aufruf:  build/scripts/gap-report.py [prusaslicer-src] [assets-dir]

SPDX-License-Identifier: AGPL-3.0-or-later
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PS = "external/PrusaSlicer"
ASSETS = "android/app/src/main/assets/psui"
MATRIX = "docs/feature-matrix.json"


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


# ----------------------------------------------------------------------
# Einordnung der fehlenden Parameter
#
# Jeder Eintrag ist eine bewusste Entscheidung, kein Rauschen. Wer hier
# etwas ergaenzt, muss begruenden koennen, warum es NICHT gebaut werden
# muss - sonst gehoert es in die Aufgabenliste.
# ----------------------------------------------------------------------

ARTEFAKT = {
    # PrintConfig baut diese Namen selbst in einer Achsenschleife
    # zusammen; der Bericht sieht nur den Praefix.
    "machine_max_feedrate_", "machine_max_acceleration_", "machine_max_jerk_",
}

INTERN = {
    # Steht in den Profildateien, wird vom Programm gepflegt, ist am
    # Desktop nirgends bedienbar.
    "printer_technology", "printer_model", "printer_vendor", "printer_variant",
    "print_settings_id", "printer_settings_id", "filament_settings_id",
    "physical_printer_settings_id", "preset_name", "preset_names",
    "profile_vendor", "profile_version", "inherits", "inherits_cummulative",
    "compatible_printers_condition_cummulative",
    "compatible_prints_condition_cummulative",
    "default_filament_profile", "default_print_profile", "extrusion_axis",
    # filament_vendor benutzen wir - zum Gruppieren der Materialliste,
    # nicht als Einstellzeile.
    "filament_vendor",
}

VERALTET = {
    "colorprint_heights",        # durch Custom-G-code-Eintraege ersetzt
    "infill_only_where_needed",  # in 2.9 entfernt
    "thumbnails_format",         # in thumbnails aufgegangen
    "duplicate_distance",        # nur noch Kommandozeile
}

UNSICHTBAR = {
    "seam_preferred_direction", "seam_preferred_direction_jitter",  # auskommentiert
    "solid_layers", "solid_min_thickness",                          # Sammelwerte
}

# Diese Werte sind nicht in tabs.json, werden aber in der mobilen
# Spezialdialogansicht bearbeitet.
MOBILE_DIALOG = {
    "bed_shape": "BedShapeDialog",
    "bed_custom_texture": "BedShapeDialog",
    "bed_custom_model": "BedShapeDialog",
    "wiping_volumes_matrix": "WipingDialog",
    "wiping_volumes_use_custom_matrix": "WipingDialog",
    "filament_ramming_parameters": "RammingDialog",
    "gcode_substitutions": "SubstitutionManager",
    "compatible_printers": "Dependencies-Widget",
    "compatible_prints": "Dependencies-Widget",
}

DIALOG = {
    # Offen: die Desktop-Physical-Printer-Konfiguration. Die
    # PrusaLink-Zielwahl ersetzt diese Mehrhost-Verwaltung nicht.
    "print_host": "PhysicalPrinterDialog",
    "host_type": "PhysicalPrinterDialog",
    "printhost_apikey": "PhysicalPrinterDialog",
    "printhost_port": "PhysicalPrinterDialog",
    "printhost_cafile": "PhysicalPrinterDialog",
    "printhost_user": "PhysicalPrinterDialog",
    "printhost_password": "PhysicalPrinterDialog",
    "printhost_ssl_ignore_revoke": "PhysicalPrinterDialog",
    "printhost_authorization_type": "PhysicalPrinterDialog",
}

AM_OBJEKT = {
    # Gehoeren in den Objektbaum, nicht auf eine Profilseite - Aufgabe 26.
    "extruder", "extruder_colour", "wipe_into_infill", "wipe_into_objects",
}


def classify(key):
    if key in ARTEFAKT:   return "Messartefakt"
    if key in INTERN:     return "intern"
    if key in VERALTET:   return "veraltet"
    if key in UNSICHTBAR: return "am Desktop unsichtbar"
    if key in MOBILE_DIALOG: return "PSMobile-Spezialdialog"
    if key in DIALOG:     return "eigener Dialog"
    if key in AM_OBJEKT:  return "am Objekt"
    return "NICHT EINGEORDNET"


def parameters():
    src = read(os.path.join(PS, "src/libslic3r/PrintConfig.cpp"))
    defined = list(dict.fromkeys(re.findall(r"this->add\(\s*\"([a-z0-9_]+)\"", src)))

    cut = src.find("void PrintConfigDef::init_sla_params")
    sla = set(re.findall(r"this->add\(\s*\"([a-z0-9_]+)\"", src[cut:])) if cut > 0 else set()
    fff = [k for k in defined if k not in sla]

    tabs = json.load(open(os.path.join(ASSETS, "tabs.json"), encoding="utf-8"))
    ours = {o["key"]
            for pages in tabs.values()
            for pg in pages
            for g in pg.get("groups", [])
            for o in g.get("options", [])}

    missing = [k for k in fff if k not in ours]

    buckets = {}
    for k in missing:
        buckets.setdefault(classify(k), []).append(k)

    print("=" * 72)
    print("PARAMETER")
    print("=" * 72)
    print("  FFF-relevant in PrintConfig      %d" % len(fff))
    in_tabs = len(fff) - len(missing)
    special = len(buckets.get("PSMobile-Spezialdialog", []))
    object_tools = len(buckets.get("am Objekt", []))
    print("  in tabs.json erreichbar          %d" % in_tabs)
    print("  in PSMobile erreichbar           %d" % (in_tabs + special + object_tools))
    print("  nicht in tabs.json               %d" % len(missing))
    print()

    braucht_nichts = 0
    for name in ("Messartefakt", "intern", "veraltet", "am Desktop unsichtbar"):
        if name in buckets:
            braucht_nichts += len(buckets[name])
    print("  davon braucht nichts             %d" % braucht_nichts)
    print("  ueber Spezialdialog erreichbar  %d" % len(buckets.get("PSMobile-Spezialdialog", [])))
    print("  echte Dialog-Luecken             %d" % len(buckets.get("eigener Dialog", [])))
    print("  gehoert an den Objektbaum        %d" % len(buckets.get("am Objekt", [])))
    print()

    for name, keys in sorted(buckets.items()):
        marker = "  !! " if name == "NICHT EINGEORDNET" else "     "
        print("%s%-24s %2d  %s" % (marker, name, len(keys), ", ".join(sorted(keys)[:6])))
        if len(keys) > 6:
            print("%s%-24s     ... und %d weitere" % (marker, "", len(keys) - 6))
    print()

    if "NICHT EINGEORDNET" in buckets:
        print("  ACHTUNG: neue Parameter ohne Einordnung. Entweder in die")
        print("  Tabellen oben eintragen (mit Begruendung) oder als Aufgabe")
        print("  aufnehmen. Eine Zahl ohne Aufschluesselung ist wertlos.")
        print()
    return missing


# ----------------------------------------------------------------------
# Werkzeuge, Menuebefehle, Dialoge
# ----------------------------------------------------------------------

# Desktop-Zuordnungen stammen aus der Statusmatrix. Damit kann ein
# Feature nicht als fehlend gezaehlt werden, wenn seine Kotlin-/native
# Evidenz bereits als gebaut oder getestet eingetragen ist.
IMPLEMENTED_STATUSES = {
    "coded", "built", "emulator_tested", "device_tested", "hardware_tested",
}

# Diese Desktop-Kommandos werden auf Android bewusst nicht nachgebaut.
# Die Beschriftung im Bericht macht aus einer fehlenden Zeile keine
# irrefuehrende Funktionsluecke.
MENU_EXCLUSIONS = {
    "Import ZIP Archive": "ZIP",
    "Import SLA Archive": "SLA",
    "Print &Host Upload Queue": "Hardware-Druckerwarteschlange",
    "Open new instance": "Desktop-Fensterverwaltung",
    "Open New Instance": "Desktop-Fensterverwaltung",
    "Open PrusaSlicer": "Desktop-Fensterverwaltung",
    "Open &PrusaSlicer": "Desktop-Fensterverwaltung",
    "E&xit": "Desktop-Fensterverwaltung",
    "&Quit": "Desktop-Fensterverwaltung",
}


def capabilities():
    """Liest umgesetzte und noch offene Desktop-Zuordnungen aus der Matrix."""
    with open(MATRIX, encoding="utf-8") as stream:
        matrix = json.load(stream)
    android_features = [
        feature for feature in matrix.get("features", [])
        if feature.get("platform") == "android"
    ]
    implemented = [
        feature for feature in android_features
        if feature.get("status") in IMPLEMENTED_STATUSES
    ]
    pending = [
        feature for feature in android_features
        if feature.get("status") not in IMPLEMENTED_STATUSES
    ]
    gizmos = {
        name for feature in implemented
        for name in feature.get("desktop_gizmos", [])
    }
    menu = {
        name for feature in implemented
        for name in feature.get("desktop_menu", [])
    }
    stale_gizmos = {
        name for feature in pending
        for name in feature.get("desktop_gizmos", [])
    }
    stale_menu = {
        name for feature in pending
        for name in feature.get("desktop_menu", [])
    }
    return gizmos, menu, stale_gizmos, stale_menu


def gizmos(haben_wir, matrix_offen):
    hpp = os.path.join(PS, "src/slic3r/GUI/Gizmos/GLGizmosManager.hpp")
    if not os.path.exists(hpp):
        return
    m = re.search(r"enum\s+EType\s*:?[^{]*\{([^}]*)\}", read(hpp))
    names = []
    if m:
        enum_body = re.sub(r"//[^\n]*", "", m.group(1))
        for entry in enum_body.split(","):
            entry = entry.strip().split("=")[0].strip()
            if entry and entry[0].isupper() and entry != "Undefined":
                names.append(entry)

    # SLA bleibt ausserhalb des Umfangs.
    names = [n for n in names if not n.startswith("Sla")]
    fehlt = [n for n in names if n not in haben_wir]

    print("=" * 72)
    print("WERKZEUGE AM MODELL")
    print("=" * 72)
    print("  PrusaSlicer (ohne SLA)  %d" % len(names))
    print("  bei uns im Code         %d" % len([n for n in names if n in haben_wir]))
    print("  nicht umgesetzt         %d   %s" % (len(fehlt), ", ".join(fehlt)))
    stale = [n for n in fehlt if n in matrix_offen]
    if stale:
        print("  MATRIX NICHT UMGESETZT  %s" % ", ".join(stale))
    print()


def menu_items(haben_wir, matrix_offen):
    path = os.path.join(PS, "src/slic3r/GUI/MainFrame.cpp")
    if not os.path.exists(path):
        return
    src = read(path)
    items = list(dict.fromkeys(
        re.findall(r"append_menu_item\([^,]+,\s*[^,]+,\s*_L\(\"((?:[^\"\\\\]|\\\\.)*)\"\)", src)))
    fehlt = [i for i in items if i not in haben_wir]
    ausgeschlossen = [i for i in fehlt if i in MENU_EXCLUSIONS]
    matrix_fehlt = [i for i in fehlt if i in matrix_offen]
    unbelegt = [i for i in fehlt if i not in MENU_EXCLUSIONS and i not in matrix_offen]

    print("=" * 72)
    print("MENUEBEFEHLE")
    print("=" * 72)
    print("  im Hauptfenster   %d" % len(items))
    print("  bei uns vorhanden %d" % (len(items) - len(fehlt)))
    print("  nicht umgesetzt   %d" % len(unbelegt))
    print("  Matrix offen      %d" % len(matrix_fehlt))
    print("  ausgenommen       %d" % len(ausgeschlossen))
    for it in matrix_fehlt:
        print("      MATRIX NICHT UMGESETZT  %s" % it)
    for it in ausgeschlossen:
        print("      AUSGENOMMEN: %-9s %s" % (MENU_EXCLUSIONS[it], it))
    for it in unbelegt:
        print("      NICHT BELEGT             %s" % it)
    print()


def dialogs():
    """Welche eigenen Dialoge bearbeiten Parameter?"""
    known = sorted(set(MOBILE_DIALOG.values()) | set(DIALOG.values()))
    print("=" * 72)
    print("EIGENE DIALOGE")
    print("=" * 72)
    for d in known:
        keys = sorted(
            k for k, v in {**MOBILE_DIALOG, **DIALOG}.items() if v == d
        )
        print("  %-24s %2d Parameter   %s" % (d, len(keys), ", ".join(keys[:4])))
    print()


def main(argv=None):
    global PS, ASSETS, MATRIX
    parser = argparse.ArgumentParser()
    parser.add_argument("prusaslicer_src", nargs="?", default=PS)
    parser.add_argument("assets_dir", nargs="?", default=ASSETS)
    parser.add_argument("--matrix", default=MATRIX)
    parser.add_argument(
        "--output",
        default="build-out/gap-report.txt",
        help="vollstaendige Parameterliste; '-' schreibt nach stdout",
    )
    args = parser.parse_args(argv)
    PS, ASSETS, MATRIX = args.prusaslicer_src, args.assets_dir, args.matrix

    missing = parameters()
    haben_gizmos, haben_menu, offene_gizmos, offene_menu = capabilities()
    gizmos(haben_gizmos, offene_gizmos)
    menu_items(haben_menu, offene_menu)
    dialogs()

    details = "".join("%-44s %s\n" % (k, classify(k)) for k in missing)
    if args.output == "-":
        print("=" * 72)
        print("VOLLSTAENDIGE PARAMETERLISTE")
        print("=" * 72)
        print(details, end="")
    else:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(details, encoding="utf-8")
        print("Vollstaendige Liste mit Einordnung: %s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
