# Phase 6 验证：设置、分类、完整备份与 UI 收尾

本次仅实现 Phase 6。没有修改 PRD、数据库结构、固定编号、三状态、完成档案、时间轴或附件阶段语义。首页沿用 Phase 5 已验收布局。V1 功能代码在此结束，不继续导入恢复、一键启动或部署。

## 当前自动化结果

| 类型 | 实际结果与边界 |
| --- | --- |
| 前端构建 | npm --prefix frontend run build 通过，包含 TypeScript 与 Vite 生产构建 |
| 前端测试 | Edge 中 27 项通过：原有 22 项（含 1 项日历函数测试）和新增 5 项设置交互测试 |
| 后端构建 | Maven verify 成功 |
| 后端自动测试 | 共 75 项，67 项通过、8 项真实 MySQL 测试 skipped；新增 4 项 Controller、5 项 Service 测试通过 |
| 模拟 API 浏览器 | 设置/分类 CRUD、刷新后重新查询、背景入口、JWT Blob、ZIP 下载触发、失败保留输入、加载、防重复导出、Escape、1366/1440/390px 布局 |
| 本地文件 / ZIP | Phase6ServiceTest 用真实临时文件和真实可解压 ZIP，JdbcTemplate 为模拟对象；覆盖分目录、同名、安全路径、缺失文件说明、敏感设置过滤、回滚清理 |
| 真实 MySQL | 当前进程 DB_USERNAME 与 DB_PASSWORD 均不存在。Phase 1 的 3 项、Phase 3/4/5 各 1 项、Phase 6 的 2 项全部明确 skipped |
| 真实浏览器 → 后端 → MySQL → 本地文件 | 本次未执行；须在 Windows 本机按下文验收 |

模拟 API 的下载夹具是测试 ZIP，不代表真实业务备份。真正的 ZIP 生成由后端临时文件测试验证；真正的 SQL、外键、事务和个人照片仍需执行 MySQL 集成测试及人工验收。未使用 skeleton 或内存数据库替代 MySQL。

原有 Phase 1～5 的可运行测试全部通过，原真实数据库测试仍保留。测试日志中的预期 NullPointerException / 文件丢失异常来自故意注入的失败场景，客户端只看到普通错误文案。

已查看设置页 1366px 截图，自动检查 1440px / 1366px / 390px 无水平溢出及弹窗边界。截图图片为白色 PNG 测试夹具，不是产品默认图片。

## 主要文件与 API

- frontend/src/views/SettingsView.vue：四区设置页，首页 / 分类 / 数据 / 文件。
- frontend/src/api/settings.ts：设置查询、白名单更新和认证备份下载。
- backend/src/main/java/com/life1000/settings/SettingsService.java、SettingsController.java。
- backend/src/main/java/com/life1000/backup/BackupService.java、BackupController.java。
- AttachmentCleanup.java / AppSettingMapper.java：现有删除事务内清除固定背景引用。
- ApiExceptionHandler.java / frontend/src/api/http.ts：日志保留技术异常，前端显示可理解的失败信息。
- theme.css / AttachmentImage.vue：焦点、Hover、克制页面出现效果、图片解码失败占位。
- GoalTile.vue / GoalDetailView.vue：统一“不分类”文案。
- Phase6ControllerTest、Phase6ServiceTest、Phase6MysqlIntegrationTest、frontend/tests/settings.spec.ts。
- 原 GoalDetailServiceTest 仅适配清理组件新增依赖；home.spec.ts 适配设置页真实路由。
- README.md 与本文。

| API | 说明 |
| --- | --- |
| GET /api/settings | mode、fixedPath、fixedImage、files（imageCount / documentCount / totalBytes） |
| PUT /api/settings | JSON 对象，只允许 HOME_BACKGROUND_MODE、HOME_FIXED_BACKGROUND_PATH；可以部分更新 |
| GET /api/settings/images | 图片附件元数据：attachmentId、slotNo、originalName、filePath、allowHomeBackground、fixed |
| GET /api/backup/export | JWT 认证，application/zip，Content-Disposition 附件下载，Cache-Control: no-store |
| 原 /api/categories CRUD | 完全复用，未增加分类 API |
| 原 PUT /api/attachments/{id}/home-background | 复用 allowed Boolean 更新随机候选 |
| 原 GET /api/attachments/{id}/content | 继续认证 fetch + Blob，不公开 uploads，不在 URL 放 JWT |

