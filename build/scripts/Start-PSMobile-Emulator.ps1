[CmdletBinding()]
param(
    [string]$AvdName = "PSM_Tablet",
    [string]$PackageName = "de.psmobile"
)

$ErrorActionPreference = "Stop"
$sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $emulator) -or -not (Test-Path -LiteralPath $adb)) {
    throw "Android SDK bzw. Emulator wurde unter '$sdkRoot' nicht gefunden."
}

function Get-RunningEmulator {
    (& $adb devices) |
        ForEach-Object {
            if ($_ -match '^(emulator-\d+)\s+device$') { $Matches[1] }
        } |
        Select-Object -First 1
}

$serial = Get-RunningEmulator
if (-not $serial) {
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName, "-netdelay", "none", "-netspeed", "full")
    & $adb wait-for-device | Out-Null
    do {
        Start-Sleep -Seconds 2
        $booted = (& $adb shell getprop sys.boot_completed).Trim()
    } until ($booted -eq "1")
    $serial = Get-RunningEmulator
}

if (-not $serial) { throw "Der Emulator '$AvdName' wurde nicht erkannt." }

$installed = (& $adb -s $serial shell pm path $PackageName).Trim()
if (-not $installed.StartsWith("package:")) {
    throw "PSMobile ist im Emulator nicht installiert. Bitte zuerst die Debug-APK installieren."
}

& $adb -s $serial shell monkey -p $PackageName 1 | Out-Null
