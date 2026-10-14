# 本机Cron启用与真实页面链路：实施合同

状态：`cron-page-r6` 已完成父线程验收并归档。冻结2,216输入，20类134/134实际通过，失败/错误/跳过均0；新增页面6/6、装配4/4以及后续18类回归全部实际运行。12张暂停/接收/完成宽窄浅深PNG的SHA、尺寸、identity和control geometry已核验并逐张目视检查。证据在 `../verification/scheduled-cron-page-enable-2026-10-09/cron-page-r6-final/`。R1至R5失败历史独立保留，不能改写为通过。实施者为 `local/01a11a54-1694-7fb3-8007-37c434531314`，模型 **SHUAIAPI/gpt-6.1-sol**、provider **opencodex**；工作者只实施与静态检查，父线程负责冻结与串行验证。原版同状态像素和原生窗口仍待验收。

## 已验收的基础与当前缺口

readonly-r2已通过12类85/85并归档：真实跨JVM五窗口、只读Room/evidence、执行组合、lease、日期/DST、Owner/store与普通持久化回归。证据：`../verification/scheduled-executor-crash-2026-10-09/readonly-r2-final/`。不重做这些能力，不削弱或修改其测试。

派发前的main为 `scheduledExecutionVerified=false`。R6验收的main已明确 `verifiedCronExecution=true` 并共用DesktopScheduledExecution；真实页面输入/点击到持久精确终态和Thread导航已通过本批验收。已有真实store、calendar、同Runtime evidence、LocalConnection.session、OS lease、executionScope和Owner；启动校验HostId及协议major，关闭Unsafe保留Runtime/lease。HarnessApp校验Local transport、同connection引用及完整binding，页面按钮调用真实repository。此结论限于本机Cron共用接线及临时真实存储、Runtime、Gateway、LocalConnection和受控模型端点；Heartbeat、原版像素、原生窗口、远端及应用退出后后台执行不由本批证明。

## 精确写集：四文件

1. `apps/jvm-app/src/main.kt`
2. 新增 `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecution.kt`
3. 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledCronPageIntegrationTest.kt`
4. 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutionTest.kt`

测试共享fixture可放在第3文件的internal声明中，不新增另一套production实现。若现有shared页面或协议出现实际阻碍，报告具体代码路径和最小额外写集，先释放这四文件；不越界修改。其他dirty/untracked保持，不reset/stash/clean/commit，不操作真实Codex/home、用户进程、真实仓库或线上provider。

## 共用正式启动接线

- 提取小型DesktopScheduledExecution装配入口，main与测试都直接使用。使用真实Context、期望HostId、**具体LocalConnection**、同一DesktopScheduledStore/Calendar、临时或正式home中的稳定leader路径、executionScope和配置。不要把Connection wrapper、内存store、假evidence替换进去。
- 提供正式start入口，经同一connection的真实host.describe验证success/data/HostId/协议major后才Owner.start，binding仍为当前本机匿名scope，generation与HarnessApp保持一致。错误/不匹配不得启动旧Owner或获得执行准入；取消原样传播。保留Owner的永久invalidate与恢复绑定。
- 测试可注入现有ScheduledClock及config中的tick/poll间隔，使到期路径确定；不新增另一套调度时钟/执行器、不通过直接调用repository.runNow代替页面点击。
- main明确开启已验证的本机Cron，并将**同一启用值、Owner、store、calendar与connection实例**传到HarnessApp执行/恢复接线。ScheduledExecutorConfig默认false保持；测试配置不得变成生产默认。
- main现有close/finally顺序保持：Owner.shutdown为Drained才取消executionScope/释放Host；Unsafe继续保留Runtime与锁并给出重查入口。不以finally强制释放未确认delivery。
- 只启用Local Cron。Heartbeat继续Unavailable；Relay/身份不匹配不执行本机任务。应用退出后后台运行不在此合同内。Unknown不重发、精确input/TurnEnd、暂停不cancel、预算与CAS语义保持。

## 必要验收：页面四项与装配三项

`ScheduledCronPageIntegrationTest`至少以下四个独立场景；全程使用真实临时JSON账本、Room、Runtime、Session/Agent/AgentLoop、Gateway、HostApiPlugin和LocalConnection、受控streaming LlmAdapter。模型目录与选择器通过现有真实ModelSettingsBackend接入。fixture假的只允许模型回答内容/完成gate与测试clock；不手填运行结果或替代SessionApi/ledger/持久证据。

