#!/bin/bash
# Faehrt den Vergleichsrundgang auf Android - Schritt fuer Schritt
# derselbe wie ScreenshotTourUITests auf iOS.
#
# Drei Dinge machen den Vergleich erst aussagekraeftig:
#
# 1. Dieselben Kennungen. `testTagsAsResourceId` in MainActivity laesst
#    jedes `Modifier.testTag("x")` in der uiautomator-Ausgabe als
#    resource-id erscheinen, genau wie `accessibilityIdentifier` auf iOS.
#    Gesteuert wird ueber Namen, nicht ueber Koordinaten - Koordinaten
#    sind das Einzige, was sich zwischen zwei Layouts ohnehin
#    unterscheidet.
#
# 2. Derselbe Vorzustand. Die Startargumente (siehe Startargumente.kt)
#    sind zeichengleich zu denen der iOS-Tests: Drucker vorgeben, Simple
#    Mode oeffnen, einen Wuerfel aufs Bett. Ohne sie stammen die beiden
#    Bilderreihen aus verschiedenen Vorzustaenden und belegen nichts.
#
# 3. Dasselbe Fenster. Die Masse haengen ueber WindowScale an der
#    Fenstergroesse in dp: ein Fenster unter 0.8 der Referenz schaltet
#    beide Apps in die kompakte Fassung. Ein Tablet mit 800x1280 dp
#    zeigt darum zu Recht ein anderes Layout als ein iPad Pro mit
#    1032x1376 dp - das ist keine Abweichung, sondern dieselbe Regel.
#    Fuer den Bildvergleich wird der Emulator deshalb auf die
#    iPad-Groesse gestellt (2064x2752 bei 320 dpi = 1032x1376 dp).
#
#   bash tools/rundgang-android.sh [Zielordner]

set -u
ZIEL="${1:-docs/port/screenshots/vergleich}"
PAKET="de.upsm"                       # Debug-Bau; release heisst de.psmobile
AKTIVITAET="$PAKET/de.psmobile.MainActivity"
cd "$(dirname "$0")/.."
mkdir -p "$ZIEL"
# Git Bash wandelt "/sdcard/..." in einen Windows-Pfad um, bevor adb es
# sieht. MSYS_NO_PATHCONV haelt das auf - und der Zielpfad muss einer
# sein, den Windows kennt.
TMP="$(mktemp -u).xml"
export MSYS_NO_PATHCONV=1

dump() {
    adb shell uiautomator dump /sdcard/w.xml >/dev/null 2>&1
    adb pull /sdcard/w.xml "$TMP" >/dev/null 2>&1
    [ -s "$TMP" ]
}

