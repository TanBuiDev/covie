[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$apiDirectory = Join-Path $repositoryRoot 'apps/api'
$stateDirectory = Join-Path $repositoryRoot '.local/dev'
$environmentFile = Join-Path $repositoryRoot '.env.local'
$privateKey = Join-Path $repositoryRoot 'infra/secrets/dev/jwt-private.pem'
$publicKey = Join-Path $repositoryRoot 'infra/secrets/dev/jwt-public.pem'

function Import-LocalEnvironment {
  param([string]$Path)

  if (-not (Test-Path $Path)) {
    throw "Missing .env.local. Create it once with: Copy-Item .env.example .env.local"
  }

  Get-Content $Path |
    Where-Object { $_ -match '^\s*[^#\s][^=]*=' } |
    ForEach-Object {
      $name, $value = $_ -split '=', 2
      Set-Item -Path "Env:$name" -Value $value
    }
}

function Test-HttpOk {
  param([string]$Url)

  try {
    return (Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2).StatusCode -eq 200
  } catch {
    return $false
  }
}

function Test-PortInUse {
  param([int]$Port)

  return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Wait-ForHealthyService {
  param([string]$Name, [string]$Url, [int]$Port)

  foreach ($attempt in 1..45) {
    if (Test-HttpOk $Url) { return }
    Start-Sleep -Seconds 1
  }

  throw "$Name did not become healthy at $Url. Check logs in $stateDirectory."
}

function Initialize-JavaHome {
  if ($env:JAVA_HOME) { return }

  $javaCommand = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($null -eq $javaCommand) {
    if (Test-Path 'E:\Tools\Java\jdk-25\bin\java.exe') {
      $env:JAVA_HOME = 'E:\Tools\Java\jdk-25'
      return
    }
    throw 'Java is required to start the CoVie API. Install JDK 25 or set JAVA_HOME.'
  }

  $candidate = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
  if (-not (Test-Path (Join-Path $candidate 'bin/java.exe'))) {
    throw "Could not infer JAVA_HOME from '$($javaCommand.Source)'. Set JAVA_HOME to the JDK installation directory."
  }

  $env:JAVA_HOME = $candidate
}

Set-Location $repositoryRoot
Import-LocalEnvironment $environmentFile

if (-not (Test-Path $privateKey) -or -not (Test-Path $publicKey)) {
  throw "Missing local JWT keys. Generate them once with: & 'C:\Program Files\Git\bin\bash.exe' infra/scripts/generate-dev-jwt-keys.sh"
}

New-Item -ItemType Directory -Force -Path $stateDirectory | Out-Null
$env:COVIE_JWT_PRIVATE_KEY_PATH = $privateKey
$env:COVIE_JWT_PUBLIC_KEY_PATH = $publicKey
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SERVER_PORT = '8081'
$env:API_INTERNAL_BASE_URL = 'http://localhost:8081'
$env:NEXT_PUBLIC_APP_URL = 'http://localhost:3000'

function Save-DevSession {
  $temporaryPath = "$sessionPath.$($session.id).tmp"
  [IO.File]::WriteAllText($temporaryPath, ($session | ConvertTo-Json -Depth 5))
  Move-Item -LiteralPath $temporaryPath -Destination $sessionPath -Force
}

$sessionPath = Join-Path $stateDirectory 'session.json'
if (Test-Path -LiteralPath $sessionPath) {
  $priorSession = Get-Content -LiteralPath $sessionPath -Raw | ConvertFrom-Json
  $priorOwner = Get-Process -Id $priorSession.ownerPid -ErrorAction SilentlyContinue
  if ($priorOwner -and $priorOwner.StartTime.ToUniversalTime().ToString('o') -eq $priorSession.ownerStartedAt) {
    throw 'A CoVie dev session is already running. Stop it with Ctrl+C or pnpm dev:stop.'
  }
  & (Join-Path $PSScriptRoot 'stop-dev.ps1') -SessionId $priorSession.id
}
foreach ($port in @(8081, 3000)) {
  if (Test-PortInUse $port) {
    throw "Port $port is already in use. Stop the old API/web session first (Ctrl+C or pnpm dev:stop), then run pnpm dev again."
  }
}
Initialize-JavaHome
$ownerStartedAt = (Get-Process -Id $PID).StartTime.ToUniversalTime().ToString('o')
$session = @{ id = [guid]::NewGuid().ToString(); ownerPid = $PID; ownerStartedAt = $ownerStartedAt; api = $null; web = $null }
Save-DevSession
try {
  $watchdogPath = Join-Path $PSScriptRoot 'dev-watchdog.ps1'
  $watchdog = Start-Process powershell.exe -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$watchdogPath`"", '-OwnerProcessId', $PID, '-OwnerStartedAt', $ownerStartedAt, '-SessionId', $session.id) `
    -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $stateDirectory 'watchdog.log') `
    -RedirectStandardError (Join-Path $stateDirectory 'watchdog.error.log')
Write-Host 'Starting PostgreSQL, Redis and MinIO...'
& docker compose -f infra/compose/compose.local.yml -f infra/compose/compose.local.runtime.yml -f infra/compose/compose.local.restore.yml up -d
if ($LASTEXITCODE -ne 0) { throw 'Local infrastructure failed to start.' }

$apiHealth = 'http://localhost:8081/actuator/health'
if (-not (Test-HttpOk $apiHealth)) {
  if (Test-PortInUse 8081) {
    throw 'Port 8081 is already in use by a service that is not a healthy local CoVie API.'
  }

  Write-Host 'Starting CoVie API on http://localhost:8081...'
  Initialize-JavaHome
  $api = Start-Process -FilePath (Join-Path $apiDirectory 'mvnw.cmd') `
    -ArgumentList @('-f', (Join-Path $apiDirectory 'pom.xml'), 'spring-boot:run') `
    -WorkingDirectory $apiDirectory -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $stateDirectory 'api.log') `
    -RedirectStandardError (Join-Path $stateDirectory 'api.error.log')
  Set-Content -LiteralPath (Join-Path $stateDirectory 'api.pid') -Value $api.Id
  $session.api = @{ pid = $api.Id; startedAt = $api.StartTime.ToUniversalTime().ToString('o') }
  Save-DevSession
  Wait-ForHealthyService 'API' $apiHealth 8081
} else {
  throw 'An API appeared during startup. Stop it before starting this managed session.'
}

$webUrl = 'http://localhost:3000/'
if (-not (Test-HttpOk $webUrl)) {
  if (Test-PortInUse 3000) {
    throw 'Port 3000 is already in use by a service that is not a healthy CoVie web app.'
  }

  Write-Host 'Starting CoVie web on http://localhost:3000...'
  $web = Start-Process -FilePath 'pnpm.cmd' -ArgumentList @('--dir', $repositoryRoot, 'web:dev') `
    -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $stateDirectory 'web.log') `
    -RedirectStandardError (Join-Path $stateDirectory 'web.error.log')
  Set-Content -LiteralPath (Join-Path $stateDirectory 'web.pid') -Value $web.Id
  $session.web = @{ pid = $web.Id; startedAt = $web.StartTime.ToUniversalTime().ToString('o') }
  Save-DevSession
  Wait-ForHealthyService 'Web app' $webUrl 3000
} else {
  throw 'A web server appeared during startup. Stop it before starting this managed session.'
}

Write-Host ''
Write-Host 'CoVie is ready: http://localhost:3000'
Write-Host "Logs: $stateDirectory"
Write-Host 'Keep this terminal open. Ctrl+C stops API, web and CoVie Docker services.'
Write-Host 'You can also stop this session with: pnpm dev:stop'
while (Test-Path -LiteralPath $sessionPath) {
  Start-Sleep -Seconds 2
  if (-not (Test-Path -LiteralPath $sessionPath)) { break }
  foreach ($child in @($session.api, $session.web)) {
    $running = Get-Process -Id $child.pid -ErrorAction SilentlyContinue
    if (-not $running -or $running.StartTime.ToUniversalTime().ToString('o') -ne $child.startedAt) {
      throw 'A CoVie child process stopped unexpectedly. Shutting down the dev session.'
    }
  }
}
} finally {
  & (Join-Path $PSScriptRoot 'stop-dev.ps1') -SessionId $session.id
}