1. **创建暂停配置→立即运行→历史→真实Thread。** 挂载完整HarnessApp及真实Client UI snapshot，真实鼠标/键盘填写和保存。磁盘确认为Paused且无run/无provider stream；点击立即运行后真实Accepted/开放回合，放行adapter后精确input对应的Completed TurnEnd已持久、ledger为Completed。页面历史显示真实run，点击打开运行会话后实际Thread展示该prompt与adapter回答；不是callback捕获id就算导航成功。
2. **页面启用→到期→暂停。** 从页面创建并保存、启用；真实cursor严格晚于当前clock。仅推进clock并tick正式executor产生一项due run，核对nominalAt、唯一input和真实持久终态；页面暂停后未来tick不投递。至少一次从真实JSON重新读取验证启停持久化。不得手工seed运行ledger。
3. **页面生命周期与binding。** 正式Owner属于应用；离开定时页再返回不会另造Owner/重复投递，真实运行仍能终结。失去verified binding或替换connection后旧Owner被invalidate，原scope的运行/启用不可继续，新的connection不得借用旧执行能力。分别记录真实stream次数、账本和Owner状态，避免只断言节点不存在。
4. **真实页面状态截图。** 从真实前述交互产生Paused、Accepted/开放回合、Completed三个状态，各取1440×900与600×800、浅深主题（12张PNG）。记录实际尺寸、state/run/input、PNG SHA、布局geometry；真实滚轮证明最后历史和打开按钮可见可点击，无隐藏节点代替可达性。此为Harness真实服务的离屏布局证据，不能称原版像素或原生窗口验收。

`DesktopScheduledExecutionTest`至少三项：

1. 通过真实Gateway/Host服务证明HostId和协议major不匹配均拒绝start，不获得lease、不create/prompt。取消传播用现有服务seam核对，不能catch为成功。
2. 配置默认false时不能runNow/激活/恢复执行；main启用值与UI恢复值同源，实际验证config gate，不靠搜索字符串当执行证明。
3. 真实provider开放回合时shutdown返回Unsafe且lease/Runtime仍有效；放行provider并获得精确持久终态后重查Drained、锁可由新owner获取。fixture最终清理仅操作测试拥有资源，不把强制清理当生产可释放证明。

全部异步等待基于真实ready状态与明确10秒期限，不只等待根节点；UI作用域用Compose rememberCoroutineScope。Room行检查复用真正活跃owner DAO，关闭后才另开，禁止重复所有权。现有鼠标helper只参考操作方法，不复用ScheduledPageTestSupport的假connection/内存store作为执行证据。

## 基线、交付与父线程验收

基线为 `/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-readonly-r2`，2,213输入；冻结manifest同路径加 `-manifest.json`。父线程派发前核对并保存四文件before SHA（新文件null）、main副本和完整manifest于 `/tmp/harness-scheduled-cron-page-enable-baseline-20261009/`。

工作者保存四文件后静态核对，交付 `/tmp/harness-scheduled-cron-page-enable-delivery-20261009.json` 与 `.md`，包含model/provider、baseline_manifest、全部before/after SHA、两类实际Test名称/数量、场景与限制、`tests_run=false`、`builds_run=false`、`write_set_released=true`、`source_writes_stopped=true`。不在交付中宣称测试已通过。释放后停止写入。

父线程核对全量input漂移、四文件SHA及完整shared接线，复制到独立source/build目录；串行先运行两个新增类，再回归85项、既有Scheduled页面路由/CRUD/截图和Owner恢复接线的相关类。每类核对实际JUnit testcase.classname、数量和零failure/error/skip，分别保存XML/日志，防止覆盖。失败即停并保留历史，具体修复继续交同一指定模型，不弱化断言。

## 只读分析依据

- main正式接线；HarnessApp scheduling绑定/恢复；ScheduledManagementPage真实按钮路径。
- DesktopScheduledExecutorIntegrationTest真实Runtime/adapter fixture用于复用服务安装和gate语义。其仅Session gateway不足以证明完整Host启动，必须补真实HostApiPlugin/registerHostApi。
- HarnessHost.start使用的真实HostApiPlugin/registerHostApi；LocalConnection直接typed gateway调用。
- ScheduledPageTestSupport只参考mouse helper，既有生产测试的假数据不属于新证明。
