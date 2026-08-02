#!/usr/bin/env python3
"""
Uebernimmt die Oberflaechen-Definition aus PrusaSlicer, statt sie
nachzubauen.

Grundregel des Projekts: Alles, was als Daten oder Logik uebernehmbar ist,
wird extrahiert - nie abgetippt. Nur echte wx-Widgets werden nachgebaut.

Herausgezogen werden:
  1. Seitenstruktur der Einstellungen aus src/slic3r/GUI/Tab.cpp
     (Seite -> Gruppe -> Parameterschluessel, in Originalreihenfolge)
  2. Deutsche Beschriftungen aus resources/localization/de/PrusaSlicer_de.po
  3. Zusammensetzung der Werkzeugleiste aus src/slic3r/GUI/GLCanvas3D.cpp
  4. Die zugehoerigen SVG-Icons aus resources/icons

Was NICHT extrahiert wird, wird ausdruecklich gemeldet - damit nichts
stillschweigend verloren geht.

Aufruf:  build/scripts/extract-ui.py <prusaslicer-src> <ziel-assets-dir>
"""

import json
import os
import re
import shutil
import sys


# ----------------------------------------------------------------------
# Einstellungsseiten aus Tab.cpp
# ----------------------------------------------------------------------

RE_PAGE   = re.compile(r'add_options_page\(\s*L\("((?:[^"\\]|\\.)*)"\)\s*,\s*"([^"]*)"')
RE_GROUP  = re.compile(r'new_optgroup\(\s*L\("((?:[^"\\]|\\.)*)"\)')
RE_GROUP_EMPTY = re.compile(r'new_optgroup\(\s*""\s*\)')
RE_OPTION = re.compile(r'append_single_option_line\(\s*"([a-z0-9_]+)"')
# Zeilen, die eine Option ueber einen Variablennamen einfuegen - die
# koennen wir statisch nicht aufloesen und melden sie als Luecke.
RE_OPTION_VAR = re.compile(r'append_single_option_line\(\s*(?!")([A-Za-z_]\w*)')

# Der zweite Weg, eine Option einzutragen:
#     option = optgroup->get_option("start_gcode");
#     option.opt.is_code = true;
#     optgroup->append_single_option_line(option);
# Genau so entstehen saemtliche Custom-G-code-Felder. Ohne diese beiden
# Muster blieben Start- und End-G-code samt allen Werkzeugwechsel- und
# Farbwechsel-Feldern unsichtbar.
RE_GET_OPTION = re.compile(r'get_option\(\s*"([a-z0-9_]+)"')
RE_IS_CODE    = re.compile(r'\.opt\.is_code\s*=\s*true')

# Der dritte Weg: mehrere Optionen teilen sich eine beschriftete Zeile.
#     line = { L("Solid layers"), "" };
#     line.append_option(optgroup->get_option("top_solid_layers"));
#     line.append_option(optgroup->get_option("bottom_solid_layers"));
#     optgroup->append_line(line);
# So stehen am Desktop "Top" und "Bottom" nebeneinander unter einer
# gemeinsamen Beschriftung. Ohne dieses Muster fehlten uns unter anderem
# top_solid_layers, first_layer_temperature, bed_temperature und die
# Luefterdrehzahlen - alles andere als Randfaelle.
RE_LINE_LABEL = re.compile(r'line\s*=\s*\{\s*L\("((?:[^"\\]|\\.)*)"\)')
RE_LINE_OPT   = re.compile(r'append_option\(\s*\w+->get_option\(\s*"([a-z0-9_]+)"')

# Seitentitel, die als Formatzeichenkette gebaut werden - die Extruder-
# seiten des Druckertabs. Eine Seite je Extruder, zur Laufzeit
# vervielfaeltigt.
RE_PAGE_FMT  = re.compile(r'wxString::Format\(\s*"([^"%]*)%d([^"]*)"')
RE_PAGE_VAR  = re.compile(r'add_options_page\(\s*(?!L\()([A-Za-z_]\w*)')

# TabPrinter hat einen eigenen Helfer statt append_single_option_line.
RE_OPT_LINE  = re.compile(r'append_option_line\(\s*\w+\s*,\s*"([a-z0-9_]+)"\s*\)')
# ... und benutzt ihn in einer Achsenschleife:
#     for (const std::string &axis : axes)
#         append_option_line(optgroup, "machine_max_feedrate_" + axis);
RE_OPT_AXIS  = re.compile(r'append_option_line\(\s*\w+\s*,\s*"([a-z0-9_]+)"\s*\+\s*(\w+)\s*\)')
RE_AXES_DEF  = re.compile(r'std::vector<std::string>\s+(\w+)\s*\{([^}]*)\}')
# for (const std::string &axis : axes)  - bindet die Laufvariable an die Liste.
RE_FOR_EACH  = re.compile(r'for\s*\(\s*const\s+std::string\s*&\s*(\w+)\s*:\s*(\w+)\s*\)')


