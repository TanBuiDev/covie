[CmdletBinding()]
param([string]$SessionId)

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

function Stop-ManagedDevSession {
  param([string]$ExpectedSessionId)
  $sessionPath = Join-Path $stateDirectory 'session.json'
  if (-not (Test-Path -LiteralPath $sessionPath)) { return }
  $session = Get-Content -LiteralPath $sessionPath -Raw | ConvertFrom-Json
  if ($ExpectedSessionId -and $session.id -ne $ExpectedSessionId) { return }
  foreach ($name in @('web', 'api')) {
    $record = $session.$name
    if (-not $record) { continue }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($record.pid)" -ErrorAction SilentlyContinue
    $started = Get-Process -Id $record.pid -ErrorAction SilentlyContinue
    $expectedFragment = if ($name -eq 'web') { 'web:dev' } else { 'spring-boot:run' }
    if ($process -and $started -and $started.StartTime.ToUniversalTime().ToString('o') -eq $record.startedAt `
      -and $process.CommandLine.IndexOf($repositoryRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0 `
      -and $process.CommandLine.IndexOf($expectedFragment, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
      Stop-ProcessTree -RootProcessId $record.pid
      Write-Host "Stopped $name."
    }
    Remove-Item -LiteralPath (Join-Path $stateDirectory "$name.pid") -Force -ErrorAction SilentlyContinue
  }
  Push-Location $repositoryRoot
  try {
    & docker compose -f infra/compose/compose.local.yml -f infra/compose/compose.local.runtime.yml -f infra/compose/compose.local.restore.yml stop
    if ($LASTEXITCODE -ne 0) { throw 'Docker shutdown failed. Retry pnpm dev:stop.' }
  } finally { Pop-Location }
  Remove-Item -LiteralPath $sessionPath -Force
  Write-Host 'CoVie dev session stopped. Docker volumes and data retained.'
}

# Serialize cleanup from Ctrl+C, the watchdog and a separate dev:stop terminal.
$cleanupMutex = New-Object Threading.Mutex($false, 'Local\CoVieDevCleanup')
$locked = $false
try {
  try { $locked = $cleanupMutex.WaitOne(60000) } catch [Threading.AbandonedMutexException] { $locked = $true }
  if (-not $locked) { throw 'Another CoVie shutdown is still running.' }
  if (Test-Path -LiteralPath (Join-Path $stateDirectory 'session.json')) {
    Stop-ManagedDevSession -ExpectedSessionId $SessionId
  } elseif (-not $SessionId) {
    # Compatibility for the previous detached launcher, with command ownership checks.
    Stop-RecordedProcess 'web' 'web:dev'
    Stop-RecordedProcess 'api' 'spring-boot:run'
    Push-Location $repositoryRoot
    try {
      & docker compose -f infra/compose/compose.local.yml -f infra/compose/compose.local.runtime.yml -f infra/compose/compose.local.restore.yml stop
      if ($LASTEXITCODE -ne 0) { throw 'Docker shutdown failed.' }
    } finally { Pop-Location }
  }
} finally {
  if ($locked) { $cleanupMutex.ReleaseMutex() }
  $cleanupMutex.Dispose()
}
