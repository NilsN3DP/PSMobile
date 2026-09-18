#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

ABI="${1:-x86_64}"
BUILD="${PSM_ROOT}/build-out/core-${ABI}"
TEST="${BUILD}/psm_contract_tests"
LIB="${BUILD}/stripped/libpsmobile_core.so"
[ -f "${LIB}" ] || LIB="${BUILD}/libpsmobile_core.so"
RES="${PSM_ROOT}/android/app/src/main/assets/psresources"
MODEL="${PSM_ROOT}/testdata/wuerfel20.stl"
PROJECT="$(mktemp "${TMPDIR:-/tmp}/psmobile-multibed-XXXXXX.3mf")"
INSTALLED_PROJECT="$(mktemp "${TMPDIR:-/tmp}/psmobile-installed-core-one-XXXXXX.3mf")"
REMOTE="/data/local/tmp/psmobile-tests-${ABI}"

if [ ! -f "${TEST}" ] || [ ! -f "${LIB}" ]; then
    psm_err "${ABI}: Vertragstest oder Core-Bibliothek fehlt"
    exit 1
fi

ADB=(adb)
if [ -n "${ANDROID_SERIAL:-}" ]; then
    ADB+=( -s "${ANDROID_SERIAL}" )
fi

cleanup() {
    "${ADB[@]}" shell rm -rf "${REMOTE}" >/dev/null 2>&1 || true
    rm -f -- "${PROJECT}" "${INSTALLED_PROJECT}"
}
trap cleanup EXIT

python3 "${PSM_ROOT}/build/scripts/make-test-project-3mf.py" "${PROJECT}"
python3 "${PSM_ROOT}/build/scripts/make-test-project-3mf.py" \
    --installed-core-one "${INSTALLED_PROJECT}"
"${ADB[@]}" shell rm -rf "${REMOTE}" >/dev/null 2>&1 || true

"${ADB[@]}" shell mkdir -p "${REMOTE}/data" "${REMOTE}/resources"
"${ADB[@]}" push "${TEST}" "${REMOTE}/psm_contract_tests" >/dev/null
"${ADB[@]}" push "${LIB}" "${REMOTE}/libpsmobile_core.so" >/dev/null
"${ADB[@]}" push "${MODEL}" "${REMOTE}/wuerfel20.stl" >/dev/null
"${ADB[@]}" push "${PROJECT}" "${REMOTE}/psmobile-multibed.3mf" >/dev/null
"${ADB[@]}" push "${INSTALLED_PROJECT}" \
    "${REMOTE}/psmobile-installed-core-one.3mf" >/dev/null
"${ADB[@]}" push "${RES}/." "${REMOTE}/resources/" >/dev/null
"${ADB[@]}" shell chmod 755 "${REMOTE}/psm_contract_tests"
"${ADB[@]}" shell \
    "LD_LIBRARY_PATH='${REMOTE}' '${REMOTE}/psm_contract_tests' '${REMOTE}/data' '${REMOTE}/resources' '${REMOTE}/wuerfel20.stl' '${REMOTE}/psmobile-multibed.3mf' '${REMOTE}/psmobile-installed-core-one.3mf'"