示例设置请求：

```json
{"HOME_BACKGROUND_MODE":"FIXED","HOME_FIXED_BACKGROUND_PATH":"goals/027/已有附件的UUID"}
```

PATH 不接受任意路径：必须匹配当前图片附件元数据且物理文件存在，通过现有 LocalFiles 安全路径检查。非法键、非法模式、文档或不存在的图片返回 400。将 PATH 设为 null 或空字符串可以清除选定引用；UI 只提供选图，不增加独立上传或图片编辑系统。

## 背景和删除

默认 RANDOM；更新立即落库，失败时单选框恢复上一次已保存的状态。选择固定图不自动修改随机候选开关，也不自动切换模式；用户选择 FIXED 后才使用指定图片。

固定图片不要求 allow_home_background=true。候选查询只选图片，涵盖 GENERAL / PROCESS / COMPLETION；按 slot_no、附件 id 稳定排序。一次查询元数据，前端先展开 12 张，再按 12 张展开，复用 AttachmentImage 读取与释放 Blob URL。

设置写入按“锁父事项，再锁图片附件”的顺序验证，兼容原删除逻辑。现有 AttachmentCleanup.prepare 在同一删除事务中清除匹配的 HOME_FIXED_BACKGROUND_PATH；因此单独删除附件、删除过程记录、删除整个事项都适用。删除失败回滚时设置一起恢复，文件仍走原 .cleanup 日志和重试机制。

删除后保持模式 FIXED，首页安全显示纸张；设置页显示“未选择或已经失效”。如果文件被应用外手动移走，设置页会检测选定固定图不存在，首页读取失败也会安全回退；这不改变其他附件数据。

## 分类和文件

分类继续只有 name / sortOrder。名称必填，排序使用整数编辑；列表 sort_order ASC、id ASC。不引入拖拽库。删除弹窗明确说明事项保留、变为“不分类”；由原外键 ON DELETE SET NULL 保证。

文件数量和空间只查询 goal_attachment：SUM(is_image=true)、SUM(is_image=false)、SUM(file_size)。所有阶段计入，包括撤销完成后保留的附件；缺失物理文件也仍计入逻辑统计。无需递归扫描磁盘。统计页保持 Phase 5 原设计。

## ZIP 内容和一致性

下载名：Life1000_Backup_YYYY-MM-DD.zip。

```text
Life1000_Backup_YYYY-MM-DD.zip
├─ life1000.json
├─ images/
│  └─ 027_123_原文件名.png
├─ documents/
│  └─ 047_456_原文件名.pdf
└─ backup-info.json
```

life1000.json 使用 UTF-8、缩进 JSON。按八张表名称组织数组：category、life_goal、goal_check_item、goal_record、goal_completion、goal_attachment、quote、app_setting；每张表按 id 升序，保留关联 id、原路径、原文件名和其他业务字段。日期转换为可读 ISO 文本。

goal_attachment 每行增加 export_file，对应 ZIP 中的文件路径。编号 + 唯一附件 id 防止覆盖；原文件名去除目录分隔符、Windows 非法字符和控制字符，按 Unicode 字符安全限制长度，完整原名仍在 JSON 中。只从现有 goals/编号/UUID 文件路径复制，不递归扫描；.cleanup、源码、缓存及非附件文件不会导出。

app_setting 只导出 V1 白名单的两项业务设置。配置在环境变量中的数据库、JWT、登录密码从未被读取；即使有人在 app_setting 人工插入 DB_PASSWORD 等非业务键，也不会将其值导出。backup-info.json 的 settingsScope 明确说明范围。

backup-info.json 包含 backupVersion=1、application、createdAt、totalGoals、totalAttachments、imageCount、documentCount、missingFiles。数量来自元数据；missingFiles 每项包含 attachmentId、slotNo、originalName 和原因。缺失、不可读或路径不合法时，该附件 export_file=null，其余附件仍生成正常 ZIP。

