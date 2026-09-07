# Life1000

帮助自己记录人生，而不是管理人生。产品规格以 [docs/PRD.md](docs/PRD.md) 为准。

当前完成 **Phase 0 骨架、Phase 1 后端基础与 Phase 2 人生千事主页面**。本阶段不实现附件、完成系统、时间轴、金句、统计和设置等后续业务，也不修改 PRD。

## 目录与技术栈

- `frontend/`：Vue 3、TypeScript、Vite、Vue Router；`src/router/` 为八个路由骨架，`src/styles/theme.css` 为暖米白纸张主题。
- `backend/pom.xml`：Java 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.16、Spring Security JWT、Flyway、MySQL 驱动。
- `backend/src/main/java/com/life1000/entity/`、`mapper/`：PRD 八张表的基础映射。
- `backend/src/main/java/com/life1000/auth/`：单用户登录、JWT 签发及认证配置。
- `backend/src/main/java/com/life1000/category/`、`goal/`：本阶段的请求对象、Service、Controller。
- `backend/src/main/java/com/life1000/common/`：基础参数和数据冲突错误处理。
- `backend/src/main/resources/db/migration/V1__initial_schema.sql`：版本化 DDL。
- `backend/src/test/java/com/life1000/`：认证、参数校验、服务单元测试与真实 MySQL 集成测试。
- `backend/scripts/Test-MySql.ps1`：检查数据库环境变量后执行 MySQL 集成测试。

## 数据库

要求 **MySQL 8.0.16+**，使用 InnoDB、utf8mb4。请先创建 `life1000` 空数据库；应用不创建数据库。使用 `mysql` profile 启动时，Flyway 自动执行一次迁移，并记录版本。无需重复手动导入 SQL，也不要修改已执行的 V1 迁移。

| 表 | 内容 / 约束 |
| --- | --- |
| category | 名称、排序、创建/更新时间 |
| life_goal | 固定编号、标题、分类、状态、原因、预留封面关联、时间字段 |
| goal_check_item | 完成条件的基础表结构 |
| goal_record | 过程记录的基础表结构 |
| goal_completion | 完成信息基础结构；goal_id 唯一，评分可空且为 1～5 |
| goal_attachment | 附件元数据基础结构；不存文件二进制 |
| quote | 金句基础结构 |
| app_setting | 设置基础结构，setting_key 唯一 |

最后六张表本阶段仅有 DDL、Entity 和 Mapper，没有业务 Service / Controller / 上传或完成行为。Flyway 另建 `flyway_schema_history` 技术版本表，不建立用户表。

`life_goal.slot_no` 同时受 `NOT NULL`、`UNIQUE` 和 `CHECK (slot_no BETWEEN 1 AND 1000)` 约束，API 也验证范围。**不预填 1000 条记录**。编号不存在记录即为空白；删除是物理删除，其他编号不会移动，同一编号可以重新创建。

状态在数据库中仅允许 `NOT_STARTED / IN_PROGRESS / COMPLETED`。新增固定为 `NOT_STARTED`；基础接口仅允许切换 `NOT_STARTED / IN_PROGRESS`，不能借此完成或撤销完成事项。删除分类通过外键把相关事项的 `category_id` 置空，保留事项。后续关联记录的外键删除约束已在 DDL 中定义。

## Windows 本地启动

准备 Node.js 22.12+、JDK 21、Maven 3.6.3+。本机已有 JDK 和 Maven 的路径如下；如安装路径不同请调整。

在后端 PowerShell 终端：

