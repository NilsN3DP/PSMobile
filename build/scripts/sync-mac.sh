#!/usr/bin/env bash
# Spiegelt den Quellstand vom Unraid auf den gemieteten Mac.
#
# Vorher lief das Datei fuer Datei per scp, jedes Mal mit eigenem
# Verbindungsaufbau - bei zehn Dateien zehn Handschlaege. Hier geht alles
# in einem Rutsch durch eine Leitung.
#
# Der Mac hat keinen Zugang zum Unraid (kein Schluessel dort, und der
# Unraid haengt hinter Tailscale). Deshalb laeuft es ueber diesen Rechner
# als Bruecke: einmal lesen, einmal schreiben, ohne Zwischendatei.
#
#   ./sync-mac.sh            Quellen, Skripte, iOS-Projekt
#   ./sync-mac.sh alles      zusaetzlich das gemeinsame Modul
set -euo pipefail

UNRAID="root@100.109.46.54"
UNRAID_KEY="${USERPROFILE}/.ssh/unraid_aipp"
MAC="user289137@FF738.macincloud.com"
MAC_KEY="${USERPROFILE}/.ssh/macincloud2"
REPO="/mnt/user/N3DP/KI Projekte/PSMobile"

PFADE=(
    ios
    core
    viewport
    cmake
    build/scripts
    CMakeLists.txt
    patches
)
if [ "${1:-}" = "alles" ]; then
    PFADE+=(android/shared android/settings.gradle.kts android/build.gradle.kts
            android/gradle/libs.versions.toml)
fi

printf 'Spiegle: %s\n' "${PFADE[*]}"

# build-Ordner bleiben draussen: sie sind gross, plattformgebunden und
# werden drueben ohnehin neu erzeugt.
ssh -i "${UNRAID_KEY}" -o StrictHostKeyChecking=no "${UNRAID}" \
    "cd '${REPO}' && tar czf - --exclude='*/build' --exclude=build-out ${PFADE[*]}" \
 | ssh -i "${MAC_KEY}" -o BatchMode=yes "${MAC}" \
    "cd ~/psmobile && tar xzf - && echo 'angekommen'"

# Von Windows geschriebene Skripte tragen CRLF - das mag keine Shell.
ssh -i "${MAC_KEY}" -o BatchMode=yes "${MAC}" \
    "cd ~/psmobile && find build/scripts -name '*.sh' -exec sed -i '' 's/\r\$//' {} + \
     && chmod +x build/scripts/*.sh && echo 'Skripte bereinigt'"
