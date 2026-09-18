#!/bin/bash
# Baut und testet die iOS-Seite auf dem MacBook im LAN.
#
# Warum ein Skript und nicht ein paar Befehle: Die iOS-Referenz ist der
# Massstab fuer die Android-Uebersetzung. Wenn niemand nachweist, dass sie
# baut, ist "Android folgt iOS" eine Behauptung. Dieser Lauf macht daraus
# einen Beleg - und er findet den Mac selbst, weil dessen IP per DHCP
# wandert.
#
#   bash tools/ios-bauen.sh              # suchen, uebertragen, bauen, testen
#   bash tools/ios-bauen.sh --nur-suchen # nur nachsehen, ob er da ist
#
# Voraussetzung auf dem Mac: Systemeinstellungen -> Allgemein -> Teilen ->
# Entfernte Anmeldung EIN. Ohne das antwortet Port 22 nicht, auch wenn das
# Geraet im Netz ist.

set -u
KEY="$HOME/.ssh/id_ed25519_macbook"
NUTZER="${PSM_MAC_USER:?PSM_MAC_USER=Benutzer auf dem Mac setzen}"
ZIEL_VERZEICHNIS="${PSM_MAC_DIR:-/Users/$NUTZER/psmobile}"
BEKANNT="${PSM_MAC:-}"

ssh_zu() { ssh -o BatchMode=yes -o ConnectTimeout=5 -o StrictHostKeyChecking=accept-new -i "$KEY" "$NUTZER@$1" "$2"; }

finde_mac() {
    # 1. Die zuletzt bekannte Adresse.
    if [ -n "$BEKANNT" ] && ssh_zu "$BEKANNT" 'true' 2>/dev/null; then echo "$BEKANNT"; return 0; fi
    # 2. Das eigene /24 absuchen. macOS meldet sich mit "Darwin".
    local basis
    basis="$(ipconfig 2>/dev/null | grep -A4 -iE 'wlan|wi-fi|ethernet' \
             | grep -oE '192\.168\.[0-9]+\.' | head -1)"
    [ -z "$basis" ] && basis="192.168.1."
    echo "Suche das MacBook in ${basis}0/24 ..." >&2
    local i
    for i in $(seq 2 254); do
        ( if ssh_zu "${basis}${i}" 'uname -s' 2>/dev/null | grep -q Darwin; then
              echo "${basis}${i}" > "${TMPDIR:-/tmp}/psm-mac.$$"
          fi ) &
        # Nicht alle 253 auf einmal - das erschlaegt die ARP-Tabelle.
        [ $((i % 40)) -eq 0 ] && wait
    done
    wait
    if [ -f "${TMPDIR:-/tmp}/psm-mac.$$" ]; then
        cat "${TMPDIR:-/tmp}/psm-mac.$$"; rm -f "${TMPDIR:-/tmp}/psm-mac.$$"; return 0
    fi
    return 1
}

MAC="$(finde_mac)" || {
    cat >&2 <<'HINWEIS'

Das MacBook ist nicht erreichbar. Der Reihe nach pruefen:

  1. Ist es an und wach? (Zugeklappt schlaeft es und antwortet nicht.)
  2. Haengt es im selben WLAN wie dieser PC?
  3. Systemeinstellungen -> Allgemein -> Teilen -> "Entfernte Anmeldung" EIN.

Danach diesen Aufruf wiederholen.
HINWEIS
    exit 1
}

echo "MacBook gefunden: $MAC"
ssh_zu "$MAC" 'sw_vers -productVersion; xcodebuild -version 2>/dev/null | head -1' \
    | sed 's/^/  /'
# Platz zuerst. Am 10.09.2026 lief die ganze Testreihe mit 119 MB freiem
# Speicher los; 26 Faelle fielen durch, das Ergebnisbuendel blieb halb
# geschrieben, und die Meldung lautete "TEST FAILED" - nicht "Platte
# voll". Ein Testlauf, der aus Platzmangel scheitert und wie ein
# Fehlschlag aussieht, ist schlimmer als keiner.
FREI_MB=$(ssh_zu "$MAC" "df -m / | tail -1 | awk '{print \$4}'")
echo "  Freier Speicher: ${FREI_MB} MB"
# Alte Ergebnisbuendel zuerst weg - jedes traegt Videos und wiegt
# 200-1000 MB; der Mac ist zu 97 % voll, und die Buendel sind nach dem
# Lesen wertlos. Das juengste bleibt fuer den Blick zurueck.
ssh_zu "$MAC" 'ls -dt ~/Library/Developer/Xcode/DerivedData/PSMobile-*/Logs/Test/*.xcresult 2>/dev/null | tail -n +2 | xargs rm -rf' 2>/dev/null
FREI_MB=$(ssh_zu "$MAC" "df -m / | tail -1 | awk '{print \$4}'")
# 1200 statt 3000: ein Lauf braucht rund 600 MB (Bau 340, Buendel 250).
if [ "${FREI_MB:-0}" -lt 1200 ]; then
    cat >&2 <<HINWEIS

