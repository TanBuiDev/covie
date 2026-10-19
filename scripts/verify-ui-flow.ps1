[CmdletBinding()]
param(
    [string]$PostgresContainer,
    [switch]$CurrentDesign
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$seedArguments = @()
$previousVisualMode = [Environment]::GetEnvironmentVariable('COVIE_UI_CURRENT_DESIGN')
if (-not [string]::IsNullOrWhiteSpace($PostgresContainer)) {
    $seedArguments = @('-PostgresContainer', $PostgresContainer)
}

Push-Location $repositoryRoot
try {
    if ($CurrentDesign) { $env:COVIE_UI_CURRENT_DESIGN = '1' }
    else { Remove-Item Env:COVIE_UI_CURRENT_DESIGN -ErrorAction SilentlyContinue }
    & pnpm ui:seed-local @seedArguments
    if ($LASTEXITCODE -ne 0) { throw "Could not seed the local UI acceptance fixture." }

    $env:COVIE_UI_FIXTURE = "1"
    & pnpm --filter @covie/web exec playwright test e2e/populated-buyer-flow.local.spec.ts --project=chromium --workers=1
    if ($LASTEXITCODE -ne 0) { throw "Populated Public Buyer flow verification failed." }
    & pnpm ui:seed-local @seedArguments
    if ($LASTEXITCODE -ne 0) { throw "Could not restore the UI acceptance fixture after the checkout journey." }
    & pnpm --filter @covie/web exec playwright test e2e/buyer-flow.local.spec.ts --project=chromium --workers=1
    if ($LASTEXITCODE -ne 0) { throw "Buyer visual acceptance verification failed." }
    & pnpm --filter @covie/web exec playwright test e2e/public-merchant-flow.local.spec.ts --project=chromium --workers=1
    if ($LASTEXITCODE -ne 0) { throw "Public merchant visual acceptance verification failed." }
    & pnpm --filter @covie/web exec playwright test e2e/seller-flow.local.spec.ts --project=chromium --workers=1
    if ($LASTEXITCODE -ne 0) { throw "Seller flow verification failed." }
    & pnpm --filter @covie/web exec playwright test e2e/admin-flow.local.spec.ts --project=chromium --workers=1
    if ($LASTEXITCODE -ne 0) { throw "Admin flow verification failed." }
    & pnpm --filter @covie/web exec playwright test e2e/visual-manifest.local.spec.ts --project=chromium --workers=1
    if ($LASTEXITCODE -ne 0) { throw "Visual evidence manifest verification failed." }
} finally {
    if ($null -eq $previousVisualMode) { Remove-Item Env:COVIE_UI_CURRENT_DESIGN -ErrorAction SilentlyContinue }
    else { $env:COVIE_UI_CURRENT_DESIGN = $previousVisualMode }
    Remove-Item Env:COVIE_UI_FIXTURE -ErrorAction SilentlyContinue
    Pop-Location
}
