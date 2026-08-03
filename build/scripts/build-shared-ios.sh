#!/usr/bin/env bash
# Baut das gemeinsame Regelmodul (E-13) als Framework fuer iOS.
#
# Laeuft NUR auf einem Mac: Kotlin/Native uebersetzt die iOS-Ziele
# ausschliesslich dort. Auf Linux baut dasselbe Modul nur seine
# Android-Fassung - das ist kein Fehler, sondern die Arbeitsteilung.
#
#   ./build-shared-ios.sh              beide Ziele
#   ./build-shared-ios.sh simulator    nur Simulator
#   ./build-shared-ios.sh device       nur Geraet
#
# Voraussetzung: ein JDK. build/scripts/macos-bootstrap.sh tools holt
# eines, wenn keines da ist, und traegt JAVA_HOME in env.sh ein.

set -euo pipefail

PSM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLS_ENV="${PSM_ROOT}/build-out/mac-tools/env.sh"
[ -f "${TOOLS_ENV}" ] && source "${TOOLS_ENV}"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

if [ "$(uname -s)" != "Darwin" ]; then
    echo "Dieses Skript braucht macOS - Kotlin/Native baut iOS nur dort." >&2
    exit 1
fi
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
    echo "Kein JDK. Erst build/scripts/macos-bootstrap.sh tools laufen lassen." >&2
    exit 1
fi

case "${1:-all}" in
    simulator) ZIELE=(linkDebugFrameworkIosSimulatorArm64) ;;
    device)    ZIELE=(linkDebugFrameworkIosArm64) ;;
    all)       ZIELE=(linkDebugFrameworkIosSimulatorArm64 linkDebugFrameworkIosArm64) ;;
    *) echo "Nutzung: $0 [all|simulator|device]" >&2; exit 2 ;;
esac

log "Regelmodul fuer iOS: ${ZIELE[*]}"
cd "${PSM_ROOT}/android"
./gradlew "${ZIELE[@]}" --no-daemon

log "Fertig"
find "${PSM_ROOT}/android/shared/build/bin" -name 'PSMShared.framework' -maxdepth 3
