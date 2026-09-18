#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Paritaetswaechter: haelt die Android-Oberflaeche auf dem iOS-Stand.

Seit dem 08.09.2026 ist die Android-Oberflaeche keine eigenstaendige
Implementierung mehr, sondern eine zeilennahe Uebersetzung der iOS-App
(siehe docs/twin-conventions.md). Diese Regel haelt nur, wenn
sie gemessen wird - Disziplin allein hat sie ein Vierteljahr lang nicht
gehalten.

Geprueft wird, was sich objektiv vergleichen laesst:

  1. ZWILLINGE   Jede Swift-Ansicht hat eine gleichnamige Kotlin-Datei
                 und umgekehrt.
  2. KENNUNGEN   Jedes accessibilityIdentifier hat ein gleichlautendes
                 testTag. Das ist der Paritaetsnachweis auf Bedienebene:
                 was ein iOS-Test findet, findet ein Android-Test auch.
  3. TEXTE       Jedes st("english", "deutsch") aus Swift kommt im
                 Kotlin-Zwilling zeichengleich vor. Faengt umformulierte
                 Beschriftungen - die Art von Abweichung, die niemand
                 bemerkt, bis der Nutzer sie meldet.
  4. NAMEN       Jedes oeffentliche `struct XyView: View` hat ein
                 oeffentliches `fun XyView(`.
  5. TESTS       Jede iOS-Oberflaechentestdatei hat einen Android-
                 Zwilling. Ohne das war die Uebersetzung zwar
                 zeilennah, aber nur auf einer Seite nachgerechnet: am
                 11.09.2026 standen 27 iOS-Testdateien einer einzigen
                 Android-Datei gegenueber, und niemandem fiel es auf.
  6. SELBSTTEST  Der eingebaute Selbsttest hat auf beiden Seiten
                 dieselben Schritte in derselben Reihenfolge. Am
                 12.09.2026 waren es 23 gegen 16, mit drei Schritten,
                 die nur einem Entwickler-Rechner galten.
  7. VERSION     versionName (Gradle) und CFBundleShortVersionString
                 (Info.plist) sind dieselbe Zeichenkette, versionCode
                 und CFBundleVersion dieselbe Zahl. Am 12.09.2026
                 standen 0.2.0 (18) gegen 0.1.7.9 (17).

Nicht geprueft (und das ist Absicht): Layoutmasse, Reihenfolge,
Verhalten. Dafuer gibt es den Blick auf den Bildschirm. Der Waechter
faengt das Mechanische, damit die Durchsicht sich auf das Inhaltliche
beschraenken kann.

Aufruf:
    python tools/paritaet_pruefen.py            # alles pruefen
    python tools/paritaet_pruefen.py --geaendert # nur geaenderte Dateien (CI/Hook)
    python tools/paritaet_pruefen.py --basis HEAD~1

