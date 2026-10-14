# 终端与子 Agent 面板：当前验收边界

初版日期：2026-10-07；状态更新：2026-10-10。本页汇总当前生产链与独立验证，不以历史分析的服务缺口代替现有实现。范围为会话面板，遵循用户已确认的排除清单。末尾同步记录替代旧批次中冲突的缺口描述。

## 子 Agent

| 层次 | 已有实现或证据 | 尚未证明 |
| --- | --- | --- |
| 默认注册 | DesktopBundle catalog/profile 安装 subagent-tools | 其他 profile 是否包含该插件，需按实际配置判断 |
| 创建与投递 | SpawnAgentHandler 使用可信父上下文、真实 registry.create/followup，保存 child ID、SUBAGENT origin 与父谱系 | accepted receipt 不表示子任务完成 |
| 工具合约 | SubagentToolsContractTest 最新15/15，无跳过 | ControlledAgent 合约本身不证明真实子循环 |
| 真实循环 | DesktopSubagentLoopIntegrationTest 最新2/2：真实默认父 AgentLoop 调用 spawn_agent、真实子 AgentLoop 自动消费 | LLM 为受控本地 adapter；未调用指定模型网络API |
| 数据面板 | 同两项组合验证 Gateway/LocalConnection descendants/readChildHistory 与 ConnectionThreadPanelsRepository | 组合未渲染 Compose，也未验证远端 Relay |
| 持久读取 | flush、完全关闭原 Runtime，再由 fresh reader 比对 header/events/messages；不restore Agent | 这是定向读取已知ID，不是重启后完整子会话枚举或crash恢复 |
| 生命周期 | RUNNING → Active；IDLE → Unknown；真实 followup复用同一child，第二轮history增加 | 单个Completed TurnEnd 不表示子 Agent 永久Done或Waiting |
| 页面动作 | ThreadPanelsInteractionTest 最新5/5：真实生产面板点击、reducer/controller、Back、Show more、页签、Refresh/retry、只读授权和紧凑滚动 | UI测试使用受控读取，不证明远端或完整Shell；尚未完成原版同尺寸像素对照 |

真实循环最终日志：`/tmp/harness-subagent-loop-final-20261007.log`。独立XML：`/tmp/harness-native-verification-evidence-20261007/subagent-loop-final/jvm/TEST-junit-jupiter.xml`。合约XML：`/tmp/harness-native-verification-evidence-20261007/subagent-contract-final/jvm`。两类结果分开，不与旧批次重复累加。

## 终端

| 层次 | 当前实现 | 下一步缺口 |
| --- | --- | --- |
| 后台执行 | 默认exec-process producer，真实ProcessBuilder argv/cwd、输出、waitFor+EOF、超时/取消/Host dispose | 既有13/13是前批结果，本轮未重跑；进程测试不证明终端视觉 |
| 面板读取 | Session API/Gateway/LocalConnection读取真实inventory/output；包含owner和producer incarnation；共享picker可选择当前真实进程并保持精确key | 初次默认最新Running，其后刷新/关闭重开保持选择；单一输出视图，不提供多个逐进程页签 |
| 输出组件 | 后台输出保留plain text阅读；交互终端另用真实typed ANSI screen/history、字符列映射、滚动跟随和选择复制 | 原版同状态像素、原生窗口与Remote待验收 |
| 输入与生命周期控制 | 后台stdin/EOF/Cancel链保留；独立PTY Text/Paste/KeyPress、真实kernel resize、周期snapshot读取、退出保留和显式close已接生产页面 | PTY Remote、原生窗口/系统IME和其他平台launch待验收；后台管道阅读不混为PTY |
| 交互终端页面 | R5同一冻结输入30类184/184、完整JVM build通过；10张宽窄浅深和异常状态完整HarnessApp离屏图逐张核验通过，完整key与revision隔离 | 当前launch仅verified LocalConnection + macOS x86_64；原生窗口/IME、Remote和原版像素独立待验收 |
| 生命周期操作 | 关闭面板关闭视图，不等于kill；当前无stop-all UI能力 | 后台clean-all需有真实owner范围的服务和结果确认，不能仅清空UI |
| 视觉 | 原版ASAR契约已保存；生产面板19场景×2尺寸×2主题共76PNG，截图测试3/3；人工查看4张代表截图 | 无同尺寸原版面板实图，不能声明pixel parity |

当前实现依据：`apps/shared/src/im/hikaru/harness/client/panels/{ThreadPanelModels,ThreadPanelsRepository,ThreadPanelsState,ThreadPanelsController,ThreadPanels}.kt`，以及默认Desktop bundle、subagent-tools、exec-jvm和相关integration tests。

## 本轮页面验收

指定实施模型：SHUAIAPI/gpt-6.1-sol；主线程审查并串行隔离构建。截图fixture仅位于测试，运行时继续消费真实服务结果。

当前结果为 **8/8**，无失败、错误或跳过（截图方法3项、交互方法5项）。隔离源码与两个测试文件逐字节一致，工作树diff检查通过。

