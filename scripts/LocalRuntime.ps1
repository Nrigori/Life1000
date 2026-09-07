# Shared local runtime helpers. No business configuration is changed.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-LocalConfig([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw '缺少本地配置。请复制 local-config.example.ps1 为 local-config.ps1 并填写私有配置。'
    }
    $config = & $Path
    if ($config -isnot [System.Collections.IDictionary]) { throw '本地配置必须返回一个 Hashtable（参考示例）。' }
    return $config
}

function Require-Value($Config, [string]$Key) {
    if (-not $Config.Contains($Key) -or [string]::IsNullOrWhiteSpace([string]$Config[$Key]) -or
        [string]$Config[$Key] -eq 'REPLACE_ME') { throw "请填写本地配置中的 $Key。" }
    return [string]$Config[$Key]
}

function Require-File([string]$Path, [string]$Message) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw $Message }
    return (Get-Item -LiteralPath $Path).FullName
}

function Get-LocalArtifacts([string]$Root, $Config) {
    $java = Require-File ([IO.Path]::Combine((Require-Value $Config 'JAVA_HOME'), 'bin/java.exe')) '找不到 Java，请检查 JAVA_HOME。'
    [xml]$pom = Get-Content -LiteralPath (Join-Path $Root 'backend/pom.xml') -Raw
    $jarName = "$($pom.project.artifactId)-$($pom.project.version).jar"
    $jar = Require-File (Join-Path $Root "backend/target/$jarName") '找不到后端 jar，请先运行 build-local.ps1。'
    $index = Require-File (Join-Path $Root 'frontend/dist/index.html') '找不到前端生产文件，请先运行 build-local.ps1。'
    $nginx = Require-File ([IO.Path]::Combine((Require-Value $Config 'NGINX_HOME'), 'nginx.exe')) '找不到 Nginx，请准备 Windows Nginx 并检查 NGINX_HOME。'
    $mime = Require-File (Join-Path (Split-Path $nginx) 'conf/mime.types') 'Nginx 缺少 conf/mime.types，请检查安装目录。'
    return @{ Java=$java; Jar=$jar; Index=$index; Nginx=$nginx; Mime=$mime }
}

function Get-BackendEnvironment($Config) {
    $result = @{}
    foreach ($key in @('DB_URL','DB_USERNAME','DB_PASSWORD','LIFE1000_USERNAME','LIFE1000_PASSWORD',
                      'LIFE1000_JWT_SECRET','LIFE1000_UPLOAD_DIRECTORY')) {
        $result[$key] = Require-Value $Config $key
    }
    try { $secret = [Convert]::FromBase64String($result.LIFE1000_JWT_SECRET) }
    catch { throw 'LIFE1000_JWT_SECRET 必须是 Base64 字符串，请按示例生成一次并保存在私有配置。' }
    if ($secret.Length -lt 32) { throw 'LIFE1000_JWT_SECRET 解码后至少需要 32 字节。' }
    if (-not [IO.Path]::IsPathFullyQualified($result.LIFE1000_UPLOAD_DIRECTORY)) {
        throw 'LIFE1000_UPLOAD_DIRECTORY 必须是已有附件目录的绝对路径。'
    }
    if (-not (Test-Path -LiteralPath $result.LIFE1000_UPLOAD_DIRECTORY -PathType Container)) {
        throw '附件目录不存在，请确认 LIFE1000_UPLOAD_DIRECTORY 指向原有附件目录，避免使用错误的空目录。'
    }
    return $result
}