```powershell
cd D:\code\myself\Life1000\backend
$env:JAVA_HOME = 'D:\JDK21'
$env:Path = "$env:JAVA_HOME\bin;F:\apache-maven-3.8.8\bin;$env:Path"

# 如果已在当前终端设置过数据库变量，可保留原值。
$env:DB_USERNAME = Read-Host 'MySQL 用户名'
$dbCredential = Read-Host 'MySQL 密码' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbCredential).Password

# DB_URL 可省略；默认为下列本机 life1000 连接，不包含凭据。
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/life1000?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'

$env:LIFE1000_USERNAME = Read-Host 'Life1000 登录账号'
$appCredential = Read-Host 'Life1000 登录密码' -AsSecureString
$env:LIFE1000_PASSWORD = [System.Net.NetworkCredential]::new('', $appCredential).Password

# JWT 密钥至少 32 个随机字节，Base64 编码；不要提交到 Git。
$jwtBytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:LIFE1000_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

mvn '-Dmaven.repo.local=../.cache/maven' '-Dspring-boot.run.profiles=mysql' spring-boot:run
```

后端监听 `http://127.0.0.1:8080`。默认 profile 也已改为 `mysql`；本阶段启动和数据库验证不使用 `skeleton`。Flyway 在启动时使用真实连接，连接或迁移失败会阻止启动。已有 Phase 0 的 `application-skeleton.yml` 仅保留为历史骨架配置。

账号、密码和 JWT 密钥必须通过环境变量或私有配置提供；仓库没有默认凭据。令牌默认有效 7200 秒，可用 `LIFE1000_TOKEN_TTL_SECONDS` 设置为 60～86400 秒。JWT 校验 HS256 签名、到期时间、签发方、接收方和当前单用户账号。没有注册、用户管理、刷新令牌或角色系统。

前端另开终端：

```powershell
cd D:\code\myself\Life1000\frontend
npm ci --cache ../.cache/npm
npm run dev
```

访问 <http://127.0.0.1:5173>，点击“人生千事”；未登录时进入最小登录页。登录后即可使用 `/goals`，所有 API 请求携带 JWT，不放开后端认证。

Vite 代理 `/api` 到 `127.0.0.1:8080`。需要更换后端端口时，复制 `frontend/.env.example` 为 `.env.local` 并修改 `API_PROXY_TARGET`，重启 Vite。前端配置中不得放数据库凭据或 JWT 签名密钥。

## Phase 1 接口

除 `POST /api/auth/login` 外，所有 `/api/**` 均需请求头 `Authorization: Bearer <accessToken>`。返回 JSON 字段为 camelCase。

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| POST | /api/auth/login | JSON：username、password；返回 accessToken、tokenType、expiresIn |
| GET | /api/categories | 按 sortOrder、id 升序列出分类 |
| POST | /api/categories | JSON：name 必填，sortOrder 可选，默认 0；返回 201 |
| PUT | /api/categories/{id} | 修改名称和排序；未传 sortOrder 则保留 |
| DELETE | /api/categories/{id} | 删除分类并保留关联事项，返回 204 |
| GET | /api/goals/range?fromSlot=1&toSlot=50 | 返回范围内实际记录，按 slotNo 升序；缺号为空白 |
| GET | /api/goals/search?keyword=日出&categoryId=1&status=IN_PROGRESS | 标题普通模糊搜索；分类、状态可选；保留固定编号 |
| GET | /api/goals/{slotNo} | 查询事项；空白编号返回 404 |
| POST | /api/goals/{slotNo} | JSON：title 必填，categoryId、reason 可选；返回 201 |
| PUT | /api/goals/{slotNo} | JSON：title 必填，categoryId、reason 可空，status 可选 |
| PUT | /api/goals/{slotNo}/status | JSON：status 必填，只支持未开始/进行中 |
| DELETE | /api/goals/{slotNo} | 删除并清空编号，返回 204；空白编号返回 404 |
| GET | /api/health | JWT 认证后的 HTTP 健康检查 |
| GET | /api/health/db | JWT 认证后的真实 SELECT 1 检查 |

完整更新 `PUT /api/goals/{slotNo}` 时，未传或显式 null 的 categoryId / reason 会清空字段；未传或 null 的 status 保留原状态。`id / slotNo / coverAttachmentId` 不能通过请求体修改，未知字段会返回 400。标题最多 255 字符，分类名称最多 100 字符。