生成采用 Spring REPEATABLE_READ 事务：
1. 按稳定顺序对父事项和附件获取 MySQL FOR SHARE 锁，保护期间有效附件不被应用删除。
2. 在同一快照读取所有业务表；不存独立备份数据模型。
3. 文件先复制到安全临时文件，再加入 ZIP，避免读取失败留下半份正式 ZIP 条目。每个暂存附件用完删除。
4. ZIP 完成后提交并释放锁，再向浏览器发送。下载阶段不会一直持有数据库锁。
5. 响应完成、传输异常或生成失败会清理临时目录；事务提交失败也注册了清理回调。正常运行不保留历史 ZIP。

临时目录由 Java Files.createTempDirectory 在系统临时目录生成 life1000-backup-*，不是公开下载目录。系统强制终止进程可能留下临时目录，属于异常退出边界；Windows 可在确认没有正在导出时清理残留。应用内附件的清理继续复用 Phase 3，不引入第二套附件管理。

导出时并发修改可能短暂等待数据库锁。前端导出请求允许最多 10 分钟等待，禁用重复按钮，并以 Blob 交给浏览器保存；非常大的个人库需在本机确认可用磁盘空间、导出时间与浏览器下载资源。未引入分布式锁、消息队列或云服务。

## Windows 测试命令

要求 Java 21、Maven、Node.js 和 Edge 可用。在根目录执行：

```powershell
cd D:\code\myself\Life1000
npm --prefix frontend run build
npm --prefix frontend run test:e2e
mvn -f backend/pom.xml '-Dmaven.repo.local=D:/code/myself/Life1000/.cache/maven' verify
```

真实测试在已经临时设置 DB_USERNAME / DB_PASSWORD 的 PowerShell 会话执行，凭据不写入 Git 文件：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/life1000_test?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
mvn -f backend/pom.xml '-Dmaven.repo.local=D:/code/myself/Life1000/.cache/maven' '-Dtest=Phase6MysqlIntegrationTest' test
```

life1000_test 需事先创建并授权；Flyway 建表。测试显式 mysql profile，并检查数据库名必须为 life1000_test。990、991 必须为空，已有记录不会被清空。建议使用专用测试库，绝不为了测试清除正式 life1000 数据。

Phase 6 两项真实测试：
- 完整 SQL / 外键 / ZIP / 文件读取测试：外层回滚事务，测试附件使用独立临时目录；清理固定图的三条删除路径均验证。它验证真实 MySQL 操作，但不声称这些夹具被永久提交。
- 模式持久化测试：不包裹回滚事务，通过实际提交的 PUT 请求，再由 GET 与独立 SQL 查询验证 RANDOM → FIXED → RANDOM。finally 恢复测试前该键的值和更新时间；原来没有该键则删除自己创建的键。

确认两项均实际运行通过，skipped 不算真实验收。全量真实回归另外需要 Phase 1～5 文档要求的空编号与测试数据条件。

## Windows 本机启动

在原来已配置数据库、登录和 JWT 环境变量的会话中，保持上传目录与 Phase 3～5 一致：

```powershell
cd D:\code\myself\Life1000\backend
# 沿用 DB_USERNAME / DB_PASSWORD、LIFE1000_USERNAME / LIFE1000_PASSWORD / LIFE1000_JWT_SECRET。
# 保持 LIFE1000_UPLOAD_DIRECTORY 指向此前真实附件目录。
mvn '-Dmaven.repo.local=../.cache/maven' package
java -jar target/life1000-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

如果刚运行过专用库测试，启动个人应用前将 DB_URL 恢复到原 life1000 地址。前端另开终端：

```powershell
cd D:\code\myself\Life1000\frontend
npm run dev
```

访问 Vite 显示的地址（默认 http://localhost:5173/settings）。

## 人工验收

