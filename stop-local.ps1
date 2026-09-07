#requires -Version 7.2
[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'scripts/LocalRuntime.ps1')
$lock = $null
try {
    $lock = Enter-RuntimeLock $PSScriptRoot
    # No configuration/password needed to stop the recorded project processes.
    # 仅按已验证的项目进程记录停止；不按 Java/Nginx 名称批量终止，也不接管 MySQL。
    Stop-OwnedNginx (Join-Path $PSScriptRoot '.local-runtime/nginx.json') $PSScriptRoot
    Stop-OwnedJava (Join-Path $PSScriptRoot '.local-runtime/backend.json')
    Write-Host '本项目管理的 Nginx 和后端已停止。MySQL 和其他程序未停止。'
} catch { Write-Host ("停止失败：{0}" -f $_.Exception.Message) -ForegroundColor Red; exit 1 }
finally { if ($null -ne $lock) { $lock.Dispose() } }