错误返回 `{ "code": "...", "message": "..." }`：参数错误 400、未认证/令牌无效 401、不存在 404、重复编号或数据冲突 409、数据库不可用 503。创建同一编号不会覆盖已有事项。后续前端实现删除时应按 PRD 提供二次确认。

登录示例（已设置 Life1000 环境变量的 PowerShell）：

```powershell
$loginBody = @{
    username = $env:LIFE1000_USERNAME
    password = $env:LIFE1000_PASSWORD
} | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/auth/login' -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Invoke-RestMethod 'http://127.0.0.1:8080/api/health/db' -Headers $headers
```

## 构建和测试

后端（先设置 JAVA_HOME / PATH）：

```powershell
cd D:\code\myself\Life1000\backend
mvn '-Dmaven.repo.local=../.cache/maven' verify
```

产物：`backend/target/life1000-backend-0.0.1-SNAPSHOT.jar`。设置好上述环境变量后也可直接启动：

```powershell
java -jar target/life1000-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

前端：

```powershell
cd D:\code\myself\Life1000\frontend
npm run build
```

默认构建执行不需要数据库凭据的服务单元测试及 MVC / JWT 测试；后者仅测试 HTTP 层和真实 JWT 签发校验，Service 使用 mock，不代表数据库验收。没有 H2，也没有 skeleton 测试。

**真实 MySQL 验证：**在已经设置 DB_USERNAME / DB_PASSWORD（可选 DB_URL）的 PowerShell 终端中执行：

```powershell
cd D:\code\myself\Life1000\backend
.\scripts\Test-MySql.ps1
```

等价 Maven 命令：

```powershell
mvn '-Dmaven.repo.local=../.cache/maven' '-Dtest=MysqlIntegrationTest' test
```

- 集成测试固定激活 `mysql`，使用真实 DataSource、Flyway、Mapper、Service、Controller 和 JWT。测试登录密码与 JWT 密钥在内存中随机生成，不需要实际应用登录凭据，不保存数据库凭据。
- 覆盖 `001 空白 → 创建 → 查询 → 修改 → 删除 → 查询 404 / 数据库无记录 → 001 重新为空白`，并检查可重新创建、相邻编号不移动。
- 验证 API 和直接 SQL 的编号越界/唯一约束，以及 Category CRUD、分类删除置空、搜索编号保留、可选字段清空。
- 测试要求 001、002、1000 为空白；遇到已有数据会失败而不会清空它。建议使用专门的空测试数据库，通过 DB_URL 指向它。
- 每个测试使用事务并回滚测试记录；MySQL 自增序列可能前移，Flyway DDL / 版本记录会保留。不会重建或删除已有数据库。
- `mvn verify` 检测不到 DB_USERNAME 时，3 项 MySQL 集成测试会明确跳过；检测到时会实际运行。辅助脚本缺少凭据会报错，不会把跳过当作成功。

Phase 1 的真实 MySQL 连接、DDL、CRUD 和约束验收已由用户在本机确认通过。当前会话的 Phase 2 验证结果见下文。

技术参考：[MyBatis-Plus](https://baomidou.com/getting-started/install/)、[Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)。

## Phase 2：人生千事页面

主要实现文件：

- `frontend/src/views/GoalsView.vue`：搜索/筛选栏、卡片/列表切换、加载和错误状态、连续分批渲染。
- `frontend/src/goals/slots.ts`：固定 001～1000 位置映射。正常浏览补齐空白，筛选时仅展示匹配记录，始终按原始 slotNo 排序。
- `frontend/src/components/GoalTile.vue`：卡片和列表共用的编号、标题、分类、状态与完成入口。
- `frontend/src/components/GoalCreateDialog.vue`、`ModalDialog.vue`：轻量新增窗口、焦点约束和 Escape 取消；标题必填，分类/为什么想做可选。
- `frontend/src/api/goals.ts`、`http.ts`、`session.ts`：复用后端接口，统一携带 JWT，401 时清理令牌并进入登录页。
- `frontend/src/views/LoginView.vue`：调用既有单用户登录接口的最小登录页，登录成功回到首页。
- `frontend/src/views/GoalDetailPlaceholderView.vue`：事项标题占位、返回入口、二次确认清空编号；没有完整详情编辑。
- `frontend/src/styles/goals.css`：PC 五列布局、270px 卡片、简洁列表、固定搜索栏。

进入 `/goals` 默认是卡片视图。未登录先进入 `/login`；使用后端环境变量中配置的 Life1000 账号密码登录，再点击顶部“人生千事”。浏览器只在当前标签页的 sessionStorage 保存访问令牌，不保存密码或签名密钥。

无筛选时通过 `GET /api/goals/range?fromSlot=1&toSlot=1000` 一次读取事项数据，再每批渲染 50 个位置。滚动接近底部时继续渲染，也有“继续向下展开”按钮作为回退，没有分页 UI、虚拟列表库或全局状态库。读取失败时显示错误和重试，不把未知数据误显示为空白。

复用的接口：

- `POST /api/auth/login`
- `GET /api/categories`
- `GET /api/goals/range`
- `GET /api/goals/search`（标题、分类、状态组合筛选）
- `POST /api/goals/{slotNo}`
- `GET /api/goals/{slotNo}`
- `DELETE /api/goals/{slotNo}`
- 原有基础连接检查继续使用 `GET /api/health`，现在也携带令牌。

**没有新增 API，也没有修改后端业务、DDL 或 PRD。** 新增成功直接更新原位置；清空编号后返回千事页重新读取数据，其他编号不移动。新增遇到 409 冲突时刷新真实记录，不覆盖另一个窗口已创建的事项。搜索采用短暂防抖和请求取消/序号校验，避免旧响应覆盖新筛选。

卡片不显示原因长文、评分等额外字段。无封面显示“✦ 尚无影像”，不请求网络默认图片。卡片与列表的快速完成 `○` 入口明确禁用并提示尚未开放；不会调用完成或状态变更接口。附件、封面接入、完整详情、完成弹窗、完成证明/感想/评分及时间轴联动仍留给后续 Phase。

### Phase 2 验证

```powershell
cd D:\code\myself\Life1000\frontend
npm ci --cache ../.cache/npm
npm run build
npm run test:e2e
```

浏览器测试默认使用本机 Edge，无需下载额外浏览器；如仅安装 Chrome，可先设置 `$env:PLAYWRIGHT_CHANNEL = 'chrome'`。测试启动自己的 Vite 服务（5180 端口），完成后关闭。Playwright 是本阶段唯一新增的开发依赖，生产页面没有新增运行时依赖。

本次结果：

- `npm run build`：TypeScript 检查与生产构建通过。
- `npm run test:e2e`：9 项真实浏览器测试全部通过。覆盖登录、五列布局、完整 001～1000 连续滚动、空白位置、037 创建并原位显示、取消/确认删除后恢复空白、两种视图、标题搜索、分类/三种状态筛选，以及编号保持不变。
- 额外覆盖空白标题、无结果、请求失败/重试、创建失败保留输入、创建冲突不覆盖、401 重登录、旧搜索响应被忽略及 1366px/窄屏布局。
- 已查看 1440px 页面截图：卡片约 232 × 270px，主体五列，两行约十个位置；没有网络图片。
- 后端 `mvn verify`：构建通过，24 项测试通过、3 项真实 MySQL 测试因当前进程没有 DB_USERNAME/DB_PASSWORD 而跳过。Phase 1 的真实 MySQL 验收已由用户确认完成。

**验证边界：**浏览器交互在真实 Edge 中执行，但 API 响应由测试模拟，不写入用户数据库。它验证前端请求和显示行为，不等同于本次重新完成浏览器到真实 MySQL 的端到端验收。本机启动真实后端后，可按同样步骤在空白编号创建、筛选、进入占位页并确认清空。

## Phase 3：事项详情

`/goals/:slotNo` 已替换占位页，默认以私人记录册的阅读模式展示。点击“编辑”才出现标题、分类、状态和为什么想做的表单。未完成事项仅可切换未开始/进行中；已完成事项可以编辑文字，不能经基础接口完成或撤销完成。

主要文件：

- `frontend/src/views/GoalDetailView.vue`、`styles/detail.css`：阅读/编辑状态、横幅封面、条件、我的记录、全部附件、二次确认删除。
- `frontend/src/api/details.ts`、`api/http.ts`：JSON、multipart 上传及带 JWT 的文件读取。
- `frontend/src/components/AttachmentImage.vue`、`GoalCover.vue`：Blob 图片预览与千事卡片封面；卸载时释放图片 URL。
- `backend/src/main/java/com/life1000/goal/GoalDetailService.java`、`GoalDetailController.java`：复用既有 Entity / Mapper，实现条件、记录、附件与封面。
- `LocalFiles.java`、`AttachmentCleanup.java`：UUID 文件存储、路径检查、事务完成后的文件处理和中断重试；原 LifeGoalService 删除同步纳入文件清理。
- `backend/src/test/java/com/life1000/goal/GoalDetailServiceTest.java`、`Phase3MysqlIntegrationTest.java`、`frontend/tests/details.spec.ts`：文件/业务测试、真实 mysql 集成入口及浏览器交互测试。

新增 API（全部需要 JWT）：

| API | 用途 |
| --- | --- |
| GET /api/goals/{slotNo}/check-items | 读取按稳定顺序排列的条件 |
| POST /api/goals/{slotNo}/check-items | 新增条件，JSON：content、completed |
| PUT /api/check-items/{id} | 修改内容或勾选状态，JSON：content、completed |
| DELETE /api/check-items/{id} | 删除条件 |
| GET /api/goals/{slotNo}/records | 读取过程记录 |
| POST /api/goals/{slotNo}/records | 新增记录，JSON：recordDate、content |
| PUT /api/records/{id} | 修改日期/正文 |
| DELETE /api/records/{id} | 删除记录及 PROCESS 附件 |
| GET /api/goals/{slotNo}/attachments | 汇总 GENERAL / PROCESS 附件 |
| POST /api/goals/{slotNo}/attachments | multipart：file、stage，可选 recordId |
| GET /api/attachments/{id}/content?download=false | 认证图片预览；其他文件强制下载 |
| GET /api/attachments/{id}/content?download=true | 强制下载文件 |
| DELETE /api/attachments/{id} | 删除元数据及物理文件 |
| GET /api/goals/{slotNo}/cover | 返回有效封面元数据；无图片返回 204 |
| PUT /api/goals/{slotNo}/cover/{attachmentId} | 指定属于当前事项的图片为封面 |
| PUT /api/attachments/{id}/home-background | JSON：allowed，切换图片候选标记 |

额外的 GET 条件/附件/封面接口用于详情读取和共用封面规则；content 接口用于认证预览与下载，避免公开上传目录。基础信息编辑继续复用 PUT /api/goals/{slotNo}，删除继续复用 DELETE /api/goals/{slotNo}。没有新增与 Phase 3 无关的业务 API。

封面优先使用 cover_attachment_id 指定的本事项图片，否则按 created_at、id 取最近上传的图片，无图片时保持无图。删除指定图片后既有外键自动把引用置空，并按上述规则回退。过程附件通过 record_id 关联记录，必须同属一个 goal_id；普通附件的 record_id 为空。删除记录/事项使用既有外键级联删除关联行，并在数据库事务提交后清理文件。

附件默认位于后端工作目录的 `uploads/goals/027/<UUID>` 等位置；可通过 `LIFE1000_UPLOAD_DIRECTORY` 设置绝对目录（例如 `D:\Life1000Data\uploads`）。原文件名、大小、MIME、图片标记和相对路径只作为元数据存入 MySQL。磁盘文件名由后端生成，路径限制在上传根目录内，并拒绝符号链接路径。上传目录已被 Git 忽略。单文件上限 50 MB，没有引入云存储或新依赖。

本阶段验证：前端构建通过；12 项浏览器测试通过；后端构建通过，32 项测试通过，4 项真实 MySQL 测试因本进程缺少 DB_USERNAME / DB_PASSWORD 跳过。浏览器测试模拟 API，后端文件测试使用真实临时目录；不代表已完成本阶段真实链路验收。

Windows 启动、mysql 测试命令及完整人工验收步骤见 [Phase 3 验证文档](docs/PHASE3-VALIDATION.md)。Phase 4 完成系统及其后续功能均未实现；首页背景本阶段仅保存附件候选标记。

## Phase 4：完成系统与时间轴

已接通人生千事卡片/列表和详情页的完成入口。两个入口共用 `CompletionDialog.vue`：完成日期必填且默认当天，感想、1～5 星评分和证明附件可空。文件只在确认时上传，取消不写入数据。完成后显示约 4 秒的“第027件，已经成为回忆。”反馈。

完成档案继续使用既有 `goal_completion`。完成时锁定事项，在同一事务内保存档案、COMPLETED 状态及 COMPLETION 附件；附件继续调用 Phase 3 上传与文件清理代码。撤销仅恢复完成前状态并保留档案/文件，再次完成更新原档案。已完成事项可以继续记录、编辑文字和完成档案、补充或删除证明。

新增/扩展接口：

| API | 行为 |
| --- | --- |
| POST /api/goals/{slotNo}/complete | 完成；JSON 或 multipart，重复完成返回 409 |
| GET /api/goals/{slotNo}/completion | 读取包括撤销后保留的档案；没有返回 204 |
| PUT /api/goals/{slotNo}/completion | 修改已完成档案，可同时补充证明 |
| POST /api/goals/{slotNo}/uncomplete | 恢复完成前状态，不删除档案和证明 |
| GET /api/timeline | 年份摘要：year、count |
| GET /api/timeline/{year} | completedDate、slotNo、title 和 year |
| 现有附件接口 | 支持 COMPLETION，record_id=NULL；须通过正式完成流程后上传新证明 |
| 现有事项查询 | 增加只读 completedDate，用于已完成卡片；不新增数据库列 |

完成 JSON 字段为 `completedDate`、`completionNote`、`rating`。multipart 使用 application/json 类型的 `completion` 部分及多个 `files` 部分；本次证明总量最多 50 MB，可完成后分次补充。

`/timeline` 按年份降序显示年度卡片，默认展开当前年，展开后只显示日期、原编号和标题。查询直接 JOIN 当前完成档案与 status=COMPLETED 的事项；年内按日期、编号升序，无冗余时间轴表。日期修改、撤销和删除会自然改变后续查询结果。

主要文件：后端 `CompletionService/Controller`、`TimelineService/Controller`、`GoalCompletionMapper`；前端 `api/completion.ts`、`CompletionDialog.vue`、`CompletionFeedback.vue`、`TimelineView.vue`，以及既有详情/卡片/列表的接入修改。数据库 DDL、PRD、存储目录和依赖均未改变。

验证：前端构建通过，15 项模拟 API 的 Edge 浏览器测试通过；后端构建通过，47 项测试通过、5 项真实 MySQL 测试因本进程没有凭据跳过。已新增真实 MySQL 完成闭环与磁盘回滚测试，未声称本次完成真实浏览器到数据库的链路验收。

Windows 命令、测试分类、数据一致性说明及人工验收清单见 [Phase 4 验证文档](docs/PHASE4-VALIDATION.md)。Phase 5 / Phase 6 尚未实现。
