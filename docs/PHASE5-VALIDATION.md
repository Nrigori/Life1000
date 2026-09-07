# Phase 5 验证：首页、金句收藏与数据统计

本次仅实现 Phase 5。PRD、数据库 DDL、迁移和依赖未修改。用户已确认 Phase 1～4 的真实链路验收；以下只报告本次实际运行的证据。

## 自动化结果与边界

| 类型 | 结果 |
| --- | --- |
| 前端生产构建 | npm --prefix frontend run build 通过，包含 TypeScript 检查 |
| 模拟 API 浏览器测试 | Edge：原有 15 项与新增 7 项通过；新增包含 1 项日历函数测试、6 项页面交互测试 |
| 自动化后端测试 | Maven verify 成功；64 项中 58 项通过、6 项 skipped；新增 11 项非数据库测试通过 |
| 真实 MySQL | 当前进程 DB_USERNAME / DB_PASSWORD 均不存在；包括 Phase5MysqlIntegrationTest 在内的 6 项明确 skipped |
| 真实附件读取 | 浏览器使用模拟 PNG 响应验证 JWT fetch、Blob 展示与释放；未执行真实浏览器 → 后端 → MySQL → 文件读取 |
| 原有本地磁盘测试 | Phase 3 / 4 的真实临时文件测试随后端回归通过；Mapper 为模拟对象 |

**模拟 API 不是数据库持久化验收。** 浏览器刷新测试验证客户端重新查询模拟存储；实际 MySQL 持久化、统计 SQL 和真实照片仍需本机验收。未使用 skeleton 或内存数据库绕过 MySQL。

已检查 1440px / 1366px 首页无纵向溢出及截图。图片背景测试使用纯色 PNG 夹具，不能代表个人真实摄影照片的最终观感。

## 实现与文件

- frontend/src/views/HomeView.vue：独立全屏封面、五个入口、本地日期、金句、两个计数及年度细线。空候选使用现有暖米白纸张，不请求互联网图片。
- frontend/src/home/calendar.ts：以本地年月日转换 UTC 日历序号，避免夏令时造成非整日差。比例 = 当年今天之前已经过去的完整天数 / 当年总天数；1 月 1 日为 0%，正确处理 365 / 366 天。午夜只更新日期，不更换背景或金句。
- frontend/src/views/QuotesView.vue：收藏/编辑弹窗、二次确认删除、内容/来源搜索、参与首页随机开关。
- frontend/src/views/StatsView.vue：七行安静数字（其中附件一行含图片和文档两个计数），没有图表。
- frontend/src/api/home.ts：上述页面 API。App.vue 为首页使用独立布局；router/index.ts 和 LoginView.vue 保留原目标路由，无目标回首页，拒绝站外回跳。
- backend/src/main/java/com/life1000/quote/：QuoteController / QuoteService，复用 Quote 和 QuoteMapper。
- backend/src/main/java/com/life1000/home/：HomeController / HomeService；LifeGoalMapper 增加单次统计 SQL。
- frontend/tests/home.spec.ts 与三个 Phase5 后端测试类：新测试；goals.spec.ts 仅适配登录回跳断言。
- README.md、本文：使用及验收说明。

## API

所有下列业务 API 继承既有 JWT 认证。

| API | 行为 |
| --- | --- |
| GET /api/quotes?keyword= | content / source 简单模糊搜索；按 id 降序，转义用户输入的 % / _ / ! |
| POST /api/quotes | content 必填，source 可空，includeHome 未传默认 true；返回 201 |
| PUT /api/quotes/{id} | 保存 content、source、includeHome；不存在返回 404 |
| DELETE /api/quotes/{id} | 删除返回 204；不存在返回 404 |
| GET /api/quotes/random | include_home=true 中 ORDER BY RAND() LIMIT 1；无候选 204 |
| GET /api/home/background | 返回 id、originalName；无有效候选 204 |
| GET /api/stats | 返回全部八个计数 |

背景元数据接口是为了选择首页图片新增的简单查询；实际文件继续走 GET /api/attachments/{id}/content，没有第二套文件入口或公开 uploads。

## 随机与固定背景

默认 RANDOM，从 is_image=true AND allow_home_background=true 中随机一张。GENERAL、PROCESS、COMPLETION 一视同仁，不要求事项已完成。不记录上次结果，不轮播。每次进入首页重新查询；同一页面上的时间更新不会查询新图片或金句。

兼容 PRD 的 app_setting 键 HOME_BACKGROUND_MODE、HOME_FIXED_BACKGROUND_PATH：
- FIXED 模式将 HOME_FIXED_BACKGROUND_PATH 匹配到现有图片附件的 file_path。
- 路径必须能对应到有效图片附件；空值、失效路径或非图片回退纸张，不读取任意磁盘文件。
- 固定模式不要求 allow_home_background；这个标记控制随机候选。
- 未提供设置写入接口或固定背景上传/设置 UI。这些属于 Phase 6。

背景用现有认证 fetch 获取 Blob，URL 不携带 JWT。图片解码失败回退纸张；替换、离开及异步过期都会释放 Blob URL。图片/金句/统计独立读取，某一项失败不会阻断其他内容。删除当前背景后，下次进入重新查询，不保留随机附件引用。

