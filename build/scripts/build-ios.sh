#!/usr/bin/env bash
# iOS-Build. Laeuft NUR auf einem Mac mit Xcode.
#
# Kein Docker: die iOS-SDKs liegen in Xcode und lassen sich nicht in einen
# Linux-Container holen. Das Skript spiegelt deshalb bewusst die Struktur
# von build-deps.sh/build-core.sh, laeuft aber direkt auf dem Host.
#
#   ./build-ios.sh deps     Dependencies bauen
#   ./build-ios.sh core     libslic3r + psmobile_core bauen
#   ./build-ios.sh all      beides

set -euo pipefail

PSM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLATFORM="${PSM_IOS_PLATFORM:-OS64}"          # OS64 | SIMULATORARM64
PS_SRC="${PSM_ROOT}/external/PrusaSlicer"
DEPS_BUILD="${PSM_ROOT}/build-out/ios-deps-${PLATFORM}"
DESTDIR="${PSM_ROOT}/build-out/ios-destdir-${PLATFORM}"
PREFIX="${DESTDIR}/usr/local"
CORE_BUILD="${PSM_ROOT}/build-out/ios-core-${PLATFORM}"
DL_CACHE="${PSM_ROOT}/build-out/dl-cache"

# Gleicher Zuschnitt wie Android - eine Liste fuer beide Plattformen.
# env.sh laesst sich hier nicht einbinden: es setzt Android-NDK-Pfade und
# ruft nproc auf, das es auf macOS nicht gibt.
source "$(dirname "${BASH_SOURCE[0]}")/dep-excludes.sh"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

if [ "$(uname -s)" != "Darwin" ]; then
    echo "Dieses Skript braucht macOS mit Xcode." >&2
    exit 1
fi
if [ ! -d "${PS_SRC}" ]; then
    echo "PrusaSlicer-Quellen fehlen. Erst build/scripts/bootstrap.sh laufen lassen." >&2
    exit 1
fi

export PSM_IOS_PLATFORM="${PLATFORM}"
export PSM_DEPS_PREFIX="${PREFIX}"

build_deps() {
    # Anmerkung zu CMake 4: mehrere Pakete (OCCT, Blosc, ...) verlangen
    # cmake_minimum_required(VERSION 3.0), was CMake 4 ablehnt. Der
    # naheliegende Weg - DEP_CMAKE_OPTS hier von aussen zu setzen - nuetzt
    # nichts: deps/CMakeLists.txt setzt die Liste selbst, im Apple-Zweig
    # sogar vollstaendig neu. Die Regel steht deshalb in patches/0005.
    log "iOS-Dependencies (${PLATFORM})"
    cmake -S "${PS_SRC}/deps" -B "${DEPS_BUILD}" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="${PSM_ROOT}/cmake/toolchains/ios.cmake" \
        -DPSM_IOS_PLATFORM="${PLATFORM}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DDESTDIR="${DESTDIR}" \
        -DDEP_DOWNLOAD_DIR="${DL_CACHE}" \
        -DPrusaSlicer_deps_PACKAGE_EXCLUDES="${DEP_EXCLUDES}" \
        -DBUILD_SHARED_LIBS=OFF
    cmake --build "${DEPS_BUILD}" -- -j 1
}

build_core() {
    log "iOS-Kern (${PLATFORM})"

    # Fuer den Simulator die Testbinaries mitbauen. Sie laufen dort ueber
    # "xcrun simctl spawn" und beantworten damit die eigentliche Frage:
    # nicht ob der Kern uebersetzt, sondern ob er auf iOS auch rechnet.
    # Auf dem Geraet bleiben sie aus - dort braeuchte jedes Binary ein
    # Signierprofil, ohne etwas beizutragen.
    WITH_TESTS=OFF
    case "${PLATFORM}" in
        SIMULATOR*) WITH_TESTS=ON ;;
    esac

    # Statische Bibliothek: iOS-Apps binden den Kern direkt ein, eine
    # eigene .dylib waere nur zusaetzlicher Signierungsaufwand.
    cmake -S "${PSM_ROOT}" -B "${CORE_BUILD}" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="${PSM_ROOT}/cmake/toolchains/ios.cmake" \
        -DPSM_IOS_PLATFORM="${PLATFORM}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_PREFIX_PATH="${PREFIX}" \
        -DPSM_BUILD_TESTCLI="${WITH_TESTS}" \
        -DBUILD_SHARED_LIBS=OFF
    cmake --build "${CORE_BUILD}" -j "$(sysctl -n hw.ncpu)"
    bundle_core
}

# Eine statische Bibliothek nimmt ihre Abhaengigkeiten nicht mit. Die App
# muesste sonst libslic3r, Boost, TBB, CGAL und zwei Dutzend weitere
# Archive einzeln aufzaehlen - eine Liste, die bei jeder Aenderung an den
# Dependencies veraltet, und deren Luecken sich erst beim Linken der App
# als Haufen fehlender Symbole zeigen.
#
# libtool legt sie stattdessen zu einer einzigen Datei zusammen. Die ist
# gross, aber der Linker nimmt aus einem Archiv ohnehin nur das heraus,
# was tatsaechlich gebraucht wird.
bundle_core() {
    local out="${CORE_BUILD}/libpsmobile_core_all.a"
    log "Alles in eine Bibliothek: $(basename "${out}")"

    local archive=()
    while IFS= read -r a; do archive+=("${a}"); done < <(
        find "${CORE_BUILD}" -name '*.a' ! -name 'libpsmobile_core_all.a'
        find "${PREFIX}/lib" -name '*.a'
    )

    rm -f "${out}"
    # -no_warning_for_no_symbols: mehrere Archive enthalten Objektdateien
    # ganz ohne Symbole. Das ist erwartet und keine Meldung wert.
    libtool -static -no_warning_for_no_symbols -o "${out}" "${archive[@]}"
    printf '    %s Archive, %s\n' "${#archive[@]}" "$(du -h "${out}" | cut -f1)"
}

case "${1:-all}" in
    deps) build_deps ;;
    core) build_core ;;
    all)  build_deps; build_core ;;
    *) echo "Nutzung: $0 [deps|core|all]" >&2; exit 2 ;;
esac

log "Fertig"
find "${CORE_BUILD}" -name 'libpsmobile_core.*' 2>/dev/null || true
