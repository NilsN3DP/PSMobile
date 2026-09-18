#!/usr/bin/env bash
# Kopiert die gebauten .so-Dateien dorthin, wo Gradle sie einpackt.
#
# Die native Bibliothek wird bewusst NICHT von Gradle gebaut, sondern im
# Docker-Container auf dem Unraid - siehe android/app/build.gradle.kts.
# Dieses Skript ist die Uebergabe zwischen beiden Welten.
#
#   stage-native.sh              alle vorhandenen ABIs
#   stage-native.sh arm64-v8a    nur eines

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

JNILIBS="${PSM_ROOT}/android/app/src/main/jniLibsFixed"
ABIS="${1:-arm64-v8a x86_64}"

staged=0
for abi in ${ABIS}; do
    build="${PSM_ROOT}/build-out/core-${abi}"
    src="${build}/stripped/libpsmobile_core.so"
    [ -f "${src}" ] || src="${build}/libpsmobile_core.so"
    fingerprint="${build}/psmobile.fingerprint"

    if [ ! -f "${src}" ]; then
        psm_err "${abi}: native Bibliothek fehlt"
        exit 1
    fi
    if [ ! -f "${fingerprint}" ]; then
        psm_err "${abi}: Build-Fingerprint fehlt"
        exit 1
    fi

    expected="$(python3 "${PSM_ROOT}/build/scripts/native-fingerprint.py" \
        --abi "${abi}" --api "${ANDROID_API}" --build-type Release)"
    actual="$(tr -d '\r\n' < "${fingerprint}")"
    if [ "${actual}" != "${expected}" ]; then
        psm_err "${abi}: native Bibliothek ist gegenueber den Quellen veraltet"
        exit 1
    fi

    mkdir -p "${JNILIBS}/${abi}"
    cp "${src}" "${JNILIBS}/${abi}/libpsmobile_core.so"
    printf '  %-12s %s\n' "${abi}" "$(du -h "${JNILIBS}/${abi}/libpsmobile_core.so" | cut -f1)"
    staged=$((staged + 1))
done

if [ "${staged}" -eq 0 ]; then
    psm_err "Nichts gestaged. Erst build/scripts/build-core.sh laufen lassen."
    exit 1
fi
psm_log "${staged} ABI(s) bereit unter android/app/src/main/jniLibs"
