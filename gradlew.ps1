param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [String[]]$Args
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$wrapperDir = Join-Path $scriptDir '.gradle-wrapper'
$gradleVersion = '8.2'
$distDir = Join-Path $wrapperDir ("gradle-{0}" -f $gradleVersion)
$gradleExe = Join-Path $distDir 'bin\gradle.bat'

if (-not (Test-Path $gradleExe)) {
    Write-Host "Gradle $gradleVersion not found locally. Downloading..."
    $zipUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    $zipFile = Join-Path $wrapperDir 'gradle.zip'
    New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
    try {
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -UseBasicParsing
        Expand-Archive -Path $zipFile -DestinationPath $wrapperDir -Force
        Remove-Item $zipFile -Force
    } catch {
        Write-Error "Failed to download or extract Gradle: $_"
        exit 2
    }
}

if (-not (Test-Path $gradleExe)) {
    Write-Error "Gradle executable still not found after download."
    exit 3
}

& $gradleExe @Args
exit $LASTEXITCODE
