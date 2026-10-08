[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repositoryRoot
try {
    & pnpm ui:seed-local
    if ($LASTEXITCODE -ne 0) { throw "Could not seed the local UI acceptance fixture." }

    $env:COVIE_UI_FIXTURE = "1"
    & pnpm --filter @covie/web exec playwright test e2e/populated-buyer-flow.local.spec.ts --project=chromium --workers=1
    if ($LASTEXITCODE -ne 0) { throw "Populated Public Buyer flow verification failed." }
    & pnpm ui:seed-local
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
    Remove-Item Env:COVIE_UI_FIXTURE -ErrorAction SilentlyContinue
    Pop-Location
}
