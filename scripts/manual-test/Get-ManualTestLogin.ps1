param([ValidateSet('ADMIN','USER')][string]$Role='ADMIN')
. (Join-Path $PSScriptRoot 'Common.ps1')
$config=Read-ManualEnv
$login=@{ADMIN='badmin01';USER='bmaint01'}[$Role]
@{loginId=$login;password=$config.MANUAL_LOGIN_PASSWORD}|ConvertTo-Json -Compress
