# 终端依赖与平台装配分析

日期：2026-10-09。当前状态：三个独立 /tmp 探针已通过 resolver、Kotlin 编译、运行链接、本机真实 PTY 与 resize；仓库正式依赖闭包、PTY 基础和页面接线待验收。下文固定证据中的“尚未”保留为研究时的历史状态，最新结果见末节。Cron R6 已通过父线程 134/134 验收。

## 固定证据

官方源码按固定提交读取，发布 POM/JAR 单独核对，不把源码 HEAD 自动当成发布制品源码证明。完整 URL、字节数、SHA256 与本地副本见 `terminal-dependency-evidence-2026-10-09/manifest.json`。研究原始目录为 `/tmp/harness-terminal-dependency-analysis-20261009/`。

| 候选 | 本次核对版本和来源 | 固定源码提交 | 实际证据与边界 |
| --- | --- | --- | --- |
| pty4j | `org.jetbrains.pty4j:pty4j:0.14.0`，Maven Central | `7dd1378d167cb451fe6c50f4b73e129ec2925897` | builder 支持初始 rows/columns、cwd/env；process 提供 setWinSize/getWinSize。JAR 含 Darwin libpty.dylib 和 spawn-helper；两者经 file 检查均为 x86_64/arm64 universal。尚未加载或启动，不等于本机 PTY 已可用 |
| JediTerm core | `org.jetbrains.jediterm:jediterm-core:3.76`，JetBrains intellij-dependencies | `1b354a63f9845aee3474b0749af0a62e406f0076` | emulator/connector 的 JVM 候选。TtyConnector 接口具有读、写、resize、waitFor、close；core 仍引用 java.awt.Dimension，不能直接放进 common 源集 |
| JediTerm UI | `org.jetbrains.jediterm:jediterm-ui:3.76`，同上 | 同上 | 当前 ui 源码是 Swing widget；可作实现参考。嵌入 Compose 的焦点、独立弹层、主题与离屏截图尚未验证 |

这些是本次固定候选版本，不是持续跟踪最新版本的承诺。JediTerm README 的模块描述仍提到旧 terminal/pty 划分；本次以当前 core/ui build 文件及发布 POM 为依据。

## 依赖与分发证据

- pty4j POM：Kotlin stdlib 2.4.20、JNA/JNA-platform 5.19.1、SLF4J 2.0.19；仓库当前 shared JVM 显式 JNA-platform 5.18.1。尚未运行实际 resolver 或编译，不能宣称版本兼容。后续必须保存最终解析依赖和重复 native 库检查。
- JediTerm core/ui POM：Kotlin stdlib 2.4.0、SLF4J 2.0.9、annotations 24.0.1；core build 的 JVM target 为 11。当前 Harness 实际编译兼容性仍待验。
- pty4j LICENSE/POM 标记 EPL 1.0。JediTerm README 明确提供 Apache 2.0/LGPLv3 双许可选择，源码有两份许可证；发布 core/ui POM 只写 LGPL 3.0。保留这一差异，不将发布 metadata 改写为已消除歧义。
- 本次三个发布 JAR 未找到 license/notice entries。正式分发须保留对应许可证与所需告知；此处只记录制品证据，未完成应用分发集成。

## 建议装配方向

优先候选为 **pty4j + JediTerm core + Compose renderer**。理由是复用成熟的 PTY/emulator，同时保持现有 Compose 面板与普通/紧凑窗口的可测绘制。Swing UI 的嵌入成本尚未证明更低；不在此阶段把整套 Swing widget 作为已确定方案。

CodeGraph 本轮核对了 `apps/shared/.../app/ClientUiSlots.kt`、`DesktopClientCatalog.kt` 和 `modules/ui-slot/.../UiSlotKey.kt`：当前 schema 未声明 terminal renderer slot。后续需要 typed terminal props 和启动时注册的 JVM renderer；公共 props 不泄露 JediTerm、Swing、java.awt 类型。遵守现有编译期 schema 与启动冻结，不引入 JS、HMR、动态 ClassLoader。未提供 renderer/capability 的平台显示真实 unavailable 状态。

后台命令和交互 shell 必须保留各自身份。已有 ExecProcessProducer/SessionBackgroundProcesses 继续承担后台命令；打开输出面板或重建 renderer 不创建 shell。PTY 明确创建/关闭，归属需包含 Host、Thread、TerminalSessionId、cwd 和 owner。旧 owner、旧 revision、断线或替换 connection 的回包不得更新新视图。

现有 bounded tail 不能作为可信 emulator 初始化流：可能从 escape、颜色或光标状态中间开始。ANSI 集成前应确定完整起始快照/有序增量与截断恢复策略；仅去掉 escape 字符或重新播放任意 tail 不构成终端仿真。保留原始输出复制和 truncated 提示。