1. 设置页只有首页、分类、数据、文件四区。刷新后模式与选定图片保持，失败时仍有明确提示和重试入口。
2. 随机候选仅包含图片；不同阶段和未完成事项的图片均可出现，文件名和固定编号正确。切换开关，刷新验证，再回首页确认随机选择生效。
3. 将未参与随机的图片设为固定背景并切换 FIXED；多次回首页应固定显示它。切回 RANDOM 则按候选抽取，不轮播。
4. 删除当前固定图，检查 app_setting 不再包含对应 PATH，首页回纸张，设置显示未选择。分别回归删除过程记录和整个事项的路径。
5. 分类新增、改名、排序，刷新检查顺序；同排序数字以 id 稳定排序。删除先取消，再确认；原事项仍存在，category_id=NULL，编号不变，重新进入详情和千事页显示“不分类”。
6. 核对文件图片数、文档数、空间与数据库聚合结果，包括完成证明。
7. 准备同名文件、图片与文档、过程记录和完成档案，导出 ZIP。确认浏览器文件名、解压、各业务表、附件关系和导出路径对应正确。
8. 在专用测试数据上模拟缺失文件：先记下 file_path，把文件移到上传目录外的临时位置，导出后查看 missingFiles，其余文件应可读取。验收后放回原位置；不要在正式资料上随意删除文件。
9. 未登录直接请求 /api/backup/export 应 401；浏览器正常下载走 Authorization，无 JWT 查询参数，无公开 uploads。
10. 完成以下 UI 回归，不重新设计页面或改变业务语义。

## ZIP 内容检查方法（PowerShell 7）

将路径换成实际下载位置。Expand-Archive 的目标选择新的检查目录：

```powershell
$backupZip='C:\Users\31033\Downloads\Life1000_Backup_2026-09-07.zip'
$backupCheck=Join-Path $env:TEMP ('Life1000-inspect-'+[guid]::NewGuid())
Expand-Archive -LiteralPath $backupZip -DestinationPath $backupCheck
$data=Get-Content (Join-Path $backupCheck 'life1000.json') -Raw -Encoding utf8 | ConvertFrom-Json
$info=Get-Content (Join-Path $backupCheck 'backup-info.json') -Raw -Encoding utf8 | ConvertFrom-Json
$data.PSObject.Properties.Name
$info | Format-List
$info.missingFiles | Format-Table
$data.goal_attachment | ForEach-Object {
  [pscustomobject]@{
    attachmentId=$_.id
    originalName=$_.original_name
    exportFile=$_.export_file
    exists=if ($_.export_file) { Test-Path -LiteralPath (Join-Path $backupCheck $_.export_file) } else { $false }
  }
} | Format-Table
```

八张表应都存在；有效附件的 exists=true，缺失附件应在 missingFiles 中逐一有说明。同名附件应有不同 export_file。随机抽取导出的图片/文档打开，并与原文件 SHA-256（Get-FileHash）对比。JSON 不应出现环境配置键 DB_PASSWORD、LIFE1000_PASSWORD 或 LIFE1000_JWT_SECRET；不要将凭据粘贴到聊天或提交文件。

## UI 回归清单

| 页面/交互 | 本次检查与本机复核 |
| --- | --- |
| 首页 | 原一屏结构、五个入口、真实明暗照片上的文字；无轮播 |
| 人生千事 | 1366/1440 五列、001～1000、卡片/列表、筛选保留编号、创建与删除恢复空白 |
| 详情 | 正文宽度、封面、长文本、无分类文案、图片失败占位、记录与附件；仍允许完成后编辑 |
| 时间轴 | 年度展开/收起、日期/编号稳定排序、跳转详情；无图片或新增事件 |
| 统计 | 原八项计数，没有图表 |
| 金句 | 搜索、弹窗、错误、空状态和删除确认 |
| 设置 | 四区间距、候选批量展开、保存失败恢复、文件数与导出状态 |
| 登录 | 标签、焦点、登录错误、原目标路由回跳 |
| Confirm | 删除事项/附件/记录/条件/金句/分类，以及撤销完成均保留二次确认；普通编辑无额外确认 |
| 键盘与弹窗 | Escape 可关闭非保存中弹窗；保存中防重复；焦点轮廓可见，弹窗可滚动且不超出屏幕 |
| 基本移动兼容 | 390px 无水平溢出，不为手机重做产品布局 |

未实现备份导入/恢复、自动备份、云同步、AI、提醒、Deadline、社交、多用户、注册、一键启动、Docker、Nginx 或服务器部署。