# Strip inherited private application values from npm, Maven and Nginx. Real database
# tests are deliberately opt-in in a separate terminal with a dedicated test database.
function Invoke-IsolatedEnvironment($Values, [scriptblock]$Action) {
    $saved = @{}
    $names = @('DB_URL','DB_USERNAME','DB_PASSWORD','LIFE1000_USERNAME','LIFE1000_PASSWORD',
        'LIFE1000_JWT_SECRET','LIFE1000_UPLOAD_DIRECTORY','SPRING_APPLICATION_JSON',
        'SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_PROFILES_ACTIVE',
        'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')
    $names += @(Get-ChildItem Env: | Where-Object { $_.Name -match '^(DB_|LIFE1000_|SPRING_)' } | ForEach-Object Name)
    $names += @($Values.Keys)
    try {
        foreach ($name in ($names | Select-Object -Unique)) {
            $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            Remove-Item -LiteralPath ('Env:' + $name) -ErrorAction SilentlyContinue
        }
        foreach ($name in $Values.Keys) { [Environment]::SetEnvironmentVariable($name, [string]$Values[$name], 'Process') }
        & $Action
    } finally {
        foreach ($name in $saved.Keys) {
            if ($null -eq $saved[$name]) { Remove-Item -LiteralPath ('Env:' + $name) -ErrorAction SilentlyContinue }
            else { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
        }
    }
}

function Enter-RuntimeLock([string]$Root) {
    $directory = Join-Path $Root '.local-runtime'
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    try { return [IO.File]::Open((Join-Path $directory 'control.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw '另一个 Life1000 启动、停止或构建脚本正在运行，请稍后重试。' }
}

function Read-ProcessRecord([string]$Path) {
    if (Test-Path -LiteralPath $Path) { return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json }
    return $null
}

function Get-OwnedProcess($Record) {
    if ($null -eq $Record) { return $null }
    $process = Get-Process -Id ([int]$Record.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    $metadata = Get-CimInstance Win32_Process -Filter "ProcessId = $($Record.pid)"
    if ($null -eq $metadata -or $process.StartTime.ToUniversalTime().Ticks.ToString() -ne $Record.started -or
        $metadata.ExecutablePath -ine $Record.executable -or
        -not $metadata.CommandLine.Contains([string]$Record.marker)) {
        throw '进程身份与本项目 PID 记录不一致。为避免影响其他程序，已停止操作；请检查 .local-runtime 中的记录。'
    }
    return $process
}

function Save-ProcessRecord([string]$Path, $Process, [string]$Executable, [string]$Marker) {
    @{ pid=$Process.Id; started=$Process.StartTime.ToUniversalTime().Ticks.ToString();
       executable=$Executable; marker=$Marker } | ConvertTo-Json | Set-Content -LiteralPath $Path -Encoding utf8
}

function Assert-PortFree([int]$Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    $listener.Server.ExclusiveAddressUse = $true
    try { $listener.Start() }
    catch { throw "本机端口 $Port 已被占用。请先停止占用程序；脚本不会关闭其他 Java / Nginx。" }
    finally { $listener.Stop() }
}

function Convert-NginxPath([string]$Path) {
    # Quotes, dollars and line breaks have special meaning in nginx configuration.
    if ($Path -match '["\x00-\x1f\$]') { throw '项目或 Nginx 路径含有不支持的特殊字符，请使用普通 Windows 路径。' }
    return $Path.Replace('\','/')
}

function Write-NginxConfig([string]$Root, $Artifacts) {
    $prefix = Join-Path $Root '.local-runtime/nginx'
    foreach ($dir in @('logs','temp/client','temp/proxy')) {
        [IO.Directory]::CreateDirectory((Join-Path $prefix $dir)) | Out-Null
    }
    $text = Get-Content -LiteralPath (Join-Path $Root 'deploy/nginx/life1000.conf') -Raw
    $text = $text.Replace('__MIME_TYPES__', (Convert-NginxPath $Artifacts.Mime))
    $text = $text.Replace('__FRONTEND_DIST__', (Convert-NginxPath (Split-Path $Artifacts.Index)))
    [IO.File]::WriteAllText((Join-Path $prefix 'nginx.conf'), $text, [Text.UTF8Encoding]::new($false))
    return ((Convert-NginxPath $prefix).TrimEnd('/') + '/')
}

function Invoke-Nginx([string]$Executable, [string]$Prefix, [string[]]$Extra) {
    Invoke-IsolatedEnvironment @{} {
        # nginx writes even successful configuration checks to stderr.
        & $Executable '-p' $Prefix '-c' 'nginx.conf' @Extra
        if ($LASTEXITCODE -ne 0) { throw 'Nginx 执行失败，请检查上方信息和 .local-runtime/nginx/logs/error.log。' }
    }
}

function Stop-OwnedJava([string]$RecordPath) {
    $record = Read-ProcessRecord $RecordPath
    $process = Get-OwnedProcess $record
    if ($null -ne $process) {
        Stop-Process -Id $process.Id -ErrorAction Stop
        if (-not $process.WaitForExit(15000)) { throw '后端尚未停止，请检查进程后重试。' }
    }
    if (Test-Path -LiteralPath $RecordPath) { Remove-Item -LiteralPath $RecordPath }
}

function Stop-OwnedNginx([string]$RecordPath, [string]$Root) {
    $record = Read-ProcessRecord $RecordPath
    $process = Get-OwnedProcess $record
    if ($null -ne $process) {
        $prefix = ((Convert-NginxPath (Join-Path $Root '.local-runtime/nginx')).TrimEnd('/') + '/')
        $pidPath = Join-Path $prefix 'logs/nginx.pid'
        if (-not (Test-Path -LiteralPath $pidPath) -or [int](Get-Content -LiteralPath $pidPath -Raw) -ne $process.Id) {
            throw 'Nginx 内部 PID 与本项目记录不一致，未发送停止信号。'
        }
        Invoke-Nginx $record.executable $prefix @('-s','quit')
        if (-not $process.WaitForExit(15000)) {
            # Revalidate before the faster stop signal; never use taskkill /IM.
            $stillOwned = Get-OwnedProcess $record
            if ($null -ne $stillOwned) {
                Invoke-Nginx $record.executable $prefix @('-s','stop')
                if (-not $stillOwned.WaitForExit(10000)) { throw 'Nginx 尚未退出，请检查日志。' }
            }
        }
    }
    if (Test-Path -LiteralPath $RecordPath) { Remove-Item -LiteralPath $RecordPath }
}

function Wait-BackendHealthy($Config, $Record, [int]$TimeoutSeconds = 120) {
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(4)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    try {
        while ([DateTime]::UtcNow -lt $deadline) {
            if ($null -eq (Get-OwnedProcess $Record)) { throw '后端已退出，请检查 .local-runtime/backend-error.log 和 backend-output.log。' }
            $response = $null
            try {
                $body = @{username=$Config.LIFE1000_USERNAME;password=$Config.LIFE1000_PASSWORD} | ConvertTo-Json -Compress
                $content = [Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, 'application/json')
                try { $response = $client.PostAsync('http://127.0.0.1:8080/api/auth/login', $content).GetAwaiter().GetResult() }
                finally { $content.Dispose() }
            } catch { Start-Sleep -Seconds 1; continue }
            try {
                if ([int]$response.StatusCode -eq 401) { throw '后端登录失败，请检查 LIFE1000_USERNAME / LIFE1000_PASSWORD。' }
                if ($response.IsSuccessStatusCode) {
                    $auth = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                    $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $auth.accessToken)
                    $health = $client.GetAsync('http://127.0.0.1:8080/api/health/db').GetAwaiter().GetResult()
                    try {
                        if ($health.IsSuccessStatusCode -and
                            ($health.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json).status -eq 'UP') { return }
                    } finally { $health.Dispose() }
                }
            } finally { $response.Dispose() }
            Start-Sleep -Seconds 1
        }
        throw '等待后端数据库健康检查超时。请确认 MySQL 已启动、配置正确，并查看 .local-runtime 下的后端日志。'
    } finally { $client.Dispose(); $handler.Dispose() }
}
