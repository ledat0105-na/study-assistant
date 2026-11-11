param([switch]$Browser, [switch]$Integration, [switch]$E2E, [switch]$ResetQaDatabase)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat test --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed. See build/reports/tests/test/index.html' }
    $jsTests = @(Get-ChildItem -LiteralPath 'src/test/js' -Filter '*.test.cjs' | ForEach-Object { $_.FullName })
    & node --test @jsTests
    if ($LASTEXITCODE -ne 0) { throw 'JavaScript tests failed.' }
    if ($Integration) {
        $integrationArgs = @('mysqlIntegrationTest', '--console=plain')
        if ($ResetQaDatabase) { $integrationArgs += '-PresetQaDatabase=true' }
        & .\gradlew.bat @integrationArgs
        if ($LASTEXITCODE -ne 0) { throw 'Integration tests failed. See build/reports/tests/mysqlIntegrationTest/index.html' }
    }
    if ($E2E) {
        & .\gradlew.bat e2eTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'MySQL/Qdrant E2E tests failed. See build/reports/tests/e2eTest/index.html' }
    }
    if ($Browser) {
        & node 'src/test/js/browser-smoke.cjs'
        if ($LASTEXITCODE -ne 0) { throw 'Browser smoke checks failed.' }
    }
    Write-Host 'All selected checks passed.'
} finally {
    Pop-Location
}