def _function_body(source: str, signature: str):
    """Rumpf einer Funktion ueber Klammerzaehlung, oder None."""
    start = source.find(signature)
    if start < 0:
        return None
    i = source.index("{", start)
    depth = 0
    for j in range(i, len(source)):
        if source[j] == "{":
            depth += 1
        elif source[j] == "}":
            depth -= 1
            if depth == 0:
                return source[i:j]
    return source[i:]


def _parse_pages(body: str, func: str, per_extruder: bool = False):
    """Seiten, Gruppen und Optionen aus einem Funktionsrumpf."""
    pages, gaps = [], []
    page = group = None
    pending = None          # ueber get_option gemerkter Schluessel
    pending_code = False
    pending_title = None    # ueber wxString::Format gemerkter Seitentitel
    line_label = None       # gemeinsame Beschriftung mehrerer Optionen

    # Listen wie  const std::vector<std::string> axes{ "x", "y", "z", "e" };
    # damit sich die Achsenschleifen der Maschinengrenzen aufloesen lassen.
    lists = {name: re.findall(r'"([a-z0-9_]+)"', items)
             for name, items in RE_AXES_DEF.findall(body)}
    # Die Laufvariable zeigt auf dieselbe Liste - im Quelltext steht
    # "... + axis", die Werte stehen aber unter "axes".
    for var, listname in RE_FOR_EACH.findall(body):
        if listname in lists:
            lists[var] = lists[listname]

    for line in body.splitlines():
        if "//" in line:
            line = line.split("//", 1)[0]

        m = RE_PAGE.search(line)
        if m:
            page = {"title": m.group(1), "icon": m.group(2), "groups": []}
            if per_extruder:
                page["per_extruder"] = True
            pages.append(page)
            group = None
            continue

        # "Extruder %d" wird eine Zeile vorher zusammengebaut und dann als
        # Variable an add_options_page gereicht.
        m = RE_PAGE_FMT.search(line)
        if m:
            pending_title = (m.group(1) + "{n}" + m.group(2)).strip()
            continue

        m = RE_PAGE_VAR.search(line)
        if m and pending_title is not None:
            page = {"title": pending_title, "icon": "funnel",
                    "groups": [], "per_extruder": True}
            pages.append(page)
            pending_title, group = None, None
            continue

        m = RE_GROUP.search(line) or RE_GROUP_EMPTY.search(line)
        if m and page is not None:
            title = m.group(1) if m.re is RE_GROUP else ""
            group = {"title": title, "options": []}
            page["groups"].append(group)
            continue

        m = RE_OPTION.search(line)
        if m and group is not None:
            group["options"].append({"key": m.group(1)})
            continue

        # "machine_max_feedrate_" + axis  ueber die Achsenliste aufloesen.
        m = RE_OPT_AXIS.search(line)
        if m and group is not None:
            prefix, listname = m.group(1), m.group(2)
            values = lists.get(listname)
            if values:
                for v in values:
                    group["options"].append({"key": prefix + v})
            else:
                gaps.append(f"{func}: Liste '{listname}' nicht aufloesbar")
            continue

        m = RE_OPT_LINE.search(line)
        if m and group is not None:
            group["options"].append({"key": m.group(1)})
            continue

        # Merken, unter welcher Beschriftung die naechsten Optionen
        # zusammenstehen - "Solid layers" ueber Top und Bottom.
        m = RE_LINE_LABEL.search(line)
        if m:
            line_label = m.group(1)
            continue

        m = RE_LINE_OPT.search(line)
        if m and group is not None:
            opt = {"key": m.group(1)}
            if line_label:
                opt["line"] = line_label
            group["options"].append(opt)
            continue

        m = RE_GET_OPTION.search(line)
        if m:
            pending, pending_code = m.group(1), False
            continue

        if pending is not None and RE_IS_CODE.search(line):
            pending_code = True
            continue

        m = RE_OPTION_VAR.search(line)
        if m:
            if pending is not None and group is not None:
                opt = {"key": pending}
                if pending_code:
                    # Mehrzeiliges G-code-Feld, kein einzeiliges Eingabefeld.
                    opt["code"] = True
                group["options"].append(opt)
                pending, pending_code = None, False
            else:
                gaps.append(f"{func}: dynamische Option ueber '{m.group(1)}'")

    return pages, gaps


