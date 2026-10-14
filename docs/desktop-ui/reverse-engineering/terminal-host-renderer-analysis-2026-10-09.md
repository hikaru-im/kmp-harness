# 终端 Host 接线与静态 renderer：后续分析

日期：2026-10-09。当前状态：PTY 基础五文件已由既有 `SHUAIAPI/gpt-6.1-sol` 工作者交付并释放。父线程 R3 在同一 2,220 输入 manifest 下实际通过五类 **38/38**，无失败、错误或跳过；完整 JVM 应用 build exit=0，正式 runtime 依赖图已核对并归档。Host/Local 合同已派发；首轮因工作者压缩后误回旧插件任务而未写入 Host 文件，现按 H1 Host、H2 Gateway、H3 Local 分阶段继续同一工作者。renderer、语义输入和 remote 尚未验收。

## 当前已核对的入口

先经精确路径 CodeGraph 读取当前源码。这里列出的能力是现有实现，不从旧 ASAR 分析中的“缺失”描述推导当前状态。

| 入口 | 当前事实 | 接线需要处理的事 |
| --- | --- | --- |
| `modules/session-api/.../SessionApi.kt:30` | core Session facade 已含后台 pipe、descendants 和 child history；DefaultSessionApi 通过 root Context 获取服务 | 新 PTY 是独立能力；按基础实际接口接入，缺服务仍返回带 requested identity 的 Unavailable |
| `modules/exec-jvm/.../ExecProcessProducer.kt:146` | `requireOwner/requireCurrent` 校验真实 root、SessionStore、AgentRegistry、Agent、Session 对象和 live store membership | PTY Host adapter 复用这种权限边界；文本 SessionId/opaque scope 不能单独授予新 shell 权限 |
| `modules/api/gateway/.../SessionApiGateway.kt:67` | root owner 注册 typed endpoint；后台 mutation 检查返回身份并保留 UnknownOutcome | PTY create/write/resize/close 各自有实际消费者；dispatch 后丢失确认不得变成安全重试 |
| `apps/jvm-app/.../SessionBackgroundProcessesRelay.kt:24` | Relay 的已有读取和 mutation helper 被 authenticated Host 路由调用 | 新 endpoint 必须显式进入 Host 路由；不能由 Local 可用推导 remote 已可用 |
| `apps/shared/.../SessionBackgroundProcessesRemote.kt:65` | mutation 只有一次 invoke；缺 endpoint 与丢失/malformed/mismatched ack 分开 | create 的不确定结果同样不能自动再创建；需通过独立读取核对 owned inventory |
| `apps/shared/.../panels/ThreadPanelsRepository.kt:112` | 后台输出 selection 有 inventory 前后身份核对；BackgroundTerminalKey 含 producer incarnation | 保留 pipe 选择及26项阅读基线；PTY 独立 key 和 snapshot，不把 pipe tail 当 emulator 初始化流 |
| `apps/shared/.../app/ClientUiSlots.kt:28` | 编译期 schema 没有 terminal renderer slot | 声明强类型 props/key，纳入 ClientUiSchema 的启动冻结；common 不传 JediTerm/AWT/Swing 对象 |
| `apps/jvm-app/src/main.kt:89` / `modules/bundle/desktop/.../DesktopBundle.kt:45` | 正式 Host 启动、LocalConnection、plugin catalog 已存在 | 最后验证正式启动装配和 shutdown；不能仅测试直接构造基础类就报告页面已可用 |

上述行号用于定位本次读取，交付后按新源码核对。ASAR 的页面组成和操作仍以 `terminal-subagents-implementation-contract-2026-10-07.md` 的固定证据为依据，不重新扫描整个 archive。

## 建议推进顺序

### 一、基础验收已完成

父线程核对唯一五文件与2,216输入基线，冻结2,220输入后实际通过 common4、真实PTY12、既有回归22，共5类38项。exec-jvm/jvm-app runtime graph 的 pty4j 0.14.0、jediterm-core 3.76、JNA/jna-platform 5.19.1、kotlin-stdlib 2.4.20 最终版本一致；jvm-app 的 stdlib-jdk7/jdk8 为2.3.10。这是依赖解析图检查，不是重复 JAR classpath 扫描。完整 JVM 应用 build exit=0。证据见 `verification/terminal-pty-foundation-2026-10-09/pty-r3/`。

### 二、接本机 Host 与 typed client 能力

目标是完整的 `真实 Session → Host owner → PTY service → Gateway → LocalConnection → client DTO` 链。先固定基础实际接口再写合同；这一批不把远端可用列为默认事实。

