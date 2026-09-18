#!/usr/bin/env bash
# M0: Build-Image bauen und PrusaSlicer-Quellen holen.
# Idempotent - kann jederzeit erneut laufen.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

# --- 1. Docker-Image ------------------------------------------------------
if docker image inspect "${PSM_IMAGE}" >/dev/null 2>&1; then
    psm_log "Image ${PSM_IMAGE} existiert bereits"
else
    psm_log "Baue Image ${PSM_IMAGE} (NDK-Download, dauert ein paar Minuten)"
    docker build -t "${PSM_IMAGE}" "${PSM_ROOT}/build/docker"
fi

psm_log "NDK-Version im Image"
docker run --rm "${PSM_IMAGE}" \
    bash -c 'cat $ANDROID_NDK_HOME/source.properties | head -2; aarch64-linux-android26-clang++ --version | head -1'

# --- 2. PrusaSlicer-Quellen ----------------------------------------------
if [ -d "${PS_SRC}/.git" ]; then
    psm_log "PrusaSlicer-Checkout vorhanden: $(git -C "${PS_SRC}" describe --tags 2>/dev/null || echo unbekannt)"
else
    psm_log "Hole PrusaSlicer ${PS_TAG}"
    mkdir -p "$(dirname "${PS_SRC}")"
    git clone --depth 1 --branch "${PS_TAG}" "${PS_REPO}" "${PS_SRC}"
fi

# --- 3. Patches ----------------------------------------------------------
# Bis hierher hat nur macos-bootstrap.sh die Patches angewendet - der
# Android-Weg holte die Quellen und liess sie, wie sie sind. Was dort
# trotzdem gebaut wurde, kam ueber Toolchain-Datei und CMake-Schalter
# zustande; 0003 (STEP ohne Nachladen) fehlte damit auf Android
# vollstaendig, und niemand hat es gemerkt, weil STEP ohnehin aus war.
psm_log "Patches"
for patch in "${PSM_ROOT}"/patches/*.patch; do
    [ -f "${patch}" ] || continue
    if git -C "${PS_SRC}" apply --reverse --check "${patch}" >/dev/null 2>&1; then
        printf '    schon angewendet: %s
' "$(basename "${patch}")"
    elif git -C "${PS_SRC}" apply --check "${patch}" >/dev/null 2>&1; then
        git -C "${PS_SRC}" apply "${patch}"
        printf '    angewendet: %s
' "$(basename "${patch}")"
    else
        psm_warn "laesst sich nicht anwenden: $(basename "${patch}")"
    fi
done

mkdir -p "${DL_CACHE}"

psm_log "Bootstrap fertig"
echo "  PrusaSlicer : ${PS_SRC}"
echo "  Deps-Prefix : ${DEPS_PREFIX}"
echo "  ABI / API   : ${ANDROID_ABI} / ${ANDROID_API}"
