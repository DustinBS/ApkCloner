# NoteNotes Wireless Deploy Setup
# Guides through Samsung S24 Wireless Debugging

param(
    [Parameter(Mandatory=$false)]
    [string]$PairAddress,
    [Parameter(Mandatory=$false)]
    [string]$ConnectAddress,
    [Parameter(Mandatory=$false)]
    [string]$PairingCode,
    [Parameter(Mandatory=$false)]
    [switch]$UseQR,
    [Parameter(Mandatory=$false)]
    [string]$QrData,
    [switch]$RunDeploy,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

function Show-Usage {
    Write-Host ''
    Write-Host 'Usage:' -ForegroundColor Cyan
    Write-Host '  .\deploy_wireless.ps1 [-PairAddress <ip:port>] [-PairingCode <code>] [-ConnectAddress <ip:port>] [-RunDeploy]' -ForegroundColor Cyan
    Write-Host '  .\deploy_wireless.ps1 -UseQR [-QrData "<paste QR text>"] [-ConnectAddress <ip:port>] [-RunDeploy]' -ForegroundColor Cyan
    Write-Host ''
    Write-Host 'Notes:' -ForegroundColor Cyan
    Write-Host '  - PairAddress is the address shown in the phone pairing dialog (format ip:port, e.g. 192.168.1.47:40109).' -ForegroundColor Cyan
    Write-Host '  - After pairing, the phone shows a NEW IP:PORT on the Wireless debugging main screen - use that as ConnectAddress (often the port changes).' -ForegroundColor Cyan
    Write-Host '  - If you use -UseQR, scan the QR code on your phone from another device and paste the decoded text via -QrData or when prompted.' -ForegroundColor Cyan
    Write-Host ''
    Write-Host 'Examples:' -ForegroundColor Cyan
    Write-Host '  .\deploy_wireless.ps1 -PairAddress 192.168.1.47:40109 -PairingCode 123456 -ConnectAddress 192.168.1.47:43061 -RunDeploy' -ForegroundColor Cyan
    Write-Host '  .\deploy_wireless.ps1 -UseQR -QrData "adb pair 192.168.1.47:40109 123456" -RunDeploy' -ForegroundColor Cyan
    Write-Host ''
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

# Utility: parse QR/pasted text for pair address and code
function Parse-QRText {
    param([string]$text)
    $res = @{ PairAddress = $null; PairingCode = $null }
    if ([string]::IsNullOrWhiteSpace($text)) { return $res }
    if ($text -match 'adb\s+pair\s+([0-9]{1,3}(?:\.[0-9]{1,3}){3}:\d{1,5})(?:\s+(\d{4,8}))?') {
        $res.PairAddress = $matches[1]
        if ($matches[2]) { $res.PairingCode = $matches[2] }
        return $res
    }
    $ipPortPattern = '([0-9]{1,3}(?:\.[0-9]{1,3}){3}:\d{1,5})'
    if ($text -match $ipPortPattern) { $res.PairAddress = $matches[1] }
    if ($text -match '\b([0-9]{4,8})\b') {
        $num = $matches[1]
        if (-not ($res.PairAddress -and $res.PairAddress -match [regex]::Escape($num))) {
            $res.PairingCode = $num
        }
    }
    return $res
}

function Validate-IpPort {
    param([string]$addr)
    if ([string]::IsNullOrWhiteSpace($addr)) { return $false }
    if ($addr -notmatch '^([0-9]{1,3}\.){3}[0-9]{1,3}:\d{1,5}$') { return $false }
    $parts = $addr.Split(':')
    $ip = $parts[0]; $port = [int]$parts[1]
    $octets = $ip.Split('.')
    foreach ($o in $octets) { if ([int]$o -lt 0 -or [int]$o -gt 255) { return $false } }
    if ($port -lt 1 -or $port -gt 65535) { return $false }
    return $true
}

function Get-IpPortFromAdbDevices {
    param([string]$adbPath)
    $out = & "$adbPath" devices 2>&1 | Out-String
    $lines = $out -split '\r?\n' | Select-Object -Skip 1
    $found = @()
    foreach ($l in $lines) {
        if ($l -match '^\s*([0-9]{1,3}(?:\.[0-9]{1,3}){3}:\d{1,5})\s+(\w+)') {
            $found += @{ Address = $matches[1]; State = $matches[2]; Raw = $l }
        }
    }
    return $found
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
