#!/usr/bin/env bash
# Einrichtung auf einem frisch gemieteten Mac - von null bis zum
# lauffaehigen Xcode-Projekt.
#
# Gedacht fuer genau den Fall, dass die Maschine nach Stunden abgerechnet
# wird: jeder Schritt meldet sich, jeder Schritt ist wiederholbar, und
# was schon fertig ist, wird uebersprungen. Bricht etwas ab, kann das
# Skript nach dem Beheben einfach erneut laufen.
#
#   ./macos-bootstrap.sh              alles der Reihe nach
#   ./macos-bootstrap.sh tools        nur Werkzeuge pruefen und holen
#   ./macos-bootstrap.sh sources      nur Quellen und Patches
#   ./macos-bootstrap.sh deps         nur Dependencies (der lange Teil)
#   ./macos-bootstrap.sh core         nur libslic3r und psmobile_core
#   ./macos-bootstrap.sh project      Ressourcen stagen, Xcode-Projekt erzeugen
#
# Plattform ueber PSM_IOS_PLATFORM:
#   SIMULATORARM64  Simulator auf Apple Silicon - fuer einen Prototyp
#   OS64            echtes Geraet, braucht ein Signierprofil
#
# Der Simulator ist die guenstigere Wahl: kein Apple-Entwicklerkonto,
# keine Provisionierung, und zum Ansehen der Oberflaeche reicht er.

set -euo pipefail

PSM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLATFORM="${PSM_IOS_PLATFORM:-SIMULATORARM64}"
PS_REPO="https://github.com/prusa3d/PrusaSlicer.git"
PS_TAG="version_2.9.6"
PS_SRC="${PSM_ROOT}/external/PrusaSlicer"

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!!  %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mXX  %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(uname -s)" = "Darwin" ] || die "Dieses Skript laeuft nur auf macOS."

step_tools() {
    log "Werkzeuge"

    if ! xcode-select -p >/dev/null 2>&1; then
        die "Xcode-Kommandozeilenwerkzeuge fehlen. 'xcode-select --install' ausfuehren und danach erneut starten."
    fi
    printf '    Xcode:    %s\n' "$(xcodebuild -version 2>/dev/null | head -1 || echo unbekannt)"

    # Ohne die iOS-Plattform kennt xcodebuild kein iphonesimulator-SDK.
    if ! xcodebuild -showsdks 2>/dev/null | grep -q iphone; then
        die "Kein iOS-SDK gefunden. In Xcode unter Settings > Components die iOS-Plattform laden."
    fi

    if ! command -v brew >/dev/null 2>&1; then
        die "Homebrew fehlt. Von https://brew.sh installieren und erneut starten."
    fi

    local missing=()
    for tool in cmake ninja xcodegen automake autoconf libtool pkg-config; do
        command -v "${tool}" >/dev/null 2>&1 || missing+=("${tool}")
    done
    if [ ${#missing[@]} -gt 0 ]; then
        log "Installiere: ${missing[*]}"
        brew install "${missing[@]}"
    else
        printf '    Alle Werkzeuge vorhanden.\n'
    fi

    # GMP und MPFR bauen per autotools und brauchen GNU-Werkzeuge; ohne
    # sie stolpert configure mit unverstaendlichen Meldungen.
    printf '    cmake:    %s\n' "$(cmake --version | head -1)"
    printf '    ninja:    %s\n' "$(ninja --version)"
    printf '    xcodegen: %s\n' "$(xcodegen --version 2>/dev/null || echo unbekannt)"
}

step_sources() {
    log "PrusaSlicer-Quellen"

    if [ -d "${PS_SRC}/.git" ]; then
        printf '    Vorhanden: %s\n' "$(git -C "${PS_SRC}" describe --tags 2>/dev/null || echo unbekannt)"
    else
        mkdir -p "$(dirname "${PS_SRC}")"
        git clone --depth 1 --branch "${PS_TAG}" "${PS_REPO}" "${PS_SRC}"
    fi

    log "Patches"
    # Der Patch heisst nach Android, ist fuer Apple aber unschaedlich: die
    # GMP/MPFR-Aenderungen trennen nur C- von C++-Flags, und die
    # PNG-Zeile fuegt lediglich ein ANDROID zur Bedingung hinzu.
    local patch
    for patch in "${PSM_ROOT}"/patches/*.patch; do
        [ -f "${patch}" ] || continue
        if git -C "${PS_SRC}" apply --reverse --check "${patch}" >/dev/null 2>&1; then
            printf '    schon angewendet: %s\n' "$(basename "${patch}")"
        else
            git -C "${PS_SRC}" apply "${patch}"
            printf '    angewendet: %s\n' "$(basename "${patch}")"
        fi
    done
}

step_deps() {
    log "Dependencies fuer ${PLATFORM} - das ist der lange Teil"
    warn "Erfahrungswert von Android: eine bis mehrere Stunden. Nicht abbrechen, wenn es lange still ist."
    PSM_IOS_PLATFORM="${PLATFORM}" "${PSM_ROOT}/build/scripts/build-ios.sh" deps
}

step_core() {
    log "libslic3r und psmobile_core fuer ${PLATFORM}"
    PSM_IOS_PLATFORM="${PLATFORM}" "${PSM_ROOT}/build/scripts/build-ios.sh" core
}

step_project() {
    log "Ressourcen ins Bundle"
    "${PSM_ROOT}/build/scripts/stage-resources.sh" ios

    log "Xcode-Projekt erzeugen"
    ( cd "${PSM_ROOT}/ios" && PSM_IOS_PLATFORM="${PLATFORM}" xcodegen generate )

    log "Fertig"
    cat <<EOF

    Naechster Schritt:

      open "${PSM_ROOT}/ios/PSMobile.xcodeproj"

    Im Simulator starten:

      xcodebuild -project "${PSM_ROOT}/ios/PSMobile.xcodeproj" \\
                 -scheme PSMobile \\
                 -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M4)' \\
                 build

EOF
}

case "${1:-all}" in
    tools)   step_tools ;;
    sources) step_sources ;;
    deps)    step_deps ;;
    core)    step_core ;;
    project) step_project ;;
    all)     step_tools; step_sources; step_deps; step_core; step_project ;;
    *) echo "Nutzung: $0 [tools|sources|deps|core|project|all]" >&2; exit 2 ;;
esac
