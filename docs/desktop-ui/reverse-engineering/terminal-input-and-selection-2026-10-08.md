# 后台命令输入与进程选择

日期：2026-10-08。实施模型沿用用户指定的 `SHUAIAPI/gpt-6.1-sol`；主线程负责集成复核、测试修正与证据保存。本文记录已验收的管道控制和真实进程选择。

## 1. 已验收：stdin、EOF、cancel

- `ExecCommand.stdin` 默认 false；只有显式后台命令启用 stdin。默认命令仍关闭管道；输出正文保持 plain text。
- API支持 `Unsupported/Open/Closed/Unknown`；默认旧服务返回 typed Unavailable。Gateway、Local、Remote与Host Relay均传递精确parent/process和typed结果。
- 输入按UTF-8字节计数；单次上限4096 bytes、默认等待1000ms，配置要求正值。NUL和超限输入先拒绝。阻塞管道IO在registry锁外执行，producer拥有对应操作。
- 输入结果超时、断连或格式/身份无法确认返回UnknownOutcome；客户端不自动重试，producer与panel保留输入门控。未知输入禁止Input/EOF，仍允许精确Cancel。
- EOF单独关闭stdin；Cancel仅请求停止所属进程，真实输出与exit记录证明退出。关闭tab是视图动作。
- panel操作按scope+processId+producerIncarnation保留pending/unknown/receipt；只有确认Input已发送才清空草稿。关闭/重开、刷新或旧owner回包均不能重新放开未知输入。
- 紧凑UI提供Send line（附加LF）、EOF、Cancel；没有消费者时不显示控制入口。未新增PTY/ANSI/resize，也没有全Thread Stop all。

## 2. 定向验证结果

最终114/114，无failure/error/skipped。每组最终运行只计一次；先前编译失败及错误filter的0-test运行不计。

| 模块 | 用例数 | 证据 |
| --- | --- | --- |
| exec-jvm | 16 | UTF-8与EOF、归属拒绝、默认关闭stdin、阻塞输入超时后仍可读取/取消 |
| shared | 58 | remote读5+mutation8、terminal15、connection panel6、controller12、state12 |
| gateway | 12 | 背景读取6+mutation6；身份匹配、旧服务、不确定结果与取消 |
| jvm-app | 28 | 读relay4+mutation transport7+真实读取8+UI7+真实mutation进程2 |

串行执行：

```sh
./kotlin test -m exec-jvm -p jvm --include-classes='*ExecProcessContractTest' --build-dir=/tmp/harness-terminal-input-20261008
./kotlin test -m shared -p jvm --include-classes='*BackgroundTerminalReadTest' --include-classes='*ThreadPanelsControllerTest' --include-classes='*ThreadPanelsStateTest' --include-classes='*ThreadPanelsRepositoryTest' --include-classes='*SessionBackground*Test' --build-dir=/tmp/harness-panels-20261008
./kotlin test -m gateway -p jvm --include-classes='*SessionBackground*Test' --build-dir=/tmp/harness-panels-20261008
./kotlin test -m jvm-app -p jvm --include-classes='*ThreadPanelsInteractionTest' --include-classes='*SessionBackground*Test' --build-dir=/tmp/harness-panels-20261008
```

JUnit摘要保存在 `../verification/terminal-input-2026-10-08/`；去除了system properties/stdout，保留用例名称、结果、耗时及原始report hash。`evidence.json`记录每组总数与创建证据时的源码指纹；后续进程选择批次会改动panel，因此这些指纹是证据保存时的观察值，不替代新批次验证。

desktop主代码和测试源码已编译。真实进程测试只启动专用JVM测试子进程；没有发起模型调用、改变当前Git工作树或连接用户终端。Remote测试使用wire fixture；未验证真实WebSocket断连时序、跨平台进程行为、全量测试或原版像素一致性。

## 3. 已实现：真实进程选择

分析先确认现有API提供真实inventory，再由指定模型实施shared panel模型、repository、state、controller和UI五个文件及对应测试。复用现有producer和传输接口，运行时不生成样例进程。

