# Run from any directory. Credentials remain in the caller's environment.
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
    throw '请先在当前 PowerShell 会话设置 DB_USERNAME；本脚本不会跳过 MySQL 测试。'
}
if (-not (Test-Path Env:DB_PASSWORD)) {
    throw '请先在当前 PowerShell 会话设置 DB_PASSWORD。'
}
Get-Command mvn -ErrorAction Stop | Out-Null
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & mvn '-Dmaven.repo.local=../.cache/maven' '-Dtest=MysqlIntegrationTest' test
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL 集成测试失败，请查看 target/surefire-reports。'
    }
} finally {
    Pop-Location
}

