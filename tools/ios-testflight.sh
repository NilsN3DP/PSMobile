#!/bin/zsh
# Archiviert die iOS-App fuer TestFlight - auf dem Mac, in einem
# Terminal der angemeldeten Sitzung.
#
# Warum nicht per SSH aus tools/ios-bauen.sh heraus: der Schluessel des
# Zertifikats "Apple Distribution" liegt im Anmelde-Schluesselbund, und
# der ist fuer eine SSH-Sitzung gesperrt. codesign meldet dann
# errSecInternalComponent (so gesehen am 12.09.2026). Am Mac selbst, in
# Terminal.app, ist der Schluesselbund offen und alles laeuft durch.
#
#   cd ~/psmobile-1zu1 && zsh tools/ios-testflight.sh
#
# Geht auch von Windows aus, solange Nils am Mac angemeldet ist: `open`
# startet ein Terminal in seiner grafischen Sitzung, und dort ist der
# Schluesselbund offen (so am 12.09.2026 gebaut, 52 s bei warmem Cache):
#   ssh mac 'open -a Terminal ~/psmobile-testflight-lauf.command'
# (die .command-Datei ruft dieses Skript auf und schreibt
#  ~/psmobile-testflight.log; Ende erkennbar an "== exit").
#
# Voraussetzungen (stehen nach tools/ios-bauen.sh und dem Kernbau bereit):
#   - build-out/ios-core-OS64/libpsmobile_core_all.a   (Geraete-Kern)
#   - android/shared/build/bin/iosArm64/debugFramework  (PSMShared)
#   - das Profil "PsmobileCert" (App Store, de.psmobile, Team 27U86M489H)
#     unter ~/Library/Developer/Xcode/UserData/Provisioning Profiles
#
# Ergebnis: build-out/testflight-<Version>/PSMobile.ipa - hochladen mit
# Transporter.app oder:
#   xcrun altool --upload-app -f build-out/testflight-*/PSMobile.ipa \
#       -t ios --apiKey <KEY-ID> --apiIssuer <ISSUER-ID>

set -euo pipefail
export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8
eval "$(/opt/homebrew/bin/brew shellenv)" 2>/dev/null || true

WURZEL="$(cd "$(dirname "$0")/.." && pwd)"
cd "$WURZEL/ios"

VERSION="$(plutil -extract CFBundleShortVersionString raw PSMobile/Support/Info.plist)"
BUILD="$(plutil -extract CFBundleVersion raw PSMobile/Support/Info.plist)"
ARCHIV="$WURZEL/build-out/PSMobile-$VERSION-$BUILD.xcarchive"
EXPORT="$WURZEL/build-out/testflight-$VERSION"
TEAM="27U86M489H"
PROFIL="PsmobileCert"

echo "== PSMobile $VERSION ($BUILD) fuer TestFlight =="
[ -f "$WURZEL/build-out/ios-core-OS64/libpsmobile_core_all.a" ] || {
    echo "Geraete-Kern fehlt: PSM_IOS_PLATFORM=OS64 bash build/scripts/build-ios.sh core" >&2; exit 1; }

xcodegen generate --quiet
( cd ../android && chmod +x gradlew && export JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
  && ./gradlew -q :shared:linkDebugFrameworkIosArm64 )

rm -rf "$ARCHIV" "$EXPORT"
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile -configuration Release \
    -sdk iphoneos -destination "generic/platform=iOS" \
    -archivePath "$ARCHIV" archive \
    CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM="$TEAM" \
    CODE_SIGN_IDENTITY="Apple Distribution" \
    PROVISIONING_PROFILE_SPECIFIER="$PROFIL" -quiet

OPTIONEN="$(mktemp -t psm-export).plist"
cat > "$OPTIONEN" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>method</key><string>app-store-connect</string>
  <key>teamID</key><string>$TEAM</string>
  <key>signingStyle</key><string>manual</string>
  <key>signingCertificate</key><string>Apple Distribution</string>
  <key>provisioningProfiles</key><dict><key>de.psmobile</key><string>$PROFIL</string></dict>
  <key>destination</key><string>export</string>
  <key>uploadSymbols</key><true/>
</dict></plist>
PL
xcodebuild -exportArchive -archivePath "$ARCHIV" -exportOptionsPlist "$OPTIONEN" \
    -exportPath "$EXPORT" -quiet

echo
echo "Fertig: $(ls "$EXPORT"/*.ipa)"
echo "Hochladen: Transporter.app oder xcrun altool --upload-app (siehe Kopf dieser Datei)."