- `BackgroundTerminalSelection`同时返回经过验证的真实进程summary列表、selectedKey与output；旧 `readTerminal(owner)`保留兼容入口，默认adapter只暴露旧服务真实返回的单个output。
- summary key必须含scope/processId/incarnation，显示真实command、生命周期等字段；列表复制并拒绝重复ID、错误parent或owner，不生成样例进程。
- 首次打开沿现有规则选择最新Running，其次Exited，再Unknown；之后刷新及tab关闭/重开保持精确key。新命令不会抢走当前选中项。
- 用户只能选择当前已读取inventory中的key；UI发typed SelectTerminal，由已有onAction/controller消费。进程消失或同ID换incarnation显示明确错误，不能静默改选或调用另一进程read。
- 切换先失效旧read，按owner、epoch、request、generation拒绝迟到结果；原有revision比较与操作门控继续按key保留。A操作回包不能投影到B，切回A仍显示其pending/unknown状态。
- 选择/刷新/关闭tab只读取和改变视图。输入/EOF/Cancel只发给当前精确selected output；草稿不能跨进程误送，unknown草稿在同key刷新后保留。
- UI复用 `SettingsFormSelect<BackgroundTerminalKey?>`，以command和lifecycle显示条目；320dp长标题省略，输入与操作行可达。
- output DTO没有incarnation，repository在读取output后重新检查inventory，发布前再次确认精确key；写操作派发前同样确认归属。
- 本批没有自动轮询、输出订阅、PTY、resize或ANSI。

## 4. 进程选择批次验证

最终106/106，无failure/error/skipped：shared71、桌面功能30、截图矩阵5。shared包含新增13个选择测试；桌面包含9个UI交互测试、真实进程读取/输入/EOF/取消与Local/Relay传输回归。这些回归与前述114项存在重叠，不累加为独立新增用例。

```sh
./kotlin test -m shared -p jvm --include-classes='*BackgroundTerminalReadTest' --include-classes='*BackgroundTerminalSelectionTest' --include-classes='*ThreadPanelsControllerTest' --include-classes='*ThreadPanelsStateTest' --include-classes='*ThreadPanelsRepositoryTest' --include-classes='*SessionBackground*Test' --build-dir=/tmp/harness-terminal-selection-20261008
./kotlin test -m jvm-app -p jvm --include-classes='*ThreadPanelsInteractionTest' --include-classes='*SessionBackground*Test' --build-dir=/tmp/harness-terminal-selection-20261008
HARNESS_THREAD_PANELS_CAPTURE_DIR=/tmp/harness-terminal-selection-captures-20261008 ./kotlin test -m jvm-app -p jvm --include-classes='*ThreadPanelsScreenshotTest' --build-dir=/tmp/harness-terminal-selection-20261008
```

首次shared运行70/71：foreign-owner DTO在构造阶段拒绝，repository正确映射Error；修正测试断言后71/71。首次桌面运行28/30：两个fixture只等待PID文件存在，内容尚未写完便解析；改为等待numeric PID及ready输出，并让子进程先写PID再发ready，最终30/30。均保留失败原因，不把首轮结果计作通过。

证据保存于 `../verification/terminal-selection-2026-10-08/`：三份脱敏JUnit摘要、`evidence.json`、108PNG与`screenshots-manifest.json`。矩阵为27状态×2尺寸（640×800、320×600）×浅深主题。已目视检查紧凑浅深进程选择、进程消失、未知输入，以及640×800深色选择状态；按钮和正文在所检查viewport内可达。截图使用测试局部快照，不能证明真实Host数据或原版像素一致；真实读写证据来自独立功能测试。源码hash仅表示证据保存时观察值。

## 5. 下一步缺口

原版同尺寸对照、完整Shell与面板组合、跨页可达性仍需验收；终端尚缺输出跟随、PTY/resize/ANSI。共享选择组件已有接入与交互验证，不代表全应用所有共享组件完成。
