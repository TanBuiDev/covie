$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptPath = Join-Path $root 'scripts/dev.ps1'
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Development launcher does not parse' }
$isolation = $ast.Find({ param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Start-IsolatedWebProcess'
}, $true)
if ($isolation) { . ([scriptblock]::Create($isolation.Extent.Text)) }
$local = Join-Path $root '.local'
New-Item -ItemType Directory -Path $local -Force | Out-Null
$fixture = Join-Path $local ('web-env-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture | Out-Null
$names = @('PAYOS_CLIENT_ID', 'PAYOS_API_KEY', 'PAYOS_CHECKSUM_KEY', 'PAYOS_PAYMENT_ENABLED', 'COVIE_BILLING_RELEASE_APPROVED', 'COVIE_WEB_ENV_TEST_CONTROL')
$original = @{}
foreach ($name in $names) { $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, 'disposable-test-only', 'Process') }
    $childScript = Join-Path $fixture 'child.ps1'
    $outputPath = Join-Path $fixture 'result.json'
    @'
param([string]$OutputPath)
$result = @{
    payosPresent = @(Get-ChildItem Env: | Where-Object Name -Like 'PAYOS_*').Count -gt 0
    releaseFlagPresent = $null -ne [Environment]::GetEnvironmentVariable('COVIE_BILLING_RELEASE_APPROVED', 'Process')
    controlPresent = [Environment]::GetEnvironmentVariable('COVIE_WEB_ENV_TEST_CONTROL', 'Process') -eq 'disposable-test-only'
}
$result | ConvertTo-Json | Set-Content -LiteralPath $OutputPath
'@ | Set-Content -LiteralPath $childScript
    $start = {
        Start-Process -FilePath powershell.exe -ArgumentList @('-NoProfile', '-File', "`"$childScript`"", '-OutputPath', "`"$outputPath`"") -WindowStyle Hidden -Wait -PassThru
    }
    $child = if ($isolation) { Start-IsolatedWebProcess $start } else { & $start }
    if ($child.ExitCode -ne 0) { throw 'Environment fixture process failed' }
    $result = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
    if ($result.payosPresent -or $result.releaseFlagPresent) { throw 'API-only payOS environment leaked to the Web child process' }
    if (-not $result.controlPresent) { throw 'Unrelated Web environment was not preserved' }
    foreach ($name in $names) {
        if ([Environment]::GetEnvironmentVariable($name, 'Process') -ne 'disposable-test-only') { throw 'Parent environment was not restored' }
    }
    $failed = $false
    try { Start-IsolatedWebProcess { throw 'Expected child startup failure' } } catch { $failed = $true }
    if (-not $failed) { throw 'Child startup failure was swallowed' }
    foreach ($name in $names) {
        if ([Environment]::GetEnvironmentVariable($name, 'Process') -ne 'disposable-test-only') { throw 'Parent environment lost after startup failure' }
    }
    $web = $ast.Find({ param($node)
        $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and $node.Left.Extent.Text -eq '$web'
    }, $true)
    if ($null -eq $web -or $web.Right.Extent.Text -notmatch 'Start-IsolatedWebProcess') { throw 'Actual Web launch bypasses environment isolation' }
    Write-Output 'PASS: actual child receives no payOS/release variables; parent/unrelated environment survives success and startup failure'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process') }
    $resolved = [IO.Path]::GetFullPath($fixture)
    $prefix = [IO.Path]::GetFullPath($local) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Refusing fixture cleanup outside local evidence directory' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
