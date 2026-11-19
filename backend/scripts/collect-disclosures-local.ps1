param(
  [Parameter(Mandatory = $true)][ValidatePattern('^[0-9]{8}$')][string]$CorpCode,
  [datetime]$To
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
  throw "Root .env not found: $envFile"
}

foreach ($entry in Get-Content -LiteralPath $envFile) {
  if ($entry -match '^\s*([^#\s][^=]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
  }
}

if ([string]::IsNullOrWhiteSpace($env:OPENDART_API_KEY)) {
  throw 'OPENDART_API_KEY is empty in the root .env'
}

$bootArgs = @(
  '--spring.profiles.active=local',
  '--disclosure.collection.run=true',
  "--corp-code=$CorpCode"
)
if ($PSBoundParameters.ContainsKey('To')) {
  $bootArgs += "--to=$($To.ToString('yyyy-MM-dd'))"
}

Push-Location (Join-Path $root 'backend')
try {
  & .\gradlew.bat bootRun "--args=$($bootArgs -join ' ')"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
  Pop-Location
}
