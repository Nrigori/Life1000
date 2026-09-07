# Windows 本地长期运行

本次只补运行工程：Nginx + Vue production build + Spring Boot jar + 现有 MySQL。不修改 PRD、业务代码、数据库结构或认证语义。

浏览器入口为 **http://life1000.test**：

- Nginx 只监听 127.0.0.1:80，直接提供 frontend/dist。
- /api/ 原样代理到 127.0.0.1:8080；Spring Boot 仍只绑定回环地址。
- history 路由通过 try_files 回退 index.html，详情页与 /timeline 等地址可以直接刷新。
- 附件仍经 JWT API 读取；Nginx 不公开 uploads，不代理 MySQL。
- 无 Vite dev server、HTTPS、公网部署、Docker 或 Windows Service。

## 首次准备

1. 准备 **PowerShell 7.2+、Java 21、Node.js 22.12+、Maven、MySQL**。使用 PowerShell 7 的终端（pwsh），不是 Windows PowerShell 5.1。MySQL 继续由原有方式启动；不新建或清空数据库。
2. 从 [Nginx 官方下载页](https://nginx.org/en/download.html) 手工下载 Windows 版本并解压，例如 D:\nginx。目录中应有 nginx.exe 和 conf/mime.types。脚本不会下载安装 Nginx，也不使用或覆盖安装目录中的 nginx.conf。可参考 [Windows 官方说明](https://nginx.org/en/docs/windows.html)。
3. 以管理员身份打开文本编辑器，编辑 C:\Windows\System32\drivers\etc\hosts，添加一行：

   ```text
   127.0.0.1 life1000.test
   ```

   hosts 不带端口或 http://。移除这个名称的其他冲突映射。启动脚本只检查解析结果，**不会修改 hosts**。通常仅这一步需要管理员权限；其余在普通终端运行。
4. 在项目根目录复制模板：

   ```powershell
   Copy-Item .\local-config.example.ps1 .\local-config.ps1
   notepad .\local-config.ps1
   ```

5. 填写本地工具目录、原有 MySQL 连接、登录账号密码、固定 JWT Secret、上传目录和 NGINX_HOME。该文件返回 Hashtable，请保留模板结构；不要改成写入全局环境变量。密码中的单引号在 PowerShell 单引号字符串中写成两个单引号。该文件属于本机可信脚本，不运行来源不明的配置。
6. **LIFE1000_UPLOAD_DIRECTORY 必须指向已验收的原有附件绝对目录**，例如 D:\code\myself\Life1000\backend\uploads。以实际环境为准；不要为了启动另建空目录或移动附件。启动脚本会拒绝不存在的目录。Java、项目和 Nginx 路径可以含空格；Nginx 配置路径不接受美元符号、双引号、换行。
7. 若尚未安装前端依赖，执行 npm --prefix frontend ci。npm 必须在 PATH 中；Maven 只在构建时需要，日常启动不需要 Maven 或 Node.js。

所有可提交模板只有占位值。local-config.ps1、.local-runtime/、构建产物、缓存及用户附件都不纳入 Git。私有配置含明文凭据，只保存在本机，不发送到聊天或共享仓库；不要把它复制进 frontend、frontend/.env 或 dist。

### 固定 JWT Secret

在 PowerShell 7 运行一次：

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

将生成值填入私有配置 LIFE1000_JWT_SECRET。不要每次启动重新生成。若已有安全的固定 Secret，可以继续使用。后端重启后，仍在有效期内的 Token 不会仅因 Secret 随机变化失效；原有过期时间与浏览器登录存储规则不变。

## 构建、启动与停止

在项目根目录运行：

```powershell
.\build-local.ps1
.\start-local.ps1
# 浏览器自动打开 http://life1000.test
.\stop-local.ps1
```

如系统执行策略不允许运行自己审阅过的本地脚本，可以在当前 PowerShell 会话设置：
`Set-ExecutionPolicy -Scope Process RemoteSigned`。不需要改变系统范围策略。

build-local.ps1 依次执行 npm run build 和 Maven package（运行测试，不跳过测试），检查：

- frontend/dist/index.html
- backend/target/life1000-backend-<pom版本>.jar

Maven 使用项目 .cache/maven 缓存。构建时只读取工具路径，不要求数据库凭据；临时移除 DB_*、LIFE1000_*、Spring 配置覆盖等继承变量，避免前端接收私密配置、避免构建误触真实数据库测试。脚本完成或失败后恢复调用进程环境。没有配置文件时会明确提示复制模板。开发依赖缺失会由 npm 明确报错，请先 npm ci。

start-local.ps1 会检查配置、Java、jar、dist、Nginx、JWT Secret、原有附件目录和 hosts，然后：

1. 检查端口占用与本项目进程身份；不会接管其他 8080/80 服务。
2. 从 deploy/nginx/life1000.conf 模板生成项目专用配置，替换绝对路径并执行 nginx -t。
3. 启动后台 Java，工作目录为 backend。仅子进程获得私有配置；命令行不含密码或 Secret，强制 mysql profile、127.0.0.1:8080。
4. 使用私有账号登录 /api/auth/login，在内存取得 JWT，再请求 /api/health/db，最长等待约 120 秒。不放开健康检查认证，不把 JWT 放入 URL 或 PID 文件。
5. 启动本项目 Nginx，或验证身份后 reload；检查首页静态响应后打开默认浏览器。

重复运行 start 不重复启动后端。修改后端私有配置后，先 stop 再 start 才会应用新值。可用 `-NoBrowser` 关闭自动开浏览器，便于终端检查。

stop-local.ps1 不需要读取密码。它核对 **PID、创建时间、可执行文件路径、项目命令行标识**；Nginx 还核对专属 nginx.pid。先向本项目 Nginx 发送 quit，超时才发送 stop，再停止本项目 Java。不会按程序名批量杀进程，也不会停止 MySQL。停止 Java 使用 Windows 进程终止，因此请在上传、保存、备份请求结束后停止；不要在写入过程中更新构建。

更新前先 stop，再 build、start，避免覆盖正在使用的 jar/dist。若启动中途失败，只回收这一次新启动的受管进程，不关闭原有实例。并发启停/构建由项目内文件锁阻止。

## 配置、日志与排错

生成目录（全部 Git 忽略）：

```text
.local-runtime/
├─ control.lock
├─ backend.json                 # PID/创建时间/程序路径/实例标识，无凭据
├─ nginx.json
├─ backend-output.log
├─ backend-error.log
└─ nginx/
   ├─ nginx.conf                 # 已替换绝对路径的实际配置，无凭据
   ├─ logs/nginx.pid
   ├─ logs/access.log
   ├─ logs/error.log
   └─ temp/
```

日志用于本机排错，不上传完整日志或私有配置。Nginx 日志会累积；长期使用可在停止后清理旧日志。启动后端会重新写本次后端日志。

| 情况 | 处理 |
| --- | --- |
| 缺少 local-config.ps1 | 复制模板并填写；不需要在每个终端设置密码 |
| 找不到 Java / Maven / Nginx | 检查相应 HOME 是否是工具根目录，不是 bin 目录 |
| 找不到 jar / dist | 先运行 build-local.ps1；构建失败不应继续启动旧产物 |
| 80 / 8080 占用 | 自行停止原开发服务器或其他服务；脚本不会杀掉它们 |
| hosts 解析不正确 | 手工添加回环映射，检查重复条目；浏览器禁用此本地域名的代理 |
| Nginx -t 失败 | 查看错误日志与实际生成配置；确认 mime.types、dist、目录访问权限 |
| 后端退出或数据库健康超时 | 查看后端日志；确认 MySQL、DB_URL、账号权限与密码、上传目录 |
| PID 身份不一致 | 不继续发停止信号；核对对应 PID 实际程序。确认不再有本项目实例后才可手工移走旧 JSON 记录，勿仅凭进程名杀进程 |
| 浏览器无法访问 | 检查系统/浏览器代理绕过 life1000.test 和 127.0.0.1；检查 hosts、端口和 Nginx 日志 |
| 配置或日志含用户私密信息 | 留在本机；不要纳入提交 |

实际 Nginx 配置可独立检查（在成功生成配置后）：

```powershell
$c = & .\local-config.ps1
$prefix = ((Join-Path $PWD '.local-runtime/nginx').Replace('\','/') + '/')
& (Join-Path $c.NGINX_HOME 'nginx.exe') -p $prefix -c nginx.conf -t
```

仓库中的 life1000.conf 是带路径占位符的模板，不能直接当实际配置使用。代理不剥离 /api 前缀；51 MB 请求上限兼容原有 50 MB 文件限制及 multipart 开销；600 秒读写超时兼容完整备份。没有任何 uploads 静态映射。Nginx 参数行为参考 [官方命令行说明](https://nginx.org/en/docs/switches.html)。

## 本次自动验证

本次运行结果（2026-09-07）：

| 检查 | 结果 |
| --- | --- |
| build-local.ps1 前端 production build | 通过，包含 TypeScript 检查 |
| build-local.ps1 后端 Maven package | 通过，jar 产物存在；未使用 skipTests |
| 后端原有自动化回归 | 75 项：67 通过、8 项真实 MySQL skipped，0 失败 |
| 前端原有回归 | 27 通过（26 项模拟 API 浏览器测试、1 项日历函数测试） |
| 新增 PowerShell 运行检查 | 39 通过；含真实临时子进程身份/停止隔离，以及入口失败退出码 |
| Nginx 示例 | 路径替换、回环监听、SPA fallback、API 前缀与上传上限静态检查通过；未运行真实 nginx -t |
| 私有文件 / bundle | git check-ignore 通过；生产 bundle 未发现 DB_PASSWORD、LIFE1000_JWT_SECRET、测试私密标记或 127.0.0.1:8080 |
| 真实 MySQL / life1000.test / 真实 Nginx 启停 | 当前没有 DB 凭据和可用 Nginx，未执行，须按下文验收 |

首次构建发现环境隔离把 Spring 配置覆盖变量留成空值，导致一项原有断言失败。已修复为删除该环境变量并增加回归检查；最终上述全量构建通过，业务代码与原有断言未修改。

可重复运行：

```powershell
.\scripts\Test-LocalRuntime.ps1
.\build-local.ps1
npm --prefix frontend run test:e2e
git check-ignore local-config.ps1 .local-runtime/backend.json
```

脚本测试使用临时夹具验证缺配置/Java/jar/Nginx、模板替换、Windows 路径、端口占用、进程身份拒绝、锁与环境隔离。**这些不是实际 Nginx 启停验收。**

现有浏览器自动测试使用 Vite 与模拟 API；它们是 UI 回归，不是 Nginx → 后端 → MySQL 验收。后端自动化包含模拟 Mapper 与真实临时磁盘测试；无 DB 凭据时真实 MySQL 集成测试 skipped。普通 build 有意隔离 DB 凭据，即使私有配置填了正式库也不会自动运行这些集成测试。

真实数据库回归请在另一个 PowerShell 会话中使用专用 **life1000_test**，遵守 README 和 Phase 3～6 VALIDATION 的空编号/测试数据前提，不要清空正式 life1000：

```powershell
$c = & .\local-config.ps1
$env:JAVA_HOME = $c.JAVA_HOME
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/life1000_test?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
$env:DB_USERNAME = $c.DB_USERNAME
$env:DB_PASSWORD = $c.DB_PASSWORD
# 该账号必须有测试库权限；不要求使用正式库账号。
& (Join-Path $c.MAVEN_HOME 'bin/mvn.cmd') -f backend/pom.xml "-Dmaven.repo.local=$PWD/.cache/maven" verify
Remove-Item Env:DB_USERNAME, Env:DB_PASSWORD, Env:DB_URL
$c = $null
```

测试使用已有 mysql profile，未添加 skeleton 绕过。仓库没有独立 Phase 1/2 VALIDATION 文档，相应记录在 README；Phase 3～6 有独立文档。

## Windows 本机最终验收

本轮没有可用 Windows Nginx 或 DB 凭据，**以下真实入口验收尚未执行**。用户以前的 Phase 验收仍然有效，但不等于本次运行架构已验收。

1. 按首次准备填写配置、hosts，确保原有 MySQL 已运行，原 Vite/后端已停止；完成 build。
2. start 自动打开 http://life1000.test，地址栏无 IP、5173 或 8080。首页一屏正常。
3. 登录后依次打开 /goals、已有 /goals/27（替换为实际事项）、/timeline、/quotes、/stats、/settings；直接刷新这些路由不出现 Nginx 404。
4. Network 中业务请求为同源 /api/；附件 content 请求带 Authorization，无 URL token，无 uploads 静态地址。未登录 API 返回 401。
5. 确认真实事项、完成档案、时间轴、金句与设置仍正确；背景和图片预览正常，文档下载正常。
6. 在可清理测试事项上传一个接近但不超过 50 MB 的附件；确认成功。超过现有后端限制仍拒绝；测试后通过 UI 删除该附件。备份 ZIP 可完整下载。
7. 再运行 start 不增加第二个 Java 或 Nginx master。修改固定配置后 stop/start；有效 Token 不因 Secret 重建而失效，已有附件仍能读取。
8. stop 后 life1000.test 无法访问，8080 不再有本项目服务，MySQL 保持运行。其他 Java/Nginx 实例不被停止。再次 stop 可安全重复。
9. 检查监听地址：

   ```powershell
   Get-NetTCPConnection -State Listen |
       Where-Object LocalPort -in 80,8080 |
       Select-Object LocalAddress,LocalPort,OwningProcess
   ```

   两个服务都应仅为 127.0.0.1，不能是 0.0.0.0 或 ::。
10. 检查 git status，不应出现私有配置、运行目录、dist、jar 或附件；不要把本机密码/Secret 放进提交。

完成这些步骤后才可确认本机新架构真实验收通过。本次不延伸公网部署或其他功能。