## 统计口径

一次 SQL 返回：
- writtenCount：life_goal 实际记录数。
- completedCount：当前 status=COMPLETED。
- inProgressCount：当前 status=IN_PROGRESS。
- blankCount：1000-writtenCount。
- completedThisYear：goal_completion JOIN life_goal，当前 COMPLETED 且 completed_date 在服务端本地当前年 [1月1日, 下一年1月1日)。
- imageCount / documentCount：goal_attachment 的 is_image=true / false；包含全部阶段，也包含撤销完成后保留的附件。
- quoteCount：全部 quote，不受 include_home 影响。

不保存冗余计数。完成、撤销、修改完成日期、删除事项/附件后，下次查询自然变化。本机 Java 时区应与 Windows 用户日期一致（本项目本机环境为 Asia/Shanghai）。

## Windows 自动化命令

在项目根目录执行；要求 Java 21、Maven、Node.js 和 Edge 已可用：

```powershell
npm --prefix frontend run build
npm --prefix frontend run test:e2e
mvn -f backend/pom.xml '-Dmaven.repo.local=D:/code/myself/Life1000/.cache/maven' verify
```

真实 MySQL 测试在已设置 DB_USERNAME / DB_PASSWORD 的同一个 PowerShell 会话执行。不把密码写入受 Git 管理的文件。

**Phase 5 集成测试请使用专用测试数据库**：数据库名通过 DB_URL 指定，由 Flyway 建立表结构。测试要求 993～995 为空、quote 为空、没有已选首页候选图片、没有上述两个首页设置键；不满足会失败，不清除用户数据。已有 life1000 包含金句时不要为了测试删除它们。

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/life1000_test?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
# DB_USERNAME / DB_PASSWORD 使用本会话已有环境变量，账号需有测试数据库权限。
mvn -f backend/pom.xml '-Dmaven.repo.local=D:/code/myself/Life1000/.cache/maven' '-Dtest=Phase5MysqlIntegrationTest' test
```

测试类明确 @ActiveProfiles("mysql")，在外层测试事务内验证真实表、SQL、认证附件字节与固定/随机模式，结束回滚数据库夹具，临时附件按现有回滚清理机制删除。登录测试凭据在内存生成，无需使用个人登录密码。必须确认 1 项实际执行通过，skipped 不算通过。全量真实测试还需满足 Phase 1～4 文档的空编号要求。

真实测试覆盖：金句 CRUD / 搜索 / 开关 / 空随机，三种附件阶段候选、排除文档、真实 JWT 文件字节、固定图失效回退、所有统计口径及撤销完成不算今年完成。

## Windows 人工验收

后端（保留本机 DB 与 LIFE1000_USERNAME / LIFE1000_PASSWORD / LIFE1000_JWT_SECRET 环境变量，不写入配置文件）：

```powershell
cd D:\code\myself\Life1000\backend
mvn '-Dmaven.repo.local=../.cache/maven' package
java -jar target/life1000-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

保持 LIFE1000_UPLOAD_DIRECTORY 指向 Phase 3 已验收的上传目录。前端另开终端：

```powershell
cd D:\code\myself\Life1000\frontend
npm run dev
```

打开 Vite 显示的本地地址（默认 http://localhost:5173），逐项检查：

1. 直接打开 /quotes，登录后回 /quotes；直接登录无目标时到 /。原 /goals、详情和 /timeline 登录回跳正常。
2. 没有背景候选和随机金句时首页仍一屏显示日期、LIFE / 1000、计数和年度比例。
3. 在 /quotes 新建内容与可选来源；默认参与首页随机。修改、刷新、关闭/打开开关，确认数据库持久化；内容和来源分别搜索；删除先取消，再确认。
4. 在事项详情分别上传 GENERAL、PROCESS、COMPLETION 图片并允许首页背景；文档不能作为背景。只有一个候选时首页应显示它，多个时可重复抽到同图。
5. 开发者工具 Network 确认附件 content 请求携带 Authorization，URL 无 token，没有公开 uploads 或第三方图源。查看真实明亮/暗色照片下文字是否清晰。
6. 从五个导航往返首页，重新随机；停留首页不轮播。删除当前背景图片后重新进入正常回退或选其他图。
7. /stats 与数据库核对八项计数。新建事项影响已写下/空白；完成影响完成数；撤销后今年完成减少；跨年修改完成日期后计数变化；附件及金句新增/删除后刷新更新。
8. 检查 1440px 和 1366px 一屏布局；金句没有来源不留占位。未创建大量统计卡片或任何图表。
9. 如需验收固定模式，在专用测试库设置上面两个 PRD 设置键，路径使用已存在图片附件 file_path；固定图片删除后应回退纸张。不要改上传目录访问方式。
10. 回归人生千事固定编号、详情记录/附件、完成/撤销/再次完成和时间轴。设置导航仍是占位页。

## 停止范围

未实现 Phase 6 设置页、分类管理 UI、固定背景上传 UI、候选集中管理、ZIP 备份/恢复、文件占用设置、一键启动或部署。未新增 AI、提醒、Deadline 或其他 V1 外功能。