- 普通面板640×800、紧凑320×600，浅/深主题。
- 后台输出Running/Exited/空buffer/truncated/长行，独立empty/loading/error/unavailable。
- 子Agent概览Active/Unknown/Done，详情正常/loading/error/empty；Done仅为明确测试状态，不代表当前live producer能永久结束子Agent。
- 点击选择/返回/Show more、切换/关闭页签、Refresh/错误retry，验证禁用与只读授权；核对紧凑长标题和滚动。
- UI状态测试与截图不替代真实循环证据，也不替代完整壳层、远端或像素验收。

截图与逐文件SHA256：`captures/thread-panels-visual-2026-10-07/manifest.json`。JUnit报告位于同目录的`TEST-junit-jupiter.xml`；日志为`/tmp/harness-thread-panels-visual-final-20261007.log`。人工检查了紧凑长行、紧凑深色概览、紧凑浅色详情及普通深色错误状态，未见明显标题/正文裁切；长行水平滚动与长记录垂直滚动保留。未增加生产假数据，也未因这些截图将PTY或永久Done标为完成。

执行顺序与其他保留页面见 `replication-plan-2026-10-07.md`。整体目标继续进行中。

## 同步：2026-10-08 输入与进程选择

输入批次114/114是历史结果；后续进程选择批次最终106/106（shared71、desktop30、截图5），含重叠回归，不相加。最新面板矩阵为27状态×640×800/320×600×浅深，共108PNG。主线程已重新核对这108个文件的SHA256、bytes、尺寸及三份XML的计数；无失败、错误或跳过。

生产HarnessApp通过标题栏入口、ThreadPanelsController和ConnectionThreadPanelsRepository接入真实Connection读取与操作；面板并非生产假数据。受控快照仅供截图测试使用。本批证据位于 `verification/terminal-selection-2026-10-08/`，契约及精确命令见 `reverse-engineering/terminal-input-and-selection-2026-10-08.md`。

既有108张面板viewport截图未覆盖完整HarnessApp组合；完整壳层本轮结果见下节。输出跟随、PTY/resize/ANSI、原版同尺寸像素对照、远端真实链路仍未完成。

历史首轮：完整壳层六项，0通过、6失败。600×800浅深主题的真实鼠标点击被面板overlay遮挡；1024×768与1440×900浅深主题切换会话后仍显示上一会话草稿。当时54张只是预期数量，未交付。失败日志为 `/tmp/harness-thread-shell-pre-fix-20261008.log`；最终扩展矩阵与结果见下节，不计入当前通过数量。

## 最新同步：2026-10-08 完整Thread壳层与草稿隔离

实施使用用户指定的 **SHUAIAPI/gpt-6.1-sol**。修复生产overlay覆盖范围与controller草稿归属，保留真实鼠标点击及严格跨会话断言。

- overlay从主内容标题栏下方开始，避开pushed navigator；判断依据是扣除rail与pushed navigator后的主区宽度，阈值672dp。1440×900主区1113dp、1024×768默认navigator主区697dp均为并排；1024×768真实拖宽navigator后主区572dp、600×800主区600dp均为overlay。
- controller作为唯一草稿cache，按SessionId及未创建Home草稿保存。切会话恢复各自草稿；同Host/身份重连保留，换Host/身份、logout及close清空。Home创建转移最新草稿，发送成功通过draft与revision确认后只清当前会话；旧发送失败不污染新选择。Composer作用域与callback检查跟随当前路由。
- `HarnessAppThreadPanelsShellTest` **8/8**：1440×900、1024×768默认navigator、1024×768真实拖宽navigator、600×800，各含浅/深主题；真实pointer覆盖页签、关闭、scrim、navigator及会话切换。
- `HarnessAppThreadShellDraftIsolationTest` **6/6**：各会话与Home草稿、重连、owner切换、创建期间编辑、发送清理和迟到失败隔离。两类完整壳层测试合计 **14/14**。
- `ConversationControllerTest` 整类最终 **31/31**，无失败、跳过或中止。旧30/31轮次的跨会话草稿断言已改为验证各自草稿及revision隔离，最终整类复验通过。
- 最终矩阵 **80PNG + 80份同名geometry**，8组各10状态。核对PNG尺寸与文件名、对应几何数据；人工查看600浅色双页签终端、1024深色拖宽navigator子Agent详情、1440浅色双页签终端。

日志：`/tmp/harness-thread-shell-verified-20261008.log`、`/tmp/harness-thread-shell-controller-verified-20261008.log`。脱敏证据目录：`verification/thread-shell-2026-10-08/`。历史114/114、106/106及本轮结果分别记录，不重复累加。

本轮使用受控typed SessionApi与面板fixture验证生产HarnessApp/controller，不调用模型网络API。离屏完整壳层验收不证明真实远端、原生窗口组合或原版像素一致；PTY/resize/ANSI与输出跟随仍未完成，整体目标继续进行中。

## 最新同步：2026-10-10 真实PTY与语义输入