def extract_tab(source: str, func: str, extra_funcs=()):
    """
    Seitenstruktur einer Tab-build()-Funktion.

    extra_funcs nennt weitere Funktionen desselben Tabs, die Seiten
    beisteuern. Beim Drucker sind das build_unregular_pages (Machine
    limits, Single extruder MM setup) und build_extruder_pages (eine
    Seite je Extruder). Ohne sie fehlt die gesamte Retraction.
    """
    signature = f"void {func}()" if "::" in func else f"void {func}::build()"
    body = _function_body(source, signature)
    if body is None:
        return None, [f"{signature} nicht gefunden"]

    pages, gaps = _parse_pages(body, func)

    for extra, per_extruder in extra_funcs:
        extra_body = _function_body(source, extra)
        if extra_body is None:
            gaps.append(f"{extra} nicht gefunden")
            continue
        more, more_gaps = _parse_pages(extra_body, extra, per_extruder)
        pages.extend(more)
        gaps.extend(more_gaps)

    # Die nachgetragenen Seiten haengen sonst hinten dran. Am Desktop
    # stehen Notes und Dependencies immer am Ende des Baums, alles andere
    # davor - siehe den Druckerbaum des XL-5T.
    tail = ("Notes", "Dependencies")
    pages.sort(key=lambda p: tail.index(p["title"]) + 1 if p["title"] in tail else 0)

    return pages, gaps


# ----------------------------------------------------------------------
# Uebersetzungen
# ----------------------------------------------------------------------

def extract_po(path: str) -> dict:
    """Liest msgid/msgstr aus einer .po-Datei, inklusive Mehrzeilern."""
    out, msgid, msgstr, state = {}, [], [], None

    def flush():
        if msgid and msgstr:
            k, v = "".join(msgid), "".join(msgstr)
            if k and v:
                out[k] = v

    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if line.startswith("msgid "):
                flush()
                msgid, msgstr, state = [unquote(line[6:])], [], "id"
            elif line.startswith("msgstr "):
                msgstr, state = [unquote(line[7:])], "str"
            elif line.startswith('"'):
                (msgid if state == "id" else msgstr).append(unquote(line))
            elif not line:
                flush()
                msgid, msgstr, state = [], [], None
    flush()
    return out


def unquote(s: str) -> str:
    s = s.strip()
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    return s.replace('\\"', '"').replace("\\n", "\n").replace("\\\\", "\\")


# ----------------------------------------------------------------------
# Werkzeugleiste
# ----------------------------------------------------------------------

RE_TB_NAME = re.compile(r'item\.name\s*=\s*"([a-z_]+)"')
RE_TB_ICON = re.compile(r'item\.icon_filename\s*=\s*"([^"]+)"')
# Tooltips stehen als _u8L("...") und sind oft mit Tastenkuerzeln
# verkettet - die Kuerzel lassen wir weg, auf dem Tablet gibt es keine.
RE_TB_TIP  = re.compile(r'item\.tooltip\s*=\s*_u8L\("((?:[^"\\]|\\.)*)"\)')


def extract_toolbar(source: str):
    """Zieht Reihenfolge, Icons und Tooltips der oberen Werkzeugleiste."""
    items, cur = [], None
    for line in source.splitlines():
        m = RE_TB_NAME.search(line)
        if m:
            cur = {"name": m.group(1), "icon": "", "tooltip": ""}
            items.append(cur)
            continue
        if cur is None:
            continue
        m = RE_TB_ICON.search(line)
        if m:
            cur["icon"] = m.group(1)
        m = RE_TB_TIP.search(line)
        if m and not cur["tooltip"]:
            cur["tooltip"] = m.group(1)
    return items


# ----------------------------------------------------------------------