# Mittelpunkt eines Elements anhand seiner Kennung.
mitte() {
    dump
    python - "$TMP" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
ziel = sys.argv[2]
for m in re.finditer(r'<node[^>]*?resource-id="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    rid, x1, y1, x2, y2 = m.groups()
    if rid == ziel:
        print((int(x1)+int(x2))//2, (int(y1)+int(y2))//2)
        break
PY
}

# Mehrfach nachsehen: ein Panel, das gerade einblendet, steht noch nicht
# im Baum. Ein einzelner Blick liefert dann "nicht gefunden", und der
# Rundgang bricht an einer Stelle ab, die in Ruhe laengst da ist.
tippe() {   # tippe <kennung>
    local k="$1" koord i
    for i in 1 2 3 4 5; do
        koord="$(mitte "$k")"
        [ -n "$koord" ] && break
        sleep 1
    done
    if [ -z "$koord" ]; then echo "  ! nicht gefunden: $k" >&2; return 1; fi
    adb shell input tap $koord
    sleep "${TAP_WAIT:-2}"
}

foto() {    # foto <nummer-name>
    sleep 1
    adb exec-out screencap -p > "$ZIEL/and-$1.png"
    echo "  $1"
}

echo "== Fenster auf iPad-Groesse =="
adb shell wm size 2064x2752 >/dev/null
adb shell wm density 320 >/dev/null
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
sleep 3

echo "== Rundgang Android ($PAKET) =="
adb shell pm clear "$PAKET" >/dev/null
# Sonst fragt die App vor dem ersten Slice nach dem Benachrichtigungsrecht.
adb shell pm grant "$PAKET" android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -n "$AKTIVITAET" \
    --esa psm-args psm-reset-settings,psm-preset-printer,psm-start-simple,psm-load-cube >/dev/null
# Der erste Start entpackt die Profile und installiert den Drucker.
sleep 30

foto 01-simple-workspace
tippe simple.werkzeug.Settings && foto 02-simple-settings-panel
tippe simple.karte.SUPPORTS   && foto 03-simple-supports
tippe simple.werkzeug.Settings >/dev/null
tippe simple.werkzeug.Material && foto 04-simple-material
tippe simple.werkzeug.Printer  && foto 05-simple-printer
tippe simple.werkzeug.Projects && foto 06-simple-projects
tippe simple.werkzeug.Settings >/dev/null
tippe simple.appeinstellungen  && foto 07-app-einstellungen

# Der einfache Modus ist sieben Bildschirme; der Expertenmodus traegt den
# Rest der App. Ihn aussen vor zu lassen hiesse, ein Drittel zu
# vergleichen und "gleich" zu sagen.
echo "== Expertenmodus =="
# Erst den Dialog zumachen, dann nach Hause, dann beenden. Ein
# force-stop ohne onStop gilt der Absturzheuristik als unsauberes Ende,
# und der naechste Start zeigt "The app didn't close normally last time"
# - mitten im Bild. Ein offener Dialog davor macht die Sache unsicher,
# also zuerst zurueck in den Arbeitsbereich.
tippe appeinstellungen.zurueck >/dev/null 2>&1
adb shell input keyevent KEYCODE_HOME
sleep 5
adb shell am force-stop "$PAKET"
adb shell am start -n "$AKTIVITAET"     --esa psm-args psm-reset-settings,psm-preset-printer,psm-start-advanced,psm-load-cube >/dev/null
sleep 25
foto 08-advanced-workspace
tippe advanced.printSettings && foto 09-advanced-print-settings
# Filament und Drucker sind Reiter *innerhalb* der Einstellungsflaeche.
# Die Zeilen advanced.filamentSettings/-printerSettings liegen dahinter
# und sind nicht mehr erreichbar, sobald die Flaeche offen ist.
tippe reiter.filament && foto 10-advanced-filament-settings
tippe reiter.printer  && foto 11-advanced-printer-settings

# Telefon im Hochformat: die schmale Fassung (unter 760 pt Breite legt sich
# die Seite ueber das Bett, die Betten werden zur Karte). Bis zum
# 13.09.2026 gab es dafuer kein Vergleichsbild - alle elf waren Tablet,
# und genau dort fiel auf Nils' Telefon die Kopfzeile unter die Statusleiste.
# 1080x2400 bei 420 dpi = 411x914 dp, ein gaengiges Telefon; iOS nimmt
# ein iPhone 17 (402x874 pt), siehe testTourTelefon in ScreenshotTourUITests.
echo "== Telefon-Hochformat =="
adb shell wm size 1080x2400 >/dev/null
adb shell wm density 420 >/dev/null
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am force-stop "$PAKET"
adb shell am start -n "$AKTIVITAET" --esa psm-args psm-reset-settings,psm-preset-printer,psm-start-simple,psm-load-cube >/dev/null
sleep 20
foto 12-telefon-simple
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am force-stop "$PAKET"
adb shell am start -n "$AKTIVITAET" --esa psm-args psm-reset-settings,psm-preset-printer,psm-start-advanced,psm-load-cube >/dev/null
sleep 20
foto 13-telefon-advanced
adb shell wm size 2064x2752 >/dev/null
adb shell wm density 320 >/dev/null

echo "Fertig. Bilder in $ZIEL"
