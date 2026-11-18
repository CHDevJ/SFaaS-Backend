param([string]$OutputPath)
. (Join-Path $PSScriptRoot 'Common.ps1')
if(-not $OutputPath){$OutputPath=Join-Path $ManualRoot 'logs/v7-manual-smoke-results.json'}
$admin=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role ADMIN
$user=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role USER
$results=[Collections.Generic.List[object]]::new()
function Check([string]$Path,[string]$Token,[int]$Expected=200){
    $watch=[Diagnostics.Stopwatch]::StartNew();$result=Invoke-ManualHttp $Path $Token -TimeoutSeconds 20
    $results.Add([pscustomobject]@{path=$Path;expected=$Expected;actual=$result.Status;result=$(if($Expected -eq $result.Status){'PASS'}else{'FAIL'});seconds=$watch.Elapsed.TotalSeconds})
    return $result
}
Check '/actuator/health' '' | Out-Null
Check '/swagger-ui/index.html' '' | Out-Null
$docs=Check '/v3/api-docs' ''; $spec=$docs.Body|ConvertFrom-Json
if($spec.info.version -ne '7.0.0'){throw 'The running server is not v7. Rebuild and restart the dedicated manual server.'}
Check '/api/v1/overview/equipment-layout' '' 401 | Out-Null
# v7 requires an explicit half-open period for histories and statistics.
$koreaNow=[DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(9))
$from=[Uri]::EscapeDataString($koreaNow.ToString('yyyy-MM-dd')+'T00:00:00+09:00')
$to=[Uri]::EscapeDataString($koreaNow.AddDays(1).ToString('yyyy-MM-dd')+'T00:00:00+09:00')
$period='from='+$from+'&to='+$to
foreach($path in @('/api/v1/lines','/api/v1/overview/equipment-layout','/api/v1/overview/assembly-stop-forecast','/api/v1/inventory','/api/v1/alarms','/api/v1/alarms/recent-24-hours','/api/v1/telegram/me/connection','/api/v1/communications','/api/v1/communications/summary','/api/v1/master/equipment','/api/v1/master/lines')){
    Check $path $user | Out-Null
}
foreach($path in @('/api/v1/alarm-histories','/api/v1/statistics/availability','/api/v1/statistics/daily-alarms','/api/v1/statistics/alarm-response','/api/v1/statistics/losses','/api/v1/statistics/equipment-rankings','/api/v1/statistics/stop-causes')){
    $query=$period+$(if($path -eq '/api/v1/statistics/equipment-rankings'){'&sortBy=faultCount'}else{''})
    Check ($path+'?'+$query) $user | Out-Null
}
# Missing required dates must still be rejected by the server.
Check '/api/v1/alarm-histories' $user 400 | Out-Null
Check '/api/v1/statistics/availability' $user 400 | Out-Null
Check ('/api/v1/system/change-histories?'+$period) $user 403 | Out-Null
Check ('/api/v1/system/change-histories?'+$period) $admin | Out-Null
Check '/api/v1/system/telegram/deliveries' $user 403 | Out-Null
Check '/api/v1/system/telegram/deliveries' $admin | Out-Null
$failed=@($results|Where-Object result -eq 'FAIL').Count
[pscustomobject]@{version='7.0.0';executedAt=[DateTimeOffset]::Now.ToString('o');passed=$results.Count-$failed;failed=$failed;cases=@($results)}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $OutputPath -Encoding utf8
Write-Host "Read-only API checks: $($results.Count-$failed) passed / $failed failed."
if($failed){throw 'One or more v7 API checks failed. See the redacted result file.'}
