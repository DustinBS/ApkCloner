# AppCloner Deploy Script
# Builds the debug APK and installs it on a connected Android device.
# Usage: .\deploy.ps1

$ErrorActionPreference = "Stop"

# --- Paths ---
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = $scriptDir

function Find-Java {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    try {
        $where = & where.exe java 2>$null
        if ($LASTEXITCODE -eq 0 -and $where) { return ($where -split "`r?`n")[0] }
    } catch {}
    return $null
}

$javaExe = Find-Java
if (-not $javaExe) {
    Write-Host "`nERROR: Java not found. Set JAVA_HOME or ensure java is on PATH." -ForegroundColor Red
    exit 1
}
$prevErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $javaVer = & $javaExe -version 2>&1 | Select-Object -First 1
} catch {
    $javaVer = ($_ | Out-String).Trim()
}
$ErrorActionPreference = $prevErrorAction
Write-Host "`n[1/5] JDK: $javaVer" -ForegroundColor Green

function Find-ADB {
    $candidates = @()
    if ($env:ANDROID_SDK_ROOT) { $candidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe" }
    if ($env:ANDROID_HOME) { $candidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
    foreach ($p in $candidates) { if (Test-Path $p) { return $p } }
    try {
        $where = & where.exe adb.exe 2>$null
        if ($LASTEXITCODE -eq 0 -and $where) { return ($where -split "`r?`n")[0] }
    } catch {}
    return $null
}

$adb = Find-ADB
if (-not $adb) {
    Write-Host "`nERROR: adb not found. Install Android SDK platform-tools or add adb to PATH." -ForegroundColor Red
    exit 1
}
Write-Host "[2/5] Using ADB: $adb" -ForegroundColor Green
Write-Host "[2/5] Checking connected devices..." -ForegroundColor Green

# Start ADB server
Write-Host "  Starting ADB server..." -ForegroundColor Yellow
& $adb start-server
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nERROR: Failed to start ADB server!" -ForegroundColor Red
    exit 1
}
$rawDevices = (& $adb devices 2>&1) -split "`r?`n"
# Filter out header/blank lines and extract serials for lines that end with 'device'
$deviceSerials = @()
foreach ($line in $rawDevices) {
    $l = $line.Trim()
    if (-not $l) { continue }
    if ($l -match '^List of devices attached') { continue }
    if ($l -match '^(\S+)\s+device$') { $deviceSerials += $Matches[1] }
}
$deviceCount = $deviceSerials.Count

if ($deviceCount -eq 0) {
    Write-Host "`nERROR: No authorized device found!" -ForegroundColor Red
    Write-Host "  - Is USB debugging enabled on your phone?"
    Write-Host "  - Did you accept the USB debugging prompt on your phone?"
    Write-Host "  - Try: & '$adb' kill-server; & '$adb' start-server; & '$adb' devices"
    exit 1
}

if ($deviceCount -gt 1) {
    Write-Host "`nMultiple devices found:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $deviceSerials.Count; $i++) {
        Write-Host "  [$i] $($deviceSerials[$i])"
    }
    $choice = Read-Host "Pick a device number (0-$($deviceCount-1))"
    $targetDevice = $deviceSerials[[int]$choice]
    Write-Host "Using device: $targetDevice" -ForegroundColor Green
    $deviceArg = @('-s', $targetDevice)
} else {
    $targetDevice = $deviceSerials[0]
    Write-Host "  Device: $targetDevice" -ForegroundColor Green
    $deviceArg = @('-s', $targetDevice)
}

