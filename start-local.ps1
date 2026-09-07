#requires -Version 7.2
[CmdletBinding()]
param([string]$ConfigPath = (Join-Path $PSScriptRoot 'local-config.ps1'), [switch]$NoBrowser)
. (Join-Path $PSScriptRoot 'scripts/LocalRuntime.ps1')
$lock = $null
$startedJava = $false
$startedNginx = $false
$javaRecordPath = Join-Path $PSScriptRoot '.local-runtime/backend.json'
$nginxRecordPath = Join-Path $PSScriptRoot '.local-runtime/nginx.json'
try {
    # 工具路径、数据库连接、登录凭据和固定 JWT 密钥来自本机私有配置，不写进命令行或 PID 文件。
    $config = Read-LocalConfig $ConfigPath
    $artifacts = Get-LocalArtifacts $PSScriptRoot $config
    $backendEnv = Get-BackendEnvironment $config
    $lock = Enter-RuntimeLock $PSScriptRoot
    $javaProcess = Get-OwnedProcess (Read-ProcessRecord $javaRecordPath)
    $nginxProcess = Get-OwnedProcess (Read-ProcessRecord $nginxRecordPath)
    if ($null -eq $javaProcess) { Assert-PortFree 8080 }
    if ($null -eq $nginxProcess) { Assert-PortFree 80 }
    $prefix = Write-NginxConfig $PSScriptRoot $artifacts
    Invoke-Nginx $artifacts.Nginx $prefix @('-t')
    if ($null -eq $javaProcess) {
        Write-Host '启动 Life1000 后端…'
        $marker = "-Dlife1000.local.instance=$([Guid]::NewGuid().ToString('N'))"
        $javaProcess = Invoke-IsolatedEnvironment $backendEnv {
            Start-Process -FilePath $artifacts.Java -ArgumentList @($marker,'-jar',('"'+$artifacts.Jar+'"'),
                '--spring.profiles.active=mysql','--server.address=127.0.0.1','--server.port=8080') `
                -WorkingDirectory (Join-Path $PSScriptRoot 'backend') -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput (Join-Path $PSScriptRoot '.local-runtime/backend-output.log') `
                -RedirectStandardError (Join-Path $PSScriptRoot '.local-runtime/backend-error.log')
        }
        Save-ProcessRecord $javaRecordPath $javaProcess $artifacts.Java $marker
        $startedJava = $true
    }
    # 进程存在不代表服务可用：通过原有登录和数据库健康接口确认就绪，不额外放开认证。
    Write-Host '等待后端与 MySQL 健康检查…'
    Wait-BackendHealthy $config (Read-ProcessRecord $javaRecordPath)
    if ($null -eq $nginxProcess) {
        # A dedicated prefix isolates this project's PID, logs and configuration.
        $nginxProcess = Invoke-IsolatedEnvironment @{} {
            Start-Process -FilePath $artifacts.Nginx -ArgumentList @('-p',('"'+$prefix+'"'),'-c','nginx.conf') `
                -WorkingDirectory (Join-Path $PSScriptRoot '.local-runtime/nginx') -WindowStyle Hidden -PassThru
        }
        Save-ProcessRecord $nginxRecordPath $nginxProcess $artifacts.Nginx $prefix
        $startedNginx = $true
        $deadline = [DateTime]::UtcNow.AddSeconds(10)
        $pidPath = Join-Path $prefix 'logs/nginx.pid'
        do {
            if ($nginxProcess.HasExited) { throw 'Nginx 启动失败，请检查 .local-runtime/nginx/logs/error.log。' }
            if ((Test-Path -LiteralPath $pidPath) -and [int](Get-Content -LiteralPath $pidPath -Raw) -eq $nginxProcess.Id) { break }
            Start-Sleep -Milliseconds 200
        } while ([DateTime]::UtcNow -lt $deadline)
        if (-not (Test-Path -LiteralPath $pidPath) -or [int](Get-Content -LiteralPath $pidPath -Raw) -ne $nginxProcess.Id) {
            throw '无法确认本项目 Nginx PID，请检查日志。'
        }
    } else {
        $pidPath = Join-Path $prefix 'logs/nginx.pid'
        if ([int](Get-Content -LiteralPath $pidPath -Raw) -ne $nginxProcess.Id) { throw 'Nginx PID 不一致，未 reload。' }
        Invoke-Nginx $artifacts.Nginx $prefix @('-s','reload')
    }
    # localhost 是浏览器入口；两端实际监听仍限定 127.0.0.1，不依赖自定义域名或 hosts 修改。
    $page = Invoke-WebRequest 'http://localhost/' -NoProxy -TimeoutSec 10
    if ($page.StatusCode -ne 200 -or -not $page.Content.Contains('<div id="app">')) {
        throw '首页静态文件检查失败，请检查 Nginx 配置和前端构建。'
    }
    Write-Host 'Life1000 已启动：http://localhost'
    if (-not $NoBrowser) { Start-Process 'http://localhost' }
} catch {
    Write-Host ("启动失败：{0}" -f $_.Exception.Message) -ForegroundColor Red
    # Roll back only processes created by this invocation; retain existing instances.
    try { if ($startedNginx) { Stop-OwnedNginx $nginxRecordPath $PSScriptRoot } } catch { Write-Warning $_.Exception.Message }
    try { if ($startedJava) { Stop-OwnedJava $javaRecordPath } } catch { Write-Warning $_.Exception.Message }
    exit 1
} finally { if ($null -ne $lock) { $lock.Dispose() } }
