# 终端语义输入设计

日期：2026-10-09。状态：源码分析、设计及隔离的发布 JAR 编码探针已完成；尚未派发或验收输入实现。当前实施批次仍是 Host/Local，不修改其写集和已验收 PTY 基础五文件。本设计在 Host/Local 验收后形成独立合同。

## 已固定的证据

- JediTerm 官方 commit：`1b354a63f9845aee3474b0749af0a62e406f0076`。新增证据位于 `terminal-semantic-input-evidence-2026-10-09/manifest.json`，从官方 codeload archive 提取12个相关源码文件，逐文件 SHA256 固定；文件使用 `.txt` 后缀，只作参考。GitHub tree API 返回403 rate limit，随后使用固定 commit archive，未猜测不存在的路径。
- 核心键盘处理器真实路径是 `core/src/com/jediterm/terminal/emulator/keyboard/TerminalKeyEventProcessor.kt`，事件路径是 `core/src/com/jediterm/core/input/KeyInputEvent.kt`。3.76 发布 JAR 的 ZIP 清单确实含这些 class。父线程另行运行发布 JAR 的隔离编码探针，记录源码、命令、JAR SHA 和实际字节；没有运行 Host/Local 构建或测试。
- 已固定 `JediTerminal.java.txt:459` 通过实时 key encoder 获取按键码；464和473分别切换 application arrows/keypad。`isModelEnabled` 的实际拼写如此，位于877；不能据名称推断它包含所有模式。971的 bracketed paste setter 仅转发 display，因此 headless display 必须保存实际 paste 模式，不能由 UI 猜测。
- 已固定 `TerminalPanel.java:1845` 区分 pressed/typed，并在 pressed 已消费后避免 typed 重复输入；1954只发送 IME committed 字符。606的 paste 实现供换行和包裹语义参考；不引入 Swing renderer。
- 当前 `JvmPtyTerminalSessions.kt` 的 submit/nativeWrite/connector 使用同一有限 IO worker。输入编码在模型 gate 内完成，编码完成必须释放 gate，再直接进入该次 native write；不能在 worker 内调用 TerminalStarter.sendString/sendBytes 重新提交自己并等待。

## 输入模型与消费者

| 输入 | common/client 请求 | JVM 消费者 | 确认对象 |
| --- | --- | --- | --- |
| Enter、方向、功能键、Ctrl/Alt 组合 | 命名键与明确 modifiers；仅必要时携带字符 | 模型 gate 内实时 `TerminalKeyEventProcessor` / encoder | 完整 TerminalKey 的一次写入结果 |
| 普通文字、IME 提交 | 已提交的完整 UTF-8 文本 | 同一受控 write 路径 | 同上，按最终字节计容量 |
| Paste | 原始文本和显式 paste 语义 | 读取 headless 实际 bracketed 模式，规范换行并编码 | 同上，包裹后字节计容量 |
| preedit、选区与复制 | UI 局部状态，无写入请求 | renderer / clipboard | 不修改 PTY |

common/client 不暴露 AWT、JediTerm、原生 keycode 或 Process。命名键映射只在 JVM adapter；不要把客户端固定 ANSI 当作实时模式支持。普通文字与 IME 发送完整已提交文本，避免把 surrogate 拆成单个 Char。控制组合必须定义 keyChar 的来源或显式控制字符映射，不能假定 Compose 自动提供 AWT 的 keyChar 行为。

JediTerm 处理器是可复用的纯 core API，返回 `Unhandled` / `StringResult` / `BytesResult` 和 scroll hint。在确定命名键、modifier、character 映射与平台设置后复用；不得盲目照搬 UI 事件。scroll hint 只影响 renderer history 视口，不更改 parser。

## 顺序、身份与不确定结果

