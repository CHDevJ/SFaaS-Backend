param(
    [string]$Version='7.0.0',
    [string]$Label='전체 회귀',
    [string]$Command='.\scripts\manual-test\Run-Tests.ps1',
    [int]$BuildExitCode=0,
    [datetime]$StartedAfter=[datetime]::MinValue
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$file=Join-Path $root 'docs/test-reports/test-run-history.json'
$cases=[Collections.Generic.List[object]]::new()
$suites=@(Get-ChildItem -LiteralPath (Join-Path $root 'build/test-results/test') -Filter 'TEST-*.xml' | Where-Object LastWriteTime -ge $StartedAfter | Sort-Object Name)
if($suites.Count -eq 0){Write-Warning 'No new JUnit XML results. No test run was recorded.';return}
$stamps=[Collections.Generic.List[string]]::new()
foreach($suite in $suites){
    [xml]$xml=Get-Content -Raw -LiteralPath $suite.FullName
    $stamps.Add([string]$xml.testsuite.timestamp)
    foreach($case in $xml.testsuite.SelectNodes('testcase')){
        $failure=$case.SelectSingleNode('failure');$error=$case.SelectSingleNode('error');$skip=$case.SelectSingleNode('skipped')
        $result=if($failure -or $error){'FAIL'}elseif($skip){'SKIP'}else{'PASS'}
        $cases.Add([ordered]@{class=[string]$case.classname;method=[string]$case.name;seconds=[double]::Parse([string]$case.time,[Globalization.CultureInfo]::InvariantCulture);result=$result;failureType=$(if($failure){$failure.GetAttribute('type')}elseif($error){$error.GetAttribute('type')}else{''})})
    }
}
$identity=$Version+'|'+(($stamps|Sort-Object -Unique)-join '|')+'|'+(($cases|ForEach-Object{$_.class+'#'+$_.method+'='+$_.result})-join '|')
$hash=[Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($identity))
$runId='RUN-'+[Convert]::ToHexString($hash).Substring(0,12)
$history=if(Test-Path -LiteralPath $file){Get-Content -Raw -LiteralPath $file|ConvertFrom-Json}else{[pscustomobject]@{formatVersion=1;runs=@()}}
if(@($history.runs|Where-Object id -eq $runId).Count){Write-Host "Already recorded: $runId";return}
$run=[ordered]@{id=$runId;version=$Version;label=$Label;recordedAt=[DateTimeOffset]::Now.ToString('o');suiteTimestamps=@($stamps|Sort-Object -Unique);command=$Command;buildExitCode=$BuildExitCode;classes=$suites.Count;cases=@($cases);note='JUnit XML 판정. 요청 본문·인증값·실패 스택 원문 제외.'}
$history.runs=@($history.runs)+$run
[IO.File]::WriteAllText($file,($history|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
Write-Host "Accumulated: $runId / $($cases.Count) tests"
