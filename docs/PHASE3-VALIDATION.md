# Phase 3 验证与本机验收

本文件记录实现与验收方法，不修改产品需求。范围仅为事项详情。

## 自动化结果（当前 Codex 进程）

- 前端 `npm run build`：TypeScript 与 Vite 生产构建通过。
- 前端 `npm run test:e2e`：12 项 Edge 测试通过，包含原有 9 项 Phase 2 回归和 3 项详情流程。
- 后端 `mvn verify`：32 项通过，4 项真实 MySQL 测试跳过，构建成功。
- 本进程没有 DB_USERNAME / DB_PASSWORD。没有使用 skeleton、H2 或其他数据库替代真实 MySQL。
- 浏览器测试使用模拟 API；后端文件测试使用真实临时目录、真实 PNG / 文档和模拟 Mapper。
- 因此上述结果不等于 Phase 3 的真实浏览器 → 后端 → MySQL → 磁盘链路验收。
- 已查看 1440px 阅读页和附件页截图；测试也检查 390px 详情页没有横向溢出。图片测试素材是极小的白色 PNG，不是产品默认图片。

## 在本机运行真实 MySQL 集成测试

在已经配置 DB_USERNAME / DB_PASSWORD 的 PowerShell 中执行。建议 DB_URL 指向专用测试数据库。

```powershell
cd D:\code\myself\Life1000\backend
mvn '-Dmaven.repo.local=../.cache/maven' '-Dtest=Phase3MysqlIntegrationTest' test
```

如需一起执行所有测试：

```powershell
mvn '-Dmaven.repo.local=../.cache/maven' verify
```

- Phase 3 测试强制 `mysql` profile，真实运行 Flyway、JWT、HTTP Controller、Mapper、MySQL 和本地文件。
- 998、999 必须为空；遇到已有事项即失败，不会清空用户数据。Phase 1 测试还要求 001、002、1000 为空。
- Phase 3 测试采用实际提交的事务，再在 finally 中删除它自己创建的数据，以便真正检查提交后的文件清理；上传文件使用独立临时目录。
- 登录测试凭据在进程内生成，不需要修改实际应用账号或把数据库凭据写入文件。
- 若强制终止测试进程，finally 无法执行，可能保留测试事项；先检查其标题确认是测试创建的数据，再手动清理。
- 缺少 DB_USERNAME 时 JUnit 会显示 skipped。必须看到 Phase3MysqlIntegrationTest 的 1 项测试实际运行且通过，才能算该项真实集成验证成功。
- 旧 `scripts/Test-MySql.ps1` 仅选择 Phase 1 测试；本阶段请使用上面的命令。

## Windows 启动

沿用 README 中现有 DB_* 和 LIFE1000_* 登录 / JWT 环境变量，不保存真实凭据到受 Git 管理的文件。

后端 PowerShell：

```powershell
cd D:\code\myself\Life1000\backend
# 可选，推荐使用稳定的绝对目录；后端会创建所需子目录。
$env:LIFE1000_UPLOAD_DIRECTORY = 'D:\Life1000Data\uploads'
mvn '-Dmaven.repo.local=../.cache/maven' spring-boot:run '-Dspring-boot.run.profiles=mysql'
```

前端另开 PowerShell：

```powershell
cd D:\code\myself\Life1000\frontend
npm run dev
```

登录后访问 `http://127.0.0.1:5173/goals`。未配置上传目录时，路径是后端进程工作目录下的 `uploads`；从 backend 启动时是 `backend/uploads`。同一上传目录用于单个后端实例，重启后沿用原目录。改变目录不会自动搬移旧文件。

## 人工链路验收

使用一个空白编号，例如 027（若已有数据，选择其他空白编号），准备两张本地 PNG/JPEG 和一份 TXT/PDF/DOCX/XLSX 文件。

1. 从人生千事写下事项，再点击进入详情。检查标题、固定编号、分类/状态、大横幅无图片占位；默认没有基础信息输入框。
2. 点击编辑，修改标题、分类、为什么想做，在未开始 / 进行中之间切换，保存并刷新。内容应保留；不要出现完成流程、目标时间或截止时间。
3. 新增两条完成条件，修改第一条，勾选再取消，删除第一条并二次确认。第二条顺序保持稳定。
4. 新增一条过程记录，选择记录日期并填写正文；修改并刷新，日期和正文仍保留。
5. 在该记录的“添加记录附件”中上传第一张图片和文档，确认记录下显示附件；全部附件显示“过程记录”。
6. 在“全部附件 → 上传附件”上传第二张图片，确认显示“事项附件”。刷新后两类附件都在。
7. 点击图片缩略图预览，再用关闭按钮或 Escape 关闭；下载文档并与原文件内容比较。
8. 未指定封面时，应自动显示最近上传的图片；将较早图片设为封面，刷新后仍使用该图片，回到 /goals 卡片也应显示该封面。
9. 勾选、取消“允许作为首页背景”，每次刷新确认标记保留。首页本阶段不会使用它。
10. 删除选中的封面附件并确认：卡片消失，封面回退到剩余最近图片；`life_goal.cover_attachment_id` 应为 NULL。
11. 删除一条记录并确认：该记录的 PROCESS 附件及磁盘文件一起消失，GENERAL 附件保留。
12. 再新增记录及附件，然后“更多 → 清空这个编号”。先取消一次，确认数据还在；再确认清空。返回 /goals，该编号恢复空白，其他编号不移动。

辅助 SQL（把 27 换成选定编号；删除前记下 goal_id 与 file_path）：

```sql
SELECT id, slot_no, cover_attachment_id FROM life_goal WHERE slot_no = 27;
SELECT id, goal_id, record_id, stage, original_name, file_path,
       file_size, mime_type, is_image, allow_home_background
FROM goal_attachment WHERE goal_id = /* 删除前记下的 id */ 123;
SELECT * FROM goal_check_item WHERE goal_id = 123;
SELECT * FROM goal_record WHERE goal_id = 123;
```

删除后检查这三张关联表无该 goal_id 的行，并确认记录过的 file_path 对应物理文件不再存在。如果 Windows 正占用文件，数据库删除提交后保留清理标记，后端每 60 秒重试；释放占用后文件及标记应被清理。

## 实现边界

- 使用现有 goal_check_item、goal_record、goal_attachment 和 life_goal 字段，无新增表、无迁移、无 PRD 修改。
- 条件只表达可选清单；勾选全部条件不会自动完成事项。
- 记录按 record_date、id 升序；条件按 sort_order、id 升序。
- GENERAL 附件 record_id 为空；PROCESS 附件必须关联当前事项的记录。
- 单文件限制 50 MB，多文件在前端逐个上传；部分上传失败时已成功的文件保留且列表刷新。
- 内联图片支持后端识别的 PNG/JPEG/GIF/BMP；其他文件均可上传下载，不内联执行 SVG/HTML，也不解析文档内容。
- 文件名使用 UUID，original_name 保存移除客户端路径和控制字符后的原名；保存相对路径，不保存文件到数据库 BLOB 或前端 assets。
- 文件读取需要 JWT，浏览器使用认证 fetch 和临时 Blob URL；没有公开静态上传目录或 URL 中的 token。
- 上传和删除写入本地 .cleanup 日志。上传成功保留文件，事务回滚清理文件；文件被 Windows 占用时继续重试。重启后通过附件元数据判断未完成上传应保留还是清理，避免未入库文件无限残留。
- 本阶段没有完成弹窗、完成日期/感想/评分/证明、COMPLETION 上传、撤销完成、年度时间轴、首页背景使用、统计、设置或分类管理 UI。
