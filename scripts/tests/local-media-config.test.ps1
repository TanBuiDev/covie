$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$composePath = Join-Path $repositoryRoot 'infra/compose/compose.local.yml'
$compose = Get-Content -LiteralPath $composePath -Raw

if ($compose -notmatch [regex]::Escape('mc anonymous set download local/covie-local')) {
  throw 'Expected the local MinIO bucket to allow public asset downloads for API media URLs.'
}