Zu wenig Platz auf dem MacBook (${FREI_MB} MB frei, noetig sind 1200).
Ein Lauf wuerde mittendrin abbrechen und wie ein Fehlschlag aussehen.

Aufraeumen laesst sich gefahrlos:
  xcrun simctl delete unavailable
  xcrun simctl erase <UDID>     # setzt einen Simulator zurueck
  rm -rf ~/Library/Developer/Xcode/DerivedData/*

HINWEIS
    exit 1
fi

[ "${1:-}" = "--nur-suchen" ] && exit 0

WURZEL="$(cd "$(dirname "$0")/.." && pwd)"
cd "$WURZEL"

echo
echo "== Quellstand uebertragen =="
# Ein Bundle traegt nur Commits. Uncommittete Aenderungen bleiben hier
# liegen - man baut dann den vorigen Stand und sucht den Fehler in einer
# Datei, die man laengst geaendert hat.
if [ -n "$(git status --porcelain)" ]; then
    echo "  ACHTUNG: uncommittete Aenderungen werden NICHT uebertragen:" >&2
    git status --porcelain | sed 's/^/    /' >&2
    echo "  Erst committen, sonst baut der Mac den vorigen Stand." >&2
fi
# Per Bundle statt rsync: das Repo liegt teils auf einer SMB-Freigabe,
# und ein Bundle traegt den Verlauf mit, ohne Rechteprobleme.
ZWEIG="$(git rev-parse --abbrev-ref HEAD)"
BUNDLE="${TMPDIR:-/tmp}/psmobile-$(git rev-parse --short HEAD).bundle"
# Den Zweig mitgeben, nicht nur HEAD: sonst kennt das Bundle den Namen
# nicht, und `git clone -b <zweig>` findet ihn drueben nicht.
git bundle create "$BUNDLE" --quiet "$ZWEIG"
scp -q -i "$KEY" "$BUNDLE" "$NUTZER@$MAC:/tmp/psmobile.bundle"

ssh_zu "$MAC" "
set -e
if [ -d '$ZIEL_VERZEICHNIS/.git' ]; then
    cd '$ZIEL_VERZEICHNIS'
    # Erst vom Zweig loesen: git weigert sich, in einen ausgecheckten
    # Zweig zu fetchen. Anfuehrungszeichen haben in diesem Block nichts
    # zu suchen - sie beenden die umschliessende Zeichenkette.
    git checkout -q --detach 2>/dev/null || true
    git fetch -q /tmp/psmobile.bundle '$ZWEIG:$ZWEIG' --force
    git checkout -q --force '$ZWEIG'
else
    git clone -q -b '$ZWEIG' /tmp/psmobile.bundle '$ZIEL_VERZEICHNIS'
fi
echo \"  Stand: \$(cd '$ZIEL_VERZEICHNIS' && git log --oneline -1)\"
"

echo
echo "== Gemeinsames Modul als iOS-Framework =="
# Das Android-Gradle-Plugin verlangt Java 17; das Standard-JDK auf dem
# Mac ist aelter. Ohne JAVA_HOME bricht der Lauf mit einer irrefuehrenden
# Meldung ueber das Plugin ab.
ssh_zu "$MAC" "
set -e
eval \"\$(/opt/homebrew/bin/brew shellenv)\" 2>/dev/null || true
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
cd '$ZIEL_VERZEICHNIS/android'
# Windows kennt kein Ausfuehrbar-Bit; nach der Uebertragung fehlt es
# hier. Ohne diese Zeile bricht der Lauf mit \"permission denied\" ab.
chmod +x ./gradlew
./gradlew -q :shared:linkDebugFrameworkIosSimulatorArm64
echo '  Framework gebaut.'
" || { echo "Framework-Bau fehlgeschlagen."; exit 1; }

echo
echo "== Xcode-Projekt und Tests =="
ssh_zu "$MAC" "
set -e
eval \"\$(/opt/homebrew/bin/brew shellenv)\" 2>/dev/null || true
cd '$ZIEL_VERZEICHNIS/ios'
# Die Bundle-Ressourcen (Druckerprofile, psui) liegen nicht im Git -
# sie werden erzeugt. Ohne sie bricht xcodegen mit einem Hinweis auf ein
# fehlendes Resources/psresources ab.
if [ ! -d Resources/psresources ] || [ ! -d Resources/psui ]; then
    echo '  Ressourcen bereitstellen ...'
    # stage-resources.sh braucht den PrusaSlicer-Quellbaum unter
    # external/, den erst bootstrap.sh holt - auf einem frischen Mac ist
    # er nicht da. Dieselben Daten liegen aber fertig im Repository, auf
    # der Android-Seite: derselbe Skriptlauf hat sie dort abgelegt, nur
    # in ein anderes Zielverzeichnis. Also von dort nehmen.
    mkdir -p Resources
    ASSETS='../android/app/src/main/assets'
    if [ -d \"\$ASSETS/psresources\" ] && [ -d \"\$ASSETS/psui\" ]; then
        cp -R \"\$ASSETS/psresources\" Resources/
        cp -R \"\$ASSETS/psui\" Resources/
        echo \"  aus den Android-Assets uebernommen (\$(du -sh Resources | cut -f1))\"
    else
        chmod +x ../build/scripts/*.sh 2>/dev/null || true
        PSM_VENDORS=all bash ../build/scripts/stage-resources.sh ios 2>&1 | tail -5
    fi
fi
command -v xcodegen >/dev/null && xcodegen generate --quiet
# Ueber die Geraete-Kennung statt ueber den Namen: die Namen tragen die
# Chip-Generation in Klammern (iPad Pro 13-inch (M5)), und wer die beim
# Zuschneiden verliert, bekommt ein Ziel, das es nicht gibt.
UDID=\$(xcrun simctl list devices available | grep -m1 -E 'iPad Pro' | grep -oE '[0-9A-F-]{36}')
[ -z \"\$UDID\" ] && UDID=\$(xcrun simctl list devices available | grep -m1 -E 'iPad' | grep -oE '[0-9A-F-]{36}')
echo \"  Simulator: \$(xcrun simctl list devices | grep \$UDID | sed 's/^ *//;s/ (\$UDID.*//')\"
set -o pipefail
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile \
    -destination \"platform=iOS Simulator,id=\$UDID\" \
    -quiet test 2>&1 | tee /tmp/psmobile-test.log | tail -40
echo \"  Volles Protokoll auf dem Mac: /tmp/psmobile-test.log\"
"
ERG=$?

# Telefon-Bilder (12, 13) fuer den Bildvergleich: derselbe Rundgang noch
# einmal auf einem iPhone, nur der eine Fall. PSM_TELEFON=1 schaltet ihn zu.
if [ "${PSM_TELEFON:-0}" = "1" ]; then
    echo
    echo "== Telefon-Hochformat (iPhone-Simulator) =="
    ssh_zu "$MAC" "
set -e
eval \"\$(/opt/homebrew/bin/brew shellenv)\" 2>/dev/null || true
cd '$ZIEL_VERZEICHNIS/ios'
UDID=\$(xcrun simctl list devices available | grep -m1 -E 'iPhone 17 \(' | grep -oE '[0-9A-F-]{36}')
[ -z \"\$UDID\" ] && UDID=\$(xcrun simctl list devices available | grep -m1 -E 'iPhone' | grep -oE '[0-9A-F-]{36}')
echo \"  Simulator: \$(xcrun simctl list devices | grep \$UDID | sed 's/^ *//;s/ (\$UDID.*//')\"
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile \
    -destination \"platform=iOS Simulator,id=\$UDID\" \
    -only-testing:PSMobileUITests/ScreenshotTourUITests/testTourTelefon \
    -quiet test 2>&1 | tail -15
"
fi

echo
if [ $ERG -eq 0 ]; then
    echo "iOS gebaut und getestet. Damit ist die Referenz belegt."
else
    echo "iOS-Lauf fehlgeschlagen (siehe oben). Die Android-Uebersetzung"
    echo "beruht damit auf einer unbestaetigten Referenz."
fi
exit $ERG