上文PTY/ANSI缺口属于旧阶段。本机基础PTY的pty-r3已验收38/38，Host/Gateway/Local的host-r3已验收70/70；standalone语义输入semantic-r1为13类86/86，最新Host语义输入host-semantic-r2为17类104/104，失败/错误/跳过均0、完整JVM app build成功，各批次包含重叠回归，不相加。指定实施模型始终为SHUAIAPI/gpt-6.1-sol / opencodex。

真实PTY、持续UTF-8与ANSI screen/history、kernel resize、owned清理，以及Text/Paste/KeyPress的mode和单次write已具功能证据；语义Host/Gateway/Local接线也已通过。最新110份归档SHA逐项复核，XML确认104项全通过，路径 `verification/terminal-host-semantic-input-2026-10-10/host-semantic-r2/`。当前页面仍使用既有后台进程面板；下一批接renderer/真实页面键盘、粘贴、滚动复制与resize。不能把本机底层通过称为页面或原版像素通过。

页面分析见 `reverse-engineering/terminal-renderer-page-analysis-2026-10-10.md`。范围包括只读打开、显式新建shell、真实inventory与typed screen/history、完整身份隔离、字符列映射和有界顺序输入；原生IME需单独实际验证。Remote、原版同状态像素、原生窗口仍独立待验收；整体复刻目标继续active。

## 最新同步：2026-10-10 交互终端页面 R5

指定 SHUAIAPI/gpt-6.1-sol / opencodex 实施。R1、R2、R3失败证据分别保留于 verification/terminal-renderer-page-2026-10-10/page-r1、page-r2、page-r3。R4功能30类184/184和完整JVM build通过，但10张实际截图发现退出标题仍Running、两张标记light的异常状态图实际dark，视觉拒绝记录保留于page-r4。

R5仅修改PtyTerminalPanel和PtyTerminalPageInteractionTest：selector完整key与selectedKey及真实snapshot匹配时采用最新lifecycle；原宽窄浅深矩阵严格断言Running/Exited (7)，Unknown/Unavailable显式Light。父线程核对2,253输入，其中写集外2,251字节不变；22文件delivery、baseline/contract及固定新增8类36方法不变，history复制方法与其余受保护方法/helper逐字节保留。工作者退出释放写集后才冻结并运行。

父线程独立串行完成30类184/184，失败/错误/跳过均0，完整JVM app build exit0。早期父验收清单误把同文件的Shell8与Draft6归为一类14，R4已纠正为两类；总184与方法全集未改，纠正前证据保留。R5全部重新运行，没有复用旧XML。正式runtime解析图核对pty4j0.14.0、jediterm-core3.76、JNA/jna-platform5.19.1、kotlin-stdlib2.4.20；这是依赖解析图核对。

10张完整HarnessApp离屏图均逐张查看：1440×900/600×800、Light/Dark的真实Running/Exited各8张，受控丢确认Unknown和能力Unavailable两张；完整key/revision、viewport/控件geometry、实际主题背景像素均通过。Running/Exited来自真实LocalConnection/Gateway/PTY，异常回执与能力状态单独标controlled，生产未加入演示数据。可达入口、显式新建、键盘/粘贴字节、history选择与复制、kernel resize、owner切换、退出保留及显式关闭已有本批证据。

本批通过的是本机功能和离屏页面验收。原生窗口/系统IME、Remote、其他平台launch和原版同状态像素仍未验收。证据、XML、10PNG及逐文件SHA在verification/terminal-renderer-page-2026-10-10/page-r5/；旧104和新36及页面回归在184中重叠覆盖，各历史批次不相加。整体目标继续active，用户必做与排除范围不变。


## 2026-10-10 消息展示最终释放

指定 SHUAIAPI/gpt-6.1-sol / opencodex 工作者 exit0 并释放写集。当前基线保留 MessageCard 的 internal 可见性以供子 Agent 复用，LiveConversationSurface 展示逻辑不变；本轮只修正字符布局范围注释，明确 STRUT/TIGHT 不等于 raster glyph ink 或 baseline 测量。父线程核对 2,253 输入、2,252 不变，以及全部可执行测试字节和 14 个消息方法不变。

同一冻结源码串行重新运行 ThreadMessageVisualInteractionTest 14/14、LiveConversationInteractionTest 11/11、HarnessAppThreadPanelsShellTest 8/8，共 33/33，失败/错误/跳过均 0。12 消息 PNG 及 geometry、80 完整壳层 PNG 及 geometry 全部核对；父线程查看全部 10 张 contact sheets 及 3 张原尺寸消息图，正文最终 marker 可见，未见明显裁切。正式证据在 verification/thread-message-2026-10-10/final/；旧 provisional 历史保留，不复用旧 XML。

这是离屏结构、布局和交互验收，字体、672dp 内容列及 85%/560dp 人类气泡仍是 Harness 适配；原版同状态像素、原生窗口、Remote 未验收。前文消息仍 provisional 的记录为历史状态；当前已经最终释放。整体目标继续 active。
