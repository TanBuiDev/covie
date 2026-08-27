[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$stateDirectory = Join-Path $repositoryRoot '.local/dev'

function Stop-RecordedProcess {
  param([string]$Name, [string]$ExpectedProcessName)

  $pidFile = Join-Path $stateDirectory "$Name.pid"
  if (-not (Test-Path $pidFile)) { return }

  $processId = [int](Get-Content $pidFile -Raw)
  $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
  if ($null -ne $process -and $process.ProcessName -like $ExpectedProcessName) {
    Stop-Process -Id $processId
    Write-Host "Stopped $Name (PID $processId)."
  }
  Remove-Item -LiteralPath $pidFile -Force
}

Stop-RecordedProcess 'web' 'node*'
Stop-RecordedProcess 'api' 'java*'
Set-Location $repositoryRoot
& pnpm infra:down
