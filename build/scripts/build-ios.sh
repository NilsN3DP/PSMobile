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

# Gleicher Zuschnitt wie Android, siehe build/scripts/env.sh
DEP_EXCLUDES='wxWidgets|GLEW|OCCT|OpenCSG|Catch2|CURL|OpenSSL'

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
    # Statische Bibliothek: iOS-Apps binden den Kern direkt ein, eine
    # eigene .dylib waere nur zusaetzlicher Signierungsaufwand.
    cmake -S "${PSM_ROOT}" -B "${CORE_BUILD}" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="${PSM_ROOT}/cmake/toolchains/ios.cmake" \
        -DPSM_IOS_PLATFORM="${PLATFORM}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_PREFIX_PATH="${PREFIX}" \
        -DPSM_BUILD_TESTCLI=OFF \
        -DBUILD_SHARED_LIBS=OFF
    cmake --build "${CORE_BUILD}" -j "$(sysctl -n hw.ncpu)"
}

case "${1:-all}" in
    deps) build_deps ;;
    core) build_core ;;
    all)  build_deps; build_core ;;
    *) echo "Nutzung: $0 [deps|core|all]" >&2; exit 2 ;;
esac

log "Fertig"
find "${CORE_BUILD}" -name 'libpsmobile_core.*' 2>/dev/null || true
