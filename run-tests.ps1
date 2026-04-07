#!/usr/bin/env pwsh
# Run unit tests for the Android app module. Tries local Gradle wrapper then system Gradle.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Push-Location $scriptDir

function Find-AndroidSdk {
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk",
        "$env:ProgramFiles\Android\Android SDK",
        "$env:ProgramFiles(x86)\Android\android-sdk",
        'C:\Android\Sdk'
    )
    foreach ($p in $candidates) {
        if ([string]::IsNullOrEmpty($p)) { continue }
        if (Test-Path $p) { return $p }
    }
    return $null
}

$createdLocal = $false
try {
    # create local.properties from environment if not present
    if (-not (Test-Path .\local.properties)) {
        $sdk = Find-AndroidSdk
        if ($sdk) {
            Write-Host "Creating temporary local.properties pointing to SDK: $sdk"
            "sdk.dir=$($sdk -replace '\\','/')" | Out-File -FilePath .\local.properties -Encoding ASCII
            $createdLocal = $true
        }
    }

    if (Test-Path .\gradlew.bat) {
        Write-Host "Running .\gradlew.bat test..."
        & .\gradlew.bat test
        exit $LASTEXITCODE
    } elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
        Write-Host "Running 'gradle test'..."
        gradle test --no-daemon
        exit $LASTEXITCODE
    } else {
        Write-Host "Gradle wrapper not found and 'gradle' is not installed on PATH."
        Write-Host "Options to run tests locally:"
        Write-Host " 1) Open 'android-app' in Android Studio and run the unit tests."
        Write-Host " 2) If you have Gradle installed, generate the wrapper: 'gradle wrapper --gradle-version 8.2' and re-run this script."
        Write-Host " 3) Ask me to add a Gradle wrapper to the repo (I can add wrapper files if you approve)."
        exit 1
    }
} finally {
    if ($createdLocal -and (Test-Path .\local.properties)) { Remove-Item .\local.properties -Force }
    Pop-Location
}
