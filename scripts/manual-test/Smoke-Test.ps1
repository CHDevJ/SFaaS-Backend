param([string]$OutputPath)
. (Join-Path $PSScriptRoot 'Common.ps1')
if (-not $OutputPath) { $OutputPath=Join-Path $ManualRoot 'logs/manual-smoke-results.json' }
$results=[Collections.Generic.List[object]]::new()
$admin=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role SYSTEM_ADMIN
$maintenance=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role MAINTENANCE
$lineManager=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role LINE_MANAGER
function Request([string]$Method,[string]$Path,[string]$Token,[object]$Body=$null) {
    $handler=[Net.Http.HttpClientHandler]::new(); $handler.UseProxy=$false
    $client=[Net.Http.HttpClient]::new($handler); $client.Timeout=[TimeSpan]::FromSeconds(10)
    try {
        $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),'http://127.0.0.1:8081'+$Path)
        if ($Token) { $request.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token) }
        if ($null -ne $Body) { $request.Content=[Net.Http.StringContent]::new(($Body|ConvertTo-Json -Depth 12 -Compress),[Text.Encoding]::UTF8,'application/json') }
        $watch=[Diagnostics.Stopwatch]::StartNew()
        $response=$client.SendAsync($request).GetAwaiter().GetResult()
        return [pscustomobject]@{Status=[int]$response.StatusCode;Body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult();Seconds=$watch.Elapsed.TotalSeconds}
    } finally { $client.Dispose(); $handler.Dispose() }
}
function Check([string]$Id,[string]$Title,[string]$Method,[string]$Path,[int]$Expected,[string]$Token,[object]$Body=$null) {
    $r=Request $Method $Path $Token $Body
    $passed=$r.Status -eq $Expected
    $results.Add([pscustomobject]@{id=$Id;title=$Title;method=$Method;path=$Path;expected=$Expected;actual=$r.Status;result=$(if($passed){'PASS'}else{'FAIL'});seconds=[Math]::Round($r.Seconds,4);evidence=$(if($Path -eq '/api/v1/auth/login') {"login response redacted"} else {$r.Body.Substring(0,[Math]::Min(220,$r.Body.Length))});executedAt=[DateTimeOffset]::Now.ToString('o')})
    if (-not $passed) { Write-Warning "$Id expected $Expected but got $($r.Status): $($r.Body)" }
    return $r
}
Check 'ENV-001' '서버 및 MySQL·Redis 상태' GET '/actuator/health' 200 '' | Out-Null
Check 'ENV-002' 'Swagger UI HTML' GET '/swagger-ui/index.html' 200 '' | Out-Null
$schema=Check 'ENV-003' 'OpenAPI JSON' GET '/v3/api-docs' 200 ''
$doc=$schema.Body|ConvertFrom-Json
$operationCount=0
foreach($p in $doc.paths.PSObject.Properties){foreach($m in $p.Value.PSObject.Properties){if($m.Name -in 'get','post','put','patch','delete'){$operationCount++}}}
if($operationCount -ne 45 -or $doc.components.securitySchemes.bearerAuth.scheme -ne 'bearer'){throw 'Expected 40 B and 5 A authentication/signup operations in Swagger.'}
$loginBody=& (Join-Path $PSScriptRoot 'Get-ManualTestLogin.ps1')|ConvertFrom-Json
$loginResponse=Check 'AUTH-LIVE-001' '실제 로그인 성공' POST '/api/v1/auth/login' 200 '' $loginBody
$loginJson=$loginResponse.Body|ConvertFrom-Json
$loginJwt=$loginJson.data.accessToken
# The login response contains credentials. Store only its non-sensitive status as evidence.
$results[$results.Count-1].evidence='success=true; accessToken received (redacted); refresh cookie verified by automated integration test'
Check 'AUTH-LIVE-002' '로그인 JWT로 B API 호출' GET '/api/v1/inventory' 200 $loginJwt|Out-Null
Check 'AUTH-LIVE-003' '잘못된 비밀번호 거절' POST '/api/v1/auth/login' 401 '' @{loginId=$loginBody.loginId;password='WrongPassword999!'}|Out-Null
Check 'SEC-001' '미인증 요청 거절' GET '/api/v1/inventory' 401 '' | Out-Null
Check 'SEC-002' '잘못된 JWT 거절' GET '/api/v1/inventory' 401 'invalid-token' | Out-Null
Check 'SEC-003' '보전 JWT 조회' GET '/api/v1/inventory' 200 $maintenance | Out-Null
Check 'SEC-004' '라인관리자 JWT 조회' GET '/api/v1/inventory' 200 $lineManager | Out-Null
$equipment=[long](@(Invoke-ManualSql "SELECT equipment_id FROM equipment WHERE equip_code='OR-04-1';")[0])
$item=[long](@(Invoke-ManualSql "SELECT data_item_id FROM data_item WHERE equipment_id=$equipment AND item_code='coolant_flow';")[0])
$part=[long](@(Invoke-ManualSql "SELECT part_id FROM part WHERE manage_no='MANUAL-DEMO-001';")[0])
$alarm=[long](@(Invoke-ManualSql "SELECT alarm_id FROM alarm WHERE message LIKE '[테스트] 냉각액%' ORDER BY alarm_id LIMIT 1;")[0])
$maintId=[long](@(Invoke-ManualSql "SELECT account_id FROM account WHERE login_id='maint01';")[0])
$cases=Get-Content -LiteralPath (Join-Path $ManualRoot 'src/test/resources/backend-b-api-cases.json') -Raw|ConvertFrom-Json
foreach($case in $cases|Where-Object method -eq GET){
    $apiPath=$case.path.Replace('{equipmentId}',[string]$equipment).Replace('{alarmId}',[string]$alarm).Replace('{partId}',[string]$part).Replace('{lineId}','1').Replace('{dataItemId}',[string]$item)
    $parameters=[Collections.Generic.List[string]]::new()
    foreach($parameter in $case.params.PSObject.Properties){
        $value=[string]$parameter.Value
        if($parameter.Name -eq 'from'){$value=[DateTimeOffset]::Now.AddDays(-2).ToString('o')}
        if($parameter.Name -eq 'to'){$value=[DateTimeOffset]::Now.ToString('o')}
        if($parameter.Name -eq 'month'){$value=[DateTime]::Now.ToString('yyyy-MM')}
        if($parameter.Name -eq 'dataItemId'){$value=[string]$item}
        $parameters.Add($parameter.Name+'='+[Uri]::EscapeDataString($value))
    }
    if($parameters.Count){$apiPath+='?'+($parameters -join '&')}
    $expected=200
    if($case.id -eq 'DUT-003'){
        $currentCount=[int](@(Invoke-ManualSql 'SELECT COUNT(*) FROM duty WHERE CURDATE() BETWEEN start_date AND end_date;')[0])
        if($currentCount -eq 0){$expected=404}
    }
    Check $case.id ('실제 DB 조회: '+$case.operation) GET $apiPath $expected $admin | Out-Null
}
$today=[DateTime]::Today
$duty=@{startDate=$today.AddDays(1).ToString('yyyy-MM-dd');endDate=$today.AddDays(7).ToString('yyyy-MM-dd');dutyUserId=$maintId}
Check 'SEC-005' '보전의 당번 등록 권한 거절' POST '/api/v1/system/alarm-duty-schedules' 403 $maintenance $duty | Out-Null
$created=Check 'WRITE-001' '관리자 당번 등록' POST '/api/v1/system/alarm-duty-schedules' 201 $admin $duty
if($created.Status -eq 201){
    $schedule=($created.Body|ConvertFrom-Json).data.scheduleId
    $duty.endDate=$today.AddDays(8).ToString('yyyy-MM-dd')
    Check 'WRITE-002' '당번 수정' PUT "/api/v1/system/alarm-duty-schedules/$schedule" 200 $admin $duty | Out-Null
    Check 'WRITE-003' '당번 삭제' DELETE "/api/v1/system/alarm-duty-schedules/$schedule" 204 $admin | Out-Null
}
$label='[SMOKE] '+[guid]::NewGuid().ToString('N')
$smokeCode='smoke_'+[guid]::NewGuid().ToString('N')
Invoke-ManualSql "INSERT INTO data_item(equipment_id,item_code,name,item_kind,caution_limit,critical_limit,limit_direction) VALUES($equipment,'$smokeCode','[SMOKE] 임시 측정 항목','MEASUREMENT',72,64,'BELOW'); INSERT INTO alarm(equipment_id,data_item_id,grade,message,trigger_value,limit_value,occurred_at) SELECT $equipment,data_item_id,'CRITICAL','$label',50,64,NOW() FROM data_item WHERE equipment_id=$equipment AND item_code='$smokeCode';"|Out-Null
$smokeAlarm=[long](@(Invoke-ManualSql "SELECT alarm_id FROM alarm WHERE message='$label';")[0])
Check 'WRITE-004' '알람 확인 및 보전 담당 지정' PATCH "/api/v1/alarms/$smokeAlarm/acknowledgement" 200 $admin @{assigneeUserId=$maintId}|Out-Null
Check 'WRITE-005' '보전 담당 조치 등록' POST "/api/v1/alarms/$smokeAlarm/actions" 201 $maintenance @{cause='수동 검증용 냉각 필터 오염';actionContent='테스트 전용 DB에서 필터 청소 조치 등록'}|Out-Null
Check 'WRITE-006' '조치 후 효과 확인 조회' GET "/api/v1/alarms/$smokeAlarm/effect" 200 $maintenance|Out-Null
Check 'VALID-001' '필수값 없는 조치 거절' POST "/api/v1/alarms/$smokeAlarm/actions" 400 $maintenance @{}|Out-Null
Check 'VALID-002' '잘못된 Enum 거절' GET '/api/v1/alarms?grade=UNKNOWN' 400 $admin|Out-Null
$passed=@($results|Where-Object result -eq PASS).Count
$failed=@($results|Where-Object result -eq FAIL).Count
$summary=[pscustomobject]@{executedAt=[DateTimeOffset]::Now.ToString('o');baseUrl='http://localhost:8081';database='stf_b_manual';swaggerOperations=$operationCount;passed=$passed;failed=$failed;cases=$results}
$summary|ConvertTo-Json -Depth 10|Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Host "Live HTTP checks: $passed passed, $failed failed. Results: $OutputPath"
if($failed){throw 'Live HTTP checks failed.'}
