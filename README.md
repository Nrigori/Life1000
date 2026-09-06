# Life1000

帮助自己记录人生，而不是管理人生。产品规格以 [docs/PRD.md](docs/PRD.md) 为准。

当前完成 **Phase 0 骨架和 Phase 1 数据库 + 后端基础**。前端保持原有占位页面。本阶段不实现附件、完成系统、时间轴、金句、统计和设置等后续业务，也不修改 PRD。

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

访问 <http://127.0.0.1:5173>。`/login` 仍是占位页，本次不做前端登录交互；使用下面的后端登录接口验收。由于按 PRD 保护了所有 `/api/**`，原首页“检查基础连接”按钮未携带 JWT 时会被返回 401，本阶段不通过放开健康检查来绕过认证。

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

本次环境验收：前端构建通过；后端 `verify` 通过，24 项自动化测试通过、3 项 MySQL 集成测试因未提供给当前进程的数据库凭据而跳过。用户已在本机验证 Phase 0 的数据库连接；**本次 DDL 在真实 MySQL 上执行及完整 CRUD 流程仍待按以上命令本机验收**。

技术参考：[MyBatis-Plus](https://baomidou.com/getting-started/install/)、[Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)。
