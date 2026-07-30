#!/usr/bin/env bash
# M1/M2: libslic3r + psmobile_core fuer Android bauen.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [ ! -d "${DEPS_PREFIX}/lib" ]; then
    psm_err "Dependencies fehlen unter ${DEPS_PREFIX}. Erst build-deps.sh laufen lassen."
    exit 1
fi

psm_log "Kern fuer ${ANDROID_ABI} (API ${ANDROID_API})"
mkdir -p "${CORE_BUILD}"

docker run --rm -i \
    -v "${PSM_ROOT}:/work" \
    -w /work \
    -e PSM_ANDROID_ABI="${ANDROID_ABI}" \
    -e PSM_ANDROID_API="${ANDROID_API}" \
    -e PSM_DEPS_PREFIX="/work/build-out/destdir-${ANDROID_ABI}/usr/local" \
    -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 \
    -e NPROC="${NPROC}" \
    "${PSM_IMAGE}" bash -euo pipefail -c '
      BLD=/work/build-out/core-${PSM_ANDROID_ABI}

      cmake -S /work -B "$BLD" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE=/work/cmake/toolchains/android.cmake \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_PREFIX_PATH="$PSM_DEPS_PREFIX" \
        -DPSM_DEPS_ROOT="$PSM_DEPS_PREFIX" \
        -DPSM_BUILD_TESTCLI=ON \
        -DPSM_BUILD_TESTS=ON

      cmake --build "$BLD" -j "${NPROC}"
    '

python3 "${PSM_ROOT}/build/scripts/native-fingerprint.py" \
    --abi "${ANDROID_ABI}" \
    --api "${ANDROID_API}" \
    --build-type Release \
    --write "${CORE_BUILD}/psmobile.fingerprint"

psm_log "Ergebnis"
find "${CORE_BUILD}" \( -name 'libpsmobile_core.so' -o -name 'psm_testcli' \
    -o -name 'psm_contract_tests' \) | while read -r f; do
    echo "  $(basename "$f")  $(du -h "$f" | cut -f1)"
done
