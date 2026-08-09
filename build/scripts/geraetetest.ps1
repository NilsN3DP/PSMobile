# Gerätetest für das Android-Tablet.
#
# Der Simulator und der Emulator beantworten die wichtigsten Fragen
# nicht: kein Speicherlimit, keine Wärmegrenze, eine andere GPU. Was auf
# dem Tablet gilt, weiß nur das Tablet.
#
# Das Skript setzt voraus, dass das Tablet per USB hängt, USB-Debugging
# an ist und die Abfrage auf dem Gerät bestätigt wurde. Ohne diese
# Bestätigung sieht adb das Gerät als "unauthorized" — das ist keine
# Fehlfunktion, sondern der Schutz, der verhindert, dass ein fremder
# Rechner einfach Apps installiert.
#
#   .\geraetetest.ps1              installiert und startet
#   .\geraetetest.ps1 -Protokoll   dazu Logcat mitschreiben
#   .\geraetetest.ps1 -Bild        dazu ein Bildschirmfoto ziehen

param(
    [switch]$Protokoll,
    [switch]$Bild,
    [string]$Projektwurzel = (Join-Path $PSScriptRoot '..\..')
)

$ErrorActionPreference = 'Stop'

$Projektwurzel = (Resolve-Path -LiteralPath $Projektwurzel).Path
$Apk      = Join-Path $Projektwurzel 'android\app\build\outputs\apk\production\debug\app-production-debug.apk'
$Paket    = 'de.psmobile'
$Ablage   = Join-Path $env:USERPROFILE 'PSMobile\Geraetetest'

function Schritt($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Fehler($text) { Write-Host "!!  $text" -ForegroundColor Red }

# --- adb finden -----------------------------------------------------------
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    foreach ($p in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
                     "$env:ProgramFiles\platform-tools\adb.exe")) {
        if (Test-Path $p) { $adb = $p; break }
    }
}
if (-not $adb) { Fehler 'adb nicht gefunden.'; exit 1 }
Schritt "adb: $adb"

# --- Gerät prüfen ---------------------------------------------------------
$zeilen = & $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() }
$bereit = $zeilen | Where-Object { $_ -match '\sdevice$' }
if (-not $bereit) {
    Fehler 'Kein bereites Gerät.'
    if ($zeilen) {
        $zeilen | ForEach-Object { Write-Host "    $_" }
        Write-Host '    "unauthorized" heißt: die Abfrage auf dem Tablet noch bestätigen.'
    } else {
        Write-Host '    USB anstecken, USB-Debugging einschalten, Abfrage bestätigen.'
    }
    exit 1
}
$seriennummer = ($bereit -split '\s+')[0]
$modell = (& $adb -s $seriennummer shell getprop ro.product.model).Trim()
$android = (& $adb -s $seriennummer shell getprop ro.build.version.release).Trim()
$abi = (& $adb -s $seriennummer shell getprop ro.product.cpu.abi).Trim()
Schritt "Gerät: $modell · Android $android · $abi"

if ($abi -ne 'arm64-v8a') {
    Fehler "Die App ist nur für arm64-v8a gebaut, das Gerät meldet $abi."
    exit 1
}

# --- Installieren ---------------------------------------------------------
if (-not (Test-Path $Apk)) { Fehler "APK fehlt: $Apk"; exit 1 }
$groesse = [math]::Round((Get-Item $Apk).Length / 1MB, 1)
Schritt "Installiere $(Split-Path -Leaf $Apk) ($groesse MB, $((Get-Item $Apk).LastWriteTime))"
& $adb -s $seriennummer install -r $Apk

# --- Protokoll ------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $Ablage | Out-Null
$stempel = Get-Date -Format 'yyyy-MM-dd-HHmm'
$logDatei = Join-Path $Ablage "logcat-$stempel.txt"
if ($Protokoll) {
    & $adb -s $seriennummer logcat -c
    Schritt "Logcat läuft nach $logDatei — mit Strg+C beenden"
}

# --- Starten --------------------------------------------------------------
Schritt 'Starte die App'
& $adb -s $seriennummer shell monkey -p $Paket -c android.intent.category.LAUNCHER 1 | Out-Null

if ($Protokoll) {
    # Nur was von uns kommt plus alles ab Warnung - alles andere ist
    # Systemrauschen, in dem die eine wichtige Zeile untergeht.
    & $adb -s $seriennummer logcat -v time PSMobile:V psmobile:V slic3r:V AndroidRuntime:E '*:W' |
        Tee-Object -FilePath $logDatei
}

if ($Bild) {
    $bildDatei = Join-Path $Ablage "bildschirm-$stempel.png"
    & $adb -s $seriennummer exec-out screencap -p > $bildDatei
    Schritt "Bildschirmfoto: $bildDatei"
}

Schritt 'Fertig.'
Write-Host "Berichte und Protokolle liegen in $Ablage"
