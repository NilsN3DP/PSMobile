# -*- coding: utf-8 -*-
"""Bestandsaufnahme, gemessen statt behauptet.

Anlass: ich habe an einem Tag dreimal aus dem Gedächtnis „das fehlt"
gesagt und dreimal danebengelegen — einmal war der Volumen-Baum längst
da, einmal Androids Materialauswahl, und einmal habe ich eine
ABI-Funktion doppelt gebaut, weil ich nach dem falschen Namen suchte.

Also nicht mehr aus dem Kopf. Dieses Skript liest den Baum und zählt.
Was es misst:

* Jede ``PSM_API``-Funktion und ob iOS, Android oder keiner sie ruft.
  Das ist die härteste Zahl im Projekt: eine Funktion, die niemand
  ruft, ist entweder tot oder eine Lücke in einer Oberfläche.
* Jede Regel im gemeinsamen Modul und wer sie benutzt. Eine Regel, die
  nur eine Seite fragt, ist noch keine gemeinsame.
* Bildschirme, Zeilen und Tests je Plattform.

Das Ergebnis geht als Markdown nach docs/. Es ist eine Momentaufnahme
und veraltet mit dem nächsten Commit — deshalb steht der Commit drin,
zu dem es gehört.
"""
import io
import os
import re
import subprocess

W = os.environ.get("PSM_REPO_ROOT", os.getcwd()) + "/"


def lies(pfad):
    try:
        return io.open(W + pfad, encoding="utf-8", errors="replace").read()
    except OSError:
        return ""


def dateien(ordner, endung, ausser=()):
    treffer = []
    for wurzel, verzeichnisse, namen in os.walk(W + ordner):
        verzeichnisse[:] = [v for v in verzeichnisse
                            if v not in ("build", "build-out", ".git", "external")]
        for name in namen:
            if name.endswith(endung) and not any(a in wurzel for a in ausser):
                treffer.append(os.path.join(wurzel, name))
    return sorted(treffer)


def zeilen(pfade):
    n = 0
    for p in pfade:
        try:
            n += sum(1 for _ in io.open(p, encoding="utf-8", errors="replace"))
        except OSError:
            pass
    return n


# --- Alles, was die Oberflaechen rufen koennten --------------------------
kopf = lies("core/include/psmobile_core.h") + lies("viewport/include/psm_viewport.h")
api = sorted(set(re.findall(r"PSM_API\s+[A-Za-z_0-9 *]+?\s(\bpsm_[a-z_0-9]+)\s*\(", kopf)))

def ohne_kommentare(text):
    """Ein Name in einem Kommentar ist kein Aufruf.

    Ohne das zaehlt die Doku sich selbst als Benutzer - und gerade die
    Kopfdateien nennen jede Funktion in ihrer eigenen Beschreibung.
    """
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


ios_text = ohne_kommentare(
    "".join(lies(p[len(W):]) for p in dateien("ios/PSMobile", ".swift")))
jni_text = ohne_kommentare(lies("android/jni/psm_jni.cpp"))
# Der Kern und der Viewport rufen einander auch - wer nur die beiden
# Oberflaechen absucht, haelt jede Funktion fuer tot, die eine Ebene
# tiefer benutzt wird.
kern_text = ohne_kommentare(
    "".join(lies(p[len(W):]) for p in dateien("viewport/src", ".cpp"))
    + "".join(lies(p[len(W):]) for p in dateien("core/src", ".cpp")))
kern_text = re.sub(r"PSM_API[^\n]*", " ", kern_text)   # Definitionen sind keine Rufe

nur_ios, nur_android, beide, nur_kern, keiner = [], [], [], [], []
for f in api:
    i = re.search(r"\b" + f + r"\b", ios_text) is not None
    a = re.search(r"\b" + f + r"\b", jni_text) is not None
    k = re.search(r"\b" + f + r"\b", kern_text) is not None
    if i and a:
        beide.append(f)
    elif i:
        nur_ios.append(f)
    elif a:
        nur_android.append(f)
    elif k:
        nur_kern.append(f)
    else:
        keiner.append(f)

# --- Das gemeinsame Modul ------------------------------------------------
regel_dateien = dateien("android/shared/src/commonMain", ".kt")
regeln = []
for p in regel_dateien:
    name = os.path.basename(p)[:-3]
    if name.endswith("Test"):
        continue
    von_ios = re.search(r"\b" + name + r"\b", ios_text) is not None
    android_text = "".join(lies(q[len(W):]) for q in dateien("android/app/src/main", ".kt"))
    von_android = re.search(r"\b" + name + r"\b", android_text) is not None
    regeln.append((name, von_ios, von_android))

# --- Umfang --------------------------------------------------------------
ios_screens = dateien("ios/PSMobile/Screens", ".swift")
android_ui = dateien("android/app/src/main/java/de/psmobile/ui", ".kt")
ios_tests = dateien("ios/PSMobileUITests", ".swift") + dateien("ios/PSMobileTests", ".swift")
shared_tests = dateien("android/shared/src/commonTest", ".kt")

