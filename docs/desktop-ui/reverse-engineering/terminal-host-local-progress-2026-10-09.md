# Terminal Host / Local 实施进度

日期：2026-10-09。此文件记录当前实施阶段；固定接口与验收条件仍以 `terminal-host-local-contract-2026-10-09.md` 为准。

## 当前任务

- 实施上下文：干净的 `codex exec --ephemeral`；旧工作者 `local/01a11a54-1694-7fb3-8007-37c434531314` 已 idle，不再派发。
- 指定模型：`SHUAIAPI/gpt-6.1-sol`，provider：`opencodex`。
- 当前阶段：**host-r3 已验收：冻结 2,229 输入，11 类 70/70，零失败/错误/跳过，完整 JVM app build exit=0**。
- 插件本地更新切片已经验收完成，不能作为本阶段交付。
- 基础 PTY：R3 已验收 5 类 38 项，完整 JVM app build 成功。

| 阶段 | 实施文件数 | 新测试方法数 | 放行条件 | 当前状态 |
| --- | ---: | ---: | --- | --- |
| H1 Host | 6 | common 4 + Host 8 | 阶段报告、六文件 SHA、源码审阅、释放写集 | host-r3 实际 12/12 |
| H2 Gateway | 3 | 6 | H1 核对完成；阶段报告与三文件 SHA | host-r3 实际 6/6 |
| H3 Client / Local | 5 | 6 | H2 核对完成；完整十四文件交付及释放 | host-r3 实际 6/6，包含正式本机链路及真实 PTY |
| 父线程统一验收 | 14 | 新 24 + 基础 38 + 既有后台 8 = 70 | 冻结 2,229 输入，串行测试、依赖图、完整 JVM build | host-r3 实际 70/70，完整 build 成功 |

H1 六文件：`SessionTerminalHost.kt`、`SessionApi.kt`、`JvmSessionTerminalHost.kt`、`ExecProcessPlugin.kt`、`SessionTerminalHostContractTest.kt`、`JvmSessionTerminalHostTest.kt`。H2 三文件：`SessionTerminalEndpoints.kt`、`SessionApiGateway.kt`、`SessionTerminalsGatewayTest.kt`。H3 五文件：client `SessionTerminals.kt`、`Connection.kt`、`LocalSessionTerminals.kt`、`LocalConnection.kt`、`SessionTerminalsConnectionTest.kt`。完整路径见固定合同。

## 防止旧任务恢复

父线程实际核对发现，工作者多次在 `contextCompaction` 后恢复了已完成的插件切片。H1 当时仅存在两份新源码；Facade、Plugin、两个测试及阶段报告均未交付。因此旧插件报告、旧插件测试与截图不能证明 Host / Local 有进展。

父线程已重新发送当前 H1 的六文件指令并显式选择指定模型。工作者 H1 turn `01a120f9-e020-75b1-a826-2b6e113bafe1` 已完成，并交付 H1 报告。后续恢复时，先核对本阶段报告和实际源码；不得从旧聊天标题或旧插件报告推断当前任务完成。

H2 turn `01a12107-7caf-74a3-86d2-71a2ac3c7c27` 起初确认 H2，但压缩后再次恢复插件任务并以零写入结束。父线程确认两个新 H2 文件与 H2 报告均不存在，因此不能记作 H2 交付。后续派发缩短为 H2a 两个生产文件、H2b 一个测试文件，按实际文件逐步核对；若仍漂移，改用新的实施上下文。

H2a turn `01a1210e-1870-7962-a036-cce77d63be58` 已真实交付两个生产文件。父线程逐项核验阶段报告、两个 before/after SHA、H1 六文件不变以及完整输入范围，通过；精确源码静态审阅覆盖七个 endpoint、逐分支身份校验、嵌套集合复制、mutation 取消返程、root 注销与部分安装失败清理。尚未编译或运行测试。

H2b turn `01a12113-2fac-7c83-a91a-107a53b7fbe6` 再次在压缩后返回插件交付，目标测试文件仍不存在。旧工作者已 idle 且没有新增写入。父线程改用干净的 `codex exec --ephemeral` 实施上下文，显式指定 `SHUAIAPI/gpt-6.1-sol` / `opencodex`；命令参数覆盖仅作用于该进程，不修改用户配置，不创建新的侧栏聊天。唯一 H2b 写集和固定六项测试不变，执行记录位于 `/tmp/harness-h2b-clean-worker-events-20261009.jsonl`。

## H1 父线程核对

实际读取 JSON/MD，核验六文件原 before/current after SHA、合同/基线 SHA、指定 model/provider、2 类 12 个固定方法以及停止写入标记。父线程沿统一验收脚本的输入范围检查了 2,220 个基线输入与 4 个新 H1 输入，写集之外没有变化；独立前端的三个 YAML 也保持固定 SHA。此前一次广域文件枚举纳入了文档/工具配置，未作为源码变更结论；正式范围检查通过。

