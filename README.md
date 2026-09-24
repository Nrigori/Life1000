# Life1000

> 帮助自己记录人生，而不是管理人生。

Life1000 是一个单用户、PC 优先的个人记录 Web 应用，用来记录「人生想做的 1000 件事」。

它不是 Todo List，也不强调截止日期、打卡、积分或效率，而是希望把想做的事、过程、照片、文件和完成后的回忆长期保存下来。

## 功能

- **人生千事**：固定 `001 ~ 1000` 个位置，删除后编号不会重排
- **事项详情**：记录「为什么想做」、完成条件、过程记录和附件
- **附件管理**：支持图片、PDF、DOCX、XLSX、TXT、ZIP 等常见文件
- **完成档案**：完成日期、感想、评分、证明附件，并支持撤销完成
- **时间轴**：按年份查看已经完成的人生事项
- **首页**：摄影背景、随机金句、日期、年度进度和完成统计
- **金句收藏**：保存、搜索并控制是否参与首页随机展示
- **数据统计**：已写下、已完成、进行中、空白、附件和金句数量
- **设置**：首页背景、分类管理、文件信息
- **完整备份**：导出业务数据和附件 ZIP  
  > V1 仅支持导出，暂不支持导入恢复

## 技术栈

### Frontend

- Vue 3
- TypeScript
- Vite
- Vue Router

### Backend

- Java 21
- Spring Boot
- MyBatis-Plus
- Spring Security + JWT
- Flyway
- MySQL

### Local Runtime

- Nginx
- PowerShell 7
- 本地文件系统附件存储

## 运行结构

```text
Browser
   │
   ▼
http://localhost
   │
   ▼
Nginx :80
   ├── frontend/dist
   └── /api/*
          │
          ▼
     Spring Boot :8080
        ├── MySQL
        └── Local Upload Directory
```

后端和 Nginx 默认只在本机使用，不需要把 Vite 开发端口暴露给日常使用。

## 环境要求

建议准备：

- Windows
- PowerShell 7.2+
- Node.js 22.12+
- JDK 21
- Maven 3.6.3+
- MySQL 8.0.16+
- Nginx for Windows

## 快速开始

### 1. 创建数据库

先在 MySQL 中创建空数据库：

```sql
CREATE DATABASE life1000
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

应用首次启动时会通过 Flyway 自动创建业务表。

### 2. 创建本地私有配置

在项目根目录执行：

```powershell
Copy-Item .\local-config.example.ps1 .\local-config.ps1
```

然后编辑 `local-config.ps1`，填写：

- `JAVA_HOME`
- `MAVEN_HOME`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `LIFE1000_USERNAME`
- `LIFE1000_PASSWORD`
- `LIFE1000_JWT_SECRET`
- `LIFE1000_UPLOAD_DIRECTORY`
- `NGINX_HOME`

生成 JWT Secret：

```powershell
[Convert]::ToBase64String(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

`local-config.ps1` 已加入 `.gitignore`，请不要提交真实密码或密钥。

### 3. 构建

```powershell
.\build-local.ps1
```

### 4. 启动

```powershell
.\start-local.ps1
```

启动成功后访问：

```text
http://localhost
```

### 5. 停止

```powershell
.\stop-local.ps1
```

## 开发模式

前端：

```powershell
cd frontend
npm ci
npm run dev
```

后端：

```powershell
cd backend
mvn spring-boot:run
```

开发模式下前端通过 Vite 将 `/api` 代理到本机 Spring Boot。

## 测试

前端：

```powershell
cd frontend
npm run build
npm run test:e2e
```

后端：

```powershell
cd backend
mvn verify
```

真实 MySQL 集成测试建议使用专门的测试数据库，避免影响正式数据。

## 数据与隐私

Life1000 V1 是单用户应用：

- 不提供注册
- 不提供多用户系统
- 不提供社交功能
- 不上传附件到第三方云存储
- 数据库存储业务数据
- 附件保存在本地文件系统
- 密码和 JWT Secret 通过本地私有配置提供
- 上传目录和私密配置不会提交到 Git

## 备份

设置页可以导出：

```text
Life1000_Backup_YYYY-MM-DD.zip
```

压缩包包含：

```text
life1000.json
images/
documents/
backup-info.json
```

建议在开始长期记录后定期导出备份。

## 项目结构

```text
Life1000/
├─ frontend/                 # Vue 前端
├─ backend/                  # Spring Boot 后端
├─ deploy/nginx/             # Nginx 配置
├─ docs/                     # PRD 与运行/验收文档
├─ build-local.ps1           # 本地生产构建
├─ start-local.ps1           # 一键启动
├─ stop-local.ps1            # 一键停止
├─ local-config.example.ps1  # 本地配置模板
└─ README.md
```

## 产品原则

Life1000 的核心并不是「完成 1000 个任务」。

它更像一本长期保存的人生手册：

> 想做的事可以慢慢写，过程可以慢慢记录，完成之后，它就成为回忆。

## Windows 桌面版

Windows Desktop 版使用 Tauri 2 内嵌现有 Vue 生产文件，并管理 Spring Boot 子进程。完成首次配置后，双击 Life1000 即可使用，无需 Nginx 或外部浏览器。现有 Web 版和本地运行脚本继续保留。

当前 Desktop V1 仍需本机 Java 21+、MySQL 和 WebView2。首次运行须配置 `%LOCALAPPDATA%\Life1000\desktop-local-config.json`，填入本机私有配置并指向原有附件目录；真实配置不得提交 Git。

构建、私有配置和验收方式见 [桌面运行说明](docs/DESKTOP-RUN.md)。Web 版运行方式保持不变。
