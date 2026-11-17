param([ValidateSet('SYSTEM_ADMIN','MAINTENANCE','LINE_MANAGER')][string]$Role='SYSTEM_ADMIN')
. (Join-Path $PSScriptRoot 'Common.ps1')
$config=Read-ManualEnv
$login=@{SYSTEM_ADMIN='badmin01';MAINTENANCE='bmaint01';LINE_MANAGER='bline01'}[$Role]
@{loginId=$login;password=$config.MANUAL_LOGIN_PASSWORD}|ConvertTo-Json -Compress
