# Life1000

帮助自己记录人生，而不是管理人生。

产品规格以 [docs/PRD.md](docs/PRD.md) 为准。本次仅建立 **Phase 0：项目骨架**，不包含登录认证、事项 CRUD、业务数据库表、附件、时间轴数据、统计或其他后续阶段功能。PRD 保持原样。

## 目录

```text
Life1000/
├─ AGENTS.md
├─ docs/PRD.md
├─ frontend/
│  ├─ src/
│  │  ├─ api/            # 基础 HTTP 通信
│  │  ├─ components/     # 基础连接检查组件
│  │  ├─ router/         # PRD 中的八个路由
│  │  ├─ styles/         # 暖米白与轻纸张纹理
│  │  ├─ views/          # 共用占位页面
│  │  ├─ App.vue
│  │  └─ main.ts
│  ├─ .env.example
│  ├─ package.json
│  └─ vite.config.ts
└─ backend/
   ├─ pom.xml
   └─ src/
      ├─ main/java/com/life1000/     # 启动类、基础健康检查
      ├─ main/resources/            # 默认配置与 MySQL 配置
      └─ test/java/com/life1000/     # 启动、接口与数据库探针测试
```

前端使用 Vue 3、TypeScript、Vite、Vue Router，基础请求使用原生 fetch。目前不需要全局状态库或 UI 组件库。后端使用 Spring Boot 3.5.16、Java 21、Maven、Spring JDBC 和 MySQL 驱动；持久化业务与认证留到 Phase 1。

## Windows 本地启动

准备 Node.js 22.12+（推荐 24 LTS）、JDK 21、Maven 3.6.3+。启用 MySQL 配置时需要 MySQL 8.0+ 及一个可访问的空数据库。

下面的命令使用 PowerShell。已在本机发现 `D:\JDK21` 和 `F:\apache-maven-3.8.8`；如果安装位置不同，请修改路径。环境变量仅影响当前终端，不修改系统设置。

### 1. 后端（终端一）

```powershell
cd D:\code\myself\Life1000\backend
$env:JAVA_HOME = 'D:\JDK21'
$env:Path = "$env:JAVA_HOME\bin;F:\apache-maven-3.8.8\bin;$env:Path"
java -version
mvn -version
mvn '-Dmaven.repo.local=../.cache/maven' spring-boot:run
```

默认使用 `skeleton` profile，不需要 MySQL 凭据，监听 `http://127.0.0.1:8080`。`/api/health` 返回 `{"status":"UP"}`，只验证后端 HTTP 通信，不代表数据库已连接。

### 2. 前端（终端二）

```powershell
cd D:\code\myself\Life1000\frontend
npm ci --cache ../.cache/npm
npm run dev
```

浏览器打开 <http://127.0.0.1:5173>。首页的“检查基础连接”按钮成功后显示“前后端通信正常”。顶部五个入口与 `/login`、`/goals/1` 均为占位页，没有登录表单或业务行为。

Vite 将 `/api` 请求代理到后端，同源访问，不需要开放跨域权限。如果后端端口变化：

```powershell
Copy-Item .env.example .env.local
# 编辑 .env.local 中的 API_PROXY_TARGET，随后重启 Vite。
```

前端环境文件不能保存数据库密码或其他服务端秘密。

### 3. 可选：启用真实 MySQL 连接

Phase 0 已准备连接配置及 `SELECT 1` 探针，没有建表脚本或自动建库行为。先自行准备名为 `life1000`、使用 `utf8mb4` 的空数据库以及有权访问它的账号。

停止默认后端后，在后端终端中执行：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/life1000?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
$env:DB_USERNAME = Read-Host 'MySQL 用户名'
$dbCredential = Read-Host 'MySQL 密码' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbCredential).Password
mvn '-Dmaven.repo.local=../.cache/maven' '-Dspring-boot.run.profiles=mysql' spring-boot:run
```

从另一终端验证：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health/db
```

成功返回 `status: UP`；连接失败返回 HTTP 503 与 `status: DOWN`。默认 `skeleton` profile 下该探针返回 404。只启用 `mysql`，不要同时启用 `skeleton`。真实连接由首次数据库探针请求触发，后端启动成功本身不代表数据库验证成功。

完成验证并停止后端后，可清除当前终端中的密码：

```powershell
Remove-Item Env:DB_PASSWORD
```

## 构建与验证

前端（包含 TypeScript 检查）：

```powershell
cd D:\code\myself\Life1000\frontend
npm run build
# 可选：预览构建产物，后端仍需单独运行。
npm run preview
```

产物在 `frontend/dist/`，预览地址为 <http://127.0.0.1:4173>。

后端（先按上文设置 JAVA_HOME 与 PATH）：

```powershell
cd D:\code\myself\Life1000\backend
mvn '-Dmaven.repo.local=../.cache/maven' verify
java -jar target/life1000-backend-0.0.1-SNAPSHOT.jar
```

测试覆盖无需数据库的应用启动、健康接口、默认不启用数据库探针，以及模拟数据库连接成功和失败。模拟测试不能替代真实 MySQL 验证。

Vite 开发代理可直接验证：

```powershell
Invoke-RestMethod http://127.0.0.1:5173/api/health
```

未来正式部署静态文件时，需要服务器为前端 history 路由回退到 `index.html`，并把 `/api` 反向代理到后端；`vite preview` 仅用于本地验收。

## 本阶段范围与本地环境说明

- 本次验证：`npm run build` 成功；Maven `verify` 成功，4 项测试通过；打包后的 JAR 启动成功，后端直连和 Vite `/api/health` 代理均返回 `UP`；八个前端路由入口均返回 HTTP 200。
- 浏览器自动化运行时启动失败，未完成浏览器内的布局、导航点击和连接按钮验收；路由 HTTP 检查仅验证 SPA 入口可访问，不等同于浏览器交互测试。

- Git 仓库已初始化；忽略 IDE 配置、依赖、构建产物、缓存、环境文件与未来上传目录。
- 不创建业务数据库结构，也不读取或修改已有 MySQL 数据。
- 本地 MySQL 服务可用，但本任务没有提供数据库账号密码；真实 MySQL 成功连接需按上面的步骤验证。
- 首次安装 npm / Maven 依赖需要联网。本机 Maven 全局配置使用阿里云镜像；若下载失败，请检查网络与 Maven 的 `settings.xml`。
- 技术版本要求参考 [Vite 文档](https://vite.dev/guide/) 与 [Spring Boot 3.5 文档](https://docs.spring.io/spring-boot/3.5/system-requirements.html)。
