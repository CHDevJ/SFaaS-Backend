param([string]$OutputPath)
. (Join-Path $PSScriptRoot 'Common.ps1')
if (-not $OutputPath) { $OutputPath=Join-Path $ManualRoot 'logs/manual-smoke-results.json' }
$results=[Collections.Generic.List[object]]::new()
$admin=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role SYSTEM_ADMIN
$maintenance=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role MAINTENANCE
$lineManager=& (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1') -Role LINE_MANAGER
function Request([string]$Method,[string]$Path,[string]$Token,[object]$Body=$null,[string]$RefreshCookie='') {
    $handler=[Net.Http.HttpClientHandler]::new(); $handler.UseProxy=$false
    $client=[Net.Http.HttpClient]::new($handler); $client.Timeout=[TimeSpan]::FromSeconds(10)
    try {
        $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),'http://127.0.0.1:8081'+$Path)
        if ($RefreshCookie) { $request.Headers.Add('Cookie',$RefreshCookie) }
        if ($Token) { $request.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token) }
        if ($null -ne $Body) { $request.Content=[Net.Http.StringContent]::new(($Body|ConvertTo-Json -Depth 12 -Compress),[Text.Encoding]::UTF8,'application/json') }
        $watch=[Diagnostics.Stopwatch]::StartNew()
        $response=$client.SendAsync($request).GetAwaiter().GetResult()
        return [pscustomobject]@{Status=[int]$response.StatusCode;Body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult();Seconds=$watch.Elapsed.TotalSeconds;Cookie=$(if($response.Headers.Contains('Set-Cookie')){(@($response.Headers.GetValues('Set-Cookie'))[0] -split ';',2)[0]}else{''})}
    } finally { $client.Dispose(); $handler.Dispose() }
}
function Check([string]$Id,[string]$Title,[string]$Method,[string]$Path,[int]$Expected,[string]$Token,[object]$Body=$null,[string]$RefreshCookie='') {
    $r=Request $Method $Path $Token $Body $RefreshCookie
    $passed=$r.Status -eq $Expected
    $results.Add([pscustomobject]@{id=$Id;title=$Title;method=$Method;path=$Path;expected=$Expected;actual=$r.Status;result=$(if($passed){'PASS'}else{'FAIL'});seconds=[Math]::Round($r.Seconds,4);evidence=$(if($Path -in '/api/v1/auth/login','/api/v1/auth/refresh') {"login response redacted"} else {$r.Body.Substring(0,[Math]::Min(220,$r.Body.Length))});executedAt=[DateTimeOffset]::Now.ToString('o')})
    if (-not $passed) { Write-Warning "$Id expected $Expected but got $($r.Status). See the redacted result record." }
    return $r
}
Check 'ENV-001' '서버 및 MySQL·Redis 상태' GET '/actuator/health' 200 '' | Out-Null
Check 'ENV-002' 'Swagger UI HTML' GET '/swagger-ui/index.html' 200 '' | Out-Null
$schema=Check 'ENV-003' 'OpenAPI JSON' GET '/v3/api-docs' 200 ''
$doc=$schema.Body|ConvertFrom-Json
$operationCount=0
foreach($p in $doc.paths.PSObject.Properties){foreach($m in $p.Value.PSObject.Properties){if($m.Name -in 'get','post','put','patch','delete'){$operationCount++}}}
if($operationCount -ne 54 -or $doc.components.securitySchemes.bearerAuth.scheme -ne 'bearer'){throw 'Expected 40 B and 14 A authentication/user/signup operations in Swagger.'}
$loginBody=& (Join-Path $PSScriptRoot 'Get-ManualTestLogin.ps1')|ConvertFrom-Json
$loginResponse=Check 'AUTH-LIVE-001' '실제 로그인 성공' POST '/api/v1/auth/login' 200 '' $loginBody
$loginJson=$loginResponse.Body|ConvertFrom-Json
$loginJwt=$loginJson.data.accessToken
# The login response contains credentials. Store only its non-sensitive status as evidence.
$results[$results.Count-1].evidence='success=true; accessToken received (redacted); refresh cookie verified by automated integration test'
Check 'AUTH-LIVE-002' '로그인 JWT로 B API 호출' GET '/api/v1/inventory' 200 $loginJwt|Out-Null
Check 'AUTH-LIVE-004' '로그인 사용자 조회' GET '/api/v1/auth/me' 200 $loginJwt|Out-Null
$refreshResponse=Check 'AUTH-LIVE-005' '쿠키로 토큰 갱신' POST '/api/v1/auth/refresh' 200 '' $null $loginResponse.Cookie
$refreshedJwt=($refreshResponse.Body|ConvertFrom-Json).data.accessToken
Check 'AUTH-LIVE-006' '갱신 JWT로 B API 호출' GET '/api/v1/inventory' 200 $refreshedJwt|Out-Null
Check 'AUTH-LIVE-007' '로그아웃 및 쿠키 폐기' POST '/api/v1/auth/logout' 204 $refreshedJwt $null $refreshResponse.Cookie|Out-Null
Check 'AUTH-LIVE-008' '로그아웃 JWT의 B API 접근 거절' GET '/api/v1/inventory' 401 $refreshedJwt|Out-Null
Check 'AUTH-LIVE-009' '로그아웃 Refresh Token 재사용 거절' POST '/api/v1/auth/refresh' 401 '' $null $refreshResponse.Cookie|Out-Null
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
# User/deletion fixtures are confined to stf_b_manual by Invoke-ManualSql.
Check 'USR-LIVE-001' '관리자 사용자 목록 조회' GET '/api/v1/system/users' 200 $admin|Out-Null
$temporaryLogin='duty'+[guid]::NewGuid().ToString('N').Substring(0,12)
$temporaryId=0L
$temporaryDuties=[Collections.Generic.List[long]]::new()
try {
    $temporaryId=[long](@(Invoke-ManualSql "INSERT INTO account(login_id,password_hash,name,role,approval_status,approved_at) SELECT '$temporaryLogin',password_hash,'[SMOKE] 당번 삭제 검증','MAINTENANCE','APPROVED',NOW() FROM account WHERE login_id='bmaint01'; SELECT LAST_INSERT_ID();")[0])
    Check 'USR-LIVE-002' '관리자 사용자 상세 조회' GET "/api/v1/system/users/$temporaryId" 200 $admin|Out-Null
    $lastEnd=[string](@(Invoke-ManualSql "SELECT COALESCE(DATE_FORMAT(MAX(end_date),'%Y-%m-%d'),'NONE') FROM duty;")[0])
    $from=if($lastEnd -eq 'NONE'){[DateTime]::Today}else{[DateTime]::ParseExact($lastEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture).AddDays(1)}
    $through=$from.AddDays(2)
    if($through -lt [DateTime]::Today){$through=[DateTime]::Today.AddDays(2)}
    foreach($range in @(@{from=$from;to=$through},@{from=$through.AddDays(1);to=$through.AddDays(4)})){
        $start=$range.from.ToString('yyyy-MM-dd');$end=$range.to.ToString('yyyy-MM-dd')
        $id=[long](@(Invoke-ManualSql "INSERT INTO duty(start_date,end_date) VALUES('$start','$end'); SET @smoke_duty_id=LAST_INSERT_ID(); INSERT INTO duty_member(duty_id,account_id) VALUES(@smoke_duty_id,$temporaryId); SELECT @smoke_duty_id;")[0])
        $temporaryDuties.Add($id)
    }
    $blocked=Check 'USR-DUT-001' '대체 당번 없는 계정 삭제 거절' DELETE "/api/v1/system/users/$temporaryId" 422 $admin
    if(($blocked.Body|ConvertFrom-Json).error.code -ne 'DUTY_REPLACEMENT_REQUIRED'){throw 'Expected the replacement-required error code.'}
    $replacement=@{startDate=$from.ToString('yyyy-MM-dd');endDate=$through.ToString('yyyy-MM-dd');dutyUserId=$maintId}
    Check 'USR-DUT-002' '첫 일정 대체 보전 담당자 지정' PUT ("/api/v1/system/alarm-duty-schedules/"+$temporaryDuties[0]) 200 $admin $replacement|Out-Null
    Check 'USR-DUT-003' '남은 예정 당번 때문에 삭제 거절' DELETE "/api/v1/system/users/$temporaryId" 422 $admin|Out-Null
    $replacement.startDate=$through.AddDays(1).ToString('yyyy-MM-dd');$replacement.endDate=$through.AddDays(4).ToString('yyyy-MM-dd')
    Check 'USR-DUT-004' '다음 일정 대체 보전 담당자 지정' PUT ("/api/v1/system/alarm-duty-schedules/"+$temporaryDuties[1]) 200 $admin $replacement|Out-Null
    Check 'USR-DUT-005' '대체 담당자 지정 후 계정 삭제 성공' DELETE "/api/v1/system/users/$temporaryId" 204 $admin|Out-Null
    Check 'USR-DUT-006' '삭제된 계정의 일반 조회 제외' GET "/api/v1/system/users/$temporaryId" 404 $admin|Out-Null
} finally {
    if($temporaryDuties.Count){
        $ids=$temporaryDuties -join ','
        Invoke-ManualSql "DELETE FROM duty_member WHERE duty_id IN ($ids); DELETE FROM duty WHERE duty_id IN ($ids);"|Out-Null
    }
    if($temporaryId -gt 0){
        Invoke-ManualSql "UPDATE account SET deleted_at=COALESCE(deleted_at,NOW()),password_hash=NULL,telegram_chat_id=NULL,telegram_linked_at=NULL WHERE account_id=$temporaryId AND login_id='$temporaryLogin';"|Out-Null
    }
}
$passed=@($results|Where-Object result -eq PASS).Count
$failed=@($results|Where-Object result -eq FAIL).Count
$summary=[pscustomobject]@{executedAt=[DateTimeOffset]::Now.ToString('o');baseUrl='http://localhost:8081';database='stf_b_manual';swaggerOperations=$operationCount;passed=$passed;failed=$failed;cases=$results}
$summary|ConvertTo-Json -Depth 10|Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Host "Live HTTP checks: $passed passed, $failed failed. Results: $OutputPath"
if($failed){throw 'Live HTTP checks failed.'}
