$paths = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'),
    (Join-Path $env:ProgramFiles 'Android\Android SDK'),
    (Join-Path ${env:ProgramFiles(x86)} 'Android\android-sdk'),
    'C:\Android\sdk'
)
foreach ($p in $paths) {
    if ([string]::IsNullOrEmpty($p)) { continue }
    if (Test-Path $p) { Write-Host ('FOUND:' + $p); exit 0 }
}
Write-Host 'NOTFOUND'
exit 1