Rueckgabe 0 = in Ordnung, 1 = Abweichungen gefunden.
"""

import argparse
import os
import re
import subprocess
import sys

WURZEL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Swift-Ansichten, die uebersetzt werden. Alles darunter muss einen
# Kotlin-Zwilling haben.
SWIFT_ORTE = [
    "ios/PSMobile/Screens",
    "ios/PSMobile/UI",
]
KOTLIN_ORT = "android/app/src/main/java/de/psmobile/ui"

# Oberflaechentests. Ein iOS-Test "XyUITests.swift" hat seinen Zwilling
# in "XyUITest.kt" - Singular, weil Kotlin-Klassen so heissen.
SWIFT_TEST_ORT = "ios/PSMobileUITests"
KOTLIN_TEST_ORT = "android/app/src/androidTest/java/de/psmobile/ui"

# iOS-Testdateien ohne Android-Zwilling, mit Begruendung. Wer hier etwas
# eintraegt, schuldet einen Satz dazu.
OHNE_ZWILLING_TESTS = {
    "Eingabehilfe.swift":
        "keine Testklasse, sondern XCUIElement.ersetzeText - das Gegenstueck "
        "ist PsmUiTest.eingabe() (Compose ersetzt Text ohne Umweg ueber ein Menue)",
    "SauberStarten.swift":
        "keine Testklasse, sondern die Starthilfe dafuer",
    "BildschirmfotoTests.swift":
        "sammelt Bilder fuers Auge; das Android-Gegenstueck ist "
        "tools/rundgang-android.sh",
    "ScreenshotTourUITests.swift":
        "derselbe Rundgang laeuft auf Android als tools/rundgang-android.sh",
    "FullTourUITests.swift":
        "Bildersammler ohne Zusicherungen; deckungsgleich mit dem Rundgang",
    "MultiBedViewportUITests.swift":
        "ausdruecklich eine Sichtpruefung - die Bilder beurteilt ein Mensch",
    "KegelUntersuchungTests.swift":
        "Untersuchung fuer eine Nachtsitzung, kein Vertragstest (steht so drin)",
}

# Swift-Dateien ohne Kotlin-Zwilling, mit Begruendung. Wer hier etwas
# eintraegt, schuldet einen Satz dazu.
OHNE_ZWILLING_SWIFT = {
    "PSScale.swift": "Fundament: liegt als ui/PsScale.kt neben den Bildschirmen",
    "PrusaColors.swift": "Farben stehen im Android-Theme (ui/theme/Theme.kt)",
    "PsUiCatalog.swift": "in ui/Texte.kt zusammen mit st() und PSMarke",
    "PreviewRange.swift": "auf Android im gemeinsamen Modul (shared/rules/PreviewRange.kt)",
}

# Kotlin-Dateien ohne Swift-Vorlage: Android-Infrastruktur, die es auf
# iOS so nicht gibt. Jede Zeile ist eine bewusste Abweichung.
OHNE_VORLAGE_KOTLIN = {
    "SceneView.kt": "GLSurfaceView + GL-Thread; iOS nutzt dafuer PSMGLView",
    "ViewportView.kt": "Compose-Huelle um SceneView, Gegenstueck zu Viewport/ViewportView.swift",
    "SfSymbol.kt": "SF-Symbol-Namen auf Material-Icons abbilden",
    "PsUi.kt": "laedt assets/psui; iOS liest dieselben Daten aus dem Bundle",
    "PsIcon.kt": "SVG-Icons aus den Assets zeichnen",
    "QrScanner.kt": "CameraX + ML Kit; iOS nutzt AVFoundation in QRPairing.swift",
    "QrCodeBild.kt": "QR-Bild erzeugen; iOS nutzt CoreImage in QRPairing.swift",
    "AndroidBedStripAdapter.kt": "Adapter auf BedStripContract im gemeinsamen Modul",
    "AppSettingsStore.kt": "SharedPreferences; iOS-Gegenstueck steht in AppSettingsView.swift",
    "Texte.kt": "st(), PsUiCatalog, PSMarke - auf iOS auf drei Dateien verteilt",
    "PsScale.kt": "Gegenstueck zu UI/PSScale.swift",
    "Teilen.kt": "Android-Teilen-Dialog; iOS nutzt ShareLink",
    "BedLockPolicy.kt": "Regel ohne iOS-Gegenstueck",
    "PrinterReadyPolicy.kt": "Gegenstueck zu Core/PrinterReadyPolicy.swift",
    "PrinterCompatibilityPolicy.kt": "Gegenstueck zu Core/PrinterCompatibilityPolicy.swift",
    "Startargumente.kt": "Intent-Extras statt ProcessInfo.arguments; iOS liest sie direkt",
    "ProjektSpiegel.kt": "Kopie nach Documents/ per MediaStore; iOS-Projekte sind ueber die Dateien-App ohnehin sichtbar",
}


# Swift-Typen, die auf Android bewusst woanders stehen. Schluessel ist
# der Swift-Dateiname, Wert die Menge der dort erlaubten Auslassungen.
ANDERSWO_DEKLARIERT = {
    "SimpleModeView.swift": {
        # PSMarke ist eine 1x1-Marke fuer die Bedienungshilfen. Auf iOS
        # steht sie oben in SimpleModeView, auf Android in Texte.kt bei
        # st() und PsUiCatalog - sie gehoert zu keinem Bildschirm.
        "PSMarke",
    },
}


def lies(pfad):
    with open(pfad, encoding="utf-8", errors="replace") as f:
        return f.read()


def normiere_kennung(k):
    """Swift schreibt "bed.card.\\(index)", Kotlin "bed.card.$index".

    Beide werden auf den festen Anfang gekuerzt, damit interpolierte
    Kennungen vergleichbar bleiben.
    """
    k = k.split("\\(")[0]
    k = k.split("$")[0]
    return k.rstrip(".")


def kennungen_swift(text):
    roh = re.findall(r'accessibilityIdentifier\(\s*"([^"]*)"', text)
    return {normiere_kennung(k) for k in roh if normiere_kennung(k)}


def kennungen_kotlin(text):
    """Jede Zeichenkette der Datei, auf den festen Anfang normiert.

    Bewusst nicht nur `testTag("x")`: die portierten Bildschirme reichen
    Kennungen an Hilfsfunktionen weiter, mal benannt (`kennung = "x"`),
    mal positional (`teilenKnopf(url, "x")`). Wer nur die eine Form
    kennt, meldet die andere faelschlich als fehlend - und ein Waechter,
    der falsch meldet, wird nach der dritten Meldung ignoriert.

    Es genuegt, dass die Kennung in der Zwillingsdatei vorkommt. Ob sie
    wirklich am richtigen Element haengt, beweist der Oberflaechentest,
    nicht dieser Textvergleich.
    """
    treffer = set()
    for m in re.findall(r'"((?:[^"\\]|\\.)*)"', text):
        n = normiere_kennung(m)
        if n:
            treffer.add(n)
    return treffer


def normiere_text(t):
    """Interpolation auf einen Platzhalter bringen.

    Swift schreibt "Bett \\(i + 1)", Kotlin "Bett ${i + 1}" oder "Bett $i".
    Verglichen wird der feste Text drumherum - der Ausdruck darin ist
    Sprachsache und darf sich unterscheiden.
    """
    t = _klammern_ersetzen(t, "\\(", "(", ")")   # Swift  \(Int((x).y()))
    t = _klammern_ersetzen(t, "${", "{", "}")    # Kotlin ${x.y()}
    t = re.sub(r'\$[A-Za-z_]\w*', '{}', t)       # Kotlin $variable
    return t


def _klammern_ersetzen(t, anfang, auf, zu):
    """Ersetzt `anfang…zu` durch {}, auch bei verschachtelten Klammern.

    Ein einfaches [^)]* bricht bei `\\(Int((x * 100).rounded()))` an der
    ersten schliessenden Klammer ab und laesst Reste stehen - genau die
    Sorte Fehlalarm, die einen Waechter unglaubwuerdig macht.
    """
    ergebnis = []
    i = 0
    while i < len(t):
        if t.startswith(anfang, i):
            tiefe = 0
            j = i + len(anfang) - 1          # steht auf der oeffnenden Klammer
            while j < len(t):
                if t[j] == auf:
                    tiefe += 1
                elif t[j] == zu:
                    tiefe -= 1
                    if tiefe == 0:
                        break
                j += 1
            ergebnis.append("{}")
            i = j + 1
        else:
            ergebnis.append(t[i])
            i += 1
    return "".join(ergebnis)


def texte(text):
    """Alle st("english", "deutsch")-Paare einer Datei, interpolationsnormiert."""
    # Das `,?` vor der schliessenden Klammer ist kein Schoenheitsfehler:
    # Kotlin schreibt mehrzeilige Aufrufe mit abschliessendem Komma
    # (`st(\n  "en",\n  "de",\n)`). Ohne das findet der Waechter genau die
    # laengeren Texte nicht - also die, bei denen sich eine Umformulierung
    # am ehesten einschleicht.
    muster = re.compile(
        r'\bst\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,?\s*\)'
    )
    return {(normiere_text(m.group(1)), normiere_text(m.group(2)))
            for m in muster.finditer(text)}


def swift_ansichten(text):
    return set(re.findall(r'^struct\s+([A-Za-z_]\w*)\s*:\s*View\b', text, re.M))


def kotlin_composables(text):
    return set(re.findall(r'^fun\s+([A-Za-z_]\w*)\s*\(', text, re.M))


def swift_dateien():
    for ort in SWIFT_ORTE:
        voll = os.path.join(WURZEL, ort)
        if not os.path.isdir(voll):
            continue
        for name in sorted(os.listdir(voll)):
            if name.endswith(".swift"):
                yield ort, name


def kotlin_pfad(name):
    return os.path.join(WURZEL, KOTLIN_ORT, name[:-6] + ".kt")


def geaenderte_dateien(basis):
    """Dateien, die sich gegenueber `basis` geaendert haben."""
    try:
        aus = subprocess.run(
            ["git", "diff", "--name-only", basis],
            cwd=WURZEL, capture_output=True, text=True, check=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    offen = subprocess.run(
        ["git", "diff", "--name-only", "HEAD"],
        cwd=WURZEL, capture_output=True, text=True,
    ).stdout
    return {z.strip().replace("\\", "/") for z in (aus + offen).splitlines() if z.strip()}


def alle_android_texte():
    """Jeder englische Text, der irgendwo im Android-Baum steht.

    Deckt st(), Bilingual(english = ...) und schlichte Zeichenketten ab -
    das gemeinsame Modul liefert manche Beschriftung, die iOS in der
    Ansicht selbst stehen hat.
    """
    # Nur das gemeinsame Modul und das Modell. Bewusst NICHT der ganze
    # Oberflaechenbaum: sonst deckt irgendein anderer Bildschirm, der
    # zufaellig dasselbe Wort fuehrt, eine umformulierte Beschriftung zu -
    # und genau die soll dieser Waechter finden.
    orte = [
        os.path.join(WURZEL, "android/shared/src/commonMain/kotlin/de/psmobile/shared"),
        os.path.join(WURZEL, "android/app/src/main/java/de/psmobile/SlicerModel.kt"),
    ]
    gefunden = set()

    def aufnehmen(pfad):
        for m in re.findall(r'"((?:[^"\\]|\\.)*)"', lies(pfad)):
            n = normiere_text(m)
            if n:
                gefunden.add(n)

    for ort in orte:
        if os.path.isfile(ort):
            aufnehmen(ort)
            continue
        for wurzel, _, dateien in os.walk(ort):
            for d in dateien:
                if d.endswith(".kt"):
                    aufnehmen(os.path.join(wurzel, d))
    return gefunden


ANDROID_ALLE_TEXTE = set()


def main():
    # Die Windows-Konsole liefert cp1252; ein Pfeil oder Umlaut aus den
    # Beschriftungen wuerde den Waechter sonst mit einem Kodierungsfehler
    # beenden statt seinen Befund zu zeigen.
    for strom in (sys.stdout, sys.stderr):
        try:
            strom.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--geaendert", action="store_true",
                   help="nur Dateien pruefen, die sich gegenueber --basis geaendert haben")
    p.add_argument("--basis", default="origin/main",
                   help="Vergleichsstand fuer --geaendert (Vorgabe: origin/main)")
    p.add_argument("--still", action="store_true", help="nur Abweichungen ausgeben")
    args = p.parse_args()

    nur = None
    if args.geaendert:
        nur = geaenderte_dateien(args.basis)
        if nur is None:
            print("Hinweis: kein Git-Vergleich moeglich, pruefe alles.")

    global ANDROID_ALLE_TEXTE
    ANDROID_ALLE_TEXTE = alle_android_texte()

    fehler = []
    hinweise = []
    geprueft = 0
    kennungen_gesamt = 0
    texte_gesamt = 0

    # --- 1. Zwillinge: Swift -> Kotlin ---------------------------------
    for ort, name in swift_dateien():
        swift_pfad = os.path.join(ort, name)
        kt = kotlin_pfad(name)
        if not os.path.exists(kt):
            if name not in OHNE_ZWILLING_SWIFT:
                fehler.append(
                    "ZWILLING FEHLT  %s\n"
                    "                hat keine Datei %s/%s.kt.\n"
                    "                Entweder uebersetzen oder in OHNE_ZWILLING_SWIFT\n"
                    "                mit Begruendung eintragen."
                    % (swift_pfad, KOTLIN_ORT, name[:-6])
                )
            continue

        if nur is not None:
            kt_rel = os.path.relpath(kt, WURZEL).replace("\\", "/")
            if swift_pfad not in nur and kt_rel not in nur:
                continue

        geprueft += 1
        s = lies(os.path.join(WURZEL, swift_pfad))
        k = lies(kt)

        # --- 2. Kennungen ---------------------------------------------
        ks, kk = kennungen_swift(s), kennungen_kotlin(k)
        kennungen_gesamt += len(ks)
        fehlend = sorted(ks - kk)
        if fehlend:
            fehler.append(
                "KENNUNG FEHLT   %s -> %s.kt\n"
                "                %s"
                % (name, name[:-6], ", ".join(fehlend))
            )

        # --- 3. Texte -------------------------------------------------
        # Ein Text darf im Zwilling fehlen, wenn Android ihn aus dem
        # gemeinsamen Modul bezieht (Bilingual/PreviewRoles/AppSettings).
        # Fehlt er im ganzen Baum, hat Android eine andere Beschriftung -
        # und das faellt dem Nutzer auf, nicht uns.
        ts, tk = texte(s), texte(k)
        texte_gesamt += len(ts)
        offen = sorted(ts - tk)
        fehlend, anderswo = [], []
        for paar in offen:
            if paar[0] and paar[0] in ANDROID_ALLE_TEXTE:
                anderswo.append(paar)
            else:
                fehlend.append(paar)
        if fehlend:
            zeilen = ["TEXT FEHLT      %s -> %s.kt" % (name, name[:-6])]
            for en, de in fehlend[:8]:
                zeilen.append('                st("%s", "%s")' % (en, de))
            if len(fehlend) > 8:
                zeilen.append("                ... und %d weitere"
                              % (len(fehlend) - 8))
            fehler.append("\n".join(zeilen))
        if anderswo and not args.still:
            hinweise.append(
                "  %s: %d Text(e) kommen aus dem gemeinsamen Modul statt aus der Datei"
                % (name[:-6] + ".kt", len(anderswo))
            )

        # --- 4. Oeffentliche Namen ------------------------------------
        ns, nk = swift_ansichten(s), kotlin_composables(k)
        ns -= ANDERSWO_DEKLARIERT.get(name, set())
        fehlende_namen = sorted(ns - nk)
        if fehlende_namen:
            fehler.append(
                "NAME FEHLT      %s -> %s.kt\n"
                "                %s"
                % (name, name[:-6], ", ".join(fehlende_namen))
            )

    # --- 1c. Zwillinge muessen zusammen wandern ------------------------
    # Die schaerfste Regel und der eigentliche Grund fuer dieses Werkzeug:
    # Wer eine iOS-Ansicht aendert und den Zwilling stehen laesst, hat die
    # Apps auseinandergebracht - auch wenn Kennungen und Texte noch
    # zufaellig passen (Layout, Reihenfolge, Verhalten misst niemand).
    if nur is not None:
        for ort, name in swift_dateien():
            swift_rel = "%s/%s" % (ort, name)
            if swift_rel not in nur:
                continue
            kt = kotlin_pfad(name)
            if not os.path.exists(kt):
                continue        # schon unter ZWILLING FEHLT gemeldet
            kt_rel = os.path.relpath(kt, WURZEL).replace("\\", "/")
            if kt_rel not in nur:
                fehler.append(
                    "ZWILLING STEHT  %s wurde geaendert, %s nicht.\n"
                    "                Die Aenderung gehoert in beide Dateien - sonst\n"
                    "                faengt das Auseinanderdriften wieder an.\n"
                    "                Ist die Aenderung wirklich iOS-only (etwa ein\n"
                    "                Kommentar), reicht eine Zeile im Kotlin-Zwilling,\n"
                    "                die das festhaelt."
                    % (swift_rel, kt_rel)
                )

    # --- 1b. Zwillinge: Kotlin -> Swift --------------------------------
    kot_dir = os.path.join(WURZEL, KOTLIN_ORT)
    swift_namen = {n for _, n in swift_dateien()}
    if os.path.isdir(kot_dir):
        for name in sorted(os.listdir(kot_dir)):
            if not name.endswith(".kt"):
                continue
            if name[:-3] + ".swift" in swift_namen:
                continue
            if name in OHNE_VORLAGE_KOTLIN:
                continue
            fehler.append(
                "VORLAGE FEHLT   %s/%s\n"
                "                hat keine Swift-Vorlage. Eine neue Android-Ansicht\n"
                "                ohne iOS-Gegenstueck bringt die Apps auseinander.\n"
                "                Entweder auf iOS bauen oder in OHNE_VORLAGE_KOTLIN\n"
                "                mit Begruendung eintragen."
                % (KOTLIN_ORT, name)
            )

    # --- 5. Tests: iOS -> Android --------------------------------------
    #
    # Nur beim vollen Lauf: beim Hook-Lauf ueber geaenderte Dateien waere
    # die Frage "gibt es den Zwilling" nicht beantwortbar, ohne alles zu
    # lesen - und sie gehoert ohnehin in die Gesamtschau.
    if nur is None:
        swift_test_dir = os.path.join(WURZEL, SWIFT_TEST_ORT)
        kot_test_dir = os.path.join(WURZEL, KOTLIN_TEST_ORT)
        if os.path.isdir(swift_test_dir) and os.path.isdir(kot_test_dir):
            kot_tests = {n for n in os.listdir(kot_test_dir) if n.endswith(".kt")}
            for name in sorted(os.listdir(swift_test_dir)):
                if not name.endswith(".swift"):
                    continue
                if name in OHNE_ZWILLING_TESTS:
                    continue
                # XyUITests.swift -> XyUITest.kt
                stamm = name[:-len(".swift")]
                erwartet = (stamm[:-1] if stamm.endswith("s") else stamm) + ".kt"
                if erwartet in kot_tests:
                    continue
                fehler.append(
                    "TESTZWILLING FEHLT  %s/%s\n"
                    "                    erwartet %s/%s.\n"
                    "                    Ein Test nur auf einer Seite heisst: die\n"
                    "                    andere ist an dieser Stelle unbelegt.\n"
                    "                    Entweder portieren oder in\n"
                    "                    OHNE_ZWILLING_TESTS mit Begruendung eintragen."
                    % (SWIFT_TEST_ORT, name, KOTLIN_TEST_ORT, erwartet[:-3])
                )

    # --- 6. SELBSTTEST: dieselben Schritte, dieselbe Reihenfolge --------
    if nur is None:
        swift_st = os.path.join(WURZEL, "ios", "PSMobile", "Selbsttest.swift")
        kot_st = os.path.join(WURZEL, "android", "app", "src", "main", "java", "de", "psmobile", "diagnose", "Selbsttest.kt")
        if os.path.isfile(swift_st) and os.path.isfile(kot_st):
            muster = re.compile(r'schritt\("([^"]*)"')
            def schritte(text, platzhalter):
                return [platzhalter.sub("N", m) for m in muster.findall(text)]
            ios_schritte = schritte(lies(swift_st), re.compile(r"\\\([^)]*\)"))
            and_schritte = schritte(lies(kot_st), re.compile(r"\$\{?[A-Za-z_][A-Za-z0-9_.]*\}?"))
            if ios_schritte != and_schritte:
                nur_ios = [x for x in ios_schritte if x not in and_schritte]
                nur_and = [x for x in and_schritte if x not in ios_schritte]
                fehler.append(
                    "SELBSTTEST WEICHT AB  %d Schritte iOS, %d Android.\n"
                    "                    nur iOS:     %s\n"
                    "                    nur Android: %s\n"
                    "                    (oder dieselben Schritte in anderer Reihenfolge)\n"
                    "                    Ein Schritt, den nur eine Seite geht, prueft nur\n"
                    "                    eine Seite."
                    % (len(ios_schritte), len(and_schritte),
                       ", ".join(nur_ios) or "-", ", ".join(nur_and) or "-")
                )

    # --- 7. VERSION: beide Apps melden sich als dieselbe Version --------
    if nur is None:
        gradle = os.path.join(WURZEL, "android", "app", "build.gradle.kts")
        plist = os.path.join(WURZEL, "ios", "PSMobile", "Support", "Info.plist")
        if os.path.isfile(gradle) and os.path.isfile(plist):
            g = lies(gradle)
            p = lies(plist)
            g_name = re.search(r'versionName\s*=\s*"([^"]+)"', g)
            g_code = re.search(r'versionCode\s*=\s*(\d+)', g)
            p_name = re.search(r'<key>CFBundleShortVersionString</key>\s*<string>([^<]+)</string>', p)
            p_code = re.search(r'<key>CFBundleVersion</key>\s*<string>([^<]+)</string>', p)
            if g_name and p_name and g_name.group(1) != p_name.group(1):
                fehler.append(
                    "VERSION WEICHT AB   Android versionName %s, iOS CFBundleShortVersionString %s.\n"
                    "                    Beide Apps sollen sich als dieselbe Version melden."
                    % (g_name.group(1), p_name.group(1)))
            if g_code and p_code and g_code.group(1) != p_code.group(1):
                fehler.append(
                    "BUILDNUMMER WEICHT AB  Android versionCode %s, iOS CFBundleVersion %s."
                    % (g_code.group(1), p_code.group(1)))

    # --- Bericht -------------------------------------------------------
    if not args.still:
        umfang = "geaenderte" if nur is not None else "alle"
        print("Paritaet iOS -> Android (%s Dateien)" % umfang)
        print("  Zwillingspaare geprueft : %d" % geprueft)
        print("  Kennungen verglichen    : %d" % kennungen_gesamt)
        print("  Textpaare verglichen    : %d" % texte_gesamt)
        print()

    if hinweise and not args.still:
        print("Bewusste Abweichungen (Text aus dem gemeinsamen Modul):")
        for h in sorted(set(hinweise)):
            print(h)
        print()

    if fehler:
        print("%d Abweichung(en):" % len(fehler))
        print()
        for f in fehler:
            print(f)
            print()
        print("Regeln: docs/twin-conventions.md")
        return 1

    if not args.still:
        print("Keine Abweichungen.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
