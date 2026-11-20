$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repoRoot 'target\password-guruji-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw 'Build the application first: run mvn package from the repository root.'
}
$workingFolder = Join-Path $PSScriptRoot 'working'
New-Item -ItemType Directory -Path $workingFolder -Force | Out-Null
$workingVault = Join-Path $workingFolder 'sample-vault.pgv'
if (-not (Test-Path -LiteralPath $workingVault)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'sample-vault.pgv') -Destination $workingVault
}
Write-Host 'Sample master password: 123456 (fictional data only)'
& java '-jar' $jar '--sample-vault' $workingVault