1. 输入使用 Host 已验收的真实 owner/incarnation 检查，完整 key 穿过 SessionApi、Gateway、Local 显式 DTO 映射。预派发拒绝、派发后 Unknown、无自动重试的语义沿用同一链路。remote 保持 Unavailable，直到另一个批次实际接线验收。
2. 在同一 bounded worker 顺序中编码并写入，gate 与 parser 的互斥保证采样一致性。不能跨 IO 等待持有模型 gate；也不承诺终端应用尚未解析的输出模式已生效。
3. StringResult 转 UTF-8，BytesResult 复制为本次私有 buffer；避免保存 encoder 内部共享 byte[]。私有复制本身无法修复 encoder 已经修改内部缓存的问题，见下项。
4. 必须覆盖连续键序列：application mode 下 modified arrow 后再 plain arrow。固定源码 `TerminalKeyEncoder.getCodeWithModifiers` 原地将共享序列第二字节 O 改成 [。发布 JAR 已复现：plain Up 从 `1b4f41` 经 Ctrl+Up `1b5b313b3541` 后变为 `1b5b41`，每次复制返回 byte[] 仍然如此。重新应用 application arrow 模式后恢复 `1b4f41`；但新增探针证明 Ctrl+F1 后 plain F1 也从 `1b4f50` 变为 `1b5b50`，重新应用 arrow 模式仍无法修复。因此不采用仅恢复箭头模式的方案。
5. 暂不通过 client 模式快照编码。那会新增模式 revision、采样到输入的竞争及协议责任，当前链路不需要这些额外状态。

## 编码器隔离决定

在 JVM 的模型 gate 内，每次命名按键使用一个新的 `TerminalKeyEncoder`，应用真实 parser 最新的 arrows/keypad/AutoNewLine/Alt 策略后编码并复制返回 bytes。`TerminalKeyEventProcessor` 仍负责 pressed 与控制组合的语义；JVM terminal adapter 的 `getCodeForKey` 提供该次私有编码器。不能仅复制旧编码器返回值，也不能仅恢复箭头模式。

真实 parser 的 mode setters 必须通过同一个 JVM adapter 记录状态并调用现有 superclass 行为，不能从 client 快照或客户端 ANSI 猜测。`setApplicationArrowKeys`、`setApplicationKeypad` 本身并不写 `myModes`，因此不能只读取 `isModelEnabled` 来推断所有编码状态。构造、reset 和 mode 更新都纳入后续 adapter 测试；生产实现不使用反射替换 private encoder，不引入 Swing renderer。

发布 JAR 的 `fresh-encoder-probe/` 实际 exit 0：覆盖 arrows 四键、F1..F4、Home/End 在 ANSI 与 application 两组模式下的 20 组 plain→Ctrl→plain 序列，以及 arrows/keypad 独立配置、AutoNewLine 开关。这里只验证发布二进制的编码器隔离策略，尚未证明 parser adapter、输入预算、真实 PTY、Local 链路或 UI 输入。

## Paste 与 IME 决定

- macOS/Unix 参考规则：CRLF 转 LF，再将 LF 转 CR；保留输入已有独立 CR。bracketed 开启时包裹 ESC[200~ 与 ESC[201~。字节预算、队列容量和 native write/flush 确认以包裹后的实际 UTF-8 为准。空 paste 的策略在实施合同中明确。
- bracketed 状态由 parser 的 display callback 更新，和编码一同受模型 gate 保护。不得因为 `isModelEnabled` 可读就认定 bracketed 有对应 enum。
- preedit 只在输入控件显示；只有 committed 文本进入 PTY。pressed 消费和文本提交必须有统一去重规则；Copy/Paste 产品快捷键与向 shell 发送控制组合分别确定消费者。
- 丢确认时 UI 展示 Unknown 并允许用户通过真实输出核对；不重发输入，不以本地文本回显伪造终端输出。

## 后续验收门槛

先验收发布 JAR 编码与基础真实 PTY，再验收生产 Local 链路，最后验收 renderer 的真实输入。测试必须观察真实进程收到的 bytes、输入次数或状态变化，不能只对照实现返回值。

- 方向键 ANSI/application 模式切换，application keypad，modified→plain 连续输入，Enter 与 AutoNewLine，Backspace/Tab/Shift+Tab，Ctrl+C、Ctrl+Space、Alt 组合。
- UTF-8 文本含中文和非 BMP 字符；IME preedit 不进入进程、commit 恰好一次；pressed/typed 去重。
- Paste CRLF/LF/CR 混合、bracketed 开关、包裹后容量拒绝；进程观测实际开始/结束字节。
- 输入、parser 输出与 resize 并发无自锁；stale owner、调用者取消前后、失确认均保持一次派发；最终 owned process/worker 清理。
- renderer 使用真实 Key/IME/clipboard 输入和生产 connection。离屏 UI 结果、原生 IME、原版同状态像素比较分别记录；离屏通过不能证明原生输入法可用。

鼠标协议和 renderer 选区冲突属于后续页面合同的明确选择；本设计不将鼠标报告记作已完成。

## 发布 JAR 探针证据

归档：`terminal-semantic-input-evidence-2026-10-09/release-jar-probe/`。`result.json` 记录实际命令、JediTerm 3.76 / Kotlin stdlib 2.4.20 等 classpath 的 SHA、JDK 路径和 exit 0；`command.log` 保存上述四段字节；`EncoderProbe.java.txt` 是隔离 Java source-file runner 的完整源码。`archive-sha256.json` 固定三份归档文件。临时运行目录是 `/tmp/harness-terminal-semantic-input-binary-probe-20261009`。

探针未启动 PTY、用户模型或应用，未修改 Kotlin/YAML 输入。这里只为下一阶段消除已发现的编码风险，不提前扩展当前 Host/Local 写集。

补充归档 `fresh-encoder-probe/` 保存独立的 `FreshEncoderProbe.java.txt`、`command.log`、`result.json` 和三文件 SHA manifest。它复用已固定的 JediTerm 3.76 / Kotlin stdlib 2.4.20 发布 JAR，并只选择一个 SLF4J API 版本；运行前去除该子进程继承的 Java options，完整命令和 classpath SHA 保存在 result。原探针与证据保持不变。
