#!/usr/bin/env bash
# Fuehrt ein Kommando im PSMobile-Build-Container aus.
# Beispiel:  build/scripts/dockerrun.sh bash -c 'aarch64-linux-android26-clang++ --version'

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

exec docker run --rm -i \
    -v "${PSM_ROOT}:/work" \
    -w /work \
    -e PSM_ANDROID_ABI="${ANDROID_ABI}" \
    -e PSM_ANDROID_API="${ANDROID_API}" \
    -e NPROC="${NPROC}" \
    --cpus "${NPROC}" \
    "${PSM_IMAGE}" "$@"
