#Requires -RunAsAdministrator
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$statusPath=Join-Path $root 'secrets/node-red-firewall-status.json'
$name='STF-NodeRed-Ingestion-8081'
$group='STF local ingestion'
New-Item -ItemType Directory -Path (Split-Path -Parent $statusPath) -Force | Out-Null
try {
    $rule=Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
    if($rule){
        if($rule.Group -ne $group){throw 'An unrelated firewall rule uses the requested name.'}
        $rule | Set-NetFirewallRule -Enabled True -Direction Inbound -Action Allow -Profile Any
        $rule | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol TCP -LocalPort 8081
        $rule | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress LocalSubnet
    } else {
        New-NetFirewallRule -Name $name -DisplayName 'STF Node-RED receiver TCP 8081 (local subnet)' -Group $group -Enabled True -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8081 -RemoteAddress LocalSubnet -Profile Any | Out-Null
    }
    $result=@{status='SUCCESS';ruleName=$name;protocol='TCP';localPort=8081;remoteAddress='LocalSubnet';recordedAt=[DateTimeOffset]::Now.ToString('o')}
    [IO.File]::WriteAllText($statusPath,($result|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    Write-Host 'Enabled inbound TCP 8081 for the local subnet. Other firewall policies were preserved.'
} catch {
    [IO.File]::WriteAllText($statusPath,(@{status='FAILURE';ruleName=$name;errorType=$_.Exception.GetType().Name}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    throw 'The scoped Node-RED firewall rule could not be configured. See the local status file.'
}
