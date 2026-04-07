<#
Pull an installed app's APK from a connected device via ADB and run the repack flow.

Usage:
  .\pull_and_repack.ps1 -PackageName com.example.app -Out cloned.apk -NewPackage com.example.app.clone -Label "My App (Clone)" -Keystore mykeystore.jks -KeyAlias myalias -StorePass pass -KeyPass pass

Requires: adb on PATH, and the device connected with USB debugging enabled.
#>

param(
    [Parameter(Mandatory=$true)][string]$PackageName,
    [Parameter(Mandatory=$true)][string]$Out,
    [string]$NewPackage,
    [string]$Label,
    [string]$Keystore,
    [string]$KeyAlias,
    [string]$StorePass,
    [string]$KeyPass
)

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error "adb not found on PATH. Install Android Platform Tools and try again."
    exit 2
}

Write-Host "Getting APK path for $PackageName on device..."
$out = adb shell pm path $PackageName 2>&1
if ($LASTEXITCODE -ne 0 -or -not $out) {
    Write-Error "Failed to query package path: $out"
    exit 3
}

$lines = $out -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }
$paths = $lines | ForEach-Object { $_ -replace '^package:', '' }
if ($paths.Length -eq 0) {
    Write-Error "No APK path found for package $PackageName"
    exit 4
}

$tmpApk = Join-Path $PSScriptRoot "pulled_${PackageName.Replace('.', '_')}.apk"
Write-Host "Pulling $($paths[0]) to $tmpApk"
adb pull $paths[0] $tmpApk | Out-Null
if (-not (Test-Path $tmpApk)) {
    Write-Error "Failed to pull APK from device"
    exit 5
}

# Call repack.ps1 in the same folder
& "$PSScriptRoot\repack.ps1" -Input $tmpApk -Out $Out -NewPackage $NewPackage -Label $Label -Keystore $Keystore -KeyAlias $KeyAlias -StorePass $StorePass -KeyPass $KeyPass
