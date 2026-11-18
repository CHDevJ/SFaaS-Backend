param(
    [string]$BotUsername = 'sfaas5_alarm_bot',
    [switch]$PrepareOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$relativePath = '.env.telegram.local.properties'
$secretPath = Join-Path $projectRoot $relativePath
$temporaryPath = $secretPath + '.tmp'
$BotUsername = $BotUsername.Trim().TrimStart('@')
if ($BotUsername -notmatch '^[A-Za-z][A-Za-z0-9_]{1,28}[Bb][Oo][Tt]$') {
    throw 'Enter a valid Telegram bot username without a URL.'
}

# Ignore rules alone do not protect a file that was already added to the index.
foreach ($relative in @($relativePath, ($relativePath + '.tmp'))) {
    & git -C $projectRoot check-ignore --quiet -- $relative
    if ($LASTEXITCODE -ne 0) { throw 'Local Telegram files must be ignored by Git before setup.' }
    $tracked = & git -C $projectRoot ls-files -- $relative
    if ($LASTEXITCODE -ne 0 -or $tracked) { throw 'Local Telegram files must not be tracked by Git.' }
}

if ($PrepareOnly) {
    if (Test-Path -LiteralPath $secretPath) {
        Write-Host 'Existing local Telegram settings were preserved.'
        return
    }
    $template = "TELEGRAM_BOT_USERNAME=$BotUsername`nTELEGRAM_POLLING_ENABLED=false`n"
    [IO.File]::WriteAllText($secretPath, $template, [Text.UTF8Encoding]::new($false))
    Write-Host 'Prepared the shared bot username. Polling stays off until a valid token is entered.'
    return
}

Write-Host 'Enter a NEW token issued by BotFather after revoking the token shared in chat.'
Write-Host 'The token is hidden during input and saved only in a Git-ignored local file.'
$secureToken = Read-Host 'New Telegram bot token' -AsSecureString
$buffer = [IntPtr]::Zero
$plainToken = $null
$settings = $null
try {
    $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
    $plainToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer).Trim()
    if ($plainToken -notmatch '^[0-9]+:[A-Za-z0-9_-]{20,}$') {
        throw 'The token format is invalid. No local settings were changed.'
    }

    # Never print request URLs or raw exceptions: the Bot API uses the token in its URL.
    try {
        $apiBase = 'https://api.telegram.org/bot' + $plainToken
        $bot = Invoke-RestMethod -Uri ($apiBase + '/getMe') -Method Get -TimeoutSec 15 -ErrorAction Stop -Verbose:$false -Debug:$false
        $webhook = Invoke-RestMethod -Uri ($apiBase + '/getWebhookInfo') -Method Get -TimeoutSec 15 -ErrorAction Stop -Verbose:$false -Debug:$false
    } catch {
        throw 'Telegram verification failed. Check the new token and network. No local settings were changed.'
    }
    if (-not $bot.ok -or -not $bot.result.is_bot -or $bot.result.username -ine $BotUsername) {
        throw 'The token does not belong to the requested bot. No local settings were changed.'
    }
    if (-not $webhook.ok) { throw 'Webhook verification failed. No local settings were changed.' }
    if (-not [string]::IsNullOrWhiteSpace($webhook.result.url)) {
        throw 'This bot already uses a webhook. Coordinate the receiving server first. No webhook or local settings were changed.'
    }

    $settings = "TELEGRAM_BOT_USERNAME=$BotUsername`nTELEGRAM_BOT_TOKEN=$plainToken`nTELEGRAM_POLLING_ENABLED=true`n"
    [IO.File]::WriteAllText($temporaryPath, $settings, [Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $secretPath) {
        [IO.File]::Replace($temporaryPath, $secretPath, [NullString]::Value)
    } else {
        [IO.File]::Move($temporaryPath, $secretPath)
    }
    Write-Host ('Verified @' + $BotUsername + ' and saved local settings. No token value was printed.')
    Write-Host 'Restart one backend instance from the stf directory with the backend-b profile.'
    Write-Host 'Then issue a connection link from your signed-in project account and press Start in Telegram.'
} finally {
    if ($buffer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
    if ($secureToken) { $secureToken.Dispose() }
    $plainToken = $null
    $settings = $null
    $apiBase = $null
    if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath -Force }
}
