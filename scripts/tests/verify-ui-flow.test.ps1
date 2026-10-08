[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptPath = Join-Path $repositoryRoot 'scripts/verify-ui-flow.ps1'
$script = Get-Content -LiteralPath $scriptPath -Raw

$requiredSpecs = @(
  'e2e/populated-buyer-flow.local.spec.ts',
  'e2e/buyer-flow.local.spec.ts',
  'e2e/public-merchant-flow.local.spec.ts',
  'e2e/seller-flow.local.spec.ts',
  'e2e/admin-flow.local.spec.ts',
  'e2e/visual-manifest.local.spec.ts'
)

$lastIndex = -1
foreach ($spec in $requiredSpecs) {
  $index = $script.IndexOf($spec, [System.StringComparison]::Ordinal)
  if ($index -lt 0) { throw "Combined UI verification is missing '$spec'." }
  if ($index -le $lastIndex) { throw "Combined UI verification must run '$spec' in the required serial order." }
  $lastIndex = $index
}

$workerCount = ([regex]::Matches($script, [regex]::Escape('--workers=1'))).Count
if ($workerCount -ne $requiredSpecs.Count) {
  throw "Every combined Playwright invocation must use --workers=1; found $workerCount for $($requiredSpecs.Count) specs."
}

$seedMatches = [regex]::Matches($script, [regex]::Escape('& pnpm ui:seed-local'))
if ($seedMatches.Count -ne 2) {
  throw "Combined UI verification must seed before the checkout journey and reseed before visual capture; found $($seedMatches.Count) seed calls."
}
$populatedIndex = $script.IndexOf('e2e/populated-buyer-flow.local.spec.ts', [System.StringComparison]::Ordinal)
$buyerIndex = $script.IndexOf('e2e/buyer-flow.local.spec.ts', [System.StringComparison]::Ordinal)
if ($seedMatches[1].Index -le $populatedIndex -or $seedMatches[1].Index -ge $buyerIndex) {
  throw 'Combined UI verification must isolate checkout mutations by reseeding between the populated journey and Buyer visual capture.'
}

Write-Output 'verify-ui-flow orchestration checks passed'
