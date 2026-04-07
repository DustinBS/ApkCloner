# NoteNotes Wireless Deploy Setup
# Guides through Samsung S24 Wireless Debugging

param(
    [Parameter(Mandatory=$false)]
    [string]$PairAddress,
    [Parameter(Mandatory=$false)]
    [string]$ConnectAddress,
    [switch]$RunDeploy,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

function Show-Usage {
    Write-Host ""
    Write-Host "Usage:" -ForegroundColor Cyan
    Write-Host "  .\deploy_wireless.ps1 [-PairAddress <ip:port>] [-ConnectAddress <ip:port>] [-RunDeploy] [-Help]" -ForegroundColor Cyan
    Write-Host "" 
    Write-Host "Examples:" -ForegroundColor Cyan
    Write-Host "  .\deploy_wireless.ps1 -PairAddress 192.168.1.47:40109 -ConnectAddress 192.168.1.47:43061 -RunDeploy" -ForegroundColor Cyan
}

if ($Help) {
    Show-Usage
    exit 0
}

function Get-AdbPath {
    # 1) Check PATH
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Path) { return $cmd.Path }

    # 2) Check common SDK environment vars
    $candidates = @()
    if ($env:ANDROID_SDK_ROOT) { $candidates += Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe' }
    if ($env:ANDROID_HOME) { $candidates += Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' }
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }

    foreach ($p in $candidates) { if (Test-Path $p) { return $p } }
    return $null
}

$adb = Get-AdbPath
if (-not $adb) {
    Write-Host "adb not found in PATH or common SDK locations." -ForegroundColor Yellow
    $manual = Read-Host "Enter full path to adb.exe (or press Enter to abort)"
    if ([string]::IsNullOrWhiteSpace($manual)) {
        Write-Host "adb not found. Please install Android SDK or set ANDROID_SDK_ROOT/ANDROID_HOME." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $manual)) {
        Write-Host "Path entered does not exist: $manual" -ForegroundColor Red
        exit 1
    }
    $adb = $manual
}

Write-Host "`n=== NoteNotes Wireless Debugging Setup ===" -ForegroundColor Cyan
Write-Host "This script helps connect your Android device over Wi-Fi using adb." -ForegroundColor Cyan
Write-Host "Make sure your phone and PC are on the SAME Wi‑Fi network and Wireless debugging is enabled.`n"

# Pairing step
if ([string]::IsNullOrWhiteSpace($PairAddress)) {
    $PairAddress = Read-Host "Step: Enter the IP address & Port shown in the phone pairing popup (e.g., 192.168.1.47:40109)"
}

if (-not [string]::IsNullOrWhiteSpace($PairAddress)) {
    Write-Host "`nPairing with $PairAddress..." -ForegroundColor Yellow
    try {
        & "$adb" pair $PairAddress 2>&1 | ForEach-Object { Write-Host $_ }
    } catch {
        Write-Host "Pairing failed:`n$_" -ForegroundColor Red
    }
} else {
    Write-Host "No pairing address provided; skipping pairing step." -ForegroundColor Yellow
}

Write-Host "`nStep: After pairing, close the popup on your phone and check the main Wireless debugging screen for the NEW IP:PORT (the connect port)`n"

if ([string]::IsNullOrWhiteSpace($ConnectAddress)) {
    $ConnectAddress = Read-Host "Enter the NEW IP address & Port for connection (e.g., 192.168.1.47:43061) [Press Enter to skip]"
}

if (-not [string]::IsNullOrWhiteSpace($ConnectAddress)) {
    Write-Host "`nConnecting to $ConnectAddress..." -ForegroundColor Yellow
    try {
        & "$adb" connect $ConnectAddress 2>&1 | ForEach-Object { Write-Host $_ }
    } catch {
        Write-Host "Connect failed:`n$_" -ForegroundColor Red
    }
} else {
    Write-Host "No connect address provided; skipping connect step." -ForegroundColor Yellow
}

Write-Host "`nVerifying connection (adb devices)..." -ForegroundColor Yellow
try {
    & "$adb" devices | ForEach-Object { Write-Host $_ }
} catch {
    Write-Host "Failed to list devices:`n$_" -ForegroundColor Red
}

# Deploy step (runs deploy.ps1 located next to this script)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$deployScript = Join-Path $scriptDir 'deploy.ps1'
if ($RunDeploy) {
    if (Test-Path $deployScript) {
        Write-Host "`nRunning deploy.ps1..." -ForegroundColor Cyan
        try { Push-Location $scriptDir; & "$deployScript" } catch { Write-Host "deploy.ps1 failed:`n$_" -ForegroundColor Red } finally { Pop-Location }
    } else { Write-Host "`ndeploy.ps1 not found at $deployScript" -ForegroundColor Red }
} else {
    $runDeployAnswer = Read-Host "`nWould you like to run the deployment script now? (y/n)"
    if ($runDeployAnswer -match '^[yY]') {
        if (Test-Path $deployScript) {
            Write-Host "`nRunning deploy.ps1..." -ForegroundColor Cyan
            try { Push-Location $scriptDir; & "$deployScript" } catch { Write-Host "deploy.ps1 failed:`n$_" -ForegroundColor Red } finally { Pop-Location }
        } else { Write-Host "`ndeploy.ps1 not found at $deployScript" -ForegroundColor Red }
    } else {
        Write-Host "`nWireless setup complete! You can run deploy.ps1 anytime." -ForegroundColor Green
    }
}
