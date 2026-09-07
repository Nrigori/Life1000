#requires -Version 7.2
[CmdletBinding()]
param([string]$ConfigPath = (Join-Path $PSScriptRoot 'local-config.ps1'))
. (Join-Path $PSScriptRoot 'scripts/LocalRuntime.ps1')
$lock = $null
try {
    $config = Read-LocalConfig $ConfigPath
    $java = Require-File (Join-Path (Require-Value $config 'JAVA_HOME') 'bin/java.exe') '找不到 Java，请检查 JAVA_HOME。'
    $maven = Require-File (Join-Path (Require-Value $config 'MAVEN_HOME') 'bin/mvn.cmd') '找不到 Maven，请检查 MAVEN_HOME。'
    if ($null -eq (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw '找不到 npm，请安装 Node.js 并将 npm 加入 PATH。' }
    $lock = Enter-RuntimeLock $PSScriptRoot
    foreach ($name in @('backend','nginx')) {
        if ($null -ne (Get-OwnedProcess (Read-ProcessRecord (Join-Path $PSScriptRoot ".local-runtime/$name.json")))) {
            throw '请先运行 stop-local.ps1，再构建更新，避免覆盖正在使用的文件。'
        }
    }
    Invoke-IsolatedEnvironment @{JAVA_HOME=$config.JAVA_HOME} {
        Write-Host '构建前端生产文件（不传递数据库或登录配置）…'
        & npm.cmd --prefix (Join-Path $PSScriptRoot 'frontend') run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败，请检查上方输出。' }
        Write-Host '构建后端并运行自动化测试（真实 MySQL 测试另行显式运行）…'
        & $maven '-f' (Join-Path $PSScriptRoot 'backend/pom.xml') "-Dmaven.repo.local=$PSScriptRoot/.cache/maven" '--batch-mode' '-ntp' 'package'
        if ($LASTEXITCODE -ne 0) { throw '后端构建或测试失败，请检查上方输出。' }
    }
    [xml]$pom = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'backend/pom.xml') -Raw
    $null = Require-File (Join-Path $PSScriptRoot 'frontend/dist/index.html') '缺少前端构建产物。'
    $null = Require-File (Join-Path $PSScriptRoot "backend/target/$($pom.project.artifactId)-$($pom.project.version).jar") '缺少后端构建产物。'
    Write-Host '生产构建完成。运行 .\start-local.ps1 启动。'
} catch { Write-Host ("构建失败：{0}" -f $_.Exception.Message) -ForegroundColor Red; exit 1 }
finally { if ($null -ne $lock) { $lock.Dispose() } }
