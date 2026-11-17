param([switch]$SkipBuild)
. (Join-Path $PSScriptRoot 'Common.ps1')
$previousDirectory = Get-Location
try {
    Set-Location -LiteralPath $ManualRoot
    New-Item -ItemType Directory -Path secrets,logs -Force | Out-Null
    if (-not (Test-Path -LiteralPath '.env.manual-test')) {
        function New-ManualSecret { $bytes = New-Object byte[] 48; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); try { $rng.GetBytes($bytes); return [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() } }
        $values = @{ MANUAL_DB_PASSWORD=(New-ManualSecret); MANUAL_DB_ROOT_PASSWORD=(New-ManualSecret); JWT_SECRET=(New-ManualSecret) }
        $content = ($values.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value }) -join [Environment]::NewLine
        [IO.File]::WriteAllText((Join-Path $ManualRoot '.env.manual-test'),$content,[Text.UTF8Encoding]::new($false))
    }
    $config = Read-ManualEnv
    if (-not $config.ContainsKey('MANUAL_LOGIN_PASSWORD')) {
        $loginPassword='T7!'+[guid]::NewGuid().ToString('N')
        [IO.File]::AppendAllText((Join-Path $ManualRoot '.env.manual-test'),[Environment]::NewLine+'MANUAL_LOGIN_PASSWORD='+$loginPassword,[Text.UTF8Encoding]::new($false))
        $config=Read-ManualEnv
    }
    $properties = "spring.datasource.password="+$config.MANUAL_DB_PASSWORD+"
JWT_SECRET="+$config.JWT_SECRET+"
JWT_ISSUER=stf-manual-test
MANUAL_LOGIN_PASSWORD="+$config.MANUAL_LOGIN_PASSWORD+"
"
    [IO.File]::WriteAllText((Join-Path $ManualRoot 'secrets/manual-test.properties'),$properties,[Text.UTF8Encoding]::new($false))
    & docker compose @ManualComposeArgs up -d --wait --wait-timeout 150
    if ($LASTEXITCODE -ne 0) { throw 'Dedicated MySQL/Redis startup failed.' }
    $alreadyRunning = $false
    try { $health = Invoke-ManualHttp '/actuator/health'; $alreadyRunning = $health.Status -eq 200 } catch { }
    if (-not $alreadyRunning) {
        $listener = Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue
        if ($listener) { throw 'Port 8081 is occupied. Existing process was not stopped.' }
        if (-not $SkipBuild) {
            Write-Host 'Building the existing local Spring Boot project...'
            & .\gradlew.bat bootJar --no-daemon *> logs/manual-test-build.log
            if ($LASTEXITCODE -ne 0) { throw 'Build failed. See logs/manual-test-build.log.' }
        }
        $jar = Get-ChildItem -LiteralPath build/libs -Filter '*.jar' | Where-Object Name -NotLike '*-plain.jar' | Select-Object -First 1
        if (-not $jar) { throw 'Application JAR is missing.' }
        $runtimeJar = Join-Path $ManualRoot 'logs/manual-test-runtime.jar'
        Copy-Item -LiteralPath $jar.FullName -Destination $runtimeJar -Force
        $jar = Get-Item -LiteralPath $runtimeJar
        $jdk = Get-ChildItem -LiteralPath 'C:\Program Files\Java' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
        $javaCommand = if ($jdk) { Join-Path $jdk.FullName 'bin/java.exe' } else { (Get-Command java.exe).Source }
        $process = Start-Process -FilePath $javaCommand -ArgumentList @('-jar',('"'+$jar.FullName+'"'),'--spring.profiles.active=backend-b,manual-test') -WorkingDirectory $ManualRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $ManualRoot 'logs/manual-test-server.log') -RedirectStandardError (Join-Path $ManualRoot 'logs/manual-test-server-error.log') -PassThru
        @{ ProcessId=$process.Id; StartTimeUtc=$process.StartTime.ToUniversalTime().ToString('o'); Jar=$jar.FullName } | ConvertTo-Json | Set-Content -LiteralPath secrets/manual-test-process.json -Encoding UTF8
        $ready = $false
        for ($attempt=0; $attempt -lt 120; $attempt++) {
            if ($process.HasExited) { throw 'Server exited. See logs/manual-test-server.log.' }
            try { if ((Invoke-ManualHttp '/actuator/health').Status -eq 200) { $ready=$true; break } } catch { }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw 'Server readiness timed out. See logs/manual-test-server.log.' }
    }
    $marker = @(Invoke-ManualSql "SELECT COUNT(*) FROM part WHERE manage_no='MANUAL-DEMO-001';")[0]
    if ([int]$marker -eq 0) {
        Write-Host 'Adding sample alarms, parts and measurements to stf_b_manual only...'
        $sql = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'demo-data.sql') -Raw
        Invoke-ManualSql $sql | Out-Null
    }
    $docs = Invoke-ManualHttp '/v3/api-docs'
    if ($docs.Status -ne 200) { throw 'Swagger schema failed. See server log.' }
    $token = & (Join-Path $PSScriptRoot 'Get-ManualTestToken.ps1')
    $check = Invoke-ManualHttp '/api/v1/overview/equipment-layout' $token
    if ($check.Status -ne 200) { throw "Authenticated API failed: $($check.Status)." }
    Write-Host 'READY: http://localhost:8081/swagger-ui.html'
    Write-Host 'Token: .\scripts\manual-test\Get-ManualTestToken.ps1 -CopyToClipboard'
    Write-Host 'Dedicated DB: 127.0.0.1:3308/stf_b_manual; Redis: 127.0.0.1:6380'
} finally { Set-Location -LiteralPath $previousDirectory }
