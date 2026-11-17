. (Join-Path $PSScriptRoot 'Common.ps1')
$config = Read-ManualEnv
$container = & docker compose @ManualComposeArgs ps -q mysql
if (-not $container) { throw 'Run Start-ManualTest.ps1 first.' }
$previousDirectory = Get-Location
$oldUrl=$env:STF_TEST_MYSQL_URL; $oldPassword=$env:STF_TEST_MYSQL_PASSWORD; $oldMysql=$env:MYSQL_PWD
try {
    $env:MYSQL_PWD=$config.MANUAL_DB_ROOT_PASSWORD
    & docker exec --env MYSQL_PWD $container mysql -u root -e 'CREATE DATABASE IF NOT EXISTS stf_b_test'
    if ($LASTEXITCODE -ne 0) { throw 'Test database creation failed.' }
    $env:STF_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3308/stf_b_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul'
    $env:STF_TEST_MYSQL_PASSWORD=$config.MANUAL_DB_ROOT_PASSWORD
    Set-Location -LiteralPath $ManualRoot
    & .\gradlew.bat clean test --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Gradle tests failed.' }
} finally { $env:STF_TEST_MYSQL_URL=$oldUrl; $env:STF_TEST_MYSQL_PASSWORD=$oldPassword; $env:MYSQL_PWD=$oldMysql; Set-Location -LiteralPath $previousDirectory }
