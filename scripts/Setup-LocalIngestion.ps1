param([switch]$CopyKeyToClipboard)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$relative='secrets/node-red-local.properties'
$path=Join-Path $root $relative
& git -C $root check-ignore --quiet -- $relative
if($LASTEXITCODE -ne 0){throw 'The local ingestion key file must be ignored by Git.'}
$tracked=& git -C $root ls-files -- $relative
if($LASTEXITCODE -ne 0 -or $tracked){throw 'The local ingestion key file must not be tracked.'}
$key=$null
try {
    if(Test-Path -LiteralPath $path){
        $line=Get-Content -LiteralPath $path | Where-Object {$_ -match '^INTERNAL_API_KEY='} | Select-Object -First 1
        if($line){$key=$line.Substring('INTERNAL_API_KEY='.Length)}
        if([string]::IsNullOrWhiteSpace($key) -or [Text.Encoding]::UTF8.GetByteCount($key) -lt 32){throw 'The existing ingestion key is invalid; the file was preserved.'}
        Write-Host 'Preserved the existing Git-ignored ingestion key.'
    } else {
        if(-not [string]::IsNullOrWhiteSpace($env:INTERNAL_API_KEY)){
            $key=$env:INTERNAL_API_KEY
            if($key -notmatch '^[A-Za-z0-9_-]{32,}$'){throw 'The existing environment key must contain at least 32 ASCII letters, digits, underscores or hyphens.'}
        } else {
            $bytes=New-Object byte[] 32
            $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
            try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
            $key=([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
        }
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
        [IO.File]::WriteAllText($path,'INTERNAL_API_KEY='+$key+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
        Write-Host 'Created the ingestion key in secrets/node-red-local.properties. No key was printed.'
    }
    if(-not [string]::IsNullOrWhiteSpace($env:INTERNAL_API_KEY) -and $env:INTERNAL_API_KEY -cne $key){throw 'The process environment key differs from the local file; resolve the override before starting.'}
    if($CopyKeyToClipboard){Set-Clipboard -Value $key;Write-Host 'Copied the ingestion key. Share it privately with the Node-RED owner; do not paste it into Git or public documentation.'}
} finally {$key=$null}
