# Uses the dedicated local test account. Credentials stay in process memory only.
param([string]$OutputPath)
. (Join-Path $PSScriptRoot 'Common.ps1')
if (-not $OutputPath) { $OutputPath=Join-Path $ManualRoot 'logs/manual-websocket-results.json' }
Add-Type -AssemblyName System.Net.WebSockets.Client
$handler=[Net.Http.HttpClientHandler]::new(); $handler.UseProxy=$false; $handler.UseCookies=$false
$http=[Net.Http.HttpClient]::new($handler)
$socket=[Net.WebSockets.ClientWebSocket]::new()
$timeout=[Threading.CancellationTokenSource]::new(30000)
function Send-Stomp([string]$Frame) {
    $bytes=[Text.Encoding]::UTF8.GetBytes($Frame+[char]0)
    $socket.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,$timeout.Token).GetAwaiter().GetResult() | Out-Null
}
function Receive-Stomp {
    $buffer=New-Object byte[] 8192
    $text=[Text.StringBuilder]::new()
    do {
        $received=$socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$timeout.Token).GetAwaiter().GetResult()
        if($received.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) { return [pscustomobject]@{closed=$true;frame=''} }
        [void]$text.Append([Text.Encoding]::UTF8.GetString($buffer,0,$received.Count))
    } while (-not $received.EndOfMessage)
    return [pscustomobject]@{closed=$false;frame=$text.ToString()}
}
try {
    $loginJson=& (Join-Path $PSScriptRoot 'Get-ManualTestLogin.ps1') -Role USER
    $content=[Net.Http.StringContent]::new($loginJson,[Text.Encoding]::UTF8,'application/json')
    $login=$http.PostAsync('http://127.0.0.1:8081/api/v1/auth/login',$content).GetAwaiter().GetResult()
    if([int]$login.StatusCode -ne 200){throw 'WebSocket test login failed; response redacted.'}
    $token=($login.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json).data.accessToken
    $cookie=(@($login.Headers.GetValues('Set-Cookie'))[0] -split ';',2)[0]
    $socket.Options.SetRequestHeader('Origin','http://localhost:5173')
    $socket.ConnectAsync([Uri]'ws://127.0.0.1:8081/ws',$timeout.Token).GetAwaiter().GetResult() | Out-Null
    Send-Stomp ("CONNECT`naccept-version:1.2`nhost:localhost`nheart-beat:0,0`nAuthorization:Bearer "+$token+"`n`n")
    $connected=Receive-Stomp
    if($connected.closed -or -not $connected.frame.StartsWith('CONNECTED')){throw 'STOMP authentication failed; frame redacted.'}
    # The simple broker does not provide STOMP receipts. Verify actual topic delivery instead.
    Send-Stomp "SUBSCRIBE`nid:inventory-test`ndestination:/topic/inventory`n`n"
    do {
        $event=Receive-Stomp
        if($event.closed){throw 'Subscription closed before receiving an inventory event.'}
    } while (-not ($event.frame.StartsWith('MESSAGE') -and $event.frame.Contains('destination:/topic/inventory')))
    $payload=($event.frame -split "`n`n",2)[1].TrimEnd([char]0)|ConvertFrom-Json
    if(-not $payload.eventId -or $null -eq $payload.quantity){throw 'Inventory event payload does not match the contract.'}
    $logout=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post,'http://127.0.0.1:8081/api/v1/auth/logout')
    $logout.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)
    $logout.Headers.Add('Cookie',$cookie)
    $response=$http.SendAsync($logout).GetAwaiter().GetResult()
    if([int]$response.StatusCode -ne 204){throw 'Logout failed in WebSocket test; response redacted.'}
    do { $closed=Receive-Stomp } while (-not $closed.closed)
    if(-not $closed.closed -or [int]$socket.CloseStatus -ne 1008){throw 'Expected revoked subscription to close with policy status 1008.'}
    $summary=[pscustomobject]@{executedAt=[DateTimeOffset]::Now.ToString('o');passed=4;failed=0;checks=@('authenticated STOMP CONNECT','inventory SUBSCRIBE and MESSAGE payload','HTTP logout 204','existing connection closed 1008');closeStatus=[int]$socket.CloseStatus}
    $summary|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $OutputPath -Encoding UTF8
    Write-Host "Live WebSocket checks: 4 passed, 0 failed. Results: $OutputPath"
} finally {
    $socket.Dispose(); $http.Dispose(); $handler.Dispose(); $timeout.Dispose()
}
