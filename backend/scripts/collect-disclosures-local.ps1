param(
  [Parameter(Mandatory = $true)][string]$CorpCode,
  [Parameter(Mandatory = $true)][datetime]$From,
  [Parameter(Mandatory = $true)][datetime]$To,
  [int]$MaxPages = 5,
  [int]$FinancialYear,
  [ValidateSet('11011', '11012', '11013', '11014')][string]$ReportCode,
  [ValidateSet('CFS', 'OFS')][string]$Basis = 'CFS'
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
  "--spring.profiles.active=local",
  '--disclosure.collection.run=true',
  "--corp-code=$CorpCode",
  "--from=$($From.ToString('yyyy-MM-dd'))",
  "--to=$($To.ToString('yyyy-MM-dd'))",
  "--max-pages=$MaxPages"
)
if ($FinancialYear -gt 0 -and $ReportCode) {
  $bootArgs += "--financial-year=$FinancialYear"
  $bootArgs += "--report-code=$ReportCode"
  $bootArgs += "--basis=$Basis"
}

Push-Location (Join-Path $root 'backend')
try {
  & .\gradlew.bat bootRun "--args=$($bootArgs -join ' ')"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
  Pop-Location
}
