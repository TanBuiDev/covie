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

$global:CovieUiVerifyRecordedCommands = [System.Collections.Generic.List[string]]::new()
$global:CovieUiVerifyRecordedModes = [System.Collections.Generic.List[string]]::new()
$previousMode = [Environment]::GetEnvironmentVariable('COVIE_UI_CURRENT_DESIGN')
function global:pnpm {
  $global:CovieUiVerifyRecordedCommands.Add(($args -join ' '))
  $global:CovieUiVerifyRecordedModes.Add([Environment]::GetEnvironmentVariable('COVIE_UI_CURRENT_DESIGN'))
  $global:LASTEXITCODE = 0
}
try {
  & $scriptPath -PostgresContainer 'billing-owned-ui-fixture'
  $seedCalls = @($global:CovieUiVerifyRecordedCommands | Where-Object { $_ -like 'ui:seed-local*' })
  if ($seedCalls.Count -ne 2 -or @($seedCalls | Where-Object { $_ -ne 'ui:seed-local -PostgresContainer billing-owned-ui-fixture' }).Count -ne 0) {
    throw 'Both seed invocations must receive the explicit owned PostgreSQL container.'
  }
  $global:CovieUiVerifyRecordedCommands.Clear()
  & $scriptPath
  $seedCalls = @($global:CovieUiVerifyRecordedCommands | Where-Object { $_ -like 'ui:seed-local*' })
  if ($seedCalls.Count -ne 2 -or @($seedCalls | Where-Object { $_ -ne 'ui:seed-local' }).Count -ne 0) {
    throw 'Default UI orchestration must preserve the seed script default without inventing a target.'
  }
  $env:COVIE_UI_CURRENT_DESIGN = 'previous-value'
  $global:CovieUiVerifyRecordedModes.Clear()
  & $scriptPath -CurrentDesign
  if (@($global:CovieUiVerifyRecordedModes | Where-Object { $_ -ne '1' }).Count -ne 0) {
    throw 'Explicit current-design mode must propagate to every child command.'
  }
  if ($env:COVIE_UI_CURRENT_DESIGN -ne 'previous-value') { throw 'Current-design mode must restore the parent environment.' }
  $global:CovieUiVerifyRecordedModes.Clear()
  & $scriptPath
  if (@($global:CovieUiVerifyRecordedModes | Where-Object { -not [string]::IsNullOrEmpty($_) }).Count -ne 0) {
    throw 'Default verification must retain strict Figma acceptance regardless of inherited mode.'
  }
  if ($env:COVIE_UI_CURRENT_DESIGN -ne 'previous-value') { throw 'Strict mode must restore the parent environment.' }
} finally {
  if ($null -eq $previousMode) { Remove-Item Env:COVIE_UI_CURRENT_DESIGN -ErrorAction SilentlyContinue }
  else { $env:COVIE_UI_CURRENT_DESIGN = $previousMode }
  Remove-Item Function:\pnpm
  Remove-Variable CovieUiVerifyRecordedCommands -Scope Global
  Remove-Variable CovieUiVerifyRecordedModes -Scope Global
}

Write-Output 'verify-ui-flow orchestration and owned-container propagation checks passed'