- root Host 拥有一个有限注册器，记录真实 Agent/Session 对象与该 Session 的基础实例。绑定的 canonical cwd 来自确认的 Session header。构造注册器、发现 capability、list/read 均不启动 shell；只有显式 create 启动。
- 请求到达时与 dispatch 前检查当前 store/registry 和真实对象；替换同文本 SessionId、换 root 或服务 incarnation 时旧身份拒绝。基础实例的 textual owner 检查是第二层，不代替 Host 权限检查。
- discovery 返回完整当前 owner 与容量/支持情况；create 返回真实 terminal key。create 回包丢失保留 Unknown，显式刷新 inventory 核对，不重发 create。
- client 与 core 继续采用各自 DTO，通过显式 mapping 逐字段保留 scope、Session、incarnation、terminal id、revision、lifecycle、styles、cursor、alternate、history 和 raw-tail。JSON 往返校验完整身份和未知结果，不只比较文字。
- 在途 create/write/resize/close 不因页面 refresh 被取消后重新执行。换 Host/身份/Connection、断线及旧请求回包由完整 owner 与 request generation 阻止发布。
- terminal 自然退出保留最终 snapshot；显式 close 证实清理后才移除。关面板只解绑 renderer；关闭 terminal 是独立操作。root shutdown 必须处理全部已拥有实例和仍 Unknown 的资源，不因移除 UI 丢失清理责任。
- 未接远端消费者的连接保持 Unavailable，不能执行父机 shell。未来 Relay 批次分别实现 Host dispatch、wire DTO、remote client 和 authenticated identity fencing，再做真实链路验收。

本批验证直接启动测试拥有的 Host、Session 和真实PTY，经 Gateway/LocalConnection 创建、输入、resize、读取和关闭。还需覆盖不存在/被替换的 Session、错误 root、陈旧 incarnation、create 丢确认后的单次启动与 shutdown。有限14文件、24个新增方法及11类70项实际门槛已固定于 `terminal-host-local-contract-2026-10-09.md`；基础五文件与旧测试保持验收字节。Host 已派发给同一指定模型，尚未交付或验收，不能记作已接线。

### 三、接静态 Compose renderer 与真实页面

- 声明 `SingleUiSlotKey<明确的 terminal props>`，纳入 ClientUiSchema；由 DesktopClientCatalog 冷启动注册。props 只包含深复制 client snapshot 和强类型输入/尺寸回调。无 renderer 的平台显示明确不可用。
- Thread 面板保持后台输出和子 Agent 的既有可达入口；新增显式“新建终端”、真实 terminal 选择、退出状态与独立关闭入口。打开面板、切 tab、刷新、主题切换和重建 renderer 都不得新建 shell。
- screen 按 terminal 列位置绘制文字/颜色/属性、cursor 和 alternate 状态；宽字符与 surrogate 的列位置须按实际 DTO 验证。raw tail 仅用于原始复制/诊断，不重新喂 parser。
- measure 后以真实字格尺寸计算 rows/columns，去重并逐次确认 resize。有限在途请求与最新尺寸意图分开；Unknown 时读取核对，不默默重发。
- 选区/Copy 消费真实 screen/history 文本；滚动历史不改变 parser。原始输出复制单独保留。显示的 cwd 先明确是创建目录，不能从 Session header 伪称 shell 当前目录。
- 焦点、键盘、IME、Paste 需要实际输入协议。普通“输入框发送一段文字”不能证明全屏终端应用可用。

## 在页面合同前必须补齐的输入设计

固定 JediTerm 源码已经揭示 renderer 不能始终发送硬编码的 ANSI 按键：

- `JediTerminal.java.txt:459` 的 `getCodeForKey` 使用当前 key encoder；`setApplicationArrowKeys`/`setApplicationKeypad` 会改变编码。
- `JediEmulator.java.txt:611` 根据真实模式序列切换 CursorKey、alternate、mouse mode 等。
- `TerminalDisplay.java.txt:41` 与 `JediTerminal.java.txt:971` 将 bracketed paste 状态投给 display。

这些文件属于已固定 SHA 的 `terminal-foundation-evidence-2026-10-09/manifest.json`，不是对发布 JAR 全部行为的运行证明。基础本批合同只承诺字符串 write 和 screen 模型；**应用方向键模式、按键编码、bracketed paste、鼠标协议和IME尚未验收**。

推荐后续增加 common 的语义按键/Paste 请求，由 JVM adapter 在模型锁内调用当前 JediTerm encoder，并通过同一受控 IO 路径发送；common API 不暴露 AWT keycode。若改为将模式快照送到 client 编码，须定义 revision/mode 一致性和相应测试。不要让 UI 绕过已验证的 write+flush/Unknown 语义。鼠标报告按保留范围另定，选区拖动与向应用发送 mouse events 要有明确消费者。

页面验收至少覆盖真实 Enter/Ctrl+C/方向键/Paste、应用方向键模式与 bracketed paste、焦点/IME、选区复制、history、resize、alternate、显式新建/关闭及 stale owner。普通/紧凑和浅深主题分别检查完整 HarnessApp 可达性与截图。完成本批截图布局之后，仍需原版同状态像素和原生窗口验收；macOS 结果不外推其他 OS。

## 本轮变更边界

R1 的 native 默认样式初始化 NPE 已由同一指定模型显式引用外层字段修复，失败历史保留。R2 common4及native12全部通过，旧Exec回归9/16通过、5 failures、2 errors；父线程传给旧测试的 JAVA_TOOL_OPTIONS 被子Java进程打印并破坏输出断言。仅验收环境改为native类专用，生产五文件和测试断言未改。R2与R3的全部2,220输入SHA一致。R3实际38/38和完整build通过；R1/R2/R3 XML、日志、交付与源码分别归档。本机 Host 已派发，renderer 与远端链路仍待。
