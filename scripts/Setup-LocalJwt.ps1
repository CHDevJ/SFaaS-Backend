# Create a local development key without printing it or changing a valid existing key.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$secretPath = Join-Path $projectRoot 'secrets/jwt-local.properties'
& git -C $projectRoot check-ignore --quiet -- secrets/jwt-local.properties
if ($LASTEXITCODE -ne 0) { throw 'The local secret file must be ignored by Git before it can be created.' }

if (Test-Path -LiteralPath $secretPath) {
    $existing = Get-Content -LiteralPath $secretPath | Where-Object { $_ -match '^JWT_SECRET=' } | Select-Object -First 1
    if (-not $existing -or [Text.Encoding]::UTF8.GetByteCount($existing.Substring(11)) -lt 32) {
        throw 'The existing local JWT key is invalid. Supply a key of at least 32 bytes; the file was not overwritten.'
    }
    Write-Host 'Existing local JWT key is valid and was preserved.'
    return
}

$bytes = New-Object byte[] 64
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$key = [Convert]::ToBase64String($bytes)
New-Item -ItemType Directory -Path (Split-Path -Parent $secretPath) -Force | Out-Null
[IO.File]::WriteAllText($secretPath, 'JWT_SECRET=' + $key + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Host 'Created a cryptographically random local JWT key in the Git-ignored secrets folder.'
Write-Host 'Run Spring Boot from the stf project directory. No key value is printed.'
