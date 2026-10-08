$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$testRoot = Join-Path $projectRoot ('.local/dev-lifecycle-tests/' + [guid]::NewGuid().ToString())
$stateDirectory = Join-Path $testRoot '.local/dev'
$repositoryRoot = $testRoot
New-Item -ItemType Directory -Force $stateDirectory | Out-Null
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $projectRoot 'scripts/stop-dev.ps1'), [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw $errors[0] }
$function = $ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Stop-ManagedDevSession'}, $true)
. ([scriptblock]::Create($function.Extent.Text))
$script:stopped = @(); $script:dockerCommands = @(); $script:dockerExit = 0
$timestamp = [datetime]'2026-10-08T00:00:00Z'
function Get-CimInstance { param($ClassName, $Filter, $ErrorAction) return [pscustomobject]@{CommandLine="$testRoot spring-boot:run web:dev"} }
function Get-Process { param($Id, $ErrorAction) return [pscustomobject]@{StartTime=$timestamp} }
function Stop-ProcessTree { param($RootProcessId) $script:stopped += $RootProcessId }
function docker { $script:dockerCommands += ($args -join ' '); $global:LASTEXITCODE = $script:dockerExit }
function Write-Fixture($apiStartedAt = $timestamp.ToUniversalTime().ToString('o')) {
  @{id='fixture';api=@{pid=123;startedAt=$apiStartedAt};web=@{pid=456;startedAt=$timestamp.ToUniversalTime().ToString('o')}} |
    ConvertTo-Json -Depth 4 | Set-Content (Join-Path $stateDirectory 'session.json')
}
try {
  Write-Fixture
  Stop-ManagedDevSession 'different-session'
  if ($script:stopped.Count -or $script:dockerCommands.Count) { throw 'Stale session must not touch the current session.' }
  Stop-ManagedDevSession 'fixture'
  if (($script:stopped -join ',') -ne '456,123') { throw 'Expected web and API shutdown.' }
  if ($script:dockerCommands.Count -ne 1 -or $script:dockerCommands[0] -notmatch 'compose.local.restore.yml stop$') { throw 'Expected matching Compose stop.' }
  if ($script:dockerCommands[0] -match 'down|--volumes') { throw 'Cleanup must preserve containers and volumes.' }
  if (Test-Path (Join-Path $stateDirectory 'session.json')) { throw 'Session should be removed after successful cleanup.' }
  Stop-ManagedDevSession 'fixture'
  if ($script:dockerCommands.Count -ne 1) { throw 'Cleanup must be idempotent.' }
  $script:stopped = @()
  Write-Fixture '2000-01-01T00:00:00.0000000Z'
  Stop-ManagedDevSession 'fixture'
  if (($script:stopped -join ',') -ne '456') { throw 'PID reuse must not kill an unrelated API process.' }
  Write-Fixture
  $script:dockerExit = 1
  $failed = $false
  try { Stop-ManagedDevSession 'fixture' } catch { $failed = $true }
  if (-not $failed -or -not (Test-Path (Join-Path $stateDirectory 'session.json'))) { throw 'Docker failure must retain retryable session state.' }
  $script:dockerExit = 0
  # Simulate closing the owner terminal; use a fake cleanup script, never real services.
  $fakeScripts = Join-Path $testRoot 'scripts'
  New-Item -ItemType Directory -Force $fakeScripts | Out-Null
  Copy-Item (Join-Path $projectRoot 'scripts/dev-watchdog.ps1') $fakeScripts
  'param($SessionId); Set-Content (Join-Path $PSScriptRoot "cleanup-called.txt") $SessionId' | Set-Content (Join-Path $fakeScripts 'stop-dev.ps1')
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $fakeScripts 'dev-watchdog.ps1') -OwnerProcessId 2147483647 -OwnerStartedAt '2000-01-01' -SessionId fixture
  if ($LASTEXITCODE -ne 0 -or (Get-Content (Join-Path $fakeScripts 'cleanup-called.txt')) -ne 'fixture') { throw 'Watchdog must clean up after owner disappears.' }
  Remove-Item (Join-Path $fakeScripts 'cleanup-called.txt')
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $fakeScripts 'dev-watchdog.ps1') -OwnerProcessId 2147483647 -OwnerStartedAt '2000-01-01' -SessionId old-session
  if (Test-Path (Join-Path $fakeScripts 'cleanup-called.txt')) { throw 'Old watchdog must not clean up a new session.' }
  Write-Host 'PASS: session isolation, process identity, matching Docker stop, idempotence, failure retry and terminal-close watchdog.'
} finally {
  # Test artifacts are retained under ignored .local; no application data is changed.
}
