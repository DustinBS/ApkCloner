<#
Repack an APK using apktool + zipalign + apksigner (Windows PowerShell)

Prereqs (install these on your PATH):
- apktool (https://ibotpeaches.github.io/Apktool/)
- zipalign (from Android SDK build-tools)
- apksigner (from Android SDK build-tools)

Usage:
.
  .\repack.ps1 -Input myapp.apk -Out cloned.apk -NewPackage com.example.myapp.clone -Label "My App (Clone)" -Keystore mykeystore.jks -KeyAlias myalias -StorePass password -KeyPass password

Note: Only use this on APKs you own or have permission to modify. This script does NOT bypass signature checks or DRM.
#>

param(
    [Parameter(Mandatory=$true)][string]$Input,
    [Parameter(Mandatory=$true)][string]$Out,
    [string]$NewPackage,
    [string]$Label,
    [string]$Keystore,
    [string]$KeyAlias,
    [string]$StorePass,
    [string]$KeyPass
)

function Check-Tool($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        Write-Error "$name not found on PATH. Install it and try again."
        exit 2
    }
}

# verify tools
Check-Tool apktool
Check-Tool zipalign
Check-Tool apksigner

if (-not (Test-Path $Input)) {
    Write-Error "Input APK not found: $Input"
    exit 2
}

$work = Join-Path $PSScriptRoot ("work_{0}" -f ([guid]::NewGuid().ToString()))
New-Item -ItemType Directory -Path $work | Out-Null

Write-Host "Decoding APK with apktool..."
apktool d -f "$Input" -o "$work" | Out-Null

# Read original package from manifest (if available)
$manifestPath = Join-Path $work "AndroidManifest.xml"
$oldPackage = $null
if (Test-Path $manifestPath) {
    $manifestText = Get-Content $manifestPath -Raw
    $m = [regex]::Match($manifestText, 'package="([^"]+)"')
    if ($m.Success) { $oldPackage = $m.Groups[1].Value }
}

if (-not $NewPackage -and $oldPackage) {
    # default new package: append .clone + random suffix
    $NewPackage = "$($oldPackage).clone$(Get-Random -Minimum 1000 -Maximum 9999)"
    Write-Host "No NewPackage provided; using generated package: $NewPackage"
}

# If we have an old package and the caller wants a new package, attempt smali-level rename
if ($oldPackage -and $NewPackage -and $oldPackage -ne $NewPackage) {
    Write-Host "Renaming package from $oldPackage to $NewPackage in smali files and directories..."

    $oldSlashed = $oldPackage -replace '\.', '/'
    $newSlashed = $NewPackage -replace '\.', '/'

    # Replace references inside .smali files
    $smaliFiles = Get-ChildItem -Path $work -Recurse -Filter *.smali -File -ErrorAction SilentlyContinue
    if ($smaliFiles -and $smaliFiles.Count -gt 0) {
        foreach ($f in $smaliFiles) {
            try {
                $text = Get-Content $f.FullName -Raw
                $newText = $text -replace [regex]::Escape("L$oldSlashed/"), "L$newSlashed/"
                $newText = $newText -replace [regex]::Escape("$oldSlashed/"), "$newSlashed/"
                if ($newText -ne $text) { Set-Content -Path $f.FullName -Value $newText -Encoding UTF8 }
            } catch {
                Write-Warning "Failed to process smali file $($f.FullName): $_"
            }
        }
    } else {
        Write-Warning "No smali files found — smali rename skipped"
    }

    # Rename package directories (deepest first to avoid nested rename conflicts)
    $oldPathPart = $oldPackage -replace '\.', [IO.Path]::DirectorySeparatorChar
    $newPathPart = $NewPackage -replace '\.', [IO.Path]::DirectorySeparatorChar
    $dirs = Get-ChildItem -Path $work -Directory -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -like "*${oldPathPart}" }
    if ($dirs) {
        $sorted = $dirs | Sort-Object { -$_.FullName.Length }
        foreach ($d in $sorted) {
            try {
                $dest = $d.FullName -replace [regex]::Escape($oldPathPart), $newPathPart
                $destParent = Split-Path -Parent $dest
                if (-not (Test-Path $destParent)) { New-Item -ItemType Directory -Path $destParent -Force | Out-Null }
                Move-Item -Path $d.FullName -Destination $dest -Force
            } catch {
                Write-Warning "Failed to move directory $($d.FullName) -> $dest: $_"
            }
        }
    }

    # Update manifest package attribute to new package
    if (Test-Path $manifestPath) {
        try {
            (Get-Content $manifestPath) -replace 'package="[^\"]+"', "package=\"$NewPackage\"" | Set-Content $manifestPath -Encoding UTF8
            Write-Host "Updated manifest package to $NewPackage"
        } catch {
            Write-Warning "Failed to update manifest package: $_"
        }
    }
}

if ($Label) {
    $stringsPath = Join-Path $work "res\values\strings.xml"
    if (Test-Path $stringsPath) {
        try {
            [xml]$sx = Get-Content $stringsPath -Raw
            $node = $sx.resources.string | Where-Object { $_.name -eq 'app_name' }
            if ($node) {
                $node.'#text' = $Label
                $sx.Save($stringsPath)
                Write-Host "Updated label to: $Label"
            } else {
                Write-Warning "app_name string not found; skipping label update"
            }
        } catch {
            Write-Warning "Failed to parse strings.xml — skipping label update: $_"
        }
    } else {
        Write-Warning "strings.xml not found — skipping label update"
    }
}

Write-Host "Rebuilding APK with apktool..."
apktool b "$work" -o "$work\unsigned.apk" | Out-Null

$unsigned = Join-Path $work "unsigned.apk"
if (-not (Test-Path $unsigned)) {
    Write-Error "Build failed: unsigned APK not found"
    Remove-Item -Recurse -Force $work
    exit 3
}

Write-Host "Aligning APK..."
zipalign -v -p 4 "$unsigned" "$Out" | Out-Null

if ($Keystore) {
    Write-Host "Signing APK with apksigner..."
    $ksPassArg = if ($StorePass) { "--ks-pass pass:$StorePass" } else { "" }
    $keyPassArg = if ($KeyPass) { "--key-pass pass:$KeyPass" } else { "" }
    $aliasArg = if ($KeyAlias) { "--ks-key-alias $KeyAlias" } else { "" }
    apksigner sign --ks "$Keystore" $ksPassArg $aliasArg $keyPassArg "--out" "$Out" "$Out"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "apksigner failed"
        Remove-Item -Recurse -Force $work
        exit 4
    }
    Write-Host "Signed: $Out"
} else {
    Write-Warning "Keystore not provided; output APK is not signed. Installation may fail."
}

Write-Host "Done. Output: $Out"
Remove-Item -Recurse -Force $work
exit 0
