[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repositoryRoot
try {
    & pnpm ui:seed-local
    if ($LASTEXITCODE -ne 0) { throw "Could not seed the local UI acceptance fixture." }

    $env:COVIE_UI_FIXTURE = "1"
    & pnpm --filter @covie/web exec playwright test e2e/populated-buyer-flow.local.spec.ts --project=chromium
    if ($LASTEXITCODE -ne 0) { throw "Populated Public Buyer flow verification failed." }
} finally {
    Remove-Item Env:COVIE_UI_FIXTURE -ErrorAction SilentlyContinue
    Pop-Location
}
