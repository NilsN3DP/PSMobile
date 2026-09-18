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
#   ./sync-mac.sh                    Quellen, Skripte, iOS-Projekt
#   ./sync-mac.sh alles              zusaetzlich das gemeinsame Modul
#   ./sync-mac.sh alles --trotzdem   auch wenn der Mac schmutzig ist
#
# Ohne --trotzdem bricht der Abgleich ab, sobald drueben uncommittete
# Aenderungen liegen. Siehe die Sperre weiter unten.
set -euo pipefail

UNRAID="${PSM_REMOTE:?PSM_REMOTE=user@build-host setzen}"
UNRAID_KEY="${PSM_REMOTE_KEY:-$HOME/.ssh/id_ed25519}"
MAC="${PSM_MAC_SSH:?PSM_MAC_SSH=user@mac setzen}"
MAC_KEY="${PSM_MAC_KEY:-$HOME/.ssh/id_ed25519}"
REPO="${PSM_REMOTE_ROOT:?PSM_REMOTE_ROOT=Pfad des Repos auf dem Build-Host}"

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

# --- Sperre: nie ueber fremde Arbeit schreiben ---------------------------
#
# Dieses Skript packt Dateien in ein tar und entpackt sie drueben. Was
# dort denselben Namen traegt, ist danach weg - ohne Rueckfrage und ohne
# Spur. Am 3. August hat genau das den Arbeitsstand eines zweiten
# Zweiges auf dem Mac ueberschrieben; gerettet hat ihn nur, dass er
# committet war.
#
# Deshalb: ist der Mac schmutzig, bricht der Abgleich ab. Wer trotzdem
# schieben will, muss drueben committen oder es ausdruecklich sagen.
if [ "${2:-}" != "--trotzdem" ]; then
    SCHMUTZ=$(ssh -i "${MAC_KEY}" -o BatchMode=yes "${MAC}" \
        "cd ~/psmobile 2>/dev/null && git status --porcelain 2>/dev/null | head -20")
    if [ -n "${SCHMUTZ}" ]; then
        printf 'Abbruch: auf dem Mac liegen uncommittete Aenderungen.\n\n'
        printf '%s\n\n' "${SCHMUTZ}"
        printf 'Erst drueben sichern:\n'
        printf "  ssh -i \"$MAC_KEY\" %s 'cd ~/psmobile && git add -A && git commit -m ...'\n\n" "${MAC}"
        printf 'Oder bewusst ueberschreiben:  ./sync-mac.sh %s --trotzdem\n' "${1:-}"
        exit 1
    fi
fi

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
