param([switch]$StopDependencies)
. (Join-Path $PSScriptRoot 'Common.ps1')
$stateFile = Join-Path $ManualRoot 'secrets/manual-test-process.json'
if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $running = Get-Process -Id $state.ProcessId -ErrorAction SilentlyContinue
    if ($running) {
        $expected = if ($state.StartTimeUtc -is [datetime]) { $state.StartTimeUtc.ToUniversalTime() } else { [DateTimeOffset]::Parse($state.StartTimeUtc).UtcDateTime }
        if ($running.ProcessName -ne 'java' -or [Math]::Abs(($running.StartTime.ToUniversalTime()-$expected).TotalSeconds) -gt 2) { throw 'Process ownership changed. No process was stopped.' }
        $expectedJar=[IO.Path]::GetFullPath((Join-Path $ManualRoot 'logs/manual-test-runtime.jar'))
        if([IO.Path]::GetFullPath($state.Jar) -ne $expectedJar){throw 'Runtime JAR ownership changed. No process was stopped.'}
        Stop-Process -Id $running.Id -ErrorAction SilentlyContinue
        Wait-Process -Id $running.Id -Timeout 20 -ErrorAction SilentlyContinue
        Write-Host 'Stopped the manual-test server on 8081.'
    }
}
if ($StopDependencies) { & docker compose @ManualComposeArgs stop; if ($LASTEXITCODE -ne 0) { throw 'Dependency stop failed.' } }
Write-Host 'Test database data is retained.'
