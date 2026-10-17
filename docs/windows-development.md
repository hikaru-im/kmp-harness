# Windows 开发环境

在仓库根目录的 PowerShell 执行以下命令：

```powershell
.\scripts\dev.ps1 check
.\scripts\dev.ps1 build-desktop
.\scripts\dev.ps1 desktop
```

Kotlin wrapper 固定使用 Toolchain 0.11.1，自动准备自身运行时及各模块所需的 JDK。
更新后的桌面端通过模块配置强制使用 JetBrains Runtime 25，由工具链自动下载；
其他模块按各自配置选择 JDK。
不要求全局 `java` 或 `kotlin` 命令。`.bat` 文件由 Git 保持 CRLF 换行。
桌面端已完成构建，并在当前 Windows 环境打开 KMP Harness 窗口。
脚本启动桌面端时使用 `.local/harness` 作为项目独立配置目录。
直接执行 `.\kotlin.bat run -m jvm-app` 则沿用应用默认 home。

## 管理后台

当前 Node 24.21.0 满足项目要求，pnpm 按 packageManager 使用 11.7.0。
首次安装或锁文件变化时执行：

```powershell
Push-Location backend/ui
pnpm install --frozen-lockfile
Pop-Location
.\scripts\dev.ps1 ui
```

开发地址为 http://127.0.0.1:5999，默认 API 指向本机后端 48080。
数据库在远程服务器不影响这个 API 地址：本机后端负责连接远程数据库。
若 API 后端也部署在服务器，可在
`backend/ui/apps/web-antdv-next/.env.development.local` 覆盖 `VITE_BASE_URL`。
该文件属于本地配置，不提交 Git。

依赖安装和 Vite 启动已验证，但现有路由中的 agent、crm、iot、bpm、im、mes、ai、mall
引用了缺失的页面文件，页面加载会报错；这些源码问题需要独立修复。

## 后端

仅验证编译，不连接数据库：

```powershell
.\scripts\dev.ps1 build-backend
```

当前后端构建受 `com.anji-plus:captcha:1.4.0` 阻塞：Maven Central 的 JAR
实际 SHA-1 为 `9f2e7d0afe0f81792bf60f27281a830bac92fc21`，发布的 `.jar.sha1`
却为 `8d4706e197f24e1d00523589ab0cc125855a51ba`，Kotlin 工具链最终报告缓存缺少 JAR。
已从 `repo1.maven.org` 和 `repo.maven.apache.org` 分别下载验证，结果相同。
需要在后续依赖修复中选择校验一致的版本或来源，再验证后端构建。

已有服务器 PostgreSQL、Redis 时，无需启动本地数据库。
连接信息保存在 Git 忽略的 `.local/backend.env.json`：

```powershell
New-Item -ItemType Directory -Force .local | Out-Null
Copy-Item scripts/backend.env.example.json .local/backend.env.json
# 编辑 .local/backend.env.json 后再启动
.\scripts\dev.ps1 backend
```

脚本不输出密码，环境变量只在后端启动期间生效。
当前 Spring 配置没有读取 `REDIS_PASSWORD`，需要密码或 TLS 的服务器须先补齐相应配置。
不要对已有数据库直接执行会删表的初始化 SQL；先确认 schema 状态。
本次下载的本地数据库工具保存在 `.local/tools`，没有初始化数据库或启动数据库服务。

## 移动端

Android 需另外安装 Android SDK 并配置设备；此次未安装。
iOS 编译和模拟器运行需 macOS 与 Xcode。
