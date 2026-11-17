Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$ManualRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$ManualComposeArgs = @('--project-name','stf-back-b-manual','--env-file',(Join-Path $ManualRoot '.env.manual-test'),'-f',(Join-Path $ManualRoot 'compose.manual-test.yaml'))
function Read-ManualEnv {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath (Join-Path $ManualRoot '.env.manual-test')) {
        if ($line -match '^([A-Z_]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
    }
    return $values
}
function Invoke-ManualHttp([string]$Path, [string]$Token = '', [int]$TimeoutSeconds = 4) {
    Add-Type -AssemblyName System.Net.Http
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    try {
        if ($Token) { $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token) }
        $result = $client.GetAsync('http://127.0.0.1:8081' + $Path).GetAwaiter().GetResult()
        return [pscustomobject]@{ Status=[int]$result.StatusCode; Body=$result.Content.ReadAsStringAsync().GetAwaiter().GetResult() }
    } finally { $client.Dispose(); $handler.Dispose() }
}
function Invoke-ManualSql([string]$Sql) {
    $config = Read-ManualEnv
    $container = & docker compose @ManualComposeArgs ps -q mysql
    if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'Manual-test MySQL is not running.' }
    $previous = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $config.MANUAL_DB_PASSWORD
        $Sql | & docker exec -i --env MYSQL_PWD $container mysql --default-character-set=utf8mb4 -u stf_b_manual -N -B stf_b_manual
        if ($LASTEXITCODE -ne 0) { throw 'Manual-test SQL failed.' }
    } finally { $env:MYSQL_PWD = $previous }
}