复核 common revision 的 Long 断言及 Host 的 raw/emulator 完成条件。H1 尚未编译或执行，不宣称 12 项通过。H2 的唯一写集为三文件，交付目标 `/tmp/harness-terminal-host-local-h2-20261009.json` 与 `.md`；H1 六文件保持本报告 SHA。

## H2 与 H3a 父线程核对

H2b 已真实交付 `SessionTerminalsGatewayTest.kt`，SHA `ed968c1f0afc837958073c2d868b260a007841362cf1ce45f769860ac44131b4`。父线程完整审阅六个固定方法及 fixtures，覆盖真实注册器、七项转发、所有业务结果分支、身份错配、集合复制、丢 create 确认、取消及实际 dispatcher 返程、root/registration 注销与替换注册保护。H2 报告的 `files` 合并三文件，`changed_files` 只记录本次 H2b 新测试；最终收集器按三文件 aggregate 核对。

H2b 与 H3a 写集独立，在 H2a 接口冻结后并行实施。H3a 四个生产文件均已交付，父线程核对当前 SHA 与释放标记，完整审阅独立 client DTO、显式双向映射及单次 mutation helper。移除本批七方法、imports 与 delegation 后，两份既有连接源码精确恢复原 baseline SHA，既有后台及子 Agent 改动保留。两个实施进程均已结束。

H3b 使用新的干净实施上下文，只写 `SessionTerminalsConnectionTest.kt`；真实 native fixture 走完整正式注册链。生产源码冻结，测试仅实施与静态检查。全部释放后仍由父线程统一冻结并串行运行，不把静态检查记作功能通过。

## 固定证据与交付

- 合同 SHA：`2896a25c7e2ab19b03c46b9a0a1bdcf20199234329ae35d97293919ad139f0c1`。
- 基线：`/tmp/harness-terminal-host-local-baseline-20261009/baseline.json`。
- 基线 SHA：`7f5247bfaf487009a3a322c8272e1ee2451f79ccc4af32de2197d46ae59c2d4c`。
- H1 交付：`/tmp/harness-terminal-host-local-h1-20261009.json` 与 `.md`。
- H2a 交付：`/tmp/harness-terminal-host-local-h2a-20261009.json` 与 `.md`。
- H2 交付：`/tmp/harness-terminal-host-local-h2-20261009.json` 与 `.md`。
- H3a 交付：`/tmp/harness-terminal-host-local-h3a-20261009.json` 与 `.md`。
- 最终交付：`/tmp/harness-terminal-host-local-delivery-20261009.json` 与 `.md`。
- 父线程验收脚本：`docs/desktop-ui/verification/terminal-host-local-2026-10-09/parent-verify.py`。

工作者仅实现与静态检查，不运行构建、测试或截图。最终十四文件释放后父线程才冻结并串行验收。所有既有 dirty / untracked 保留。

## host-r1 / host-r2 实际失败与 host-r3 窄修重验

H3b 六项测试已真实交付。host-r1 的 common4/Host8 通过，Gateway 编译在跨模块 nullable key 的 smart cast 失败。同一指定模型仅捕获局部 terminalKey，保留身份断言及深复制；其余 2,228 输入不变。host-r2 的 common4/Host8/Gateway6 通过，Local 测试编译在错误的 io.ktor.util.InternalAPI 导入失败。同一指定模型仅改为编译器明确要求的 io.ktor.utils.io.InternalAPI；其余 2,228 输入再次逐项 SHA 核对无变化，六个固定方法和全部断言保持。

失败日志、JUnit XML、冻结源码、交付和 SHA 分别归档于 verification/terminal-host-local-2026-10-09/host-r1（34 文件）及 host-r2（39 文件）。两个修复报告与阶段报告历史保留于 /tmp；最终收集器重新合并 14 文件、保留原 baseline 和合同 SHA 后，host-r3 冻结 2,229 输入，按原 11 类 70 项、正式 runtime dependency graph 和完整 JVM build 串行验收。不能将此前 18 项通过记作 70 项通过。

## 后续顺序（当前）

host-r3 同一 manifest 串行通过 11 类 70/70；生产及测试编译、完整 JVM app build 成功，全工作树与冻结输入一致。正式 exec-jvm / jvm-app runtime graph 已人工核对：pty4j 0.14.0、jediterm-core 3.76、JNA/jna-platform 5.19.1、kotlin-stdlib 2.4.20；jvm-app stdlib-jdk7/jdk8 和 reflect 为 2.3.10。此核对是解析图，未扫描重复 JAR。

87 份归档文件及 SHA 位于 verification/terminal-host-local-2026-10-09/host-r3/，包含全部 XML、日志、14 份冻结源码、输入 manifest、修复历史与依赖图。r1/r2 失败证据独立保留。

继续语义输入合同及实现，再接 renderer 与页面。Remote、原版像素、原生窗口分别验收；本阶段通过不能记作这些能力完成。整体目标仍 active。
