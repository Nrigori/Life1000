#requires -Version 7.2
[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenHome = $env:MAVEN_HOME,
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'local-config.ps1')
)
. (Join-Path $PSScriptRoot 'scripts/LocalRuntime.ps1')
$lock = $null
try {
    # 本地配置仅用于找构建工具；秘密不会传给 npm、Cargo 或打包器。
    if (Test-Path -LiteralPath $ConfigPath) {
        $config = Read-LocalConfig $ConfigPath
        if (-not $JavaHome) { $JavaHome = $config.JAVA_HOME }
        if (-not $MavenHome) { $MavenHome = $config.MAVEN_HOME }
    }
    if (-not $JavaHome) { throw '请设置 JAVA_HOME 或传入 -JavaHome（Java 21 或更高版本）。' }
    $null = Require-File (Join-Path $JavaHome 'bin/java.exe') '找不到 Java，请检查 JavaHome。'
    $maven = if ($MavenHome) { Require-File (Join-Path $MavenHome 'bin/mvn.cmd') '找不到 Maven。' }
        else { (Get-Command mvn.cmd -ErrorAction Stop).Source }
    foreach ($tool in @('npm.cmd','cargo.exe')) {
        if ($null -eq (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "缺少 $tool，请按 docs/DESKTOP-RUN.md 安装构建工具。" }
    }
    $lock = Enter-RuntimeLock $PSScriptRoot
    if ($null -ne (Get-OwnedProcess (Read-ProcessRecord (Join-Path $PSScriptRoot '.local-runtime/backend.json')))) {
        throw 'Web 后端仍在运行，请先运行 stop-local.ps1，避免覆盖正在使用的 jar。'
    }
    Invoke-IsolatedEnvironment @{ JAVA_HOME=$JavaHome } {
        if (-not (Test-Path (Join-Path $PSScriptRoot 'frontend/node_modules'))) {
            & npm.cmd --prefix (Join-Path $PSScriptRoot 'frontend') ci
            if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败。' }
        }
        & npm.cmd --prefix (Join-Path $PSScriptRoot 'frontend') run build
        if ($LASTEXITCODE -ne 0) { throw '前端生产构建失败。' }
        & $maven -f (Join-Path $PSScriptRoot 'backend/pom.xml') "-Dmaven.repo.local=$PSScriptRoot/.cache/maven" --batch-mode -ntp verify
        if ($LASTEXITCODE -ne 0) { throw '后端测试或构建失败。请检查上方输出；若 jar 被占用，请先关闭正在运行的后端。' }
        [xml]$pom = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'backend/pom.xml') -Raw
        $jar = Require-File (Join-Path $PSScriptRoot "backend/target/$($pom.project.artifactId)-$($pom.project.version).jar") '后端 jar 缺失。'
        $null = Require-File (Join-Path $PSScriptRoot 'frontend/dist/index.html') '前端产物缺失。'
        $resources = Join-Path $PSScriptRoot 'desktop/src-tauri/resources'
        [IO.Directory]::CreateDirectory($resources) | Out-Null
        Copy-Item -LiteralPath $jar -Destination (Join-Path $resources 'backend.jar') -Force
        & npm.cmd --prefix (Join-Path $PSScriptRoot 'desktop') ci
        if ($LASTEXITCODE -ne 0) { throw 'Tauri 构建工具安装失败。' }
        Push-Location (Join-Path $PSScriptRoot 'desktop')
        try {
            & cargo.exe test --locked --manifest-path src-tauri/Cargo.toml
            if ($LASTEXITCODE -ne 0) { throw '桌面运行时测试失败，请检查 MSVC / Windows SDK / Rust 安装。' }
            & npm.cmd run build
            if ($LASTEXITCODE -ne 0) { throw 'Tauri 构建失败，请检查上方输出及 docs/DESKTOP-RUN.md。' }
        } finally { Pop-Location }
    }
    $null = Require-File (Join-Path $PSScriptRoot 'desktop/src-tauri/target/release/Life1000.exe') '桌面 exe 未生成。'
    $installers = @(Get-ChildItem (Join-Path $PSScriptRoot 'desktop/src-tauri/target/release/bundle/nsis') -Filter '*.exe')
    if ($installers.Count -eq 0) { throw 'NSIS 安装包未生成。' }
    Write-Host '桌面构建完成。安装包：'
    $installers.FullName | Write-Host
    Write-Host '首次运行前请按 docs/DESKTOP-RUN.md 准备私有配置；构建不会启动 MySQL、Nginx 或浏览器。'
} catch { Write-Host ("桌面构建失败：{0}" -f $_.Exception.Message) -ForegroundColor Red; exit 1 }
finally { if ($null -ne $lock) { $lock.Dispose() } }
