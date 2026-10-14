# 终端 PTY 与 ANSI：剩余缺口

日期：2026-10-09。状态：只读分析，未派发、未构建或运行测试。当前工作者继续实施本机 Cron 页面批次；本文不扩大其写集。

## 本轮证据

先经 CodeGraph 读取当前 `ExecProcessProducer.kt`、`ExecProcessPlugin.kt`、`SessionBackgroundProcesses.kt`。随后从当前安装包按已有索引核验 terminal-panel、terminal-tab、xterm-output-panel 三个资源的完整 SHA256、大小和偏移，全部与既有分析一致。证据见 `terminal-pty-ansi-gap-2026-10-09.evidence.json`；没有重新全量扫描 ASAR。原版行为沿用 `terminal-subagents-implementation-contract-2026-10-07.md` 和 `terminal-reading-analysis-2026-10-08.md`。

## 当前实际能力

| 能力 | 当前正式代码 | 剩余工作 |
| --- | --- | --- |
| 真实进程 | producer 第92行 ProcessBuilder，精确 Session/Agent/call 归属；plugin 注册并拥有清理 | 已实现，不另造后台进程服务 |
| 输出 | UTF-8 reader、revision、bounded tail、EOF 与 waitFor 共同确认 exit | 现有字符串快照不包含终端 screen/cursor 状态 |
| stdin/EOF/cancel | typed service 第164–172行及 producer 第259–350行 | 保留已有 Unknown 与归属门控，不把管道输入称为 PTY |
| 阅读状态 | 终端阅读 R3 已验收26/26，保存两轴、初次/底部跟随及旧身份失效 | 原版同状态对照仍待；不重复开发阅读行为 |
| PTY | 当前启动使用普通 pipes；服务没有 create/resize/getShellCwd/PTY session | 需要明确真实 PTY provider、独立会话身份与 typed capability |
| ANSI | 当前输出仍是原始字符串，未有 screen model/emulator | 需颜色、CR/光标、清屏、alternate screen、选区等实际渲染 |

`ExecProcessConfig.maxOutputChars` 当前为256×1024；producer 在字符层切尾且只保护 surrogate。ANSI escape、当前颜色及光标状态可能在切尾前开始。因此不能仅对当前 tail 去除转义字符就宣称终端仿真完成。后台输出仍须可复制原始内容并保留 truncated 提示。

## 推荐后续切片

1. 先确定 Desktop 的终端 renderer 与 PTY provider 的正式装配契约，以及 common UI 的静态平台插槽。依赖选择须核对平台/native 打包与许可，不能在此分析中猜库版本或把浏览器 renderer 加入排除范围。
2. 后台命令 ANSI 阅读以既有精确 process key 为身份，定义可信初始化快照、增量/替换与截断后的恢复策略。不能让重复 revision、旧 owner 或断线回包污染新 renderer。背景阅读权限保持原有契约。
3. 交互 PTY 用实际 TerminalSessionId、Host/Thread/cwd/owner；显式创建和关闭，resize 传 rows/cols 并有真实结果，输入不重发 Unknown。打开输出面板或重建 renderer不启动新 shell。
4. 验收至少覆盖真实TTY判断、resize结果、UTF-8/ANSI分块、CR进度重绘、alternate screen、退出/取消、精确归属及宽窄浅深页面可达性。远端 wire fixture 与本机进程证据分开报告。

已有后台服务、Gateway、Local/Relay、面板选择与阅读代码作为保留基线。具体依赖和有限写集分析完成后，再交同一 `SHUAIAPI/gpt-6.1-sol` 实施。Cron 本批通过前不并发修改 shared 面板或 Host协议。

依赖静态研究已整理于 `terminal-dependency-analysis-2026-10-09.md`，固定官方源码与发布 metadata/POM/JAR 的 SHA 证据另存 `terminal-dependency-evidence-2026-10-09/manifest.json`。当前候选 pty4j 0.14.0 与 JediTerm core 3.76 尚未做 resolver、native 或运行兼容验收；不能把此分析当成已实施 PTY/ANSI。
