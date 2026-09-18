#!/usr/bin/env bash
# Baut die iOS-App fuers Geraet und packt sie als .ipa zum Sideloaden.
#
#   build/scripts/package-ipa.sh          nach ~/PSMobile-unsigniert.ipa
#   build/scripts/package-ipa.sh /pfad    an einen anderen Ort
#
# Der Signierschritt ist der Punkt, an dem es beim ersten Mal
# gescheitert ist. Xcode baut mit CODE_SIGNING_ALLOWED=NO eine App ganz
# ohne Signatur - und installd auf dem Geraet lehnt das mit
# "0xe800801c (No code signature found)" ab, auch wenn Sideloadly
# anschliessend neu signiert. Eine Ad-hoc-Signatur genuegt: sie ist
# nicht vertrauenswuerdig, aber vorhanden und gueltig, und Sideloadly
# ersetzt sie sauber durch die des Nutzers.
#
# Warum nicht gleich mit -CODE_SIGN_IDENTITY=- bauen: das lehnt Xcode
# fuer das iOS-SDK ab ("Ad Hoc code signing is not allowed"). Nachtraeglich
# mit codesign geht es.
set -euo pipefail

ZIEL="${1:-$HOME/PSMobile-unsigniert.ipa}"
WURZEL="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BAU="${WURZEL}/build-out/ios-derived-device"
APP="${BAU}/Build/Products/Debug-iphoneos/PSMobile.app"
LOG_VERZEICHNIS="${WURZEL}/build-out/logs"
XCODE_LOG="${LOG_VERZEICHNIS}/package-ipa-xcodebuild.log"

printf '==> Regelmodul fuer das Geraet\n'
bash "${WURZEL}/build/scripts/build-shared-ios.sh" device >/dev/null

printf '==> Ressourcen\n'
bash "${WURZEL}/build/scripts/stage-resources.sh" ios >/dev/null

printf '==> App\n'
cd "${WURZEL}/ios"
PATH="${WURZEL}/build-out/mac-tools/bin:${PATH}" xcodegen generate >/dev/null
# Ein vorheriger, fehlgeschlagener Lauf darf niemals seine App an diesen
# Lauf vererben. Sonst wuerde die Exit-Code-Maskierung unten ein altes
# Bundle signieren und als angeblich frisches IPA ausgeben.
rm -rf "${BAU}"
mkdir -p "${LOG_VERZEICHNIS}"
rm -f "${XCODE_LOG}"
if ! xcodebuild -project PSMobile.xcodeproj -scheme PSMobile \
    -sdk iphoneos -configuration Debug \
    -derivedDataPath "${BAU}" \
    CODE_SIGN_IDENTITY='' CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO \
    build 2>&1 | tee "${XCODE_LOG}"; then
    printf 'Der Xcode-Build ist fehlgeschlagen. Vollstaendiges Log: %s\n' \
        "${XCODE_LOG}" >&2
    exit 1
fi

test -d "${APP}" || { printf 'Die App wurde nicht gebaut.\n' >&2; exit 1; }

printf '==> Ad-hoc signieren\n'
codesign --force --sign - --timestamp=none "${APP}"
codesign --verify --verbose=2 "${APP}"

printf '==> Packen\n'
ARBEIT="$(mktemp -d)"
TEMP_IPA=''
aufraeumen() {
    rm -rf "${ARBEIT}"
    [ -z "${TEMP_IPA}" ] || rm -f "${TEMP_IPA}"
}
trap aufraeumen EXIT
mkdir -p "${ARBEIT}/Payload"
cp -R "${APP}" "${ARBEIT}/Payload/"
ZIEL_VERZEICHNIS="$(dirname "${ZIEL}")"
mkdir -p "${ZIEL_VERZEICHNIS}"
TEMP_IPA="$(mktemp "${ZIEL_VERZEICHNIS}/.PSMobile.ipa.XXXXXX")"
# mktemp legt die Datei an, damit der Name reserviert ist. zip haelt
# eine vorhandene Datei aber fuer ein Archiv, das es ergaenzen soll,
# und bricht mit "Zip file structure invalid" ab. Also den Namen
# behalten und die leere Huelle wegnehmen.
rm -f "${TEMP_IPA}"
(cd "${ARBEIT}" && zip -qry "${TEMP_IPA}" Payload)
unzip -t "${TEMP_IPA}" >/dev/null
mv -f "${TEMP_IPA}" "${ZIEL}"
TEMP_IPA=''

printf '\n%s\n' "${ZIEL}"
ls -la "${ZIEL}"
