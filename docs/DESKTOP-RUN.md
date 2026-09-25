# Life1000 Windows 桌面版

桌面版是现有 V1 的新入口：Tauri 2 / WebView2 内嵌 Vue production build，启动自己管理的 Spring Boot jar，再连接原有 MySQL 和本地附件目录。没有 Nginx、Vite 开发服务器或外部浏览器，也没有新业务表、接口或附件存储格式。Web 版的三个 local 脚本保持可用。

## Desktop 1.1 窗口

Desktop 1.1 关闭 Windows 默认标题栏，使用 Life1000 自定义顶栏整合 LIFE / 1000、五个一级导航和最小化、最大化/还原、关闭按钮。顶栏空白区域可拖动，双击该区域切换最大化；导航、按钮和页面表单不属于拖动区域。

首页照片从窗口最顶部开始铺设，自定义顶栏叠在照片上；有照片时使用暖深色低透明玻璃，无照片和普通页面使用暖米白玻璃。内容卡片、详情正文、时间轴、统计和设置继续使用原有纸张背景。

窗口大小、位置和最大化状态由 Tauri 官方 window-state 插件保存到：

```text
%APPDATA%\com.life1000.desktop\.window-state.json
```

该文件只包含窗口几何状态，不包含账号、数据库、附件或业务数据。恢复失败时仍使用代码中的主显示器默认尺寸。

Desktop 1.1 是 1.0 的原位升级：`productName` 仍为 `Life1000`，bundle identifier 仍为 `com.life1000.desktop`，只将版本提升为 `1.1.0`。NSIS 使用相同应用身份覆盖安装目录，不管理也不会删除以下外部数据：

- `%LOCALAPPDATA%\Life1000\desktop-local-config.json`
- MySQL 数据库
- `LIFE1000_UPLOAD_DIRECTORY` 指向的附件
- 用户其他本地目录

## 运行前准备

- Windows 10/11 x64，Microsoft Edge WebView2 Runtime。
- 本机 Java **21 或更新版本**，MySQL 已启动，原有 Life1000 数据库可连接。
- 桌面版不会安装或启动 MySQL，也不会初始化、替换用户附件目录。
- 桌面版和 Web 版后端都使用 8080，不能同时启动两个后端。切换前先关闭桌面窗口，或运行 Web 版的 `stop-local.ps1`。程序不会杀死其他 Java。

首次安装可能需要联网安装 WebView2（机器已有时复用）。这是未签名的个人应用，Windows 可能显示发布者未知提示；请确认安装包来自自己的构建。

## 私有配置

将仓库的 `desktop-local-config.example.json` 复制为：

```text
%LOCALAPPDATA%\Life1000\desktop-local-config.json
```

PowerShell 示例（不覆盖已有配置）：

```powershell
$directory = Join-Path $env:LOCALAPPDATA 'Life1000'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$destination = Join-Path $directory 'desktop-local-config.json'
if (Test-Path -LiteralPath $destination) { throw '已有桌面配置，请直接编辑，不要覆盖。' }
Copy-Item .\desktop-local-config.example.json $destination -ErrorAction Stop
notepad (Join-Path $directory 'desktop-local-config.json')
```

已有配置时直接编辑，不要再次复制模板。填写现有数据库、Life1000 登录账号/密码、固定 JWT Secret、**原有附件目录的绝对路径**。使用已有 JWT Secret 可避免因为切换运行方式而改变签名密钥。JSON 路径使用 `D:/Life1000Data/uploads` 或双反斜杠。

`JAVA_HOME` 可填在 JSON；也可以删除该键，程序会先读取环境变量 JAVA_HOME，最后查找 PATH 中的 java。无论来源都会检查版本 >= 21。

如需使用其他位置，设置用户环境变量 `LIFE1000_DESKTOP_CONFIG` 为配置文件的绝对路径，再重新启动桌面应用。仓库根目录的 `desktop-local-config.json` 已被忽略，example 可以提交。配置不会编译进前端、exe 或安装包；运行中只向 Java 子进程注入白名单配置，不向 WebView 返回秘密，也不输出 Java 的原始日志。

