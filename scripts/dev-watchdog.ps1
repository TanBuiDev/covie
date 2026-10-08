[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][int]$OwnerProcessId,
  [Parameter(Mandatory = $true)][string]$OwnerStartedAt,
  [Parameter(Mandatory = $true)][string]$SessionId
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$sessionPath = Join-Path $repositoryRoot '.local/dev/session.json'
while (Test-Path -LiteralPath $sessionPath) {
  $session = Get-Content -LiteralPath $sessionPath -Raw | ConvertFrom-Json
  if ($session.id -ne $SessionId) { break }
  $owner = Get-Process -Id $OwnerProcessId -ErrorAction SilentlyContinue
  if (-not $owner -or $owner.StartTime.ToUniversalTime().ToString('o') -ne $OwnerStartedAt) {
    & (Join-Path $PSScriptRoot 'stop-dev.ps1') -SessionId $SessionId
    break
  }
  Start-Sleep -Seconds 2
}