# --- Detect project package and main activity ---
$packageName = $null
$buildGradle = Join-Path $projectRoot "app\build.gradle"
if (Test-Path $buildGradle) {
    $gradleText = Get-Content $buildGradle -Raw
    if ($gradleText -match 'applicationId\s+["'']([^"'']+)["'']') { $packageName = $Matches[1] }
}
$manifestPath = Join-Path $projectRoot "app\src\main\AndroidManifest.xml"
$mainActivity = $null
if (Test-Path $manifestPath) {
    $manifestText = Get-Content $manifestPath -Raw
    if (-not $packageName -and ($manifestText -match 'package\s*=\s*"([^"]+)"')) { $packageName = $Matches[1] }

    try {
        [xml]$xml = $manifestText
        $androidNs = 'http://schemas.android.com/apk/res/android'
        $nsmgr = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
        $nsmgr.AddNamespace('android', $androidNs)

        # Find activities that declare the MAIN action
        $xpathMain = "//activity[intent-filter/action[@android:name='android.intent.action.MAIN']]"
        $mainActs = $xml.SelectNodes($xpathMain, $nsmgr)
        if ($mainActs -and $mainActs.Count -gt 0) {
            # Prefer one that's explicitly exported
            foreach ($a in $mainActs) {
                $name = $a.GetAttribute('name', $androidNs)
                if (-not $name) { $name = $a.GetAttribute('android:name') }
                $exported = $a.GetAttribute('exported', $androidNs)
                if ($exported -eq 'true') { $mainActivity = $name; break }
            }
            if (-not $mainActivity) {
                $a = $mainActs[0]
                $mainActivity = $a.GetAttribute('name', $androidNs)
                if (-not $mainActivity) { $mainActivity = $a.GetAttribute('android:name') }
            }
        } else {
            # Fallback: find any exported activity
            $allActs = $xml.SelectNodes('//activity', $nsmgr)
            foreach ($a in $allActs) {
                $exported = $a.GetAttribute('exported', $androidNs)
                if ($exported -eq 'true') {
                    $mainActivity = $a.GetAttribute('name', $androidNs)
                    if (-not $mainActivity) { $mainActivity = $a.GetAttribute('android:name') }
                    break
                }
            }
        }
    } catch {
        # XML parse failed; fall back to previous regex behavior
        if ($manifestText -match '(?s)<activity\b[^>]*android:name\s*=\s*"([^"]+)"[^>]*>.*?<intent-filter>.*?<action\b[^>]*android:name\s*=\s*"android.intent.action.MAIN"') { $mainActivity = $Matches[1] }
    }
}
if (-not $packageName) { $packageName = Read-Host "Could not detect applicationId. Enter package name (e.g. com.example.appcloner)"; if (-not $packageName) { Write-Host "ERROR: No package name provided. Aborting." -ForegroundColor Red; exit 1 } }
if (-not $mainActivity) { $mainActivity = ".MainActivity" }

# Normalize activity for am start (-n expects package/Activity)
if ($mainActivity -match '^\.') {
    # already relative (e.g. .MainActivity)
} elseif ($mainActivity -match '\.') {
    # fully-qualified (com.example.app.MainActivity) — leave as-is
} else {
    # simple name (MainActivity) — make relative
    $mainActivity = '.' + $mainActivity
}
Write-Host "  Launch activity: $mainActivity" -ForegroundColor Green

Write-Host "`n=== AppCloner Deploy ($packageName) ===" -ForegroundColor Cyan

# --- Step 3: Build ---
Write-Host "[3/5] Building debug APK..." -ForegroundColor Green
$gradlew = Join-Path $projectRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) { $gradlew = "gradlew.bat" }
Push-Location $projectRoot
& $gradlew assembleDebug --daemon
$buildExit = $LASTEXITCODE
Pop-Location
if ($buildExit -ne 0) {
    Write-Host "`nERROR: Build failed! See errors above." -ForegroundColor Red
    exit 1
}
Write-Host "  Build successful." -ForegroundColor Green

# --- Step 4: Find APK and Install ---
Write-Host "[4/5] Locating APK..." -ForegroundColor Green
$apkDir = Join-Path $projectRoot "app\build\outputs\apk"
$apkFile = Get-ChildItem -Path $apkDir -Filter "*debug*.apk" -Recurse -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apkFile) {
    Write-Host "`nERROR: No debug APK found under $apkDir" -ForegroundColor Red
    exit 1
}
$apk = $apkFile.FullName
Write-Host "  Found APK: $apk" -ForegroundColor Green

# Re-verify device is still connected before installing
Write-Host "[4/5] Verifying device connection..." -ForegroundColor Green
$devicesCheck = & $adb devices 2>&1 | Where-Object { $_ -match "$targetDevice.*device$" }
if (-not $devicesCheck) {
    Write-Host "  Device lost connection. Restarting ADB server..." -ForegroundColor Yellow
    & $adb kill-server | Out-Null
    Start-Sleep -Milliseconds 500
    & $adb start-server | Out-Null
    Start-Sleep -Milliseconds 1000
}

Write-Host "  Installing APK..." -ForegroundColor Green
& $adb @deviceArg install -r $apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nERROR: Install failed!" -ForegroundColor Red
    exit 1
}
Write-Host "  Install successful." -ForegroundColor Green

# --- Step 5: Launch ---
Write-Host "[5/5] Launching app..." -ForegroundColor Green
$component = "$packageName/$mainActivity"
& $adb @deviceArg shell am start -n $component
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Warning: Could not auto-launch. Open the app from your device." -ForegroundColor Yellow
} else {
    Write-Host "  App launched!" -ForegroundColor Green
}

Write-Host "`n=== Done! Check your phone. ===" -ForegroundColor Cyan
