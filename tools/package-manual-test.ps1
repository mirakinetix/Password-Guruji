[CmdletBinding()]
param(
    [string]$BuildId = (Get-Date -Format 'yyyyMMdd')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $repoRoot 'target'
$version = '0.1.0'
$releaseName = "Password-Guruji-Manual-Test-$version-$BuildId"
$releaseDir = Join-Path $targetDir $releaseName
$zipPath = Join-Path $targetDir "$releaseName.zip"
$checksumPath = "$zipPath.sha256"
$inputDir = Join-Path $targetDir "package-input-$BuildId"

foreach ($path in @($releaseDir, $zipPath, $checksumPath, $inputDir)) {
    if (Test-Path -LiteralPath $path) {
        throw "Output already exists; choose another BuildId: $path"
    }
}

$maven = Get-Command mvn -ErrorAction Stop
$java = Get-Command java -ErrorAction Stop
$jpackagePath = Join-Path (Split-Path $java.Source -Parent) 'jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackagePath)) {
    throw "jpackage.exe was not found beside Java. Install a full JDK to create the portable package."
}

$pomPath = Join-Path $repoRoot 'pom.xml'
& $maven.Source '-f' $pomPath '-DskipTests' 'package'
if ($LASTEXITCODE -ne 0) {
    throw "Maven packaging failed with exit code $LASTEXITCODE."
}

$jarPath = Join-Path $targetDir "password-guruji-$version.jar"
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Expected application JAR was not created: $jarPath"
}

New-Item -ItemType Directory -Path $inputDir | Out-Null
Copy-Item -LiteralPath $jarPath -Destination $inputDir

$iconPath = Join-Path $repoRoot 'documentation\brand-assets\password-guruji\password-guruji.ico'
$jpackageArgs = @(
    '--type', 'app-image',
    '--name', 'Password Guruji',
    '--app-version', $version,
    '--vendor', 'Mirakinetix',
    '--description', 'Password Guruji manual-testing build',
    '--input', $inputDir,
    '--main-jar', (Split-Path $jarPath -Leaf),
    '--main-class', 'com.mirakinetix.passwordguruji.PasswordGurujiApp',
    '--icon', $iconPath,
    '--dest', $releaseDir
)
& $jpackagePath @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

$appFolder = Join-Path $releaseDir 'Password Guruji'
$launcherPath = Join-Path $appFolder 'Password Guruji.exe'
if (-not (Test-Path -LiteralPath $launcherPath)) {
    throw "Expected app launcher was not created: $launcherPath"
}

$docs = @(
    'documentation\USER-MANUAL.md',
    'documentation\INSTALLATION-GUIDE.md',
    'documentation\MANUAL-TEST-GUIDE.md',
    'documentation\SAMPLE-VAULT.md',
    'documentation\THIRD-PARTY-NOTICES.md'
)
foreach ($relativePath in $docs) {
    $sourcePath = Join-Path $repoRoot $relativePath
    if (-not (Test-Path -LiteralPath $sourcePath)) {
        throw "Required package document is missing: $sourcePath"
    }
    Copy-Item -LiteralPath $sourcePath -Destination $releaseDir
}

$sampleTools = Join-Path $targetDir "sample-tools-$BuildId"
New-Item -ItemType Directory -Path $sampleTools | Out-Null
$javacPath = Join-Path (Split-Path $java.Source -Parent) 'javac.exe'
& $javacPath '--release' '17' '-encoding' 'UTF-8' '-cp' $jarPath '-d' $sampleTools (Join-Path $repoRoot 'tools\CreateSampleVault.java')
if ($LASTEXITCODE -ne 0) { throw 'Sample vault tool compilation failed.' }
$samplePath = Join-Path $releaseDir 'Sample\sample-vault.pgv'
& $java.Source '-cp' "$sampleTools;$jarPath" 'com.mirakinetix.passwordguruji.CreateSampleVault' $samplePath
if ($LASTEXITCODE -ne 0) { throw 'Sample vault generation or verification failed.' }
$sampleLauncher = '@echo off' + [Environment]::NewLine + 'start "" "%~dp0Password Guruji\Password Guruji.exe" --sample-vault "%~dp0Sample\sample-vault.pgv"' + [Environment]::NewLine
[System.IO.File]::WriteAllText((Join-Path $releaseDir 'Start Sample Vault.cmd'), $sampleLauncher, [System.Text.Encoding]::ASCII)

$startText = @'
Password Guruji 0.1.0 - Manual Testing Build
Build ID: __BUILD_ID__

This portable Windows package includes its own Java runtime.
Use synthetic test data only. Do not enter live account credentials,
identity/payment data, recovery codes, or live authenticator secrets.

1. Keep this extracted folder together.
2. Read INSTALLATION-GUIDE.md and MANUAL-TEST-GUIDE.md.
3. Open Password Guruji\Password Guruji.exe to launch the app.
4. Read USER-MANUAL.md for feature instructions.
5. For fictional examples, open Start Sample Vault.cmd. Master password: 123456.
   Read SAMPLE-VAULT.md for sample records, settings, and reset instructions.

The app works offline. Footer links open a browser only when clicked.
This is an unsigned manual-testing build, not a production-certified release.
'@ -replace '__BUILD_ID__', $BuildId
[System.IO.File]::WriteAllText(
    (Join-Path $releaseDir 'START-HERE.txt'),
    $startText,
    [System.Text.UTF8Encoding]::new($false)
)

Compress-Archive -Path (Join-Path $releaseDir '*') -DestinationPath $zipPath -CompressionLevel Optimal
$hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
$checksumText = "$hash  $(Split-Path $zipPath -Leaf)" + [Environment]::NewLine
[System.IO.File]::WriteAllText($checksumPath, $checksumText, [System.Text.UTF8Encoding]::new($false))

Write-Host "Package: $zipPath"
Write-Host "Checksum: $checksumPath"
Write-Host "SHA-256: $hash"