commit = subprocess.run(["git", "-C", W, "log", "-1", "--format=%h %ad %s",
                         "--date=short"], capture_output=True, text=True).stdout.strip()

# --- Schreiben -----------------------------------------------------------
z = []
z.append("# Bestandsaufnahme")
z.append("")
z.append("Gemessen, nicht erinnert. Erzeugt von `build/scripts/bestand.py`.")
z.append("")
z.append("Stand: `%s`" % commit)
z.append("")
z.append("Anlass: an einem Tag dreimal aus dem Gedächtnis „das fehlt“ gesagt und")
z.append("dreimal danebengelegen. Diese Datei zählt stattdessen.")
z.append("")

z.append("## Die C-ABI: wer ruft was")
z.append("")
z.append("Die härteste Zahl im Projekt. Eine Funktion, die keine Seite ruft, ist")
z.append("entweder tot oder eine Lücke in einer Oberfläche — und beides sieht man")
z.append("nur so.")
z.append("")
z.append("| | Anzahl |")
z.append("|---|---|")
z.append("| Funktionen in den Kopfdateien | %d |" % len(api))
z.append("| von beiden Plattformen gerufen | %d |" % len(beide))
z.append("| nur von iOS | %d |" % len(nur_ios))
z.append("| nur von Android | %d |" % len(nur_android))
z.append("| nur intern im Kern oder Viewport | %d |" % len(nur_kern))
z.append("| **von niemandem gerufen** | **%d** |" % len(keiner))
z.append("")

if nur_ios:
    z.append("### Nur iOS ruft sie — Android fehlt hier etwas")
    z.append("")
    for f in nur_ios:
        z.append("* `%s`" % f)
    z.append("")
if nur_android:
    z.append("### Nur Android ruft sie — iOS fehlt hier etwas")
    z.append("")
    for f in nur_android:
        z.append("* `%s`" % f)
    z.append("")
if nur_kern:
    z.append("### Nur intern gerufen")
    z.append("")
    z.append("Keine Lücke: der Viewport oder der Kern selbst benutzt sie. Sie")
    z.append("stehen hier, damit sie nicht beim nächsten Aufräumen versehentlich")
    z.append("als tot gelten.")
    z.append("")
    for f in nur_kern:
        z.append("* `%s`" % f)
    z.append("")
if keiner:
    z.append("### Von niemandem gerufen")
    z.append("")
    z.append("Entweder tot und wegzuräumen, oder eine Funktion, die beide")
    z.append("Oberflächen noch nicht anbieten.")
    z.append("")
    for f in keiner:
        z.append("* `%s`" % f)
    z.append("")

z.append("## Das gemeinsame Modul")
z.append("")
z.append("Eine Regel, die nur eine Seite fragt, ist noch keine gemeinsame — sie")
z.append("ist eine Regel, die auf der anderen Seite gleich noch einmal")
z.append("geschrieben wird.")
z.append("")
z.append("| Regel | iOS | Android |")
z.append("|---|---|---|")
for name, i, a in sorted(regeln):
    z.append("| `%s` | %s | %s |" % (name, "✓" if i else "—", "✓" if a else "—"))
z.append("")
einseitig = [n for n, i, a in regeln if i != a]
if einseitig:
    z.append("Einseitig benutzt: %s." % ", ".join("`%s`" % n for n in sorted(einseitig)))
    z.append("")

z.append("## Umfang")
z.append("")
z.append("| | Dateien | Zeilen |")
z.append("|---|---|---|")
z.append("| iOS Bildschirme | %d | %d |" % (len(ios_screens), zeilen(ios_screens)))
z.append("| Android Oberfläche | %d | %d |" % (len(android_ui), zeilen(android_ui)))
z.append("| Gemeinsame Regeln | %d | %d |" % (len(regel_dateien), zeilen(regel_dateien)))
z.append("| iOS Tests | %d | %d |" % (len(ios_tests), zeilen(ios_tests)))
z.append("| Regel-Tests | %d | %d |" % (len(shared_tests), zeilen(shared_tests)))
z.append("")

z.append("### iOS-Bildschirme")
z.append("")
for p in ios_screens:
    n = sum(1 for _ in io.open(p, encoding="utf-8", errors="replace"))
    z.append("* `%s` — %d Zeilen" % (os.path.basename(p), n))
z.append("")
z.append("### Android-Oberfläche")
z.append("")
for p in android_ui:
    n = sum(1 for _ in io.open(p, encoding="utf-8", errors="replace"))
    z.append("* `%s` — %d Zeilen" % (os.path.basename(p), n))
z.append("")

io.open(W + "docs/bestand.md", "w", encoding="utf-8").write("\n".join(z) + "\n")
print("docs/bestand.md geschrieben")
print("ABI %d · beide %d · nur iOS %d · nur Android %d · nur Kern %d · niemand %d"
      % (len(api), len(beide), len(nur_ios), len(nur_android), len(nur_kern), len(keiner)))
