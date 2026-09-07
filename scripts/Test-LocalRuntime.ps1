#requires -Version 7.2
# No MySQL, Nginx installation or private configuration required.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
. (Join-Path $PSScriptRoot 'LocalRuntime.ps1')
$count = 0
function Assert-True([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:count++
    Write-Host "PASS: $Name"
}
function Assert-Failure([scriptblock]$Action, [string]$Expected, [string]$Name) {
    $message = ''
    try { & $Action | Out-Null } catch { $message = $_.Exception.Message }
    Assert-True ($message.Contains($Expected)) $Name
}
$fixtureRoot = Join-Path $root ('.cache/runtime-tests/' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
foreach ($file in @('java/bin/java.exe','nginx/nginx.exe','nginx/conf/mime.types','backend/pom.xml',
                    'frontend/dist/index.html','deploy/nginx/life1000.conf')) {
    $path = Join-Path $fixtureRoot $file
    [IO.Directory]::CreateDirectory((Split-Path $path)) | Out-Null
    [IO.File]::WriteAllText($path, '')
}
Copy-Item -LiteralPath (Join-Path $root 'backend/pom.xml') -Destination (Join-Path $fixtureRoot 'backend/pom.xml')
Copy-Item -LiteralPath (Join-Path $root 'deploy/nginx/life1000.conf') -Destination (Join-Path $fixtureRoot 'deploy/nginx/life1000.conf')
$config = @{ JAVA_HOME=(Join-Path $fixtureRoot 'java'); NGINX_HOME=(Join-Path $fixtureRoot 'nginx') }
Assert-Failure { Read-LocalConfig (Join-Path $fixtureRoot 'absent.ps1') } '缺少本地配置' 'missing configuration'
Assert-Failure { Get-LocalArtifacts $fixtureRoot @{JAVA_HOME='X:/not-installed'} } '找不到 Java' 'missing Java'
Assert-Failure { Get-LocalArtifacts $fixtureRoot $config } '找不到后端 jar' 'missing jar'
[xml]$pom = Get-Content (Join-Path $fixtureRoot 'backend/pom.xml') -Raw
$jarPath = Join-Path $fixtureRoot "backend/target/$($pom.project.artifactId)-$($pom.project.version).jar"
[IO.Directory]::CreateDirectory((Split-Path $jarPath)) | Out-Null
[IO.File]::WriteAllText($jarPath,'fixture only')
$badNginx = $config.Clone(); $badNginx.NGINX_HOME = Join-Path $fixtureRoot 'missing'
Assert-Failure { Get-LocalArtifacts $fixtureRoot $badNginx } '找不到 Nginx' 'missing Nginx'
$artifacts = Get-LocalArtifacts $fixtureRoot $config
Assert-True ($artifacts.Jar -eq $jarPath) 'exact pom artifact, no wildcard ambiguity'
$prefix = Write-NginxConfig $fixtureRoot $artifacts
$nginxText = Get-Content (Join-Path $prefix 'nginx.conf') -Raw
Assert-True (-not $nginxText.Contains('__')) 'nginx template rendered'
Assert-True ($nginxText.Contains('listen 127.0.0.1:80;')) 'loopback-only nginx'
Assert-True ($nginxText.Contains('proxy_pass http://127.0.0.1:8080;')) 'API prefix preserved'
Assert-True ($nginxText.Contains('try_files $uri $uri/ /index.html;')) 'SPA fallback'
Assert-True ($nginxText.Contains('client_max_body_size 51m;')) 'multipart upload limit'
Assert-True (-not $nginxText.Contains('alias ')) 'no uploads alias'
Assert-Failure { Convert-NginxPath 'D:/bad$path' } '特殊字符' 'nginx path interpolation rejected'
Assert-True ((Convert-NginxPath 'D:\My Project\frontend\dist') -eq 'D:/My Project/frontend/dist') 'Windows paths and spaces'
Assert-Failure { Get-BackendEnvironment @{} } 'DB_URL' 'required configuration error'
$private = @{
    DB_URL='jdbc:mysql://127.0.0.1:3306/life1000_test'
    DB_USERNAME='fixture'; DB_PASSWORD='fixture'; LIFE1000_USERNAME='fixture'; LIFE1000_PASSWORD='fixture'
    LIFE1000_JWT_SECRET=[Convert]::ToBase64String([byte[]]::new(32))
    LIFE1000_UPLOAD_DIRECTORY=$fixtureRoot
}
$values = Get-BackendEnvironment $private
Assert-True ($values.Count -eq 7) 'backend environment allowlist'
$invalid = $private.Clone(); $invalid.LIFE1000_JWT_SECRET='invalid'
Assert-Failure { Get-BackendEnvironment $invalid } 'Base64' 'invalid JWT rejected'
$invalid.LIFE1000_JWT_SECRET=[Convert]::ToBase64String([byte[]]::new(8))
Assert-Failure { Get-BackendEnvironment $invalid } '32 字节' 'short JWT rejected'
$invalid = $private.Clone(); $invalid.LIFE1000_UPLOAD_DIRECTORY='uploads'
Assert-Failure { Get-BackendEnvironment $invalid } '绝对路径' 'relative uploads rejected'
$oldPassword = $env:DB_PASSWORD
try {
    $env:DB_PASSWORD='test-only-private-sentinel'
    Invoke-IsolatedEnvironment @{} {
        Assert-True (-not (Test-Path Env:DB_PASSWORD)) 'build children do not inherit DB password'
        Assert-True (-not (Test-Path Env:SPRING_CONFIG_LOCATION)) 'Spring config override is absent, not an empty value'
    }
    Assert-True ($env:DB_PASSWORD -eq 'test-only-private-sentinel') 'parent environment restored'
    try { Invoke-IsolatedEnvironment @{} { throw 'expected' } } catch {}
    Assert-True ($env:DB_PASSWORD -eq 'test-only-private-sentinel') 'environment restored after failure'
} finally { $env:DB_PASSWORD=$oldPassword }
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start()
try { Assert-Failure { Assert-PortFree $listener.LocalEndpoint.Port } '已被占用' 'occupied port rejected without stopping owner' }
finally { $listener.Stop() }
Assert-True ($null -eq (Get-OwnedProcess $null)) 'no PID record is safe'
$current = Get-Process -Id $PID
$wrongRecord = @{pid=$PID;started='0';executable='incorrect';marker='incorrect'}
Assert-Failure { Get-OwnedProcess $wrongRecord } '身份' 'reused or unrelated PID rejected'
Assert-True (-not $current.HasExited) 'unrelated process remains alive'
$lock = Enter-RuntimeLock $fixtureRoot
try { Assert-Failure { Enter-RuntimeLock $fixtureRoot } '正在运行' 'concurrent scripts rejected' }
finally { $lock.Dispose() }
foreach ($file in @('build-local.ps1','start-local.ps1','stop-local.ps1','local-config.example.ps1','scripts/LocalRuntime.ps1','scripts/Test-LocalRuntime.ps1')) {
    $tokens=$null; $errors=$null
    [Management.Automation.Language.Parser]::ParseFile((Join-Path $root $file),[ref]$tokens,[ref]$errors) | Out-Null
    Assert-True ($errors.Count -eq 0) "PowerShell syntax: $file"
}

# Actual Windows child processes verify that stopping one owned process does not stop another.
$pwsh = (Get-Process -Id $PID).Path
$childScript = Join-Path $fixtureRoot 'wait.ps1'
[IO.File]::WriteAllText($childScript, 'param([string]$Marker) Start-Sleep -Seconds 45')
$recordPaths = @()
try {
    $children = @()
    foreach ($name in @('owned','other')) {
        $marker = 'runtime-fixture-' + [Guid]::NewGuid().ToString('N')
        $process = Start-Process -FilePath $pwsh -ArgumentList @('-NoProfile','-File',('"'+$childScript+'"'),'-Marker',$marker) -WindowStyle Hidden -PassThru
        $recordPath = Join-Path $fixtureRoot "$name.json"
        Save-ProcessRecord $recordPath $process $pwsh $marker
        $recordPaths += $recordPath
        $children += $process
    }
    Assert-True ($null -ne (Get-OwnedProcess (Read-ProcessRecord $recordPaths[0]))) 'actual child identity verified'
    Stop-OwnedJava $recordPaths[0]
    Assert-True ($children[0].HasExited) 'owned child stopped'
    Assert-True (-not $children[1].HasExited) 'separate child not stopped'
    Stop-OwnedJava $recordPaths[0]
    Assert-True (-not (Test-Path -LiteralPath $recordPaths[0])) 'repeat stop without record is safe'
} finally {
    foreach ($recordPath in $recordPaths) { Stop-OwnedJava $recordPath }
}
# Execute entry points in a fresh process so exit codes are observable.
$output = & $pwsh -NoProfile -File (Join-Path $root 'start-local.ps1') -ConfigPath (Join-Path $fixtureRoot 'absent.ps1') 2>&1
Assert-True ($LASTEXITCODE -eq 1 -and ($output -join ' ').Contains('缺少本地配置')) 'start entry point missing config exit code'
$output = & $pwsh -NoProfile -File (Join-Path $root 'build-local.ps1') -ConfigPath (Join-Path $fixtureRoot 'absent.ps1') 2>&1
Assert-True ($LASTEXITCODE -eq 1 -and ($output -join ' ').Contains('缺少本地配置')) 'build entry point missing config exit code'

Write-Host "$count local runtime checks passed. No real Nginx/MySQL chain was used."
