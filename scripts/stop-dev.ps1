[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$stateDirectory = Join-Path $repositoryRoot '.local/dev'

function Stop-ProcessTree {
  param([int]$RootProcessId)

  $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $RootProcessId" -ErrorAction SilentlyContinue)
  foreach ($child in $children) {
    Stop-ProcessTree -RootProcessId ([int]$child.ProcessId)
  }
  Stop-Process -Id $RootProcessId -Force -ErrorAction SilentlyContinue
}

function Stop-RecordedProcess {
  param([string]$Name, [string]$ExpectedCommandFragment)

  $pidFile = Join-Path $stateDirectory "$Name.pid"
  if (-not (Test-Path $pidFile)) { return }

  $processId = [int](Get-Content $pidFile -Raw)
  $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
  $commandLine = if ($null -eq $process.CommandLine) { '' } else { [string]$process.CommandLine }
  $isOwnedLauncher = $null -ne $process `
    -and $commandLine.IndexOf($repositoryRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0 `
    -and $commandLine.IndexOf($ExpectedCommandFragment, [StringComparison]::OrdinalIgnoreCase) -ge 0
  if ($isOwnedLauncher) {
    Stop-ProcessTree -RootProcessId $processId
    Write-Host "Stopped $Name (PID $processId)."
  }
  Remove-Item -LiteralPath $pidFile -Force
}

Stop-RecordedProcess 'web' 'web:dev'
Stop-RecordedProcess 'api' 'spring-boot:run'
Set-Location $repositoryRoot
& pnpm infra:down
