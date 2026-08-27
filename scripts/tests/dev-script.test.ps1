$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptPath = Join-Path $repositoryRoot 'scripts/dev.ps1'

if (-not (Test-Path $scriptPath)) {
  throw 'Expected scripts/dev.ps1 to provide the single-command local development launcher.'
}

$script = Get-Content $scriptPath -Raw
foreach ($requiredFragment in @('pnpm infra:up', 'SERVER_PORT', '8081', 'API_INTERNAL_BASE_URL', 'Start-Process', 'actuator/health')) {
  if ($script -notmatch [regex]::Escape($requiredFragment)) {
    throw "Expected dev launcher to contain '$requiredFragment'."
  }
}

if ($script -notmatch [regex]::Escape("-WorkingDirectory `$apiDirectory")) {
  throw 'Expected the API launcher to run Maven Wrapper from apps/api so it can resolve .mvn/wrapper.'
}

$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) {
  throw "Expected scripts/dev.ps1 to parse successfully: $($parseErrors[0].Message)"
}

$javaHomeFunction = $ast.Find({
  param($node)
  $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Initialize-JavaHome'
}, $true)
if ($null -eq $javaHomeFunction) {
  throw 'Expected the launcher to define Initialize-JavaHome for machines where java is on PATH but JAVA_HOME is unset.'
}

$originalJavaHome = $env:JAVA_HOME
try {
  Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
  . ([scriptblock]::Create($javaHomeFunction.Extent.Text))
  Initialize-JavaHome
  if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Expected Initialize-JavaHome to resolve a valid JDK from java on PATH.'
  }
} finally {
  if ($null -eq $originalJavaHome) {
    Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
  } else {
    $env:JAVA_HOME = $originalJavaHome
  }
}
