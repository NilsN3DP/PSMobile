#!/usr/bin/env bash
# Baut das APK im Container. Setzt voraus, dass build-core.sh und
# stage-resources.sh/stage-native.sh gelaufen sind.
#
#   build-apk.sh            Debug-APK
#   build-apk.sh release    Release-APK (unsigniert)

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

VARIANT="${1:-debug}"
IMAGE="psmobile-app:1"
CACHE="${PSM_ROOT}/build-out/gradle-cache"

if ! docker image inspect "${IMAGE}" >/dev/null 2>&1; then
    psm_log "Baue ${IMAGE}"
    docker build -f "${PSM_ROOT}/build/docker/Dockerfile.app" -t "${IMAGE}" "${PSM_ROOT}/build/docker"
fi

if [ ! -d "${PSM_ROOT}/android/app/src/main/jniLibs" ]; then
    psm_err "Native Bibliothek fehlt. Erst build/scripts/stage-native.sh laufen lassen."
    exit 1
fi
if [ ! -d "${PSM_ROOT}/android/app/src/main/assets/psresources" ]; then
    psm_err "Ressourcen fehlen. Erst build/scripts/stage-resources.sh android laufen lassen."
    exit 1
fi

mkdir -p "${CACHE}"

case "${VARIANT}" in
    debug)   TASK=':app:assembleProductionDebug' ;;
    release) TASK=':app:assembleProductionRelease' ;;
    test)    TASK=':app:testProductionDebugUnitTest' ;;
    # Die Regeln aus dem gemeinsamen Modul (E-13). Sie haengen an keiner
    # Plattform und laufen ohne Emulator und ohne NDK. Auf Linux baut nur
    # das Android-Ziel des Moduls; die iOS-Fassung entsteht auf dem Mac.
    shared)  TASK=':shared:testDebugUnitTest' ;;
    *) echo "Unbekannte Variante: ${VARIANT}" >&2
       echo "Erlaubt: debug release test shared" >&2; exit 2 ;;
esac

psm_log "Gradle ${TASK}"
docker run --rm -i \
    -v "${PSM_ROOT}:/work" \
    -v "${CACHE}:/gradle-cache" \
    -w /work/android \
    "${IMAGE}" bash -euo pipefail -c "
      # Der Wrapper-Jar ist nicht eingecheckt (Binaerballast). Beim ersten
      # Lauf erzeugt ihn eine im Container geholte Gradle-Distribution.
      if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
          GRADLE_VER=\$(sed -n 's|.*gradle-\\([0-9.]*\\)-bin.zip|\\1|p' gradle/wrapper/gradle-wrapper.properties)
          echo \"Hole Gradle \${GRADLE_VER} zum Erzeugen des Wrappers\"
          curl -fsSL -o /tmp/gradle.zip \"https://services.gradle.org/distributions/gradle-\${GRADLE_VER}-bin.zip\"
          unzip -q /tmp/gradle.zip -d /opt
          /opt/gradle-\${GRADLE_VER}/bin/gradle wrapper --gradle-version \${GRADLE_VER} --no-daemon
      fi
      ./gradlew ${TASK} --no-daemon --stacktrace
    "

psm_log "APK"
find "${PSM_ROOT}/android/app/build/outputs/apk" -name '*.apk' -exec ls -lh {} \; 2>/dev/null \
    || psm_warn "kein APK gefunden"