原有 `local-config.ps1` 仍服务于 Web 运行脚本。桌面 JSON 手工复用其中的值；不要把真实配置复制到 example。桌面端不执行任意 PowerShell 配置脚本。

## 构建

仅构建机器额外需要：

- Node.js >= 22.12、npm；
- Maven、Java 21；
- Rust stable（MSVC x64 工具链）；
- Visual Studio Build Tools 的“使用 C++ 的桌面开发”和 Windows SDK。

参考 [Tauri 官方 Windows 前置条件](https://v2.tauri.app/start/prerequisites/)。

使用 PowerShell 7.2+，在仓库根目录执行：

```powershell
.\build-desktop.ps1 -JavaHome 'D:\JDK21' -MavenHome 'F:\apache-maven-3.8.8'
```

也可省略参数，从环境变量或已有 `local-config.ps1` 读取构建工具路径。构建不需要数据库密码；即使读取了现有配置，也会在调用 npm / Maven / Cargo 前隔离数据库、登录及 Spring 环境变量。

脚本依次构建前端、运行 Maven verify、复制后端 target 的 executable jar 到被忽略的桌面资源暂存目录、运行 Rust 测试、生成 Tauri NSIS 安装包。首次构建需要联网获取依赖和 NSIS 工具；不自动安装 Visual Studio、Rust、Java 或 MySQL。

产物：

```text
frontend/dist/
backend/target/life1000-backend-0.0.1-SNAPSHOT.jar
desktop/src-tauri/target/release/Life1000.exe
desktop/src-tauri/target/release/bundle/nsis/Life1000_1.1.0_x64-setup.exe
```

推荐运行 NSIS 安装包。安装后的 exe 旁边有 `backend/life1000-backend.jar`，由安装器一并安装；不能只复制裸 exe 而遗漏 jar。前端资源已嵌入 exe。构建目录中的 exe 同样依赖同目录的 backend 资源。更新后端必须重新构建安装包。

## 启动、关闭和故障提示

双击安装后的 Life1000。窗口先显示启动状态，HTTP 就绪后才挂载现有 Vue 应用。

- 后端强制使用 `mysql,desktop` profile、`127.0.0.1:8080`，只读取 jar 内的 Spring 配置和白名单环境变量。
- Java 版本检查最多 10 秒；后端 HTTP 检查总等待约 120 秒，单次 2 秒。
- `GET /api/health` 返回 200 / 401 / 403 才视为 HTTP 已工作；仅 TCP 连接成功不算就绪。健康检查仍要求 JWT，桌面就绪检查不取消认证。
- 同一桌面应用重复启动会聚焦已有窗口。Web 后端或其他程序占用 8080 时，显示明确错误，不接管、不终止该程序。
- 关闭窗口终止持有句柄的 Java 子进程并回收句柄；Windows Job Object 也会在桌面进程异常退出时终止关联子进程。没有 Windows Service、全局 Java 扫描或 PID 名字匹配。
- 没有后端远程 shutdown 接口。退出通过本机进程终止完成，正在进行的数据库事务由连接断开回滚，物理附件清理继续沿用已有事务/清理日志机制。请让正在保存或上传的操作完成后再关闭窗口。
- 缺配置、Java、jar、端口冲突、后端退出、HTTP 超时都有可读提示，可修正后重试。后端退出时优先检查 MySQL、数据库权限和 JSON 中的值；原始进程输出不进入 UI，以防泄露秘密。

桌面 CORS **仅在 desktop profile** 放行 `http://tauri.localhost`，不允许任意网站或 Cookie 凭据；JWT 仍由现有登录生成并保存在 WebView sessionStorage。Web 继续使用同源 `/api`。桌面访问 `http://127.0.0.1:8080/api`。

附件的读取、图片/Markdown/TXT/PDF 预览、上传和完成证明继续走现有认证 API。上传目录不通过 Tauri asset protocol 或静态服务器公开。下载附件/ZIP 时出现原生保存对话框，取消不会保存文件。

## 自动化验证

```powershell
npm --prefix frontend run build
npm --prefix frontend run test:e2e
# JAVA_HOME / Maven 路径按本机设置；真实 MySQL 用已有 VALIDATION 文档的专用测试库命令
mvn -f backend/pom.xml verify
cargo test --locked --manifest-path desktop/src-tauri/Cargo.toml
npm --prefix desktop run build
```

前端 Playwright 测试里的桌面桥和 API 是模拟，不代表 Tauri → Spring → MySQL 验收。Rust 测试会实际创建本机 HTTP/TCP 服务和独立子进程，但不连接业务数据库。后端 CORS 测试为 MockMvc。真实数据库集成测试仍须显式使用专用 `life1000_test`，不能用正式库运行清理型测试。

## 本机验收清单

建议先完成完整 ZIP 导出，再在专用测试库与独立附件目录中做写入型验收。不要清空正式库。

1. 关闭 Web 后端和 Nginx，双击桌面应用；没有打开 Edge/Chrome 或外部浏览器地址栏。
2. 任务管理器确认 Life1000 的 Java 子进程；`Get-NetTCPConnection -LocalPort 8080 -State Listen` 只有 127.0.0.1。
3. 登录后依次验证首页、设置、分类新增/修改、事项新增/修改、固定编号不变。
4. 上传图片、MD、TXT、PDF；刷新详情仍存在，分别预览，关闭预览后再次打开正常。
5. 下载附件时选择路径并校对文件内容；取消保存不应生成文件。
6. 完成事项、撤销、再次完成，时间轴跳转正确。
7. 设置固定/随机背景，返回首页确认生效；导出 ZIP 并检查两个 JSON 和附件内容。
8. 关闭桌面窗口，确认它启动的 Java 和 8080 监听消失；无关 Java 不受影响。再次双击仍能正常登录使用。
9. 运行第二个 Life1000.exe 只聚焦已有窗口。
10. 临时指定不存在的配置路径，确认有错误提示；修复后重试。以其他程序占用 8080，确认不会被关闭。
11. 重新运行 Web 的 build/start/stop 脚本，确认 Web 首页、登录和附件仍正常。

本次构建与测试的实际执行结果见下面的验证记录。未执行的桌面操作不得视为已经通过。

## Desktop 1.1 验证记录（2026-09-25）

| 验证范围 | 结果与边界 |
| --- | --- |
| Vue production build | 通过 |
| 前端 Playwright | 38 项通过，含启动期标题栏、窗口按钮、拖动区和导航点击隔离 |
| 1366×768 页面布局 | 首页、人生千事、详情、时间轴、设置已截图检查；首页照片从 y=0 铺满，人生千事 sticky 工具栏滚动后停在 52px 标题栏下 |
| Maven verify | 92 项中 84 项通过、8 项真实 MySQL 测试按条件跳过；构建通过 |
| Rust / Tauri 测试 | 6 项通过；1 项需专用数据库配置的真实 Java 测试默认 ignored |
| Tauri release / NSIS | 通过，生成 `Life1000_1.1.0_x64-setup.exe` |
| 原生业务链路 | 专用 `life1000_test` 通过登录、第二实例、分类、事项、附件与预览、完成、时间轴、设置、ZIP 导出、关闭清理和再次启动 |
| 原生窗口控制 | 最小化、最大化、还原、关闭、第二实例唤醒和最大化状态恢复通过；真实拖动、双击最大化及导航点击隔离已完成人工验收 |
| Web 本地脚本 | 39 项检查通过；没有连接真实 MySQL 或启动浏览器 |
| 安装/覆盖升级 | 安装包已生成且应用身份保持不变；Desktop 1.1 人工安装、启动以及功能和视觉验收通过 |

## Desktop 1.0 验证记录（2026-09-24）

| 验证范围 | 结果与边界 |
| --- | --- |
| Vue production build | 通过；生产资源嵌入 Tauri，Web 版仍使用相对 `/api` |
| 前端 Playwright | 37 项通过，含 2 项桌面启动桥模拟测试；此组本身不连接真实后端 |
| Maven verify | 92 项中 84 项通过、8 项真实 MySQL 测试按条件跳过；构建通过 |
| 真实 MySQL 集成测试 | 另行在 `life1000_test` 运行 Phase 1、3、4、5、6 的 8 项测试，全部通过；未清理正式库 |
| Rust 默认测试 | 6 项通过；1 项需私有数据库配置的真实 Java 测试默认 ignored |
| Rust 真实 Java 测试 | 显式运行该 ignored 测试通过：Windows Job 内启动 Java、真实 HTTP 就绪、子进程退出 |
| Web 本地运行脚本 | 原有 39 项检查通过 |
| 完整桌面构建 | `build-desktop.ps1` 通过，生成 release exe 和 NSIS 安装包 |
| 原生桌面真实链路 | release exe → WebView2 → Spring Boot → 测试 MySQL → 独立本地附件目录通过，见下文 |

原生测试使用 `desktop/tests/native-smoke.mjs`，不是模拟 API。实际验证登录、第二实例复用、分类新增、固定编号事项新增/编辑、图片/MD/TXT/PDF 上传与刷新后保留、图片/Markdown/TXT 预览、完成事项、时间轴跳转、背景模式持久化、带 JWT 的 ZIP 导出、窗口关闭后后端退出及重新启动。检查了导出 ZIP 的两个 JSON、images 和 documents 内容；测试创建的数据与附件已清理。

PDF 的认证 Blob 和 iframe 加载已自动验证；用户另外确认原生窗口中能看到 PDF 阅读器和页面。WebView2 的原生 PDF 绘制层未被 CDP 截图完整捕获，因此不以截图中的深灰区域判定预览失败，也未引入另一套 PDF 渲染依赖。

尚需人工确认：NSIS 安装向导、安装后快捷方式启动、原生“另存为”对话框的保存/取消，以及使用用户正式私有配置的首次运行。本次没有执行安装向导；原生交互助手无法启动，未将 API 导出成功当作保存对话框验收。Web 版已通过构建、原有自动测试和运行脚本检查，未重新执行整套正式数据人工验收。

### 重跑真实桌面测试

先构建桌面版，关闭现有 Life1000 / Web 后端；不要运行在正式库。准备一个私有测试 JSON（可放在已忽略的 `.cache/desktop-native/config.json`），字段同 example，但必须使用 `life1000_test` 和仓库 `.cache` 内的独立附件目录。该目录需事先创建；填写专用测试登录账号与有效 JWT Secret。配置含密码，不得提交。

```powershell
New-Item -ItemType Directory -Force .\.cache\desktop-native\uploads | Out-Null
# 手工准备上述私有配置后：
node .\desktop\tests\native-smoke.mjs .\.cache\desktop-native\config.json

# 可选：单独验证 Java / Windows Job / HTTP 生命周期
$env:LIFE1000_DESKTOP_CONFIG = (Resolve-Path .\.cache\desktop-native\config.json).Path
try {
    cargo test --locked --manifest-path desktop/src-tauri/Cargo.toml `
        process::real_java_test::java21_in_windows_job_starts_http_and_stops -- --ignored
} finally {
    Remove-Item Env:LIFE1000_DESKTOP_CONFIG -ErrorAction SilentlyContinue
}
```

原生脚本只为测试启动的 WebView2 打开回环调试端口 9229，正式应用不设置调试参数。脚本拒绝正式数据库、仓库 `.cache` 外的附件路径、已有桌面实例及端口占用；只删除本次创建的测试事项/分类并恢复测试前背景设置，不清空数据库。若测试被外部强制终止，应人工核对测试数据是否清理。

真实 MySQL 的 Maven 运行命令沿用 `README.md` 及 `docs/PHASE3-VALIDATION.md` 至 `docs/PHASE6-VALIDATION.md` 中的环境变量和专用测试库配置；不要将其中的测试数据库 URL 改成正式 `life1000`。

## 用户人工验收确认

用户已完成桌面版人工验收，并确认核心功能全部正常。以上自动化记录与执行边界保留，用于区分自动检查和用户本机人工验收；本次最终收尾不再修改业务逻辑。
