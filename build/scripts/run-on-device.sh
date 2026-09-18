#!/usr/bin/env bash
# M2-Nachweis: psm_testcli auf einem echten Android-Geraet laufen lassen.
#
# Nutzung (vom Windows-Arbeitsplatz mit angeschlossenem Geraet):
#   build/scripts/run-on-device.sh modell.stl
#
# Das Skript holt die Artefakte vom Unraid, schiebt sie aufs Geraet,
# slict dort und zieht den G-Code zurueck.

set -euo pipefail

MODEL="${1:-}"
ABI="${ANDROID_ABI:-arm64-v8a}"
REMOTE="${PSM_REMOTE:?PSM_REMOTE=user@build-host setzen}"
REMOTE_ROOT="${PSM_REMOTE_ROOT:?PSM_REMOTE_ROOT=Pfad des Repos auf dem Build-Host}"
DEV_DIR=/data/local/tmp/psm
ADB="${ADB:-adb}"

if [ -z "${MODEL}" ]; then
    echo "Nutzung: $0 MODELL.stl" >&2
    exit 2
fi
if [ ! -f "${MODEL}" ]; then
    echo "Modell nicht gefunden: ${MODEL}" >&2
    exit 1
fi

# Der Emulator ist x86_64, echte Geraete sind arm64. Passendes Artefakt
# waehlen, statt am Ende ein "Exec format error" zu ernten.
DEV_ABI="$("${ADB}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [ -n "${DEV_ABI}" ] && [ "${DEV_ABI}" != "${ABI}" ]; then
    echo "Geraet meldet ABI ${DEV_ABI}, verwende dieses statt ${ABI}."
    ABI="${DEV_ABI}"
fi

STAGE="$(mktemp -d)"
trap 'rm -rf "${STAGE}"' EXIT

echo "==> Artefakte holen (${ABI})"
scp -q "${REMOTE}:${REMOTE_ROOT}/build-out/core-${ABI}/psm_testcli"                  "${STAGE}/"
scp -q "${REMOTE}:${REMOTE_ROOT}/build-out/core-${ABI}/stripped/libpsmobile_core.so" "${STAGE}/" \
  || scp -q "${REMOTE}:${REMOTE_ROOT}/build-out/core-${ABI}/libpsmobile_core.so"     "${STAGE}/"
scp -qr "${REMOTE}:${REMOTE_ROOT}/android/app/src/main/assets/psresources"           "${STAGE}/resources"

echo "==> aufs Geraet schieben"
"${ADB}" shell "rm -rf ${DEV_DIR}; mkdir -p ${DEV_DIR}/psmdata"
"${ADB}" push -q "${STAGE}/psm_testcli"          "${DEV_DIR}/" >/dev/null
"${ADB}" push -q "${STAGE}/libpsmobile_core.so"  "${DEV_DIR}/" >/dev/null
"${ADB}" push -q "${STAGE}/resources"            "${DEV_DIR}/" >/dev/null
"${ADB}" push -q "${MODEL}"                      "${DEV_DIR}/modell" >/dev/null
"${ADB}" shell "chmod 755 ${DEV_DIR}/psm_testcli"

echo "==> slicen auf dem Geraet"
"${ADB}" shell "cd ${DEV_DIR} && LD_LIBRARY_PATH=${DEV_DIR} ./psm_testcli \
    --res ${DEV_DIR}/resources \
    --data ${DEV_DIR}/psmdata \
    --out ${DEV_DIR}/out.gcode \
    ${DEV_DIR}/modell"

echo "==> G-Code zurueckholen"
"${ADB}" pull -q "${DEV_DIR}/out.gcode" ./out.gcode >/dev/null
ls -lh ./out.gcode
head -20 ./out.gcode
