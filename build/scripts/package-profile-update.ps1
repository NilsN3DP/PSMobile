<#
Erzeugt ein signiertes-inhaltsgeprüftes Profilpaket plus Manifest für den
Android-Updatekanal. Das Paket wird anschließend unverändert auf einen
HTTPS-Host hochgeladen; die App akzeptiert nur die im Build freigegebene URL.
#>
param(
    [Parameter(Mandatory = $true)] [string] $Version,
    [Parameter(Mandatory = $true)] [string] $PackageUrl,
    [Parameter(Mandatory = $true)] [string] $OutputDirectory,
    [string] $ResourceRoot = "android/app/src/main/assets/psresources",
    [string] $MinSlic3rVersion = "2.9.6",
    [string[]] $ReleaseNote = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Version -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
    throw 'Version muss MAJOR.MINOR.PATCH sein.'
}
if ($MinSlic3rVersion -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
    throw 'MinSlic3rVersion muss MAJOR.MINOR.PATCH sein.'
}
$uri = [Uri] $PackageUrl
if ($uri.Scheme -ne 'https') { throw 'PackageUrl muss HTTPS verwenden.' }

$root = (Resolve-Path -LiteralPath $ResourceRoot).Path
$required = @(
    'profiles/PrusaResearch.ini',
    'profiles/PrusaResearch.idx',
    'profiles/PrusaResearchSLA.ini',
    'profiles/PrusaResearchSLA.idx'
)
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $relative) -PathType Leaf)) {
        throw "Pflichtdatei fehlt: $relative"
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $root 'shaders/ES') -PathType Container)) {
    throw 'Pflichtordner fehlt: shaders/ES'
}

$out = New-Item -ItemType Directory -Force -Path $OutputDirectory
$package = Join-Path $out.FullName "psmobile-profiles-$Version.zip"
if (Test-Path -LiteralPath $package) { Remove-Item -LiteralPath $package -Force }
Compress-Archive -Path (Join-Path $root 'profiles'), (Join-Path $root 'shaders') -DestinationPath $package -CompressionLevel Optimal
$sha256 = (Get-FileHash -LiteralPath $package -Algorithm SHA256).Hash.ToLowerInvariant()

$manifest = [ordered]@{
    version             = $Version
    package_url         = $PackageUrl
    sha256              = $sha256
    min_slic3r_version  = $MinSlic3rVersion
    release_notes       = @($ReleaseNote)
}
$manifestPath = Join-Path $out.FullName 'manifest.json'
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
Write-Host "Paket:    $package"
Write-Host "Manifest: $manifestPath"
Write-Host "SHA-256:  $sha256"
