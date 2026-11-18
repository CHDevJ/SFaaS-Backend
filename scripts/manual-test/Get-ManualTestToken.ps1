param(
    [ValidateSet('ADMIN','USER')][string]$Role='ADMIN',
    [switch]$CopyToClipboard
)
. (Join-Path $PSScriptRoot 'Common.ps1')
$body=& (Join-Path $PSScriptRoot 'Get-ManualTestLogin.ps1') -Role $Role
$handler=[Net.Http.HttpClientHandler]::new();$handler.UseProxy=$false
$client=[Net.Http.HttpClient]::new($handler);$client.Timeout=[TimeSpan]::FromSeconds(10)
try {
    $content=[Net.Http.StringContent]::new($body,[Text.Encoding]::UTF8,'application/json')
    $response=$client.PostAsync('http://127.0.0.1:8081/api/v1/auth/login',$content).GetAwaiter().GetResult()
    if([int]$response.StatusCode -ne 200){throw "Manual login failed (HTTP $([int]$response.StatusCode)). Check the local-only account settings."}
    $data=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json
    $token=$data.data.accessToken
    if(-not $token){throw 'Login did not return an access token.'}
    if($CopyToClipboard){Set-Clipboard -Value $token;Write-Host "Copied a 30-minute $Role test token. Paste into Swagger Authorize."}
    else{Write-Output $token}
} finally {$client.Dispose();$handler.Dispose()}