def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    src, dst = sys.argv[1], sys.argv[2]
    tab_cpp = os.path.join(src, "src/slic3r/GUI/Tab.cpp")
    canvas  = os.path.join(src, "src/slic3r/GUI/GLCanvas3D.cpp")
    po_de   = os.path.join(src, "resources/localization/de/PrusaSlicer_de.po")
    icons   = os.path.join(src, "resources/icons")

    for p in (tab_cpp, canvas, po_de, icons):
        if not os.path.exists(p):
            print(f"FEHLT: {p}", file=sys.stderr)
            return 1

    os.makedirs(dst, exist_ok=True)
    all_gaps = []

    # --- Einstellungsseiten -------------------------------------------
    tab_src = open(tab_cpp, encoding="utf-8", errors="replace").read()
    tabs = {}
    # TabPrinter::build() delegiert an build_fff() bzw. build_sla() -
    # der FFF-Zweig ist der, den v1 braucht.
    #
    # Der Druckertab baut einen Teil seiner Seiten erst zur Laufzeit:
    # build_unregular_pages() liefert Machine limits und die Einrichtung
    # fuer Single-Extruder-Multimaterial, build_extruder_pages() eine
    # Seite je Extruder - und dort liegt die gesamte Retraction. Ohne
    # diese beiden Funktionen fehlten uns 326 der 573 Optionen.
    extra = {
        "printer": (("PageShp TabPrinter::build_kinematics_page", False),
                    ("void TabPrinter::build_unregular_pages",    False),
                    ("void TabPrinter::build_extruder_pages",     True)),
    }
    for key, func in (("print", "TabPrint"),
                      ("filament", "TabFilament"),
                      ("printer", "TabPrinter::build_fff")):
        pages, gaps = extract_tab(tab_src, func, extra.get(key, ()))
        all_gaps += gaps
        if pages:
            tabs[key] = pages

    with open(os.path.join(dst, "tabs.json"), "w", encoding="utf-8") as f:
        json.dump(tabs, f, ensure_ascii=False, indent=1)

    # --- Werkzeugleiste -----------------------------------------------
    tb = extract_toolbar(open(canvas, encoding="utf-8", errors="replace").read())
    with open(os.path.join(dst, "toolbar.json"), "w", encoding="utf-8") as f:
        json.dump(tb, f, ensure_ascii=False, indent=1)

    # --- Uebersetzungen -------------------------------------------------
    # Englisch braucht keine Datei: die msgid IST der englische Text und
    # damit die Standardsprache der App.
    loc_dir = os.path.join(src, "resources/localization")
    langs = {}
    for entry in sorted(os.listdir(loc_dir)):
        d = os.path.join(loc_dir, entry)
        if not os.path.isdir(d) or entry in ("wx_locale", "en"):
            continue
        po = next((os.path.join(d, f) for f in os.listdir(d)
                   if f.endswith(".po")), None)
        if po is None:
            continue
        table = extract_po(po)
        if not table:
            continue
        with open(os.path.join(dst, f"lang_{entry}.json"), "w", encoding="utf-8") as f:
            json.dump(table, f, ensure_ascii=False)
        langs[entry] = len(table)

    with open(os.path.join(dst, "languages.json"), "w", encoding="utf-8") as f:
        json.dump({"default": "en", "available": ["en"] + sorted(langs)},
                  f, ensure_ascii=False, indent=1)
    de = langs

    # --- Icons ---------------------------------------------------------
    icon_dir = os.path.join(dst, "icons")
    os.makedirs(icon_dir, exist_ok=True)
    wanted = {i["icon"] for i in tb if i["icon"]}
    for page_list in tabs.values():
        for p in page_list:
            if p["icon"]:
                wanted.add(p["icon"] + ".svg")
    # Gizmo-Icons kommen aus GLGizmosManager und heissen wie die Werkzeuge
    wanted |= {f"{n}.svg" for n in
               ("move", "scale", "rotate", "place", "cut", "measure",
                "fdm_supports", "seam", "mmu_segmentation")}

    copied, missing = 0, []
    for name in sorted(wanted):
        srcp = os.path.join(icons, name)
        if os.path.exists(srcp):
            shutil.copy2(srcp, os.path.join(icon_dir, name))
            copied += 1
        else:
            missing.append(name)

    # --- Bericht -------------------------------------------------------
    n_pages = sum(len(v) for v in tabs.values())
    n_opts = sum(len(g["options"])
                 for v in tabs.values() for p in v for g in p["groups"])
    print(f"Seiten:        {n_pages}")
    print(f"Parameter:     {n_opts}")
    print(f"Werkzeuge:     {len(tb)}")
    print(f"Sprachen:      en (Standard) + {len(de)} uebersetzte "
          f"({sum(de.values())} Eintraege gesamt)")
    print(f"Icons:         {copied} kopiert")

    if missing:
        print(f"\nNicht gefundene Icons ({len(missing)}): {', '.join(missing[:10])}")
    if all_gaps:
        # Bewusst laut: was hier steht, fehlt in der App und muesste
        # von Hand ergaenzt werden.
        print(f"\nNicht uebernommen ({len(all_gaps)}):")
        for g in sorted(set(all_gaps))[:15]:
            print(f"  - {g}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
