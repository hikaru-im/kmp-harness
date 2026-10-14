# 交互终端 renderer 与真实页面分析

日期：2026-10-10。分析已经收敛，实施与验收以 [页面合同 R1](terminal-renderer-page-contract-2026-10-10.md) 为准。实施模型使用用户指定的 `SHUAIAPI/gpt-6.1-sol` / `opencodex`。

## 已确认的接入点

- `HarnessApp.kt` 已按当前 Thread、conversation host 与 verified connection 绑定 `ThreadPanelsController`；effect 发布前用 `isBoundTo(panelOwner)` 隐藏旧 owner 状态。新增终端消费者必须沿用这项检查。
- `ThreadPanelTab` 当前只有 `BackgroundOutput` 和 `Subagents`；后台输出已接真实 ProcessBuilder、stdin、EOF 与取消。交互 PTY 应另设视图，保留这两个入口。关闭面板视图不等于关闭 PTY。
- `LocalConnection → LocalSessionTerminals → Gateway → DefaultSessionApi → JvmSessionTerminalHost → JvmPtyTerminalSessions` 已具 create/list/read/input/resize/close 接口；Host 语义输入 R2 的 17 类 104 项与完整 JVM app 构建已经通过，归档 SHA 已独立复核。
- `apps/jvm-app/src/main.kt` 是平台能力的正式注入点；`DesktopClientCatalog` 和 `ClientUiSlots` 在 startup 固定。没有证据表明终端页面需要新增动态插件或 slot。
- `settings/ShortcutsKeyEvents.kt` 的 preview 负责捕获/释放，bubble 才执行 app command。`HarnessApp.kt:1139` 的页面根遵循这一顺序；终端不能再创建一个全局快捷键 root。
- 原 ASAR 分析的终端正文内边距为 start16、top8、bottom12，scrollbar10；依据 `terminal-subagents-implementation-contract-2026-10-07.md`。尺寸还需在实际页面截图中核对。

## 需要一起实现的页面切片

1. 页面绑定真实 owner 和 terminal inventory，显示不可用、空列表、加载、错误、运行、退出及结果不确定状态。打开、刷新、切会话、切主题和 renderer 重建只读取，不能启动 shell。
2. 仅点击“新建终端”派发一次 create；返回真实 TerminalKey 后选择该终端。丢 create 确认不能重试或编造 key；通过 inventory 恢复。
3. 新建、选择、显式关闭终端和关闭视图分别有独立动作；退出后保留最终 screen/history，直到显式 close。
4. 从 typed screen/history 绘制行、颜色、属性和 cursor。rawTail 只用于诊断，不能重新喂 parser。history 滚动、选择和复制使用同一字符/列映射。
5. 从实际 viewport 和字体指标计算 rows/columns，去重后发送真实 resize；只改变 Compose 布局不能算 resize 成功。
6. 文本提交、键盘和 Paste 使用现有语义输入；不增加本地 echo，不把粘贴拆成逐键。输入队列和在途操作必须有界，并保持用户事件顺序。
7. 切 owner/terminal、关闭视图和断线后，迟到 snapshot/输入确认/resize 结果不能写入新视图；完整 key、connection owner、request generation 和 revision 各自保留。

这些部分应组成一个有可操作页面的实施批次。不要再次单独交付只有空 UI 的 renderer 壳。

## 字符与终端列：明确缺口

当前两套独立 DTO 的 `TerminalTextRun(column, span, text, style)` 只保存起始列与总 span。`JvmPtyTerminalSessions.kt:514` 的 `copyLine` 从 native entry length 取得列数，删除 DWC sentinel，并将 NUL 转为空格；text 的 UTF-16 长度因此不能用于定位 terminal cell。

固定依赖证据 `terminal-foundation-evidence-2026-10-09/JediTerminal.java.txt:1082` 显示：BMP 宽字符追加 DWC；surrogate pair 保留两个 code units且不追加 DWC；写入前先做 NFC。渲染器自行运行另一套 Unicode width 算法会与这个 parser 的列模型不一致。

定案是在 core/shared run 中添加 portable `columnOffsets`：每个可见 UTF-16 code unit 在原 entry 的相对列，再加末端列边界。producer 在删除 DWC 时同时生成，Local 双向深复制。旧 JSON 缺字段仍能解码；仅严格可打印 ASCII 且 text.length==span 的旧 run 可推导连续列，其它旧 run 显示快照不兼容。

末列宽字符截断、surrogate pair 截断及 entry 边界、DWC-only/leading continuation、NUL、样式与 history 的处理已经在合同 A 节确定；实施后用真实 parser 与独立 literal 预期验证，不以被测算法生成 expected。

## 平台与输入的定案及验证边界

- 架构为 common Compose grid renderer；JVM 启动层注入绑定同一 LocalConnection 对象及 expectedHostId 的 launch provider。cwd 使用 discovery.canonicalCreationCwd，Host 最终验证；仅已验收的 macOS x86_64 提供 launch，默认与其它连接返回 Unavailable。
- SHELL 必须是绝对 regular/executable 路径，失败依次验证 /bin/zsh 与 /bin/sh；argv 为 shell 加 -i，固定 TERM/COLORTERM，缺 PATH 时补标准系统路径，环境值不得泄露。
- IME 使用 BasicTextField/TextFieldValue composition；preedit 不派发，仍在同 focus/epoch 的 commit 一次 Text，cancel/blur/reset 丢弃。组合 Enter/Backspace 不双写。合同与桥测试不能证明原生 IME 已通过，实际输入法保留独立验收边界。
- 快捷键应在 focused terminal 内消费已支持事件，让已有 root 处理剩余 app command；自动重复与 key-up 单独覆盖。

## 父线程验收要求

- 真实生产 Local/Host/PTY 链路覆盖 create → committed text/key/paste → ANSI screen/history → kernel resize → exit/close，核对精确 bytes、真实 PID 与 owned workers cleanup。
- controller 验证纯 read 不启动、重复点击不重试、完整 owner/key fencing、迟到响应、revision high-water、输入顺序、关闭视图与关闭进程的不同效果。
- renderer 独立预期覆盖 ASCII/中文/emoji、样式、cursor、history/alternate、selection/copy、viewport resize 和空/错误/不可用状态。
- 完整 HarnessApp 用真实 pointer 验证宽/窄、浅/深，保留后台输出和子 Agent 回归；截图 fixture 只能作为视觉证据，不能冒充真实 shell 功能。
- build、功能、离屏截图、原版同状态像素和原生窗口分别记录。原生 IME 未实测时明确保留未验收状态。

下一步保存已验收的 2,238 输入基线并派发页面合同的唯一 22 文件写集。worker 退出释放写集后，父线程独立审查并冻结、串行运行旧 104 项、新 36 项与受影响页面回归，再生成完整页面截图和归档。
