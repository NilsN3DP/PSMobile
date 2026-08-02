#!/usr/bin/env bash
# M1: PrusaSlicer-Dependencies fuer Android cross-bauen.
#
# Laeuft auf dem Host und startet den eigentlichen Build im Container.
# Ausgabe landet in build-out/destdir-<abi>/usr/local.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [ ! -d "${PS_SRC}/deps" ]; then
    psm_err "PrusaSlicer-Quellen fehlen. Erst build/scripts/bootstrap.sh laufen lassen."
    exit 1
fi

psm_log "Dependencies fuer ${ANDROID_ABI} (API ${ANDROID_API})"
echo "  ausgeschlossen: ${DEP_EXCLUDES}"

mkdir -p "${DEPS_BUILD}" "${DL_CACHE}"

docker run --rm -i \
    -v "${PSM_ROOT}:/work" \
    -w /work \
    -e PSM_ANDROID_ABI="${ANDROID_ABI}" \
    -e PSM_ANDROID_API="${ANDROID_API}" \
    -e DEP_EXCLUDES="${DEP_EXCLUDES}" \
    -e NPROC="${NPROC}" \
    -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 \
    -e PSM_DEPS_PREFIX="/work/build-out/destdir-${ANDROID_ABI}/usr/local" \
    "${PSM_IMAGE}" bash -euo pipefail -c '
      source /work/build/scripts/mk-autotools-wrappers.sh

      SRC=/work/external/PrusaSlicer/deps
      BLD=/work/build-out/deps-${PSM_ANDROID_ABI}
      DST=/work/build-out/destdir-${PSM_ANDROID_ABI}
      DL=/work/build-out/dl-cache

      cmake -S "$SRC" -B "$BLD" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE=/work/cmake/toolchains/android.cmake \
        -DCMAKE_BUILD_TYPE=Release \
        -DDESTDIR="$DST" \
        -DDEP_DOWNLOAD_DIR="$DL" \
        -DPrusaSlicer_deps_PACKAGE_EXCLUDES="$DEP_EXCLUDES" \
        -DBUILD_SHARED_LIBS=OFF

      # Top-Level bewusst seriell (-j 1): die einzelnen Pakete
      # parallelisieren intern selbst, siehe deps/CMakeLists.txt.
      cmake --build "$BLD" -- -j 1
    '

psm_log "Fertig. Prefix: ${DEPS_PREFIX}"
ls "${DEPS_PREFIX}/lib" 2>/dev/null | head -40 || psm_warn "kein lib-Verzeichnis - Build unvollstaendig"