## 下批实施前的必要结论

1. 固定 JVM 依赖和 repository 配置，实际 resolver/编译及 native 提取位置证据。
2. 明确 renderer 的 typed props、screen snapshot、增量顺序、重置与销毁，以及静态 slot 装配路径。
3. 明确 PTY typed service 的 create/read/write/resize/close 能力和 Unknown 不重发规则；不能越界复用 pipe stdin 协议来宣称 PTY。
4. 将有限写集和回归类写进独立实施合同，再交现有 SHUAIAPI/gpt-6.1-sol 工作者。Cron 验收前不并发修改 shared 面板或 Host 协议。

验收至少包括真实 TTY 判断、stty rows/cols 改变、UTF-8/ANSI 分块、CR 进度重绘、光标/清屏、alternate screen、退出/取消、精确归属、原始复制和 1440×900/600×800 浅深可达性。macOS 本机、远端 wire、其他 OS 分别报告，不能由 JAR 打包列表推导全平台运行通过。

## 连续解析与快照补充证据

15份固定JediTerm源码已逐项核对bytes/SHA并落盘于 `terminal-foundation-evidence-2026-10-09/manifest.json`。这批为API/线程语义证据，不是运行验收。

- ArrayTerminalDataStream缓冲耗尽即EOF，DataStreamIteratingEmulator.next捕获后设置永久EOF标记。每个PTY块不能作为独立流，也不能用resetEof声称恢复已经消耗的半个escape。候选应使用持续TtyConnector/TtyBasedArrayDataStream，阻塞read等待后续字节，真正流结束才EOF；UTF-8解码器同样跨read保留状态。
- TerminalTextBuffer提供screen/history、alternate与lock/unlock。读取屏幕时锁内深复制文字、样式和状态，锁外发布纯common DTO；不要保留TerminalLine、TextEntry、CharBuffer或TextStyle引用。TerminalLine.copy仍共享TextEntry，不能单凭copy认定深复制完成。阻塞PTY读取不得占用snapshot锁。
- TerminalStarter.start为同步解析循环；requestEmulatorStop只设置标记，必须由connector关闭真实输入来解除阻塞，再等待测试拥有的worker结束。PtyProcess输入和输出stream需显式取得并关闭，不能仅destroy process。
- TerminalStarter发送队列会捕获IOException并记日志，不能把成功入队作为write Applied。write需通过可观察的connector结果确认；可能已写入的失败为UnknownOutcome，不重发。resize同时更新仿真模型和真实PTY，不能仅重排Compose内容。

下一合同先限定JVM PTY/仿真基础，验证上述语义后再接Host/client和Compose静态renderer。common DTO放session-api、JVM基础放现有exec-jvm是当前候选划分；未派发、未修改生产或依赖配置。Cron验收前不并发修改这些模块。

## 本机依赖与 native 探针实际结果

证据归档：`terminal-native-probe-evidence-2026-10-09/manifest.json`，22 份输入及日志逐项核对 SHA。源码和 YAML 以 `.txt` 归档，不加入生产源码统计；未复制隔离 HOME 的工具缓存。

| 探针 | 实际执行 | 结果 |
| --- | --- | --- |
| R1 | wrapper 0.11.1、compiler 2.3.10；pty4j 0.14.0 / JediTerm core 3.76；编译并调用 Kotlin TerminalTextBuffer / TermSize 和 pty4j API | build / run exit 0；未跳过 metadata 检查 |
| R2 | Mac OS X/x86_64；仅启动测试拥有的 `/bin/sh -c`，临时 cwd/HOME；检查三个标准流真实 TTY、`stty size` 和 setWinSize | 初始 24×80，调整后 30×100；进程正常 exit 0、读取线程结束；finally 关闭 streams/process |
| R3 | 在 R2 加入仓库现有 JNA-platform 5.18.1 声明；隔离 java.io.tmpdir / user.home，启用 JNA 实际加载日志 | 相同 TTY / resize / exit 验证通过；native 从隔离路径加载 |

R3 实际 runtime 依赖图已归档。stdlib 从声明的 2.3.10 提升至 2.4.20；JNA-platform 5.18.1 提升至 5.19.1，JNA 单一解析版本为 5.19.1；SLF4J 解析为 2.0.19。resolver 的版本提升 warning 保留。native.log 记录 JNA dispatch 来自 jna-5.19.1.jar，libpty.dylib 来自隔离 native-tmp/pty4j-darwin 目录。

以上是小项目真实执行证据。正式 exec-jvm / JVM 应用的完整依赖闭包、ANSI 状态、生命周期和 Host/Compose 接线仍须验证；不能从小探针推导整个应用或其他 OS 兼容。下一批合同见 `terminal-pty-foundation-contract-2026-10-09.md`。
