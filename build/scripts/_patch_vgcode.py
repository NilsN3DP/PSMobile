#!/usr/bin/env python3
"""
Zwei Reparaturen, damit die G-Code-Vorschau ohne GUI und unter GLES baut.

1. libvgcode: Der ES-Pfad laedt Positionen und Hoehen/Breiten/Winkel als
   RGB32F mit Vec3-Schrittweite. Der Desktop-Pfad liefert seit 2.9
   aber Vec4. Das ist ein Fehler im weniger benutzten ES-Zweig.
   Die ES-Shader lesen ohnehin nur `.xyz`, deshalb genuegt die
   Umstellung auf RGBA32F - kein Shader muss angefasst werden.

2. LibVGCodeWrapper: Eine einzige Funktion wandelt einen GUI-Enum um und
   zieht dafuer wx ueber GUI_Preview.hpp herein. Wir brauchen sie nicht.
"""
import os
import sys

PS = os.path.join(os.path.dirname(__file__), "..", "..", "external/PrusaSlicer")

# --- 1. libvgcode: ES-Texturen auf RGBA ------------------------------
# Der GLES-Pfad arbeitet mit vier Float-Komponenten pro Texel. Upstream
# definiert nur Vec3; der Alias hält die Signaturen und lokalen Vektoren
# konsistent, ohne die Desktop-Datenstruktur zu verändern.
p = os.path.join(PS, "src/libvgcode/include/Types.hpp")
s = open(p, encoding="utf-8").read()
types_before = s
s = s.replace(
    "using Vec3 = std::array<float, 3>;",
    "using Vec3 = std::array<float, 3>;\nusing Vec4 = std::array<float, 4>;",
)
if s == types_before:
    if "using Vec4 = std::array<float, 4>;" in s:
        print("libvgcode: Vec4 bereits definiert")
    else:
        print("WARNUNG: Types.hpp unveraendert - Vec3-Alias nicht gefunden")
else:
    print("libvgcode: Vec4 fuer GLES-Texturen definiert")
open(p, "w", encoding="utf-8").write(s)

p = os.path.join(PS, "src/libvgcode/src/ViewerImpl.hpp")
s = open(p, encoding="utf-8").read()
before = s
s = s.replace("void set_positions(const std::vector<Vec3>& positions);",
              "void set_positions(const std::vector<Vec4>& positions);")
s = s.replace(
    "void set_heights_widths_angles(const std::vector<Vec3>& heights_widths_angles);",
    "void set_heights_widths_angles(const std::vector<Vec4>& heights_widths_angles);")
if s == before:
    print("WARNUNG: ViewerImpl.hpp unveraendert - Signaturen nicht gefunden")
open(p, "w", encoding="utf-8").write(s)

p = os.path.join(PS, "src/libvgcode/src/ViewerImpl.cpp")
s = open(p, encoding="utf-8").read()
s = s.replace("void ViewerImpl::TextureData::set_positions(const std::vector<Vec3>& positions)",
              "void ViewerImpl::TextureData::set_positions(const std::vector<Vec4>& positions)")
s = s.replace(
    "void ViewerImpl::TextureData::set_heights_widths_angles(const std::vector<Vec3>& heights_widths_angles)",
    "void ViewerImpl::TextureData::set_heights_widths_angles(const std::vector<Vec4>& heights_widths_angles)")
# Texturformat und Schrittweite mitziehen
n_fmt = s.count("GL_RGB32F")
s = s.replace("GL_RGB32F", "GL_RGBA32F")
s = s.replace("0, GL_RGB, GL_FLOAT", "0, GL_RGBA, GL_FLOAT")
s = s.replace("GL_RGB, GL_FLOAT, &positions", "GL_RGBA, GL_FLOAT, &positions")
s = s.replace("GL_RGB, GL_FLOAT, &heights_widths_angles", "GL_RGBA, GL_FLOAT, &heights_widths_angles")
n_sz = s.count("positions.second * sizeof(Vec3)")
s = s.replace("positions.second * sizeof(Vec3)", "positions.second * sizeof(Vec4)")
s = s.replace("heights_widths_angles.second * sizeof(Vec3)",
              "heights_widths_angles.second * sizeof(Vec4)")
s = s.replace("std::vector<Vec3> heights_widths_angles;",
              "std::vector<Vec4> heights_widths_angles;")
open(p, "w", encoding="utf-8").write(s)
print("libvgcode: %d Texturformate, %d Groessenangaben umgestellt" % (n_fmt, n_sz))

# --- 2. Wrapper: GUI-Enum-Umwandlung herausnehmen --------------------
#
# Der Header ist Teil des mobilen Viewport-Übersetzungswegs. Wird nur die
# Implementierung geguardet, zieht der Header weiterhin GUI_Preview.hpp und
# damit wx/panel.h ein, bevor PSM_NO_GUI_TYPES wirken kann.
p = os.path.join(PS, "src/slic3r/GUI/LibVGCode/LibVGCodeWrapper.hpp")
s = open(p, encoding="utf-8").read()
header_before = s
s = s.replace(
    '#include "slic3r/GUI/GUI_Preview.hpp"',
    '#ifndef PSM_NO_GUI_TYPES\n#include "slic3r/GUI/GUI_Preview.hpp"\n#endif',
)
s = s.replace(
    'extern EOptionType convert(const Slic3r::GUI::Preview::OptionType& type);',
    '#ifndef PSM_NO_GUI_TYPES\n'
    'extern EOptionType convert(const Slic3r::GUI::Preview::OptionType& type);\n'
    '#endif',
)
if s == header_before:
    if "#ifndef PSM_NO_GUI_TYPES\n#include \"slic3r/GUI/GUI_Preview.hpp\"" in s:
        print("Wrapper-Header: GUI-Enum bereits geguardet")
    else:
        print("WARNUNG: LibVGCodeWrapper.hpp unveraendert - GUI-Signaturen nicht gefunden")
else:
    print("Wrapper-Header: wx-abhängigen GUI-Enum geguardet")
open(p, "w", encoding="utf-8").write(s)

p = os.path.join(PS, "src/slic3r/GUI/LibVGCode/LibVGCodeWrapper.cpp")
s = open(p, encoding="utf-8").read()
s = s.replace('#include "../GUI_Preview.hpp"',
              '#ifndef PSM_NO_GUI_TYPES\n#include "../GUI_Preview.hpp"\n#endif')

signature = "EOptionType convert(const Slic3r::GUI::Preview::OptionType& type)"
start = s.find(signature)
already_guarded = "#ifndef PSM_NO_GUI_TYPES\n" + signature in s
if already_guarded:
    print("Wrapper: GUI-Enum-Umwandlung bereits ausgeklammert")
elif start > 0:
    # Funktionsrumpf ueber Klammerzaehlung abgrenzen
    i = s.index("{", start)
    depth, end = 0, len(s)
    for j in range(i, len(s)):
        if s[j] == "{":
            depth += 1
        elif s[j] == "}":
            depth -= 1
            if depth == 0:
                end = j + 1
                break
    s = (s[:start]
         + "#ifndef PSM_NO_GUI_TYPES\n" + s[start:end] + "\n#endif // PSM_NO_GUI_TYPES\n"
         + s[end:])
    print("Wrapper: GUI-Enum-Umwandlung ausgeklammert")
else:
    print("WARNUNG: convert(Preview::OptionType) nicht gefunden")

open(p, "w", encoding="utf-8").write(s)
