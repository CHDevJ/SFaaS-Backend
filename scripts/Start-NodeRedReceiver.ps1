param([switch]$SkipBuild)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$previousDirectory=Get-Location
$oldOrigins=$env:STF_WS_ORIGINS
try {
    Set-Location -LiteralPath $root
    & (Join-Path $PSScriptRoot 'Setup-LocalIngestion.ps1')
    New-Item -ItemType Directory -Path logs -Force | Out-Null
    if(-not $SkipBuild){
        & .\gradlew.bat bootJar --no-daemon *> logs/node-red-build.log
        if($LASTEXITCODE -ne 0){throw 'Build failed. Existing server was retained. See logs/node-red-build.log.'}
    }
    $addresses=@(Get-NetIPConfiguration | Where-Object IPv4DefaultGateway | ForEach-Object {$_.IPv4Address.IPAddress} | Where-Object {$_ -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)'})
    $origins=@('http://localhost:5173','http://127.0.0.1:5173','http://localhost:5174','http://127.0.0.1:5174')
    if($oldOrigins){$origins+=$oldOrigins -split ','}
    foreach($address in $addresses){$origins+='http://'+$address+':5174'}
    $env:STF_WS_ORIGINS=($origins|Select-Object -Unique)-join ','
    & (Join-Path $PSScriptRoot 'manual-test/Stop-ManualTest.ps1')
    & (Join-Path $PSScriptRoot 'manual-test/Start-ManualTest.ps1') -SkipBuild -EnableIngestion
    foreach($address in $addresses){Write-Host ('NODE-RED POST: http://'+$address+':8081/internal/v1/measurements')}
    Write-Host 'Receiver is listening on 0.0.0.0:8081. The Node-RED host must be able to reach this PC.'
    Write-Host 'Set the local-subnet firewall rule with scripts/Enable-NodeRedReceiverFirewall.ps1 as administrator.'
} finally {$env:STF_WS_ORIGINS=$oldOrigins;Set-Location -LiteralPath $previousDirectory}
