param(
    [ValidateSet('SYSTEM_ADMIN','MAINTENANCE','LINE_MANAGER')][string]$Role='SYSTEM_ADMIN',
    [switch]$CopyToClipboard
)
. (Join-Path $PSScriptRoot 'Common.ps1')
$config = Read-ManualEnv
$login = @{ SYSTEM_ADMIN='admin01'; MAINTENANCE='maint01'; LINE_MANAGER='linemgr01' }[$Role]
$userId = @(Invoke-ManualSql "SELECT account_id FROM account WHERE login_id='$login' AND role='$Role' AND approval_status='APPROVED' AND deleted_at IS NULL;")[0]
if (-not $userId) { throw 'Expected approved test account is missing.' }
function ConvertTo-Base64Url([byte[]]$Value) { return [Convert]::ToBase64String($Value).TrimEnd('=').Replace('+','-').Replace('/','_') }
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
$claims = @{ iss='stf-manual-test'; sub=[string]$userId; jti=[guid]::NewGuid().ToString(); iat=$now; exp=$now+1800; position=$Role } | ConvertTo-Json -Compress
$payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($claims))
$hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($config.JWT_SECRET))
try { $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes("$header.$payload"))) } finally { $hmac.Dispose() }
$token = "$header.$payload.$signature"
if ($CopyToClipboard) { Set-Clipboard -Value $token; Write-Host "Copied a 30-minute $Role test token. Paste into Swagger Authorize (without Bearer)." }
else { Write-Output $token }
