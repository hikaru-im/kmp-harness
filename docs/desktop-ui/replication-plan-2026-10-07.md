# Codex 视觉复刻：组件审计与页面计划

审计日期：2026-10-07。依据当前 Kotlin 源码、已有 app.asar 分析和仓库参考截图。当前目标是按确认范围复刻视觉，并逐页接入已有本机/Host 服务；尚缺服务的动作显示不可用，定时执行与远端链路另行验收。

状态阅读规则：下方页面覆盖表与文末最新同步记录表示当前状态；各批推进记录保留当时事实，其“待实施/下一批”不自动代表当前缺口。本文按主线程隔离验证的实际结果更新；2026-10-08 已实现 managed Worktree M1 的独立登记、归属核验与页面；具体最终数量见文末 M1 记录。此前创建批次52/52、已有分支切换72/72、插件登记/Skills消费59/59各自保留为历史批次证据，不与新批次重复累加。

## 已确认范围（2026-10-07）

此范围以用户本轮明确选择为准，取代下方历史审计中的全量候选，以及旧页面规划中冲突的纳入/排除决定。

| 范围 | 页面与功能 |
| --- | --- |
| 核心保留 | 全局壳层、Home、Thread、Projects/Project、Recent、账号登录、Host 连接 |
| 核心共享区域 | Composer、模型选择、消息与工具结果、文件/变更卡片、必要的右侧与底部面板 |
| 基础设置 | 常规、外观、模型/Provider 配置、连接、快捷键 |
| 用户明确需做 | 定时任务、插件管理、Git/Worktrees、终端面板、子 Agent 面板 |
| 用户明确排除 | ChatGPT 云端项目、Codex 云端环境、自动代码审查、团队管理、计费、家长控制、受信任联系人、语音、宠物、电脑操控、应用快照、浏览器操控、Cloud computer、图片库 |

范围边界：

- 插件管理按已安装列表、详情、配置、启停及适用的安装/卸载流程规划；公开插件市场未获纳入，暂不做市场发现、推荐与商城页面。
- 定时任务采用本地/Host 所属任务与运行历史；现有团队示例、团队筛选、“打开团队”入口退出目标 UI。创建、编辑、暂停/恢复、运行结果是保留的页面交互；调度执行服务单独验收。
- Git/Worktrees 保留仓库、分支、变更/差异和工作树相关 UI；不包含云端环境和自动代码审查。Worktrees 环境设置只覆盖本地工作树所需内容。
- 终端和子 Agent 使用 Thread 的面板区域，不扩展成浏览器操控或云电脑页面。
- 图片库排除；消息附件与产物的必要预览保留在会话中，不新增独立图库导航。
- 账号登录保留；团队管理与计费入口排除。使用量显示未被本轮排除，但独立用量页面尚未确认，不能随计费页自动加入。
- 通知、导入、个性化、钩子、独立个人资料/用量页、归档管理页等未在本轮明确选择的扩展项暂列待定，不混入必做批次；Recent 中已有的归档操作可随任务列表规划。
- 排除项从后续导航、路由和验收清单中移除；仅为排除项服务的组件不再投入。历史反向分析和截图作为证据保留。
- 排除入口已完成首轮 P0 清理，具体源码、验证边界和剩余事项见文末推进记录。

## 当前执行顺序

以下顺序替代历史推进记录中的“下一批”。逐批先核对已有 ASAR 契约和真实服务，再实施并验收页面；已实现的服务不重复开发。

| 顺序 | 本批交付目标 | 当前可复用结果 | 尚需交付 |
| --- | --- | --- | --- |
| 1（终端阅读 R3 26/26；子 Agent typed 历史 R1 59/59） | 终端 / 子 Agent 面板视觉和点击 | Exec producer、子 Agent producer、descendants/history、面板controller；终端阅读 R3 已冻结验证 26/26，12 张阅读图与 80 张壳层图已归档。子 Agent 真实 typed 历史复用 MessageCard，R1 59/59 及 8 张历史/80 张壳层图已归档 | 原版同尺寸像素对照、原生窗口组合、远端真实链路、完整持久后代历史仍待；本机PTY交互页面最新验收见第6项。各批数量保留为独立证据，不累加作覆盖率 |
| 2（页面及路由已验收；Host观察host-r5 38/38；聊天固定pin-r2 56/56；Session准入gate-r3 35/35；Inbox inbox-r2 54/54；Registry registry-r2 60/60；driver driver-r2 69/69；Runtime边界D runtime105/105；归档存储E store4/4；归档链F controller5/5+integration6/6；忙碌门禁G integration8/8；入口门禁H session5/5+agent4/4；提交后quiesce I agent4/4+integration10/10；页面归档按钮J interaction4/4；静止恢复K sp12/12+agent2/2；取消归档恢复L integration13/13；Recent同步M shared33/33+interaction1/1；移除保护N shared6/6+integration6/6；新建分支AP shared63/63+desktop7/7） | Git / Worktree 管理页 | 真实创建与已有分支切换、真实新建并切换分支（AP）；M1 独立managed登记/owner隔离、真实Git归属核验和页面；仓库/变更读取及差异；C1已有暂存变更提交15/15；持久 header 目录18/18；关联聊天固定/取消固定、owner分区存储与fresh bootstrap读取已验收 | pin-r2冻结2,259输入，10类56/56、完整JVM build exit0；4张真实固定状态图及8张观察回归图、geometry已核验。host-r5的38/38和host-r4失败历史独立保留。目录与固定能力不等于完整保护；归档后 live 列表失效已做（Recent 本身只列 live Agent）；移除保护已做（N 真实 Git 删除 + 全维度阻断 + 无 force/prune）；AP 只实现有证据的 create-and-checkout 一种形态（详见文末 AP），原版同尺寸视觉及原生窗口仍待；各批证据不重复累加 |
| 3（主路径交互14/14、独立页截图4/4，本批通过） | Projects / Recent / Home / Thread 主路径 | 本地项目登记与cwd绑定、真实会话、Composer、typed历史、Host作用域隔离 | 完整HarnessApp连续操作已覆盖项目详情→Home选择→Thread发送→Recent；1440×900/600×800、浅深主题新增16PNG及16份geometry核验通过。既有独立页120PNG保留。临时真实存储/LocalConnection/Gateway接受控模型端点，不等于真实模型或Agent运行；原版视觉、原生窗口及远端链路仍待。无title/updatedAt/unread/运行状态/pin/archive服务，不伪造这些操作 |
| 4（Cron页面R6已验收134/134；会话跟进O已验收；导航器视觉P已对齐；模板卡描边AE已对齐） | 定时任务页面和执行门槛 | 共用正式启动接线、本机Cron启用、真实页面操作到持久终态和Thread导航；R6冻结2,216输入，20类134/134，零失败/错误/跳过；12张暂停/接收/完成宽窄浅深PNG及geometry已核验归档。readonly-r2的85/85和公共菜单29/29保留为独立历史证据 | 会话跟进（Heartbeat）已接入既有会话投递并定向验收（O 批次），不再 Unavailable；导航器视觉已按原版截图实测对齐（P 批次：任务行左对齐、新建任务行左对齐+图标、小节标题「即将执行」、去掉多余分割线），P 见文末；落地模板卡外观也按原版截图实测收口（AE 批次：`HarnessSurface` 新增虚线分支画 1px 3-on/2-off 外框、新增令牌 `Corners.Card = 22.dp`（相切弧拟合参考 22.6 / 我们 22dp 路径 21.94）、落地标题块 ink 间隙 19→11，AE 见文末）；应用退出后后台执行未纳入本批；原版同状态像素和原生窗口待验收。PTY交互页面已另行验收，见第6项 |
| 5（页面已验收；本地Skills更新65/65；插件导航器视觉Q已对齐；设置导航器视觉R已对齐；设置正文视觉S已对齐；设置表单页框架T已对齐；设置右侧控件AA已对齐；设置细节描边AC已对齐；设置下拉内缩AD已对齐；inline Apps AF已接入；能力清单 AI已接入） | 插件管理和基础设置收口 | 插件本机登记/Skills消费、inline Apps与MCP/hooks/scripts名称清单消费、源目录更新及真实下一Agent请求消费；General/外观/模型/连接/快捷键已有批次 | 本地Skills更新65/65与8PNG已归档，不重复安排。导航器已按原版截图实测对齐并接真实已登记库存（Q 批次：289 宽、31 行距、标签内缩 +40、右边界竖线，Q 见文末）。基础设置导航器同样实测对齐（R 批次：导航器右边界线、共享搜索框内边距 12、占位图标回到 16px 单元格，R 见文末）。设置页正文也已实测对齐（S 批次：内容列 728、行分隔线内缩 17、页标题 28px/34 行盒，首卡纵向 189/266/359 与参考逐像素一致，S 见文末）；6 个设置表单页也统一到同一列与同一套卡片（T 批次：内容列 728、-8 偏移、卡片 12 圆角/SurfaceElevated/BorderSubtle、页标题 28px，T 见文末）；设置页右侧控件也按同一张参考图实测对齐（AA 批次：行级下拉由实心 Secondary 按钮改为白底 + 1px 描边的 28px 药丸并加手绘箭头、紧凑按钮水平内边距 16→10、紧凑控件圆角 8→9，AA 见文末）；设置页细节描边也按同一张参考图收口（AC 批次：浅色 BorderSubtle 由 alpha-05 的 #f5f5f5 加深到 #eeeeee，卡片外框/行分隔线/控件描边由 Δ7 收敛到 Δ≤1；圆角复核后维持 9，开关高与轨道端边残余见文末，AC 见文末）；设置下拉触发器的内容内缩与手绘箭头也按同一张参考图收口（AD 批次：内缩改用实测令牌 14、箭头 12×6→10×6、更正 radius-6 注释，AD 见文末）；manifest inline Apps 已接入本地详情（AF 批次：数组/对象键转为真实 Apps 名称、无 Apps 显示确认空列表，AF 见文末）；manifest MCP/hooks/scripts 名称清单也已接入本地详情（AI 批次：只读名称、schema 3 迁移、配置与执行仍明确不可用，AI 见文末）。MCP server、hooks、scripts、文件型 app 包、授权与连接生命周期、完整原生窗口仍待；Linux/Windows滚轮转换仅核对源码，未运行验证；紧凑 More 入口契约已判定（U 批次：Plugins 内容优先、其余进入即开抽屉，见文末）；插件页标题带尚未对齐（实测：我们标题 ink 顶相对内容顶 20、字号 20sp，参考同页为 35、ink 高 23 约合 24–25sp）；该参考页是市场页，已按确认范围排除（不做市场发现/推荐/商城），故不向其 hero 对齐；本条按范围判定关闭，不再留待定；若仍需对齐 shell 标题带，应以本地页面为准另行实测 |
| 6（PTY页面R5：184/184；紧凑视觉R1：35/35；字体消费者R1：30/30） | 交互终端能力与整体验收 | 真实PTY/ANSI、Host/Gateway/Local、语义输入及生产页面已接通；R5真实kernel resize与history复制已验证。紧凑视觉R1复用共享IconButton/Menu/Tooltip，一行selector/新建/更多、主题scrollbar，12张终端/80张壳层PNG及8张contact sheets已归档。字体消费者R1已接共享代码字体，固定面板内随字号重新计算行列并同步真实kernel；6类30/30、完整JVM build exit0，4张字体图及12张页面回归图已核验归档 | 原生窗口/IME、Remote及原版同状态像素仍待；launch仅macOS x86_64 verified LocalConnection。行高沿用Harness共享代码样式，未证明与原版一致；功能/离屏页面通过不等于原版像素通过。各批保留独立证据，回归不相加 |

当前 Git/Worktrees 聊天固定 pin-r2、Session准入A1（gate-r3）、Inbox准入A2a（inbox-r2）、Registry准入B（registry-r2）和driver准入C（driver-r2）已验收。driver批次父线程独立串行69/69及完整JVM build通过；各批独立保留，不累加。Runtime生命周期等待边界已按「锁只做同步状态提交、回调与等待在锁外」重构并定向验收（runtime 105/105、9类验收合同69/69、完整JVM build exit0），有限owner清理与显式静止恢复、资源静止、持久归档及Worktrees/Recent接线继续。尚未证明完整静止或启用归档按钮；完整移除保护继续单列。

此表中的截图和状态测试只作为对应批次证据，不等同像素一致或全部功能完成。账号登录和Host连接属于核心保留范围，沿实际缺口在主路径验收中接入；未确认的扩展页面不加入上述批次。

## 完成标准

- 已定义路由、已写 Composable、已通过状态测试、已接入页面、已完成截图对照是不同状态，不合并成一个“完成”。
- 页面验收必须覆盖可达入口、实际内容切换、主要操作、正常/空/错误状态、浅深主题和普通/紧凑窗口。
- 固定数据可用于截图测试；运行时的用户创建内容要有明确存储边界，不把每次重启恢复的示例数据称为真实功能。
- 后端暂缺时可以完成视觉和本地状态，但不得让空回调、无内容目标页、虚构执行结果通过交互验收。

## 公共组件现状

| 组件族 | 当前状态 | 待完成 |
| --- | --- | --- |
| Token、主题、文字、Surface、图标、Badge | 已有基础实现和部分测量 | 全量组件视觉表；占位图标和未量测状态逐项补证据 |
| Button / IconButton | 已有变体、大小、选中、禁用、加载、错误、鼠标与焦点状态 | 各页面统一接入；各状态截图对照 |
| Switch / Checkbox | 已有共享控件 | 页面接入和视觉验收 |
| Menu / Popover | 已有锚点定位与类型化菜单；项目/Git和定时任务迁移已定向验收，后者29/29；PTY操作已接共享typed Menu，真实指针/键盘及scope失效随紧凑视觉35/35验收 | 未迁移的旧调用按实际页面需要收敛；原版同状态视觉和原生窗口组合待验收 |
| Tabs | 已有选中、关闭、键盘导航 | 接入页面/面板；核对页签样式和面板归属 |
| Row / Section | 已有层级、展开、选中、错误重试；新增 SettingsFormFrame / Section / Card / 响应式 Row，外观和模型页已接入 | 其他设置页逐步统一；视觉对照 |
| Loading / Empty / Error | 已有共享实现 | 统一接入；补 unavailable、成功通知、Toast/Alert |
| Input / Search / Textarea | Search 已接 Settings / Scheduled；外观页新增真实高度的 BasicTextField 数字/颜色校验输入和 typed Menu 选择器 | 迁移其他表单；分段控件、Composer 多行与快捷键 |
| Dialog | 已新增受控 Confirm Dialog 与未保存目的地消费状态，JVM 交互测试通过 | 接入删除/退出等业务流程；专用视觉验收 |
| Tooltip | anchored Popup与Rail已接入；hover200ms、focus立即、关闭/抑制及壳层回归37/37，保留旧visible API | 其它业务入口按需要接入；原版同状态视觉待验收 |
| Scrollbar / Resize | 基础Scrollbar仍只绘制；Workspace navigator已有拖拽尺寸逻辑；PTY grid已有真实scrollback/拖动/viewport resize，本批10dp滚动条接Surface/Border/BorderStrong主题 | 其他基础滚动条绑定与拖动按页面补齐；右/底部面板尺寸与折叠、原生窗口及原版视觉待验收 |
| 业务共享组件 | 真实Composer、设置页框架、插件卡片和消息/工具披露已接入；子Agent历史复用MessageCard，59/59；消息最终释放33/33及92PNG已验收归档 | 文件/产物列表和原版同状态视觉按实际缺口推进；消息离屏验收不证明原版像素一致 |

源码依据：modules/ui-foundation/src/im/hikaru/harness/ui/foundation/ 下的 HarnessComponents、HarnessActionControls、HarnessCollections、HarnessOverlays、HarnessFeedback；apps/shared/src/im/hikaru/harness/client/page 与 shell。

## 页面覆盖情况

| 页面族 | 当前状态 | 本轮审计确认的缺口 |
| --- | --- | --- |
| 全局壳层 | Rail、Navigator、账号菜单、More、窗口尺寸已有实现和截图；Thread面板在完整HarnessApp的普通/紧凑浅深及拖宽navigator组合已定向验收 | 部分标题栏/通知操作为空；其余弹层与原生窗口组合仍需验收 |
| Home | 已接真实会话控制器与本机模型选择；Local project picker 传实际 cwd，prepare/association 成功后才 prompt，selection/binding fence 拒绝过期操作 | 既有17项交互测试与16张浅深/宽窄截图只覆盖此前批次；本次 Projects 集成的4项 desktop交互测试已通过；附件/权限仍禁用，最终像素对照待做 |
| Thread | 共用真实会话控制器与 Composer；typed 历史/工具结果；断线与同 Host 恢复；子 Agent live descendants/已验证 child history/显式刷新；默认 subagent-tools 已安装，真实父/子 loop 与持久化读取组合 2/2 通过；ExecProcessProducer 已实现真实进程生命周期、dispose和显式stdin/EOF/取消，面板已接控制入口 | stdin114/114、进程选择106/106及108PNG已通过；独立PTY/ANSI/resize基础38/38和Host/Local70/70已通过；PTY真实页面R5为184/184及10图，紧凑视觉R1为35/35及12终端/80壳层图，字体消费者R1为30/30及4字体/12页面图，分别归档不累加。原生IME、Remote及原版同状态像素仍待；子Agent15/15与组合2/2不证明持久历史枚举、永久Done或远端链路；完整视觉验收继续 |
| Recent | 当前Host真实SessionSummary、createdAt排序/搜索/读取状态/重试/滚动及Thread导航已接入；完整主路径交互和独立页截图批次已验收 | 原版同状态视觉和原生窗口仍待；SessionSummary无未读/运行状态/归档字段，不伪造这些操作 |
| Projects / Project | 真实本机登记、目录验证/cwd绑定、Home picker与会话关联；完整项目详情→Home→Thread→Recent主路径已有14项交互和16张截图证据 | 持久 membership 不等于重启后完整会话枚举/恢复；置顶/扩展管理无对应服务，原版像素/原生窗口及远端仍待 |
| Scheduled Tasks | typed配置/CRUD、JSON/CAS、Owner/容量、运行账本与页面已接入；共用正式接线已启用本机Cron，R6真实页面到持久终态/Thread导航及回归20类134/134通过，12状态PNG已核验归档；只读证据85/85保留为历史批次 | 会话跟进（Heartbeat）已接入既有会话投递并定向验收（O 批次）；导航器视觉已按原版截图实测对齐（P 批次：任务行左对齐、新建任务行左对齐+图标、小节标题「即将执行」、去掉多余分割线）；应用退出后后台执行未纳入本批；原版同状态像素及原生窗口待验收 |
| Settings General | 关闭策略已真实持久化并接入桌面窗口；加载/失败/重试、插件导航与紧凑布局已实现；导航器（R 批次）与正文（S 批次）都已按原版截图实测核对，正文的标题、分节、卡片宽 728、行内缩 17、分隔线内缩 17 与首卡纵向节奏已逐像素对齐 | 其他偏好明确禁用并说明未接服务（因此第二张卡片比参考高，行高 73 vs 61）；Thread 面板偏好等消费者待接入；右侧控件颜色/形状与完整原生窗口验收待做 |
| Settings Appearance | 已有 Visual / Advanced 页面、校验和导入；桌面配置持久化，已接实际主题/字号/字体；PTY空视口和正文已消费共享代码字体，字号变化触发真实viewport/kernel resize，字体族/样式与UI字号独立性已定向验收30/30 | 原版任意字体目录、代码预设、原生透明/Dock、平滑与系统减少动画适配、原版分享格式兼容、原图像素验收 |
| Settings Models / Provider | 本机 Host 配置入口和实际目录；新增/编辑/删除配置、凭据保存、搜索、revision 冲突和刷新失败处理；Provider 实现归 bundle 服务层，desktop adapter 只作 DTO 映射 | 当前仅本机 Desktop，Composer 已消费本机模型选择；远端探测、远程 Host 配置与远端 Composer 目录未接入；无原版 Provider CRUD 原图，不能宣称像素一致 |
| Settings 其余条目 | 侧栏收敛到范围内条目并分组（个人/集成/编码）；连接已接Host发现/选择/详情，快捷键已有类型化配置与事件消费；Git/Worktrees已接真实读取/创建/分支切换、M1 managed inventory、C1已有暂存提交15/15与真实新建并切换分支（AP）；导航器几何已按原版截图实测对齐（R 批次）；页面框架与「常规」页统一（T 批次） | Worktree归档/恢复/删除、完整保护数据与本地环境设置仍待做；连接/快捷键/Git/Worktrees页面级视觉验收待做 |
| Plugins / PluginDetail | 本机manifest/Skills快照登记、列表/详情/启停/移除及下一请求消费已接线；本地源目录更新65/65及8张截图已归档，CAS/lease与身份隔离保留；导航器已按原版截图实测重建并接真实已登记库存（Q 批次） | Apps/MCP/hooks/scripts/技能附件与授权配置生命周期仍待；本地Skills更新不等于完整包升级；市场暂不纳入；原版同尺寸几何已在生产 Shell 上核对（Q 批次），完整原生窗口仍待 |
| Images | 独立图库入口、路由和页面已移除 | 历史参考证据保留 |
| Hosts / Account | 已有连接/账号功能页面 | 按参考重排视觉、Host 列表与详情、账户菜单与外部入口 |

历史设置条目清单（仅用于审计现有代码；是否实施以上方已确认范围为准，external 条目不等于独立本地页面）：

- 个人：常规、通知、导入、个人资料（external）、外观、家长控制、受信任联系人、语音、配置、个性化、Mini 与虚拟宠物、键盘快捷键、使用情况和计费、账户（external）。
- 集成：插件、电脑操控、应用快照、浏览器、Cloud computer。
- 编码：钩子、连接、Codex 云端、Codex 云端（旧版，占位映射）、代码审查、Git、Worktrees（含 environments 子 tab）。
- 已归档：已归档的聊天（当前映射需再核对，与参考 data-controls 不能自动等同）。
- 团队：已恢复 /settings/teams/:teamId 的导航证据，当前无对应内容页。

新扫描见 reverse-engineering/inventory-2026-10-07：81 个选定 bundle，70 个路由字符串记录、68 个唯一字符串、40 个数据源记录。它们含 API 路径、动态模板和排除页面，须逐条分类，不能直接当成页面总数。旧根目录 routes.json 为空、data-sources.json 只有 2 条，不再用于覆盖率结论。

## 实施顺序与交付物

| 批次 | 内容 | 依赖与验收 |
| --- | --- | --- |
| P0 范围与入口收口 | 按确认范围过滤路由、Settings 条目和 Rail/More 入口；移除图库与团队等排除入口；建立保留页面的证据/状态/动作表 | 更新对应导航测试，不再以固定复刻全部 Codex 入口作为要求；不删除历史参考证据 |
| P1 高频公共组件 | 统一 Input/Search/Select/Segmented；补 Dialog、Tooltip、Toast/Alert、滚动与面板容器；建立组件状态展示页 | 先满足下一批页面所需组件；浅深色、焦点、禁用、加载、错误截图 |
| P2 Settings 路由与基础页 | 侧栏点击→typed route→section 内容；General 收口；外观、模型/Provider 配置、连接、快捷键 | 复用设置行/卡片；切换与返回有效；视觉参数一致；配置边界明确 |
| P3 Home / Thread 与面板 | Composer、模型/模式/附件选择；消息/工具/文件卡片；右侧摘要、终端和子 Agent 面板 | 复用已有 Session 功能；固定视觉数据与会话内容分离；面板切换、关闭、状态与可用动作验证 |
| P4 列表与任务 | Projects/Project/Recent 完整状态；Scheduled 创建/编辑/暂停/历史 UI 和导航缺口 | 复用 Row/Menu/Dialog；同一对象跨页面一致；新建后可见；不伪造真实定时执行 |
| P5 插件管理 / Hosts / Account | 已安装插件列表、详情、配置、启停与适用的安装/卸载弹层；连接/账号视觉对齐 | 参考证据先行；本地交互闭环；不含公开市场、图库、团队和计费 |
| P6 Git / Worktrees | Git 设置、分支/变更/差异相关 UI、工作树列表与创建/管理、本地环境设置 | 复用文件卡片、Tabs、Dialog 和面板；真实仓库变更按对应操作单独验收；不含云端与自动审查 |
| P7 全面视觉验收 | 每个已交付页面/弹层按窗口尺寸和主题截图对比，核对文字/图标/间距/滚动/焦点 | 每批也做局部验收；最终扫全覆盖表。真实后端对接单列后续任务 |

执行方式：主线程先分析当前批次所需 app.asar 片段与参考截图，输出组件树、动作表、几何参数、验收项；再交给 SHUAIAPI/gpt-6.1-sol 实现（2026-10-07 更新为委托模型选择；2026-10-10 起用户要求后续统一用 deepSeek v4.1 flash 直接推进，不再委托子代理）。每个委托只负责一个页面族或一个组件族，限定写入文件；主线程检查实际 diff、关键交互和对应截图。公共 API 未稳定前不并行修改多个依赖页面。历史记录中的 ZeroCat 委托保留实际发生的模型名称。

优先启动 P0 + P1 所需组件 + P2 路由。每批记录“分析完成 / 组件实现 / 页面接入 / 交互验证 / 视觉验收 / 数据来源”，不以测试总数替代页面进度。

## 现有验证边界

历史批次已记录 shared JVM 109/109；desktop 44 通过、1 已有取消传播断言失败、5 live 测试跳过。测试与截图结论仅适用于各次明确覆盖的案例，不能外推到后续实现或完整页面视觉。最新主线程反馈为 shared 定向73/73已过、Exec 初轮12/13（fixture service 重复注册待修）、desktop 初轮因 test 源码引号错误未编译，正在修复验证；此处不记为 desktop 通过。计划初稿及本次旁路同步均未重跑构建或测试；历史实施结果见下方记录，最新边界见文末。

## 推进记录：2026-10-07 P0 与 Dialog

| 事项 | 当前证据 | 剩余工作 |
| --- | --- | --- |
| 范围导航 | Settings 8 项；移除 Images 路由/导航/页面；Scheduled 移除 team model/过滤/跳转 | 后续页面逐项按范围接入 |
| 设置路由 | 行点击→typed PageId；选中项由当前 route 派生；General/Plugins 已有内容，其余分别显示待实现页 | 外观/模型/连接/快捷键/Git/Worktrees 内容尚未完成 |
| 普通/紧凑入口 | More 暴露 Scheduled/Hosts/Settings，紧凑模式另含 Plugins；底栏保留 More 并正确打开菜单 | 其他弹层、键盘操作和像素验收 |
| Scheduled 状态 | 抽屉 mode 由 repository 持有；查询不扩张 backstack；runtime 默认空数据，新建默认暂停且无虚构 runs；seed 仅供 fixture | 当前仍为内存数据；创建配置表单、编辑/暂停恢复、历史内容、持久化和实际执行待做 |
| Confirm Dialog | Ready/Submitting/Failed；危险取消焦点、长内容滚动、失败重试、提交阻止重复、Escape 与焦点恢复；消费一次未保存目的地 | 页面接入和具体视觉参数待验收 |
| Search | ZeroCat/gpt-6.1-sol 实现，主线程审核/修正测试；共享字段已接 Settings / Scheduled；清除、长输入、焦点与禁用测试通过 | 其他搜索/表单组件与完整组件视觉表 |
| 外观分析 | 从实际 ASAR 提取 AppearanceSettings 和 general-settings，确认 visual/advanced 分组和 local state | 配置键/表单/API/几何未齐，尚未委托页面 |

本批最终验证：ui-foundation JVM XML 37 tests / 0 failures；shared JVM 109 / 0；桌面 ScopeNavigationInteractionTest 三项点击测试与 renderScopedNavigationSnapshots 一项截图测试共 4 / 0。桌面编译通过。点击覆盖设置内容/选中切换、compact More 到 Plugins/Scheduled、筛选后新建在主页与 navigator 同时可见。8 张 1440×900 / 600×800、Light / Dark 截图位于 captures/scope；人工查看 compact More 浅深色与 wide dark Scheduled，菜单未裁切，共享 Search 修复了文字裁切。Scheduled 内容仍是原型布局；本批不声称像素一致。Search 新交互测试按契约放在 test@jvm。

首次未限定 platform 的 ui-foundation test 同时运行 Android：8 个 UI interaction cases 因 Android mock 的 Build.FINGERPRINT=null 失败，JVM 35/35 通过。桌面目标后续使用 -p jvm；Android UI test 环境另行处理。本批未重跑全部 desktop suite 或 live 集成测试。

反向库存本批重新按 ASAR header offsets 提取完整选定文件，实测归档 SHA 与 manifest 一致；新 inventory 独立保存，历史证据保留。正则字符串库存仅提供定位线索，并非完整按钮行为或页面实现契约。

## 推进记录：2026-10-07 P2 外观设置

本批继续已确认范围，未扩大到排除项。分析与页面契约完成后委托 ZeroCat/gpt-6.1-sol；该次调用终止于 429 且未留下页面文件，主线程完成本批实现与验收。当时下一批委托计划仍为 ZeroCat/gpt-6.1-sol；最新选择已在执行方式段更新为 SHUAIAPI/gpt-6.1-sol。

| 阶段 | 实际交付与证据 | 边界 |
| --- | --- | --- |
| 反向分析 | Visual / Advanced 组件树、Ys / Ia / La / No、真实 descriptor oF、保存函数 dxn / kRs、HOi / nki / aUs 几何；appearance-settings-evidence.json 与 appearance-implementation-contract.md | 无本页原图；General 的728主列与顶部73只作为页面框架依据 |
| 公共组合 | SettingsFormFrame / Section / Card / responsive Row / typed Select / BasicTextField 校验输入 | 外观已接入；其他设置页尚未迁移 |
| 页面与路由 | Settings > Appearance 显示实际内容；三种模式单选、键盘与焦点；高级展开、独立浅深调整、数字/颜色校验、重置、复制与导入 | 分开显示是本地 UI 状态；不修改任何主题值；重置保留视觉颜色/UI字体 |
| 数据边界 | AppearanceRepository 成功写入后发布；DesktopAppearanceStore 原子替换本客户端 home 下 client-appearance.json；读取错误与未完成读取均禁止编辑；取消传播；重复提交被阻止；可重试 | MemoryAppearanceStore 只供平台暂未接存储时和测试使用；坏配置不自动覆盖；新实例重读验证模拟重启 |
| 实际生效 | 已保存 mode/customization 接入窗口、根 Slot 和 AppShell；颜色/对比度、UI字号/字体、参考 Thread 内容/代码字体与代码字号；共享按钮/开关的指针偏好；共享加载指示器减少动画；参考变更计数符号偏好 | 尚未覆盖所有业务控件/所有消息 renderer；系统减少动画、字体平滑未接平台 adapter |
| 导入与失败 | 独立 harness-theme-v1 编码；只有 saveRevision 增长且已保存 candidate 相符才关闭导入弹窗；格式错误可修正、写失败可重试；复制反馈在实际剪贴板调用返回后显示 | 与原版 Codex 分享格式不兼容；字体目录仅 System/Sans/Serif/Monospace；剪贴板调用仍使用已弃用但可用的 Compose API |
| 不可用项 | 字体平滑禁用；代码预设、半透明窗口、Dock 图标明确不可用 | 不能算原版外观全部功能完成 |
| 视觉检查 | captures/appearance 共12张：1440×900、600×800、1440×2400，各含 light/dark 与 visual/advanced；人工查看普通浅色、紧凑浅深色和深色完整高级区域；紧凑滚动到高级/返回导入通过点击测试 | 2400高为离屏完整内容检查；普通/紧凑截图从顶部开始；不声称像素一致或原生窗口实时验收 |

本批构建与测试输出在 /tmp/harness-appearance-20261007。ui-foundation JVM 最终39/39；shared JVM 曾全量120/120，最后的取消读取保护修改后补跑 AppearanceRepositoryTest + DesktopAppearanceStoreTest 11/11；桌面最终 AppearanceInteractionTest 7项 + ScopeNavigationInteractionTest 3项 + renderAppearanceSettingsStates 1项，共11/11。桌面 main 与共享页面编译通过，git diff --check 通过。本批未重跑全部 desktop suite、Android/iOS 构建或 live 集成；上一批取消传播断言问题不在本批验证范围内。

下一批顺序仍按已确认范围：先完成 General 的保存/有效动作以及模型/Provider、连接设置的分析契约和实施，再继续快捷键、Home/Thread 的真实 Composer 与终端/子 Agent 面板；随后完成 Projects/Recent、Scheduled 表单与持久化、插件管理、Hosts/Account、Git/Worktrees。总目标仍未完成。

## 推进记录：2026-10-07 P2 模型 / Provider

本批先补 app.asar 配置/连接证据和页面契约，再尝试委托 ZeroCat/gpt-6.1-sol。该次委托在生成页面文件前因 429 终止，已关闭；主线程完成实现、真实 Host adapter 和验收。

| 阶段 | 实际交付与证据 | 边界 |
| --- | --- | --- |
| 反向分析 | models-connections-analysis-2026-10-07.md 与 models-implementation-contract.md；按当前归档 offset 读取 configuration-panel 与 remote-connections 片段，记录 size/SHA256 | settings-configs 是排除的云端 environment configs；没有原版 Provider CRUD 原图，页面按 Harness 真实能力适配，不能声称字段和像素一一复刻 |
| 页面 | Settings > 模型与 Provider；搜索、新建/编辑/删除配置、模型详情、凭据弹窗；普通/紧凑浅深主题；表单可滚动，footer 在滚动区域外 | 复用 Settings 框架、Search、typed Select、Button、Dialog；未形成全部公共组件展示页 |
| 真实来源 | Desktop 注入本机 runtime.context；SettingsKey 的 llm-koog namespace、LlmRuntime 的声明入口/注册目录/模型能力、CredentialsKey 状态；页面展示所属作用域 | 仅本机 Desktop adapter；其他客户端缺 adapter 时明确 unavailable；远程 Relay 仍没有可用模型配置与凭据写入接口 |
| 保存与保留 | expectedRevision 防覆盖；只写本页拥有字段，保留其他 Provider/namespace、模型描述与能力、modelOverrides；真实文件 round trip 与新 Host 重读通过 | models 与 modelOverrides 是互斥配置；沿用 overrides 时加入自定义 models 会被真实 validator 拒绝，并保留原文件；不能悄悄删除 overrides |
| 状态与失败 | 成功 write 后才增加 saveRevision，再重读发布；已保存但重读失败要求刷新，不自动重复写；编辑冲突保留草稿并显式重载；删除冲突刷新后旧确认窗口禁用删除，需重新检查 | 保存不表示网络/API key 验证通过；“路由已注册”只描述本机当前已加载状态，凭据重载可能异步完成 |
| 凭据 | 值仅留在弹窗草稿与一次服务写入；密码字段、取消清空、写失败错误净化、成功重读后关闭；读取/配置 DTO 和 settings.json 不含值 | 实际来源不可写时禁止提交；没有远程凭据编辑、远端探测、API model discovery |
| 截图验收 | captures/models 共20张：12张页面（1440×900 / 600×800，light/dark，配置/未配置/不可用）与8张真实600×800弹窗（模型详情、表单顶部/底部、空凭据）；人工查看普通浅色、紧凑浅深、不可用深色及四种弹窗 | 截图使用显式测试 fixture；生产不注入示例 Provider。表单长内容通过滚动访问；无原版本页截图，不声称像素一致或实时原生窗口验收 |

本批构建输出：/tmp/harness-models-20261007。最终桌面 DesktopModelSettingsBackendTest 6项、ModelSettingsInteractionTest 8项、ScopeNavigationInteractionTest 3项、AppearanceInteractionTest 7项、renderModelSettingsStates 1项，共25/25；main/shared/test 编译通过。共享 JVM 128/128 的最终重跑结果记录在同目录 reports/shared/jvm。git diff --check 通过。验证未发起模型网络调用、没有写入用户真实凭据或 home 配置；未跑全部 desktop/live/Android/iOS 套件。

后续顺序：General 有效动作与存储、连接、快捷键；Home/Thread 真实 Composer、终端和子 Agent 面板；Projects/Recent 与 Scheduled 表单/持久化；插件管理/Hosts/Account；Git/Worktrees。已确认排除范围保持不变，总目标继续。

## 推进记录：2026-10-07 P2 General 与服务边界

用户本批将委托模型改为 SHUAIAPI/gpt-6.1-sol，实际委托成功。该 Agent 完成 General 鼠标测试与窗口快捷键/确认/取消测试；主线程审查后修正 Compose 内部事件 API 的测试构造，统一编译与运行。

| 阶段 | 实际交付与证据 | 边界 |
| --- | --- | --- |
| General 分析 | general-implementation-contract.md：confirm-before-close 三策略及默认 keyboardOnly、projectless root、prevent sleep、terminal location 的 descriptor；窗口确认片段与既有布局测量 | 参考截图状态不是运行时默认值或已接服务证明 |
| 保存与有效动作 | GeneralSettingsRepository 成功写入后才发布；DesktopGeneralSettingsStore 原子写 client-general.json；读取/写入错误、重试、取消与重复提交保护；真实插件管理导航 | 插件目标页管理仍待实现；其他偏好禁用并说明具体缺失能力 |
| 窗口消费者 | DesktopWindowCloseController 区分 NativeControl/KeyboardShortcut；Never/KeyboardOnly/Always；Cmd-W 或 Ctrl-W 的 KeyDown；确认/取消/重复请求只关闭一次 | Cmd-Q/系统注销不属于本窗口策略；离屏交互测试没有关闭用户真实窗口 |
| 紧凑布局 | 600dp 进入上下布局；728dp 主列无法容纳时左右16dp padding；420/600/720dp 标题和卡片 bounds 检查；修复左侧裁切 | 维持大窗口参考几何；不声称像素一致 |
| Provider 服务边界 | DesktopProviderSettings 实现归 modules/bundle/desktop；desktop adapter 只作 bundle/shared DTO 与冲突映射；真实 round trip 与 production boundary 测试通过 | 保留模型配置能力、描述、overrides 和未知配置；没有放宽边界测试 |
| 取消传播 | HarnessApp 历史重试在映射普通错误前重新抛出 CancellationException | 全部 desktop production boundary 断言通过 |
| 截图 | captures/general-runtime 八张，2560×1440 / 1440×900 / 600×800 / 1440×1800 × light/dark；人工检查修后600浅深及完整深色内容 | fixture 显式 loaded/durable/closePolicyAvailable；非原生窗口实机与原图像素验收 |

最终构建输出：/tmp/kmp-harness-provider-20261007。desktop 全量发现87项，5项live因未提供HARNESS_OPENAI_LIVE_API_KEY跳过，启动82项、82通过、0失败；包括General四项、窗口controller/shortcut三项、窗口UI三项、Provider六项、production boundary两项及所有现有截图测试。shared本批全量134/134通过发生在紧凑布局修正前，修后的shared页面由最终desktop运行重新编译；未重跑Android/iOS。git diff --check通过。

后续从连接页继续，参考连接原版包含控制其他设备/控制本机的页面，这些属于已排除的电脑操控范围；本项目仅接编程Host的连接状态、发现、选择与详情。SSH adapter当前缺失，不能用Relay Host列表冒充SSH连接，也不伪造SSH可用。具体分析见connections-implementation-contract.md。


## 推进记录：2026-10-07 P3 真实会话与面板

本批沿用 SHUAIAPI/gpt-6.1-sol，先完成当前 ASAR 的 Home/Thread 与终端/子 Agent 契约，再接共享 typed 控制器与会话 surface。

| 阶段 | 实际交付与证据 | 边界 |
| --- | --- | --- |
| 真实会话 | ConversationController 消费 Session list/create/history/prompt/cancel；Home 创建成功返回的 ID 导航到 Thread；Recent 使用 SessionSummary；本机模型选择传入 AgentOptions | 未提供远端模型目录时不使用本机目录；Relay delivery acknowledgement 不代表 Agent 执行结束 |
| 作用域与恢复 | 切 Host/身份清旧scope；同 Host 断线保留 Thread/draft、清旧history、禁发；新 describe验证后重新绑定；新 repository 的collector使用独立 key，修复换Host期间借用旧describe而读取旧Thread的问题 | 旧请求回包须由generation/scope拒绝；远程真实链路仍另行验收 |
| 消息与输入 | typed Message/Text/Reasoning/ToolCall/ToolResult；多行输入、键盘发送、长输入与紧凑布局；Home/Thread共用Composer | 附件、权限等缺服务的按钮禁用并显示原因；最终消息与工具样式仍需原图对照 |
| 终端与子Agent | typed panel state、tabs、宽度与loading/error/unavailable；Host/Thread作用域隔离；真实缺服务状态有说明 | 当前Connection没有面板数据或管理接口，生产明确unavailable；这不等于终端/子Agent功能完成 |
| 测试 | LiveConversationInteractionTest 11项、LiveHarnessConversationInteractionTest 6项，最终17/17；RemoteConnectionOwnerReconnectTest与RemoteConnectionDiscoveryTest 7/7 | shared全量191/191是此前批次结果；本次未重跑全部shared/desktop/live/Android/iOS |
| 截图 | LiveConversationScreenshotTest 4/4；captures/live-conversation 共16张：Home、typed Thread、终端unavailable、子Agent unavailable ×1440×900/600×800 ×light/dark；人工查看四类代表截图，无明显裁切 | 离屏surface截图不是全Shell实机或像素一致证明；Home宽度与footer几何仍需补原版证据，不能任意套42rem |

本次串行构建目录：/tmp/kmp-harness-shared-next-20261007c。报告文件会由下一次同目录测试覆盖，17项通过记录来自最终交互运行；当前XML分别保存4项截图与7项远程连接测试。测试fixtures仅在测试代码中使用。

下一批：插件、Git/Worktrees、Scheduled 当前ASAR分析分别限定写集并行进行，审核契约后再委托实现。插件现有假卡片/开关、Scheduled无表单创建和缺chatRef时合成Thread ID均须替换；缺服务不能宣称功能完成。

## 推进记录：2026-10-07 插件、Git/Worktrees 与定时配置

本批三组分析与实现均交给 SHUAIAPI/gpt-6.1-sol；主线程在各写集释放后串行编译、修正集成错误并验证。

| 事项 | 实际交付与证据 | 剩余边界 |
| --- | --- | --- |
| 插件 | 当前 ASAR 契约、typed InstalledPluginRepository/controller；真实 list/filter/detail 路由，生产移除示例卡片；无服务显示 unavailable | Desktop 尚无用户插件 inventory/manifest/启用配置/包管理服务；运行时 Loader/Fiber 不具有同一语义，不能代填安装列表。管理与生产读取仍未完成 |
| Git/Worktrees | DesktopGitBackend 对真实本机目录执行 status/branches/worktree list/diff；NUL 解析、HEAD 前后校验、无外部 diff/textconv、输出与超时上限；Local Host 验证、scope 隔离和实际页面路由 | 只读服务；checkout/commit/工作树创建归档删除/本地环境配置未实施；diff 不包含未跟踪内容，工作区与暂存区不是原子快照；视觉对照待做 |
| 定时任务配置 | 真实创建/编辑表单、搜索详情、暂停配置 CRUD；DesktopScheduledStore 版本化 JSON、owner/revision/文件字节 CAS、锁、原子替换与 tombstones；跨 Store 实例重读验证 | 当前新建/编辑后暂停；激活/立即执行/运行账本尚不可用；关闭页不创建或执行会话；坏文档不可覆盖 |
| 时间规则 | Hourly 固定时长，Daily/Weekdays/Weekly 按真实时区；DST gap/overlap 与跨年/闰年验证 | 自定义 RRULE 尚无消费链；计算下一次时间不是后台已执行的证明 |
| 本机 bootstrap | main 注入真实 Git adapter、client-scheduled-tasks.json Store 与 Calendar；显式 Local transport，避免本机持有账号对象时被误判 Relay | 远端没有 Git/定时接口时不读取父机数据；桌面增加 JSON 序列化编译配置 |
| 验证 | shared InstalledPluginsControllerTest 11、GitControllerTest 8、ScheduledRepositoryTest 8、ScheduleEngineTest 2，共29/29；desktop Git parser/backend 10、Store/Calendar 10、Plugin routing 3、Scheduled routing 2、scope navigation 3、live conversation 6，共34/34 | main/shared/test 源编译通过；测试真实存储使用临时目录，未改用户配置、仓库或发起模型执行；本批尚无专用截图/像素对照；未跑全量desktop/Android/iOS/live |

构建目录 `/tmp/kmp-harness-shared-next-20261007c`，当前 desktop XML 记录34项全通过；复用目录时报告可能被后续运行覆盖。主线程修正共享 Row state 构造、Git 跨模块可空属性校验及桌面序列化配置；Git 无效 base ref 在任何 Git 命令前被拒绝，保留该安全行为的测试。`git diff --check` 已通过。

下一批进行真实子 Agent descendants/history 的 Host→Gateway→Local/Relay→panel 链与本地调度执行器分析；终端 PTY/process 服务与用户插件后台仍需建立，不把 unavailable 页算功能完成。目标保持进行中。

## 最新同步：2026-10-07 本机 Projects、Exec 与 Cron 门槛

本节依据当前 CodeGraph 源码核对、相关契约及主线程本轮提供的进度。本次结果已由主线程在隔离源码与构建目录验证；未提交或执行 reset/stash/clean。此前实施前契约用于解释边界，不能覆盖已经交付的实现。

| 事项 | 当前实现状态 | 验证与剩余门槛 |
| --- | --- | --- |
| 本机 Projects | DesktopLocalProjectStore/Repository 已实现真实持久登记与目录验证；校验 Local transport、真实 LocalConnection、Host describe 与连接实例；Home picker 消费真实项目，create 使用确认 cwd，membership 校验真实 SessionSummary | 首批创建是登记已有目录，不等于 mkdir/Git 初始化。持久关联与 live summaries 分开；未 live 的已知会话不能伪装可恢复历史。desktop 存储/仓库10项、真实目录与重连交互4项通过；扩展管理和视觉待做 |
| Conversation 顺序与隔离 | prepare 成功才 create；create 返回真实 ID 后 association 成功才 prompt；失败保留已创建 ID/草稿，关联重试不再 create、不自动 prompt；selection/binding fence 阻止旧作用域更新 | 本轮 shared Conversation/Projects 41/41、desktop 关联失败及同连接恢复回归通过；关联阶段可 Stop，等待本地清理且不触发 backend cancel/prompt；已有会话不因 picker 改变而改写 cwd |
| 终端与面板 | ExecProcessProducer 已有真实 ProcessBuilder 进程、输出、生命周期、超时/取消与 dispose；HarnessApp panel state 始终收集刷新已修；子 Agent 保留真实 descendants/history 消费 | Exec fixture 已修，定向13/13通过；进程输出能力不等于交互 PTY。面板关闭不作为杀进程动作；默认子 Agent spawn、完整交互和视觉仍待验收 |
| Cron R1/R2/R3 与基础组合 | R1 prompt 前安全撤回、R2 Unknown 只读重核、R3 副作用入口 scope 校验已实现；本批真实 executor/JSON store/leader/Runtime/LocalConnection/Room 组合 3/3、双 JVM leader 2/2 通过 | verifiedCronExecution=false；Heartbeat unavailable。正常关闭重开与 leader holder 强制退出已验证；executor crash recovery、跨进程 executor recovery、停止/CAS/DST/missed wakeup 完整故障矩阵仍待验证 |
| 本轮测试快照 | 最新 shared Conversation/Projects 定向41/41；Exec 13/13；desktop本批定向34/34 | 已修测试JSON字面量、Compose waitUntil参数及session DTO类型映射；隔离验证未改用户配置或执行模型请求。未跑全量desktop/Android/iOS/live，不将定向通过扩展为完整验收 |
| 其余保留范围 | 插件本机登记/详情/启停/移除与下一请求Skills已接真实服务，59项定向通过；Git/Worktrees 真实只读链及52/52验证的本地创建、Home/Thread 与基础设置已有基础 | 插件完整包生命周期/配置与Apps/MCP等消费者、其他 Git 写操作、Worktree管理/会话绑定、本地环境配置、各页面视觉验收仍未完成 |

依据与边界：

- `projects-local-implementation-contract-2026-10-07.md` 是实施前契约；其中“没有项目 store/目录绑定/Home cwd”已由本轮实现替代。持久历史枚举/恢复限制仍适用。
- `scheduled-cron-readiness-2026-10-07.md` 的 R1/R2/R3 作为已实现修复的验收项保留；不继续列为尚未写代码。Room adapter 已有 6/6 记录，本批另验证基础 executor 组合 3/3 与双 JVM leader 2/2；R4 启动失败恢复 Owner/入口与 R5 跨 task 并发预算本批已实现并定向验证；完整真实故障矩阵仍待完成。
- `reverse-engineering/terminal-producer-gap-2026-10-07.md` 的 producer 缺口属于实施前分析；当前已有真实 producer/dispose。交互 PTY、完整面板、子 Agent spawn 与视觉边界仍单列。
- 保留范围继续为定时任务、插件管理、Git/Worktrees、终端/子 Agent 面板、本机 Projects、Home/Thread、基础设置及已确认壳层/连接；云端、团队、计费、语音、宠物、电脑/浏览器操控、快照、图片库继续排除。

### 建议下一最小批次与后续顺序

1. **当前验证批次已完成**：Exec 13/13、shared Conversation/Projects 41/41、desktop定向34/34通过，均无跳过。Cron生产仍关闭；完整产品及视觉验收继续推进。
2. **Cron 基础组合批次已完成**：runNow、activation/tick、精确 terminal 落 ledger、正常关闭后换 reader/executor 不重投，以及双 JVM leader 的竞争/退出接管均通过。R4/R5 本批已通过定向验证，下一批限定真实停止与 crash/CAS/DST/missed wakeup 组合门槛；基础路径不替代生产启用审查，Heartbeat 继续 unavailable。
3. **剩余服务按单一页面族推进**：插件本机文本登记及Skills消费已完成服务接线；完整包生命周期与配置/Apps/MCP等消费继续单列。Git 写操作与 Worktree 管理按真实动作分别验收；Projects 扩展管理及持久历史恢复按需求独立排期。
4. **逐页视觉验收**：本机 Projects/Home picker、终端/子 Agent、Scheduled、Plugins、Git/Worktrees 与基础设置，覆盖正常/空/错误/unavailable、浅深主题、普通/紧凑窗口、焦点与滚动；历史截图只证明对应旧案例，完整视觉与像素对照仍未完成。


### 本批验证命令与隔离边界

- 源码快照：`/tmp/harness-native-verification-20261007`；构建目录：`/tmp/harness-native-verification-build-20261007`。串行运行，报告可能被后续同模块测试覆盖。
- `./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m exec-jvm --include-classes=im.hikaru.harness.exec.jvm.ExecProcessContractTest`：13/13。
- 同一命令使用 `-m shared`，限定 `ConversationControllerTest` 与 `LocalProjectsControllerTest`：41/41（31+10）。
- 同一命令使用 `-m jvm-app`，限定 `DesktopLocalProjectStoreTest`、`DesktopLocalProjectRepositoryTest`、`LocalProjectsInteractionTest`、`SessionBackgroundProcessesRelayTest`、`LocalScheduledSessionAccessIntegrationTest`、`LocalServiceTransportInteractionTest`、`LiveHarnessConversationInteractionTest`：34/34（5+5+4+4+6+3+7）。
- 项目交互采用真实临时目录、Store、Repository与LocalConnection；Session端点和模型目录为测试fixture。调度adapter采用真实Runtime、Gateway、AgentLoop和Room，provider受控。后续新增 Cron 基础组合采用真实 executor、JSON ledger、OS leader 和同一真实 session 链；时钟/provider 受控，不替代完整故障或像素验收。
- 指定 `SHUAIAPI/gpt-6.1-sol` 委托桌面重连交互与共享重验证回归；主线程审查集成并串行运行验证。
- 本批继续使用指定模型委托 `DesktopScheduledExecutorIntegrationTest`（3/3）与 `DesktopScheduledLeaderLeaseProcessTest`（2/2）；同上命令限定相应类，串行通过，均无跳过。组合测试初跑因测试 reader 重复打开已被当前 Runtime 持有的 Room location 失败；主线程修正为原 fixture 关闭、新 reader 重读并关闭、最后新 executor 重开的顺序后通过。未改生产 Cron 开关。
- 两类 JUnit XML 分别保存于 `/tmp/harness-native-verification-evidence-20261007/cron-composition/jvm` 与 `/tmp/harness-native-verification-evidence-20261007/leader-process/jvm`；`git diff --check` 通过。


## 最新同步：2026-10-07 Cron R4/R5

本批继续由 `SHUAIAPI/gpt-6.1-sol` 实施 Owner 和容量代码，主线程完成 main/HarnessApp/恢复页面接线、审查与隔离串行验证。生产 `verifiedCronExecution=false`，main 的恢复入口也由同一 false gate 关闭。

| 交付 | 行为 | 本批证据 |
| --- | --- | --- |
| R4 Owner | 同一完整 binding 显式恢复；原实例 Drained 后才构造替代实例；Unsafe/超时/坏账本保留原实例与锁；invalidate/shutdown 永久 fence；未发布候选仍受跟踪，不取消底层副作用方法 | ScheduledExecutorOwnerTest 13/13，无跳过；真实 executor + 受控 store/access/lease，create/prompt 为零 |
| 桌面恢复接线 | 当前 verified connection 实例、完整 scope 与 Owner binding 一致才显示恢复；执行器替换保持 binding，transport/身份验证丢失永久停止准入；main 统一 Owner 生命周期 | LocalServiceTransportInteractionTest 5/5，无跳过；含成功替换、Relay 切换永久 fence、坏账本保留原实例；main/shared 编译通过 |
| R5 容量 | 默认预算2，持久 blocksTask 占额度，包含 Unknown/已删除任务历史；request 去重优先；手动满额拒绝，定时 CAS 保存 SkipCapacity/未来 cursor，无排队补跑；持久终态/Withdrawn 释放额度 | ScheduledExecutorCapacityTest 最终16/16；已有 Executor/LedgerRules/Repository 50/50，无跳过 |

验证复用 `/tmp/harness-native-verification-20261007` 源码快照与 `/tmp/harness-native-verification-build-20261007` 构建目录，串行执行各限定类。JUnit 报告分别保存于 `/tmp/harness-native-verification-evidence-20261007/r4-owner/jvm`、`r4-desktop-recovery/jvm`、`r5-capacity-final/jvm`、`r5-existing-regression/jvm`。16项容量包含初次13项之后新增的同request并发重试、terminal CAS失败额度保留、全部精确终态释放，不叠加计数。

当前批次不证明 executor 进程 crash、跨进程 executor recovery、完整故障矩阵或像素一致；未跑全量 desktop/shared、Android/iOS/live。下一步按保留范围继续真实故障组合、用户插件 inventory/管理、Git写操作/Worktree管理、终端交互/子Agent spawn及逐页视觉验收。复刻整体仍未完成。

最终源码同步核对发现 Owner 静态收尾包含 start binding CAS、候选清理 finally 解锁与错误保留调整；主线程复制最新源码后重新合跑 Owner/Capacity，29/29（13+16）通过，无跳过。最终报告保存于 `/tmp/harness-native-verification-evidence-20261007/r4-r5-final/jvm`；桌面恢复交互亦按最终源码重新验证，报告仍保存于 `r4-desktop-recovery/jvm`。源码快照与当前 R4/R5 落盘文件逐字节一致。

## 最新同步：2026-10-07 本地 Worktree 创建

实施使用 `SHUAIAPI/gpt-6.1-sol`。主线程审查、补充真实 Git 与桌面交互测试，并完成串行隔离验证；详细命令与证据见 `worktree-creation-readiness-2026-10-07.md`。

| 交付 | 实际行为 | 验证与边界 |
| --- | --- | --- |
| 本机创建 capability | 明确绝对路径、新分支及起点；起点解析为真实 OID，argv执行 Git worktree add；拒绝已有目录、相对/option-like路径、源仓库和Git元数据内部目标 | Desktop Git 28/28，包括5项真实创建/失败/取消测试；普通本地worktree，不等于managed inventory |
| Controller与状态 | 创建单飞，refresh不取消mutation；身份/Host/commonDir隔离，迟到结果不发布至新scope；unknown要求重新扫描；创建后的读取失败保留receipt | Shared Git22/22；不自动重发或清理未知副作用 |
| 实际设置入口 | 从Worktrees表单真实创建后读取新目录；成功提示绑定receipt；Relay整体替换为unavailable且不创建目录 | UI2/2；验证源HEAD/index/未暂存/未跟踪内容保留；视觉及像素对照仍待做 |

最终52/52，无失败或跳过；报告分别保存 `worktree-shared-final/jvm`、`worktree-desktop-git-final/jvm`、`worktree-desktop-ui-final/jvm`，位于 `/tmp/harness-native-verification-evidence-20261007`。源码与隔离副本一致，`git diff --check`通过。首轮未匹配筛选和UI断言修正前的失败不计通过数。

下一步保留范围：用户插件真实inventory/config/启停；其他Git写操作与Worktree管理；Cron真实故障组合；终端交互PTY和默认子Agent spawn；逐页浅深/紧凑/状态/视觉验收。Worktree创建不再列为尚未实现。生产Cron仍关闭，整体目标继续进行中。


## 最新同步：2026-10-07 本机插件登记与 Skills 消费

本批沿用已指定的 `SHUAIAPI/gpt-6.1-sol` 代理，主线程负责接线、审查与隔离串行验证。详细行为和证据见 `plugins-local-registration-readiness-2026-10-07.md`。历史表中的“无用户插件服务”是实施前状态，已由本节替代。

| 交付 | 实际行为 | 验证 |
| --- | --- | --- |
| 本机服务 | 读取本地 manifest/Skills 文本快照，默认停用、真实列表/详情/启停/移除登记；CAS、文件锁与原子替换；源目录保留 | DesktopUserPluginsTest 17/17 |
| 桌面接线 | 同一服务供页面与Host消费；Local transport/connection/Host/scope校验；CAS token来自页面已读快照 | DesktopInstalledPluginRepositoryTest 9/9 |
| 管理控制器 | scope隔离、写入单飞、迟到结果拒收；保存成功刷新失败保留receipt | InstalledPluginsControllerTest 11/11、PluginManagementControllerTest 9/9 |
| 请求消费 | 启用Skills在每次请求构造前读取；system与RequestHeader同源；重试不累积；失败/取消不启动流 | AgentInstructionsContractTest 7/7 |
| 实际页面 | 路由/筛选/详情及Host切换；本机登记、路径校验、详情滚动、启用、确认移除、只读/无登录Relay隐藏管理动作 | PluginRoutingInteractionTest 3/3、PluginManagementInteractionTest 3/3 |

共59项，无失败、错误或跳过，main/shared/desktop test源码编译通过；源码与隔离副本一致，`git diff --check`通过。六组JUnit证据分别保存于 `/tmp/harness-native-verification-evidence-20261007/plugins-{agent-instructions,service,adapter,controller,routing,management-ui}/jvm`。测试使用临时目录与受控adapter，不修改真实Codex插件目录或发模型请求。初次管理测试参数和断言问题修正后重跑通过，失败轮次不累计。

当前能力是本机Skills文本快照登记，不等于完整包安装/升级/卸载。插件配置/授权、Apps/MCP/hooks/scripts/技能附件消费尚未实现；公开市场仍不纳入。

下一批优先完成保留页面的视觉与状态验收，先从Plugins列表/详情/登记与移除弹窗开始，按浅深主题及普通/紧凑窗口核对原版组件树和实际截图。后续继续Cron故障组合门槛、Git其他写操作与Worktree管理、终端PTY/stdin与默认子Agent spawn。Cron生产开关仍为false；整体复刻继续进行中。


## 最新同步：2026-10-07 插件视觉与公共输入框

指定模型新增插件离屏截图和公共输入实现；主线程分析 ASAR 管理布局、审查绘制回归、修正测试语义边界并串行隔离验证。`HarnessTextField` 已改为独立标签/说明与最小 36dp 的 BasicTextField 编辑区域，字号增长时按内容扩展；保留现有调用参数、真实 editor testTag、禁用、错误、单/多行和密码转换，新增默认 `readOnly=false`。登记路径文字、标签和说明不再被 Material floating label 的整体 36dp 约束裁切。

- `HarnessTextFieldInteractionTest`：最终 **3/3**，其中绘制方法覆盖浅/深 × 280/480dp × fontScale 1/1.5 × normal/error/disabled/readOnly 共32种组合；与独立 Text 文字像素参照比较，验证空值不画假文字，以及真实输入/替换、禁用/只读、错误语义、多行与密码布局。测试几何用标签与说明之间的完整编辑区域；BasicTextField 可编辑语义是内部文字高度，截图不能额外裁掉 glyph 边缘。初轮编译和断言修正前的失败不计为通过数。
- desktop 定向回归最终 **24/24**：`PluginsScreenshotTest` 15、`PluginManagementInteractionTest` 3、`LocalProjectsInteractionTest` 4、`WorktreeCreationInteractionTest` 2；无失败、错误或跳过。项目与 Worktree 包含真实临时目录/Git 服务，截图数据仅测试 fixture。
- 最终 **64张 PNG**：浅/深 × 1440×900/600×800，包含列表、详情长文与 Skills/Apps 滚动、空/错误/不可用/加载、只读及登记/错误路径/移除弹窗。保存于 `captures/plugins-visual-2026-10-07/`，`manifest.json` 记录尺寸、SHA256 与测试边界。弹窗截图为含 scrim/背景的完整 page viewport，不是紧贴弹窗的 crop；旧 `dialog-crop-from-page-viewport` 文件未纳入最终证据。

源码快照与当前输入组件/测试/插件截图测试逐字节一致。命令使用相同隔离源码与 build-dir，先 jvm-app 后 ui-foundation 串行运行。JUnit 报告分别保存 `/tmp/harness-native-verification-evidence-20261007/plugins-visual-final/jvm`、`/tmp/harness-native-verification-evidence-20261007/text-field-final/jvm`。人工复核普通/紧凑登记弹窗和错误路径文字，以及列表、详情滚动与状态截图可读性；没有同尺寸真实原版管理页参考，所以不声明像素一致，也不替代全量 desktop/shared、Android/iOS 或实际窗口验收。前批59项插件功能测试仍是前批结果，不与本批失败/重跑叠加。

ASAR管理内容明确 `isSingleColumn: true`，市场截图不能用作管理页双列布局依据；详情 Apps/Skills 使用 row/line-clamp-1，见 `reverse-engineering/plugins-layout-supplement-2026-10-07.md` 与同名证据JSON。后续仍需逐页几何对齐及保留范围的真实服务补齐。Cron 生产开关继续为 false；本批完成不表示整体复刻完成。

## 最新同步：2026-10-07 Cron 真实停止/CAS 与子 Agent 状态校正

`SHUAIAPI/gpt-6.1-sol` 新增四项真实 executor 组合，主线程审查与隔离串行验证最终 **7/7**（同类原 3 方法已包含，不累加），无失败、错误或跳过。覆盖 Dispatching 真提交后停止且零 prompt/Withdrawn、accepted 挂起与 Unknown 期间 OS 保锁、迟到精确终态后重复 shutdown 才释放、claim 与 Dispatching 前外部同 revision 字节冲突。使用生产 JSON store/OS lease/Runtime/Room/AgentLoop/LocalConnection，无网络 provider；详见 `scheduled-cron-readiness-2026-10-07.md`。

最终日志 `/tmp/harness-cron-fault-composition-final-20261007.log`，XML `/tmp/harness-native-verification-evidence-20261007/cron-fault-composition-final/jvm`。源码与隔离副本逐字节一致，`git diff --check` 通过。有序同进程重启不证明进程 crash；create/receipt/terminal CAS、真正 crash/recovery、DST/回拨/missed wakeup 和用户/视觉门槛仍继续。生产 `verifiedCronExecution=false`，Heartbeat unavailable。

当前源码核对同时发现：DesktopBundle catalog 与默认 profile **已注册并安装 subagent-tools**；SpawnAgentHandler 已执行真实 registry.create/followup，并保留 accepted/unknown delivery、child ID 与 lineage。历史“默认 spawn 待接线”表述由本节替代。现有 SubagentToolsContractTest 使用 ControlledAgent，首项手工追加 UserMessage 以验证 history，因此仍需单独核对真实 AgentLoop 的工具派发、子 Agent 自动消费和面板链；不能把 IDLE/awaitIdle 当永久 Done，也不重新实现已有 producer。

## 最新同步：2026-10-07 子 Agent 真实循环与历史读取

`SHUAIAPI/gpt-6.1-sol` 新增 `DesktopSubagentLoopIntegrationTest`，主线程审查后隔离串行运行 **2/2**，无失败、错误或跳过。使用临时 home 的真实默认 Desktop profile、父 AgentLoop 的 spawn_agent 工具派发、真实子 Agent 自动消费、Gateway/LocalConnection、共享面板 repository 和 Room；只有 LLM 输出受控，无网络调用、手工 append 或 Agent restore。

- 第一项验证 accepted receipt 与真实 inputMessageId 区分、SUBAGENT origin/父谱系/cwd/model、运行中的 Active 投影、真实 UserMessage → AssistantMessage → Completed TurnEnd，以及 flush 后完全关闭原 Runtime 再由 fresh reader 比对完整 header/events/messages。reader 不恢复 Agent。
- 第二项验证第一轮结束后 IDLE 投影为 Unknown；经真实 LocalConnection 向同一 child 再次 followup，运行时 Active，第二轮真实历史刷新后仍为 Unknown。TurnEnd 仅证明对应轮次完成。

合约测试 **15/15** 与本次组合 **2/2** 分别记录，不与旧 desktop 批次重复相加。源码与隔离副本逐字节一致。日志 `/tmp/harness-subagent-loop-final-20261007.log`，XML `/tmp/harness-native-verification-evidence-20261007/subagent-loop-final/jvm`。该证据不含 Compose 渲染、远程传输、完整历史枚举、进程 crash 或像素一致。

当时下一批为面板截图与点击；已由下节验收结果替代。Cron生产开关仍为false；整体复刻保持进行中。

## 最新同步：2026-10-07 终端与子 Agent 面板截图、点击

同一指定模型新增ThreadPanelsScreenshotTest及ThreadPanelsInteractionTest。主线程审查并隔离运行 **8/8**，0失败/错误/跳过；19场景×640×800/320×600×浅/深主题共 **76PNG**。覆盖真实生产组件的输出Running/Exited/空buffer/truncated/长行、概览Active/Unknown/Done，以及正常/空/loading/error/unavailable；5项交互验证选择/返回/Show more、键盘切换及关闭页签、controller刷新/retry、禁用加载中操作、双层canInteract和紧凑长记录滚动。

人工查看紧凑长行、紧凑深色概览、紧凑浅色详情及普通深色错误，未见明显裁切。测试fixture只在测试目录，运行时数据仍走真实服务；截图不证明完整Shell、远端、PTY、永久Done或pixel parity。证据和边界见`thread-panels-readiness-2026-10-07.md`，76PNG、manifest及独立XML在`captures/thread-panels-visual-2026-10-07/`。

当时下一项为同一个SHUAIAPI/gpt-6.1-sol实施 Git 本地已有分支真实切换；已由下节定向验收结果替代。managed Worktree删除/保护、提交、交互终端和其他页面仍按保留范围推进。

## 最新同步：2026-10-07 已有本地分支切换

实施由指定的 SHUAIAPI/gpt-6.1-sol 完成，限定四个生产文件与四个新增测试文件；父线程审查后在隔离副本串行验证，最终 **72/72**（Shared Git29、Desktop Git39、checkout UI2、Worktree创建UI回归2；其中新增 checkout20）。四组 JUnit 分别保存，未重复累加历史批次数。契约、命令、日志、源码hash与证据目录见 `reverse-engineering/git-branch-switch-implementation-2026-10-07.md`。

生产 picker 仅选择实际本地 ref，按钮显式切换；Backend 复验来源/目标OID、目录/Host与clean状态，再执行 argv，禁覆盖ignored文件和递归更新子模块。实际HEAD/status/branches/worktrees确认后才显示成功；已派发后无法确认则未知，无自动重试/回滚。Controller 与创建共用单飞；导航后的旧回执不泄漏，当前同仓库重读；未知需要完整真实重读后显示独立Rescanned，不宣称旧操作成功。

当时下一批为 managed Worktree归属与保护契约；已由下节 M1 实施验收替代。普通worktree list不表示managed记录，live session list不能证明磁盘没有关联会话，缺pin服务也不能解释为空。完整保护/移除、交互PTY与原版同尺寸视觉继续待做；Cron生产开关仍false，整体目标继续进行中。

## 最新同步：2026-10-08 Managed Worktree M1

沿用用户指定的 SHUAIAPI/gpt-6.1-sol。M1 已实现独立 managed-worktrees.json、完整 identity/Host/home namespace 隔离、Prepared→Verified→Registered、完整原始 bytes CAS/OS lease/atomic rename/fsync告警。Prepared持久性未确认或创建前复验失败不调用Git；真实Git创建后登记失败保留工作树和Created回执，不自动重发/删除。重启只认持久 verified evidence；HEAD/branch实时读取，不沿用创建时OID。

main/HarnessApp已注入真实owner验证服务与decorator；无validator时同步禁用创建。页面显示Managed/UnknownOwnership/FingerprintMismatch/Missing、真实刷新/重试与Stale/Error/Unavailable；owner切换首帧过滤旧数据，accepted登记仍写入捕获owner。关联会话及完整共享/内容保护Unknown，pin/archive Unavailable；没有删除/自动清理/归档 consumer。

最终 **92/92**：Shared Git/controller33、Desktop Git/store/integration52、managed页面3、原有创建交互2、分支切换交互2；无失败/错误/跳过，各组最终XML均独立保存。证据、精确命令和12文件hash见 `reverse-engineering/managed-worktree-protection-implementation-2026-10-07.md` 第11节；页面初轮断言失败与报告重跑不累计。M1基线与隔离源码逐字节一致，git diff --check通过；后续C1修改需另建验证基线。

未证明真实进程crash、完整持久会话/pin/archive、远端、全量跨平台或像素一致。M1完成仅代表登记/归属/保护未知展示完成。C1 commit契约已独立分析，现已完成真实已有暂存变更提交的实施与定向验收；结果与边界见下节。原版同尺寸视觉、交互PTY、其他保留页面和Cron完整门槛继续；生产Cron仍false，整体目标进行中。

## 最新同步：2026-10-07 Git C1 本地提交与未知结果门控

沿用用户指定的 `SHUAIAPI/gpt-6.1-sol`。C1 实现限定在已验证的本机 Host、已有本地分支和用户已经暂存的变更；提交不自动 stage、不处理未跟踪/ignored 文件、不 push、不 amend、不创建空提交。Desktop backend 在 dispatch 前重验 repository scope、HEAD/ref、完整 index entries、staged delta 与进行中操作 marker，执行固定 no-hook/no-signing argv，提交后再核对真实 commit object、parent、tree、message、branch、worktree 与 index。

结果状态分为 Submitting、Rejected、Verified、UnknownOutcome、Rescanned。真实 Git 已被调用但提交结果无法确认时，controller 显示“结果未知”，保留 uncertainty gate，禁止直接重放；关闭弹窗、导航或 refresh 不会把未知状态伪装成失败或成功，必须完成真实重读后才进入 Rescanned。

`operationReason()` 改为直接检查已解析 `gitDir` 下的 9 个 marker，并使用 `Files.exists(..., NOFOLLOW_LINKS)`，避免确认阶段为每个 marker 重启 Git 子进程。此前确认阶段的重复进程开销会把真实提交后的验证推过测试超时门槛。

最终 C1 定向验证 **15/15**：`DesktopGitCommitTest` 7/7、`DesktopGitCommitProcessTest` 6/6、`GitCommitInteractionTest` 2/2，无失败、错误或跳过；`git diff --check` 通过。测试只使用临时仓库，未修改当前用户工作区的 Git 数据。

C1 尚未覆盖原版同尺寸像素一致性、完整 Desktop 测试套件、跨平台 Git 行为、远端 push、stage/unstage、签名或 hooks 可配置。下一步仍按保留范围推进 Worktree 其他管理、终端 PTY、视觉验收和 Cron 完整故障门槛；生产 Cron 继续关闭。

## 最新同步：2026-10-08 终端输入与真实进程选择

实施沿用用户指定的 SHUAIAPI/gpt-6.1-sol。主线程在写集释放后检查代码、修正测试并串行验证；没有更改用户确认的纳入/排除范围。

- 显式后台stdin输入、EOF、精确取消先前批次114/114通过；真实进程选择本批106/106通过（shared71、desktop30、截图5），重复回归用例不累加为独立新增测试。
- inventory来自真实会话producer；首次选最新Running，其后保留精确scope/process/incarnation。刷新、tab关闭/重开不跳转；消失或替换显示错误。旧owner或旧request回包不能覆盖当前选择。
- 复用共享SettingsFormSelect；输入草稿按进程隔离，unknown/pending状态切换后保留，A操作结果不能显示成B结果。读取output后及mutation派发前重新确认inventory身份。
- 保存108张面板viewport PNG（27状态、640×800/320×600、浅深主题）、三份脱敏JUnit摘要与hash manifest。已目视检查代表状态；不是全Shell或原版像素验收。
- 首轮shared70/71为归属拒绝断言不匹配，修正后71/71；首轮desktop28/30为测试PID就绪竞态，修正后30/30；不把失败首轮计作通过。

精确契约、命令和证据：`reverse-engineering/terminal-input-and-selection-2026-10-08.md`、`verification/terminal-selection-2026-10-08/`。

接下来优先补完整Shell中的终端/子Agent组合与原版同尺寸视觉对照；随后继续页面主路径。输出跟随、PTY/resize/ANSI、Worktree完整移除/保护、Cron故障及时间边界、插件完整包生命周期仍属于未完成项；Cron生产开关仍为false。整体目标保持进行中。

## 最新同步：2026-10-08 完整Thread壳层定向验收

沿用 **SHUAIAPI/gpt-6.1-sol**。完整壳层首轮0/6暴露的overlay遮挡与跨会话草稿串用已修复；失败轮次保留为历史证据。AppShell overlay避开标题栏及pushed navigator；ConversationController唯一草稿cache按会话与未创建Home保存，创建转移、发送清理、重连及owner切换均遵守scope/revision检查。

最终 `HarnessAppThreadPanelsShellTest` **8/8**、`HarnessAppThreadShellDraftIsolationTest` **6/6**，合计完整HarnessApp **14/14**；随后 `ConversationControllerTest` 整类 **31/31**。均无失败、跳过或中止，串行构建；controller旧轮次30/31的断言已按会话草稿隔离契约修正并整类复验。

8组壳层场景：1440×900、1024×768默认navigator、1024×768真实拖宽navigator、600×800 × 浅/深主题。10状态/组，共 **80PNG与80份geometry**；原54张是早期预期。主区宽度分别1113、697、572、600dp，后两组进入overlay；真实pointer验收标题栏、页签/关闭/scrim、navigator和会话切换，不通过语义点击绕过遮挡。

证据：`verification/thread-shell-2026-10-08/`；详细修复与边界见 `thread-panels-readiness-2026-10-07.md`、`reference/shell-width-classes.md`。最终日志为 `/tmp/harness-thread-shell-verified-20261008.log` 与 `/tmp/harness-thread-shell-controller-verified-20261008.log`。截图使用受控typed API验证生产壳层，未调用模型网络API；不证明原版像素一致、原生窗口组合或远端真实链路。

下一批按上方执行顺序核对Git/Worktrees页面原版组件树与当前真实能力，明确持久会话/pin/archive/移除保护的可实施范围后交给指定模型。输出跟随、PTY/resize/ANSI、Projects/Recent菜单与排序、Cron剩余故障及时间边界、插件完整包生命周期继续单列；生产Cron仍false，整体目标继续进行中。


## 最新同步：2026-10-08 页面修复复验

本轮实施继续指定 SHUAIAPI/gpt-6.1-sol；主线程独立审阅、串行构建与验收。已有未提交修改保留。

| 批次 | 本轮确认结果 | 证据与边界 |
| --- | --- | --- |
| Scheduled | 交互12/12、完整路由3/3、截图3/3 | captures/scheduled-page-2026-10-08 共144PNG与144份geometry，尺寸/哈希逐项核验；人工查看contact sheet；Cron生产 verifiedCronExecution=false、Heartbeat unavailable，真实调度完整门槛仍待 |
| Projects/Recent | 交互11/11、截图4/4 | captures/projects-recent-page-2026-10-08 共120PNG与120份geometry，尺寸/哈希逐项核验；人工查看普通与紧凑detail；交互XML被后续运行覆盖，已保存主线程输出记录，不冒充留有原XML |
| Git独立页 | GitPageInteractionTest 6/6 | XML保存在 /tmp/harness-pages-verified-20261008/git-interaction-final；状态、实际长值、只读选择、真实鼠标、pending互斥通过 |
| Worktrees独立页 | WorktreePageInteractionTest 7/7 | XML保存在 /tmp/harness-pages-verified-20261008/worktree-interaction-final；嵌套滚动与鼠标修复后通过，未知归属/保护与不确定创建保持区分 |
| Git完整路由 | GitSettingsRouteInteractionTest最新候选0/7 | 2026-10-08 11:32:41最终XML：7tests/7failures/0errors；列表1次成功返回两个cwd。分离hover并从所属root真实点击后仍无菜单，anchor(16,89)-(584,129)、root(0,0)-(600,800)、注入(300,109)在前后稳定。已核对冻结副本与worker交付hash一致。XML独立保存于 /tmp/harness-pages-verified-20261008/git-pointer-candidate-final/TEST-junit-jupiter.xml，源码/tmp/harness-git-pointer-candidate-20261008-1130。SHUAIAPI继续定位click/expanded/dismiss/overlay，尚未计通过 |
| Git/Worktrees截图 | GitWorktreesScreenshotTest 6/6 | captures/git-worktrees共408PNG与408份geometry，400张页面与8张popup；bitmap尺寸/状态核对无错误，另存SHA256 manifest；已人工查看diff、Managed归属与长分支popup的contact sheet。完整路由与原版像素对照仍待 |
| Plugins | repair-3复验仍为2/7 | XML为7 tests、5 errors、0 failures，五项错误为筛选按钮、两条注册弹窗、read-only详情点击、长列表滚动。实际编译产物javap确认220行列表调用等待已通过，225行等待plugin-detail-name超时，撤回列表未读取的推断；行表保存于/tmp/harness-plugins-repair3-readonly.javap.txt。XML独立保存在 /tmp/harness-pages-verified-20261008/plugins-interaction-repair3/；repair4旧会话最终在2026-10-08 11:40:02压缩时adapter_eof退出、未交付补丁。确认退出后启动新的SHUAIAPI/gpt-6.1-sol上下文（01a1199c-8526-7360-aa7f-a06cf1dbb1a0），仅允许修改PluginsPageTestSupport.kt，接续真实指针/滚轮诊断，未计通过 |

Scheduled/Projects截图为离屏页面或明确测试fixture，紧凑Projects fixture刻意保留336dp左侧空间以验证264dp主区域；不把它当作生产完整壳截图。截图文件完整性、人工视觉检查、原版同尺寸像素一致、原生窗口与真实远端验证分别记账；后两类本轮未完成。

总目标继续：Git完整路由与插件修复 → 页面截图及原版对照 → 保留页面主路径/公共组件缺口与整体验收。PTY/resize/ANSI、Cron执行完整门槛、完整插件包生命周期及Worktree保护数据仍是明确能力缺口。

## 最新同步：2026-10-08 主路径矩阵与插件滚动复验

本节替代上节 Git完整路由0/7、Plugins2/7及后续reader3/4的未通过记录；历史失败保留用于追溯。实施仍由用户指定的 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 完成，父线程在冻结副本串行验证。

| 批次 | 最终结果 | 独立证据与范围 |
| --- | --- | --- |
| Git完整路由 | 7/7，无失败、错误或跳过 | `/tmp/harness-pages-verified-20261008/navigation-final/GitSettingsRouteInteractionTest/summary.json`；独立页13/13、408PNG/geometry维持既有结果 |
| Projects完整主路径 | 14/14，无失败、错误或跳过 | `/tmp/harness-pages-verified-20261008/main-path-matrix-final/ProjectsRecentInteractionTest/`；完整HarnessApp四个检查点×两尺寸×浅深色，新增16PNG和16geometry位于 `captures/projects-main-path-2026-10-08` |
| Plugins最终交互 | 9/9，无失败、错误或跳过 | `/tmp/harness-pages-verified-20261008/plugins-reader-units-final/PluginsPageInteractionTest/` |
| Plugins最终截图 | 4/4，无失败、错误或跳过 | 同上 `PluginsPageScreenshotTest/`；64PNG和64geometry位于 `captures/plugins-page-2026-10-08`；长字段、详情、列表及弹窗均含普通/紧凑浅深色 |
| Plugins滚轮诊断 | 24/24前后配对断言通过 | `/tmp/harness-plugins-reader-units-20261008-diagnostics.json` 与 `/tmp/harness-plugins-reader-units-assertions-20261008.json`；目标到达末尾，父列表值及目标bounds不变 |

本轮Projects/Plugins的独立JUnit XML、summary及24组reader诊断/断言已另存至 `verification/main-path-and-plugins-2026-10-08`，供后续复查；原始命令日志仍位于上述临时证据目录。

插件根因是测试把像素距离直接注入为原生滚轮单位。helper依据Compose 1.11.1的平台转换把剩余像素换算为wheel单位；保留真实hover/滚轮、所属root、最大值及末尾断言、10秒门槛与异常传播。只修所属root的先前候选未修复紧凑reader，不能将最终结果归因于该候选。macOS已实际验证；Linux/Windows分支仅有源码和缓存字节码依据，未运行验证。长source只修改截图测试fixture，生产及共享fixture没有改变。

父线程核对PNG尺寸、状态、文件数和geometry，并人工查看主路径及插件contact sheet、紧凑Home/长字段/登记弹窗和宽窗Thread发送后的原图。以上证明对应批次交互及截图完整性；不证明原版像素一致。主路径使用临时真实存储、LocalConnection/Gateway和受控模型/session端点；不等于真实模型或Agent运行。插件截图为独立页/弹窗，不等于完整壳层或原生窗口验收。

下一项先盘点公共组件实际实现、生产调用与证据，再交指定模型实施一个窄切片。原版同尺寸视觉、原生窗口、远端链路、PTY/resize/ANSI/输出跟随、Cron crash/CAS/时间门槛、插件完整包生命周期与Worktree保护数据继续单列；生产Cron保持false，整体目标尚未完成。

## 最新同步：2026-10-08 公共组件审计与菜单迁移进行中

公共组件审计已交付：`reverse-engineering/common-component-status-2026-10-08.md`，覆盖13族组件的实现、生产调用、测试来源和原版证据。父线程已核对审计最终SHA与交付元数据一致。该报告最终复读包含正在实施的项目/Git typed Menu代码；“已接入”只表示源码存在，未表示本次构建、交互或原版视觉验收通过。

父线程通过两项工作的CLI运行头和会话元数据独立核实模型均为 `SHUAIAPI/gpt-6.1-sol`、provider为 `opencodex`。审计报告中的“本地未核验”描述的是审计工作者自己的检查范围，由本段补充父线程证据。

该节首次同步时，窄切片为LocalProjectPicker、GitPageSelect接入同一typed Menu，新增选项/owner生命周期校验与关闭后派发测试。GitSessionDirectoryPicker外层源码已与实施前快照逐字节核对，保持一致。当时实施工作者尚未释放最终写集，父线程未运行该候选构建/测试；最新验收结果见下节。

本切片释放并验收后，下一候选为审计报告推荐的ScheduledPagePicker迁移；共享Tooltip、Checkbox生产接入、unsaved导航与消息/文件卡片缺口按实际页面需要继续。保持既定范围与串行验收规则，整体目标仍未完成。

## 最新同步：2026-10-08 项目/Git 公共菜单迁移验收

实施沿用 `SHUAIAPI/gpt-6.1-sol` / `opencodex`。LocalProjectPicker 与 GitPageSelect 已接入 typed HarnessMenu，项目按ID区分相同名称，长标签完整换行，宽度/高度按窗口约束并保留真实滚动。业务派发等待 popup 实际卸载；disabled、移除、重排、owner变化与dispose均拒绝过期选择。

父线程冻结源码验收最终 **75/75**：公共模型12、公共交互19、定位5、迁移交互12、Projects主路径14、Git独立页6、Git完整路由7，均无失败/错误/跳过。公共36项与Projects14项的源码manifest早于最后Git-only修复；其对应源码未改变，不把结果称作全量单次运行。独立XML、summary、逐次源码manifest、worker交付及哈希保存在 `verification/menu-migration-2026-10-08/`。

历史失败已保存：missing click import、条件lambda类型错误造成编译0tests；之后迁移11/12确证重排后相同index/label复用旧semantics action并误派发业务。Git菜单用 `key(optionsLifetime)` 重建生命周期后12/12。测试断言保持原样。最后Git两类曾被父线程误并行启动导致共享XML覆盖，均作废；`final-serial`逐类重跑并保存匹配XML，未累计重复轮次。

600×800、1440×900 × 浅深色 × 项目/Git生成 **8PNG与8geometry**，独立popup root、bitmap尺寸/哈希、窗口内menu边界逐项核验；父线程目视查看紧凑项目、紧凑深色Git及宽窗浅色Git。截图位于 `captures/menu-migration-2026-10-08/`。这是生产selector在受控fixture中的离屏交互与截图，原版同状态像素、原生窗口、真实远端未验收。

下一窄切片已交同一指定模型：ScheduledPagePicker保留page/service fences，接入公共Menu并按token重建控件；仅允许selector与必要测试写集，由父线程继续冻结与串行验收。生产Cron仍false，Heartbeat unavailable。整体目标继续进行中；PTY/resize/ANSI/输出跟随、Cron完整故障门槛、插件包生命周期、Worktree完整会话/保护仍未完成。

## 最新同步：2026-10-08 定时任务公共菜单迁移验收

`SHUAIAPI/gpt-6.1-sol` / `opencodex` 已释放 ScheduledPagePicker 迁移。生产差异只在 picker，landing/value reader 与页面/service guards 逐字节保持原样；有序 options 快照、完整 token/key 生命周期和最新回调拒绝过期索引。目标、模型、频率、星期四处编辑器选择器复用 typed HarnessMenu，业务派发发生在实际 popup content 卸载之后。

父线程冻结 2,199 个输入，同一源码manifest逐类串行验收 **29/29**：新增 ScheduledPickerMigrationInteractionTest 14、既有 ScheduledPageInteractionTest 12、完整 ScheduledPageRoutingInteractionTest 3，均无失败/错误/跳过。新增用例覆盖真实 draft 更新、六种 queued selection 失效、重排后重复标签旧 action 拒绝、等值列表使用最新回调、键盘和外部关闭。独立 XML、summary、源码manifest、worker交付、静态复核和artifact hashes保存至 `verification/scheduled-menu-migration-2026-10-08/`。

600×800、1440×900 × 浅深色产生 **4PNG与4geometry**，逐项验证哈希/bitmap尺寸、独立popup root及8px窗口边界；目视检查紧凑浅色与宽窗深色，完整长标签换行且popup按520dp宽/280dp高约束。位于 `captures/scheduled-menu-migration-2026-10-08/`。这证明受控fixture的离屏交互与截图完整性，不证明原版像素一致、原生窗口或远端行为。既有历史批次的144PNG未算作本次迁移截图。

下一公共组件候选是有原版触发时序依据的共享Tooltip，先分析生产调用和最小契约，再交指定模型。生产Cron保持false，Heartbeat unavailable；整体目标继续。

## 最新同步：2026-10-08 共享 Tooltip 实施中

当前 ASAR wrapper 与 hover200ms、focus立即、关闭及popup抑制时序已分析，证据与窄契约保存在 `reverse-engineering/tooltip-rail-contract-2026-10-08.md` 及同名来源文件。实施继续使用用户指定 `SHUAIAPI/gpt-6.1-sol` / `opencodex`。上下文恢复曾误引用已完成的Menu任务，父线程已明确纠正，保留原四文件基线；未安排重复Menu迁移。

本批只做共享anchored Tooltip、旧visible API兼容和真实RailNavItem接入。生产代码已开始写入，新增测试仍在实施；尚未释放最终写集，尚无本批构建/测试通过结论。父线程已准备冻结与串行验收脚本，拟跑新Tooltip suite和8项完整Thread壳层回归。显示提示不得挤动布局或新增焦点节点，点击仍单次派发原真实动作，content/page/menu改变必须撤销过期提示。

纳入/排除范围保持用户已确定的清单；生产Cron保持false，整体目标进行中。

## 最新同步：2026-10-08 共享 Tooltip 与侧栏接入验收

`SHUAIAPI/gpt-6.1-sol` / `opencodex` 已释放四文件写集；父线程再次核对CLI会话元数据与文件SHA。新增`HarnessTooltipTrigger`使用nonfocusable anchored Popup，保留旧visible API；默认hover200ms、focus立即，真实RailNavItem按ID及page/workspace scope管理提示，More/account/overlay/panel抑制提示，原业务回调和52/36/44dp几何保留。

父线程冻结2,201个输入，同一源码manifest逐类串行验收 **37/37**：TooltipInteractionTest29、完整Thread壳层HarnessAppThreadPanelsShellTest8，无失败/错误/跳过。初轮编译0tests和后续28/29已保存为历史；最后一项真实滚轮失败确认是用例冻结动画时钟，在滚轮阶段恢复自动推进后通过。公共helper与生产Tooltip未因该失败改动，全文/换行/末尾/10秒断言保留。

本批 **84PNG及84geometry**：4组Tooltip独立Popup root截图，80组完整壳层回归截图。文件数、hash、bitmap尺寸、Popup独立root/窗口边界逐项检查；代表Tooltip和壳层图人工复核。证据位于`verification/tooltip-rail-2026-10-08/`，截图位于`captures/tooltip-rail-2026-10-08/`，详细契约与失败来源见`reverse-engineering/tooltip-rail-contract-2026-10-08.md`。受控fixture的离屏测试不证明原版像素一致、native或远端。

下一项优先分析真实Thread消息/工具展示与原版local turn结构，先明确数据映射和窄视觉契约，再交指定模型实施。Checkbox接入/unsaved导航按实际页面需要安排，不为组件覆盖率新增业务。PTY/resize/ANSI/输出跟随、Cron完整故障门槛、插件包生命周期、Worktree完整会话/保护及同状态原版视觉继续单列；生产Cron仍false，整体目标未完成。


## 最新同步：2026-10-08 Thread消息分析与窄切片实施

父线程已审阅 `reverse-engineering/thread-message-analysis-2026-10-08.md` 及 evidence JSON，独立验证两份文档、七份只读源码SHA、五份ASAR原始资源范围、九个ASAR短片段和七个源码片段。2201冻结构建输入在分析交付时无漂移。实施继续复用指定 `SHUAIAPI/gpt-6.1-sol` 工作者，当前turn配置已由会话元数据核验；不创建新的工作线程。

首批契约为 `reverse-engineering/thread-message-contract-2026-10-08.md`：真实User来源的末端气泡、助手正文去统一卡片、中性可访问Reasoning/ToolCall disclosure、完整递归工具输出；修复USER角色的工具来源误标人类。Host/member/session timeline key只隔离展示状态，不改controller、composer、retry。禁止跨消息配对、虚构running/success/timing及新增markdown/附件协议；宽度与字体仍是明确适配，原版运行时样式和同状态截图未验收。

本节为实施中状态，尚无本批构建或测试通过结论。待worker释放三文件写集后，父线程冻结并串行验证新交互suite、既有LiveConversationInteractionTest和完整Thread壳层；保留现有断言并单独记录fixture、截图与像素验收边界。整体目标继续，用户排除范围与生产Cron=false保持既定结论。

## 最新同步：2026-10-08 Thread消息冻结副本验证与待交付事项

实现沿用 `SHUAIAPI/gpt-6.1-sol` / `opencodex`。父线程对三个实施文件制作临时冻结副本，复制前后核对SHA，冻结2,202个构建输入；没有改写生产或测试代码。当前工作区三个文件与该冻结副本一致。本节替代上节“尚无本批构建或测试通过结论”，但不表示工作者最终交付已验收。

同一源码manifest逐类串行验证 **33/33**：ThreadMessageVisualInteractionTest 14、LiveConversationInteractionTest 11、HarnessAppThreadPanelsShellTest 8，均无失败、错误或跳过。真实鼠标/键盘/滚轮、全文末尾和无省略检查通过；同scope刷新保留展开与滚动，Host/member/session变化重置展示状态。User气泡依据角色与typed来源共同判断；USER角色的工具消息不会被误作人类输入。

已归档 **92PNG与92份几何证据**：12张消息截图覆盖600×800、1440×900、浅深色、收起/展开/长内容；80张完整Thread壳层回归截图。逐项核对文件数、bitmap尺寸和SHA，人工检查紧凑浅色展开、紧凑深色长内容、宽窗深色展开及终端壳层代表图。证据位于 `verification/thread-message-2026-10-08/strut-provisional/`，截图位于 `captures/thread-message-2026-10-08/`。

初轮3/14及几何修复轮5/14的失败记录仍保留。父线程核对缓存Compose 1.11.1的实际字节码：getBoundingBox调用Paragraph.getRectsForRange，高度使用STRUT、宽度使用TIGHT，不能当作独立栅格字形墨迹证据。最终冻结断言用行框检查纵向布局、字符框检查横向布局，并保留全文/末尾/无省略及真实输入；自然文本的hasVisualOverflow标志也不能独自证明裁切。字节码证据保存为 `verification/thread-message-2026-10-08/parent-compose-strut-bytecode.txt`。测试注释中旧“glyph/基线”解释仍需工作者纠正，不能据此宣称像素级裁切验收。

**状态仍是临时父线程验证通过**：旧worker delivery对应前一候选，测试文件SHA已经不匹配，不能作为当前写集释放。收尾请求已发送给同一指定模型，要求仅纠正注释、更新真实哈希与释放报告；当前没有新的工作者输出。验收元数据明确保留 `write_set_released=false`，不把待交付计为最终完成。若最终变化只有注释和报告，逐字节核对可执行内容后复用本次测试证据；可执行代码变化则重新冻结并做必要串行验证。

宽度/字体属于Harness适配；上述截图为typed fixture的离屏生产UI，不证明原版同状态像素一致、原生窗口或真实远端。终端代表图仍可见长输出横向截断，PTY/ANSI/resize/输出跟随继续列为缺口；生产Cron仍false。后续顺序为本批收尾 → 终端输出与子Agent面板的原版/生产链路分析 → 按窄契约交指定模型实施。插件包生命周期、Worktree完整会话/保护、Cron故障与时间门槛继续保留，用户排除范围不变，整体目标未完成。

## 最新同步：2026-10-08 终端阅读分析与实施派发

当前安装包全文SHA仍为 `797be85cd4c5b1be359c55c1d57970671cfba284a5eacf7612ee0ae9add1c360`。本次重新读取九个终端资源，记录八个关键字节范围和五份生产源码SHA，见 `reverse-engineering/terminal-reading-analysis-2026-10-08.md`、同名 evidence JSON 及 `terminal-reading-contract-2026-10-08.md`。原版增量写入前检查 `viewportY >= baseY`，仅底部阅读者跟随；非前缀输出会重置Xterm。此前“接近底部”简述在分析文档中纠正为实际底部条件。

Harness长行已有横向滚动；缺口是Ready分支卸载导致刷新后两轴位置丢失，以及没有输出跟随。本批只修改ThreadPanels.kt的展示接线/局部状态并新增TerminalReadingInteractionTest.kt；scope、ownerGeneration、processId、producerIncarnation共同隔离阅读状态。Loader/Error保留位置而不冒充Ready；tab切换保留、关闭释放；按用户更新前的位置决定跟随，新缓冲替换明确clamp。1px容差与非前缀像素位置策略为Harness适配，不宣称Xterm一致。

新契约已派发给现有 `SHUAIAPI/gpt-6.1-sol` 工作者，session元数据再次确认provider=`opencodex`。上一消息区域的最终报告长期未刷新，本批暂存其父线程33/33临时验证；不再安排反复字体调查，消息三文件保持当前哈希并继续标记待最终释放。尚无终端新批实现或测试通过结论。工作者不运行构建；父线程收回两文件写集后冻结并串行验收。

本切片不引入轮询/订阅、PTY/ANSI/resize协议，不扩大到子Agent历史重构。实时生产输出链路、同状态原版视觉、原生/远端、Cron门槛、插件生命周期与Worktree完整会话继续保留；排除范围和生产Cron=false不变，整体目标继续。

## 最新同步：2026-10-08 子 Agent typed 历史复用分析完成

父线程继续核对当前 ASAR，三个子 Agent 资源、四段精确字节及八段生产源码保存至 `reverse-engineering/subagent-history-analysis-2026-10-08.evidence.json`。全文归档 SHA 与终端分析一致，资源和提取内容逐字节匹配。分析及窄契约分别见 `subagent-history-analysis-2026-10-08.md` 和 `subagent-history-contract-2026-10-08.md`。原版选中子 Agent 直接复用 local conversation thread；Harness 的 SessionApi 已返回 typed Message，缺口是 repository 压平与详情纯文本渲染。

建议保留兼容 entries 并增加 nullable typedMessages：从真实历史保留全部来源、推理/工具结构及嵌套错误标志，在快照边界复制集合，子 Agent 复用主对话同一内部 MessageCard。明确空 typed 历史与 legacy null 不同；谱系、owner、请求、选中 child 和只读权限防护保留。本切片尚未派发，ThreadPanels.kt 与终端写集重叠，必须顺序实施。未运行本批构建或测试，也未宣称具备完整持久后代历史或刷新阅读状态保留。

指定 `SHUAIAPI/gpt-6.1-sol` 工作者当前终端 turn 仍为 inProgress，多个有限等待没有新消息或工具输出；ThreadPanels.kt SHA 仍为派发前基线，新测试及终端交付文件尚不存在。父线程已用明确 model override 发送继续请求，但尚无实际执行证据，不将其算作实现进度。仍保持同一工作者和指定模型。前一消息33/33继续是临时父线程验证，最终释放未完成；整体目标尚未完成。

## 最新同步：2026-10-08 指定模型再次确认与执行阻塞

2026-10-08 19:56:43（Asia/Shanghai）核验现有工作者 `local/01a11a54-1694-7fb3-8007-37c434531314` 的 session settings：model=`SHUAIAPI/gpt-6.1-sol`，model_provider_id=`opencodex`，reasoning_effort=`high`。父线程已通过显式 model override 重发契约，并通过同一 agent 的 interrupt 入口恢复实施；没有创建其他工作者。

当前 turn `01a11b5b-e9f1-7471-a2ff-68b17493532b` 于 19:52:51 开始，仍为 inProgress，但截至上述核验没有任何模型消息或工具调用。此前三轮也没有输出；实际无输出原因未知，不能据此认定是构建失败或断言供应商错误。本机应用日志未提供与该轮关联的明确请求错误。ThreadPanels.kt SHA 仍为 `a7cc50a9a3ff41f22b4d32b2207109769d2294b5dcdb4007a37c956c3ae21c83`，新终端测试和交付报告均不存在。本轮未运行构建或测试。

终端阅读维持“分析完成、实施待交付”；子 Agent typed 历史维持“分析完成、尚未派发”。先取得终端真实交付和写集释放，再使用已准备的父线程冻结/验证脚本串行验收，随后交给同一指定模型实施子 Agent 契约。用户确认范围及生产 Cron=false 保持原决策。

连续三轮父目标审计再次核验同一活跃工作者：四轮工作者请求均无模型消息或工具调用，当前生产哈希仍不变，新测试和交付仍缺失。证据见 `verification/terminal-reading-2026-10-08/blocked-audit.json`。指定模型配置已生效，原因仍未知；目标标记为 blocked，等待该工作者产生实际输出或外部运行状态恢复后继续。工作者未被关闭或重启，完整复刻范围未缩减，目标未完成。

## 最新同步：2026-10-08 上游失败确认与同一工作者恢复

本节更新此前“原因未知/blocked”的历史状态。read_thread 现已确认 turn `01a11b5b-e9f1-7471-a2ff-68b17493532b` 与 `01a11bb0-7729-7b32-9bf8-9a0ee24b982e` 均因 `502 Bad Gateway` 失败；后续 turn `01a11bed-24e6-7292-bc66-ea95075a31f8` 已中断。父目标恢复为 active，并以显式 `SHUAIAPI/gpt-6.1-sol` override 恢复同一工作者。当前 turn `01a11bfe-205e-7110-a456-535fff92f0ec` 仍在进行中，尚无消息或工具输出，尚不能判断本轮会否遇到同样错误。

本轮重新核对 2,202 个冻结源码输入，与当前工作区均一致；ThreadPanels.kt 保持原 SHA，新终端测试与两份交付文件仍不存在。没有运行构建或测试，没有将无输出计作进度，也没有增加工作者。当前快照与父线程准备脚本见 `verification/terminal-reading-2026-10-08/recovery-audit.json`。

终端冻结脚本补上 delivery before SHA 和复制后 SHA/基线校验。子 Agent 的六文件冻结及串行验证脚本准备完成，已作语法检查；验证顺序和当前类级数量见 `reverse-engineering/subagent-history-parent-verification-2026-10-08.md`。子 Agent 仍尚未派发，等待终端释放/验收后使用同一指定模型实施；前一消息区域 33/33 仍为临时父线程验证。整体范围和生产 Cron=false 保持既定决策。

随后旧恢复 turn `01a11bfe-205e-7110-a456-535fff92f0ec` 已中断，状态未附原因。父线程确认终止后，仍使用同一工作者和明确模型 override，以短指令读取现有契约继续实施。新 turn `01a11c05-157b-77d0-8683-a426750366d7` 已产生实际模型消息和源码读取工具调用，正在核对现有滚轮、截图、controller 和 fixture；这更新了此前“尚无输出”的当前状态，但尚无终端交付或测试通过结论。


## 最新同步：2026-10-08 终端首轮交付与父线程验收修正

同一 SHUAIAPI/gpt-6.1-sol / opencodex 工作者已恢复实际实施并释放两文件写集。父线程核对交付SHA后冻结2,203个输入。首轮沙箱执行在Kotlin telemetry缓存写入失败，测试未启动；授权环境重跑已编译并执行9项，8通过、1失败、无错误/跳过。失败为pending-follow用例比较持有实时lambda的两个ScrollAxisRange，不能证明追加后范围增长；已回交原工作者修正，测试不得削弱。

首轮XML原始suite名称为JUnit引擎，不是类名；父线程脚本原错误名称断言中断了summary生成，现已按每项testcase.classname及用例数验证并恢复同轮XML与8/9统计。空引擎suite允许0项，但非目标测试类仍拒绝。历史证据见 verification/terminal-reading-2026-10-08/first-release-failed。子Agent验收脚本同样补充类名/数量检查，尚未执行。

终端回改仍限定同两文件，不由工作者构建/测试；除失败用例，审阅发现同process的新incarnation覆盖不独立、截图缺少阶段/末尾布局断言、没有实际truncated fixture及布局/生命周期跟随风险，已一次性提交明确修正清单。当前候选不计作完成，子Agent历史等其最终释放及串行回归后再派发。生产Cron=false，范围及指定模型不变，整体目标保持active。

## 2026-10-08 终端阅读 R3 最终定向验收（UTC）

同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者释放最终两文件写集。父线程核对源码与交付SHA，冻结2,203个输入，同一manifest串行通过 **26/26**：TerminalReadingInteractionTest 9、ThreadPanelsInteractionTest 9、HarnessAppThreadPanelsShellTest 8，无失败、错误、跳过。终端生产SHA为 `bc4d243d0f7af1b10ec4835b9ccb1c3776a64f84bf3a235821f2231817f1652e`。初轮8/9与R2的7/9保留为失败历史；R3修正测试的实时范围读数、水平坐标与实际换行fixture，没有削弱断言。

阅读状态按scope/owner/process/incarnation隔离；Loading/Error及tab切换保留位置，关闭或身份变化重置。跟随等待本轮正文实际完成测量；上滚取消pending-follow并持续保持，回到底部恢复跟随。新增生命周期/视口变动与真实truncated覆盖，未引入PTY/ANSI/resize RPC。

证据归档 `verification/terminal-reading-2026-10-08/reviewed-r3-final/`：交付、脚本、原始XML、日志、同一manifest、最终统计与截图审计。`captures/terminal-reading-2026-10-08/reviewed-r3/` 保存12张阅读图与80张完整壳层图及相应几何；父线程核对全部尺寸/复制前后SHA并查看阅读contact sheet与代表壳层图。受控fixture的离屏生产UI不证明原版同状态像素一致、原生窗口或真实远端。前一消息区域仍为临时33/33验证，完整复刻未完成。

下一切片使用同一工作者实施已分析的子Agent typed历史合同，基线为本次验收候选；有限六文件，终端生产段保持上述最终版本。插件本地更新分析继续收敛；用户已确认范围及生产Cron=false不变。


## 最新同步：2026-10-09 子 Agent 历史 R1 验收与插件更新派发

继续固定同一工作者 SHUAIAPI/gpt-6.1-sol / opencodex，沿用 2026-10-08 批次名。子 Agent 历史六文件写集已释放；父线程冻结 2,204 个输入，同一 manifest 串行验收 **59/59**：仓库 9、历史 UI 10、面板 controller 12、既有面板 UI 9、主对话 11、完整壳层 8，无失败、错误或跳过。实际历史保留来源和递归工具/错误结构，子 Agent 复用主对话 MessageCard；nullable legacy、明确 typed 空历史及加载/失败/不可用分支分开，展示 key 含 scope、ownerGeneration、child 和 message。终端生产段保留 R3。

证据保存于 verification/subagent-history-2026-10-08/r1-final/；captures/subagent-history-2026-10-08/r1/ 包含 8 张历史及 80 张壳层 PNG 和 geometry。320×600、640×800 的浅深色收起/展开历史截图已核对尺寸、SHA、最后文本行及真实滚轮可达性，contact sheet 已目视复核。初轮 0 tests 基础设施/参数失败和 ChildAgentSummary 缺 canInteract 的编译错误另存 initial-history；同一工作者补参数后才验收 R1。交付 JSON baseline 日期笔误由父线程纠正，原件及正确 before SHA/manifest 保留。

插件本地更新分析及九文件合同已交同一指定模型实施，基线为本次 R1。新增从既有源目录更新真实持久 Skills 快照，保留启用状态和其它记录；使用既有 lease、CAS、预算、原子提交和真实下一 Agent 请求消费者，源失效保留旧内容，重复相同更新保持幂等。工作者只实施，父线程负责冻结和串行测试。本批尚无插件更新测试通过结论；原版的本地替换事务未确认，明确标作 Harness 本地适配，不混作 hosted upload。

本次历史验收属于受控 typed fixture 的离屏生产 UI，不证明原版同状态像素一致、原生窗口、远端或完整持久后代历史。PTY/ANSI/resize、Cron 故障与时间门槛、插件 MCP/Apps/hooks/受管附件生命周期、Worktree 会话/pin/archive/移除保护及同状态原版视觉仍待；生产 Cron 保持 false，整体目标继续。

## 最新同步：2026-10-09 插件更新交付与部分验收

同一 SHUAIAPI/gpt-6.1-sol / opencodex 工作者已交付插件更新九文件并释放写集；父线程冻结 2,205 个输入。DesktopUserPluginsTest **21/21** 和 DesktopInstalledPluginRepositoryTest **11/11** 均通过，无失败/错误/跳过。真实 AgentLoop 请求及 REQUEST_HEADER 断言覆盖旧技能、更新技能、停用更新、再启用，并检查保留 base system 与无重复累积。

父线程已解决沙箱内 Kotlin 遥测、Skiko 缓存与 macOS AWT 初始化问题：使用临时 Kotlin user.home、临时 skiko.data.path 和 java.awt.headless=true 运行离屏生产 UI。三组既有 controller/管理 UI 回归另通过 **23/23**；当前五组完整通过共 **55 项**。PluginLocalUpdateInteractionTest 实际执行 10 项，**9 通过、1 错误**：真实滚轮 helper 未能露出最后技能名称，尚不能验收截图矩阵。此前基础设施失败与本次实际 UI 错误分别保存在 verification/plugin-local-update-2026-10-08/environment-r2、environment-r3、partial-r4。

同一指定模型随后仅修复了 PluginLocalUpdateInteractionTest.kt 的滚轮定位；保留 10 秒期限、真实 wheel、原几何与文本断言及 8 张矩阵要求。生产八文件保持已审阅版本。父线程已用同一 2,205 输入 manifest 串行验收六组 **65/65**，无失败、错误或跳过；8 张 320×600/640×800 浅深色更新前后截图及末尾布局、无省略、滚动终点、marker 可见性证明已核对并归档到 `verification/plugin-local-update-2026-10-08/wheel-r1-final/` 与 `captures/plugin-local-update-2026-10-08/wheel-r1/`。本批边界仍是本地 Harness 的源目录更新适配，不宣称原版 hosted upload、MCP/Apps/hooks 或远端生命周期。

Worktrees 下一依赖已分析为持久 header 只读目录，合同见 `reverse-engineering/worktree-session-catalog-contract-2026-10-09.md`，限 RoomSessionPersistence.kt 与新增真实 Room 测试两个文件；插件验收基线已就绪，进入实施。目录不 load 历史会话、不解析或修补 events、不写数据库、不启用删除。生产 Cron 仍 false，整体目标保持进行中。

## 2026-10-09 持久目录验收与 Worktrees Host 观察层实施

持久 header 目录两文件已释放并由父线程验收 **18/18**（目录10、持久契约7、数据库factory1），无失败、错误或跳过。冻结输入2,206个，证据在 `verification/session-header-catalog-2026-10-09/catalog-r1-final/`。这是独立 Room 目录能力，尚不能证明 Worktree 会话归属或安全删除。

旧工作者最近一轮只重新交了已验收插件报告，Host 观察层尚未实现。父线程因此新增唯一任务合同 `reverse-engineering/worktree-host-session-observation-contract-2026-10-09.md`，核对当前工作树与全部2,206个基线输入一致，保存七文件 before SHA/副本到 `/tmp/harness-worktree-host-observation-baseline-20261009/`，再派发到同一 `local/01a11a54-1694-7fb3-8007-37c434531314`，明确 `SHUAIAPI/gpt-6.1-sol` override、provider `opencodex`。工作者已确认新任务并开始源码读取。

目标为同一 Runtime live/Room headers 合并、按id去重、typed来源/父子/未知原因、native真实路径边界，以及main和managed页面真实只读接线。可选默认关闭、旧构造兼容、服务引用身份和owner前后重验；没有跨服务事务，结果只称本次观察。冻结与串行验收脚本已准备在 `verification/worktree-host-session-observation-2026-10-09/`。目前尚无本切片交付或测试通过结论；生产Cron=false及整体待办范围保持。

## 2026-10-09 当前实施纠偏与下一定时任务合同

Worktrees Host 观察层的五个生产文件已有实现初稿，两个新增测试和本批交付仍待。工作者在上下文恢复后再次回到已验收插件任务；父线程已发最新明确授权，重申同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex`、保留生产初稿、只补本合同测试及交付。工作者现已确认 Worktrees 并继续读取相关源码。本状态不计作验收通过，也不重复开发插件更新。

定时任务当前源码已复核：夏令时/跨年/闰年、真实跨进程领导锁及真实本机执行链均已有测试源码；不能继续把这些列为未实现。剩余优先项为执行器真正跨进程 crash 的 create/dispatch/receipt/accepted/terminal 提交窗口。现状见 `reverse-engineering/scheduled-execution-readiness-2026-10-09.md`，下一窄合同见 `reverse-engineering/scheduled-executor-crash-contract-2026-10-09.md`，仅新增两个真实子 JVM 测试文件，尚未派发、尚未运行。

顺序为本批 Worktrees 释放及冻结验收 → 同一指定模型补执行器 crash 窗口 → 按证据处理生产 Cron 启用；随后插件余下生命周期、Worktree 保护/移除、PTY/ANSI/resize及原版同状态视觉。排除清单维持用户确认范围，整体目标仍 active。


## 2026-10-09 Worktrees Host 观察层首轮验收

同一 SHUAIAPI/gpt-6.1-sol / opencodex 工作者释放七文件并交付11项observer、4项UI测试。父线程核对完整before/after SHA，冻结2,209输入并启动七类串行验收。host-r1的生产模块编译成功，新增测试源码因Room/SQLite缺直接测试依赖、Compose waitUntil位置参数和GitState.pageReady跨模块internal访问失败；实际测试0项，无截图通过结论。完整日志、manifest及首次delivery保留在 /tmp/harness-pages-verified-20261009/worktree-host-observation/host-r1/ 。

已将具体诊断交回同一指定模型，有限回改为两个新增测试和apps/jvm-app/module.yaml的test-dependencies，五个生产文件保持首轮交付SHA。合同记录新增Room 3.0.0和当前JVM bundled SQLite 2.6.2直接测试依赖，保持既有x64平台版本。修正后重新冻结唯一候选，按11/4及既有23项回归验收38项；失败历史不记为功能完成。

下一定时任务跨进程合同仍待Worktrees验收后派发。父线程已准备独立freeze/verify/all/archive脚本，目标为五窗口及executor、lease、calendar、owner、store、evidence共48项定向验收；目前只做脚本语法检查，尚未运行。生产Cron=false，整体目标继续。

## 2026-10-09 Worktrees host-r4结果及最小创建修正

host-r3观察层11/11、UI0/4的失败历史保留。指定工作者仅将新增UI场景改为Compose的rememberCoroutineScope调度，初始化/关闭随Composition生命周期；四个用例正文、readiness和10秒期限未改。host-r4的同一2,209输入实际运行32项：新增UI4/4、observer11/11、managed integration8/8、controller4/4、managed UI3/3通过，旧创建测试0/2；最后GitWorktreesScreenshotTest六项未运行。不能称38/38。

两项旧创建测试都在输入前失败，创建路径字段Disabled。父线程确认其只等待根目录节点，而pageReady要求其它异步读取完成。仅授权同一SHUAIAPI/gpt-6.1-sol修正旧创建测试就绪等待；完整九文件交付的原八SHA保持host-r4。before快照在 /tmp/harness-worktree-creation-readiness-baseline-20261009/ ，新候选host-r5先两项创建再同一manifest剩余36项。八张观察PNG和八份geometry已目视检查，lineage/end分开证明，目前尚未归档。


## 2026-10-09 Worktrees host-r5验收与定时任务crash派发

同一SHUAIAPI/gpt-6.1-sol / opencodex工作者只修正旧创建测试两处就绪谓词；原八交付源码保持host-r4 SHA。父线程冻结2,209输入，同一manifest串行通过 **38/38**：创建2、观察UI4、observer11、managed integration8、controller4、managed UI3、Git截图6，零失败、错误、跳过。八张观察PNG及八份geometry已逐张目视核查，完整尺寸/主题/lineage/end矩阵与复制SHA通过归档门槛。证据在 `verification/worktree-host-session-observation-2026-10-09/host-r5-final/`，截图在 `captures/worktree-host-session-observation-2026-10-09/host-r5/`。本轮不能外推原版像素、原生窗口、远端或安全移除；历史失败轮次独立保留。

父线程已核对工作树全部2,209输入与host-r5一致，保存定时任务两个新增文件的before=null和完整manifest到 `/tmp/harness-scheduled-executor-crash-baseline-20261009/`。真实跨进程五窗口合同已派同一工作者，明确SHUAIAPI/gpt-6.1-sol override、provider opencodex；只新增两个测试和tmp交付报告，不build/test、不改生产。重点覆盖Accepted后真实持久开放回合，独立原始Room证据不得由repairOpenTurn补写的Interrupted替代。父线程在释放后冻结新候选，计划48项串行验收；新增crash测试尚未运行。生产Cron=false，整体目标active。

## 2026-10-09 定时任务实施拆分与恢复修复设计

crash 首轮工作者在 contextCompaction 后误返回已验收插件任务，两个授权测试文件及本合同交付报告均未生成，不能记作新交付。父线程再次核对2,209输入无漂移，向同一SHUAIAPI/gpt-6.1-sol发出纠正：先将真实子JVM helper保存到磁盘，再补五个父JVM用例，最终共同释放；阶段报告不触发构建。

父线程已同步 `scheduled-execution-readiness-2026-10-09.md` 为五个独立窗口，并准备 `scheduled-evidence-readonly-repair-design-2026-10-09.md`：若真实 Accepted 开放回合复现误判，最小生产修复聚焦现有 persistence 的只读事件能力与定时任务 evidence 消费。当前是源码路径设计，未声称跨进程失败已复现，不提前修改生产。

## 2026-10-09 定时任务崩溃恢复实际失败与修复

父线程已运行首轮真实跨 JVM 五窗口：3/5通过、失败2、错误/跳过0，其余43项未运行。Accepted开放回合已复现恢复误判；Dispatching的Agent计数断言混淆create与prompt。失败证据保留于 verification/scheduled-executor-crash-2026-10-09/initial-failed/。同一指定模型进入五文件只读证据修复合同，生产Cron保持false。 见 reverse-engineering/scheduled-evidence-readonly-repair-contract-2026-10-09.md。

## 2026-10-09 定时任务只读证据 readonly-r2 最终验收

同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者修复测试Room重复所有权接线后释放写集。父线程核对五文件交付、完整工作树与冻结候选的2,213个输入，串行通过12类 **85/85**，失败/错误/跳过均0。真实执行器跨JVM五窗口5、Room只读事件9、只读证据10、执行组合7、进程锁2、日期/DST3、Owner13、文件store15、旧证据3、普通持久恢复7、header目录10、数据库factory1。证据归档于 `verification/scheduled-executor-crash-2026-10-09/readonly-r2-final/`。

历史证据只读，不再通过load修补开放回合；live精确终态flush后重读持久事件。既有普通load修补语义、已持久Completed/Interrupted、生产数据库所有权保护和业务断言保留。首轮3/5与readonly-r1的fixture错误仍保留在独立失败目录，不能覆盖为成功。

故障门槛通过不等于生产已开启。main当前仍为false；下一批先收敛共用正式启动接线和真实页面操作合同，再交同一指定模型实施。Heartbeat仍Unavailable，无应用退出后后台运行承诺；PTY/ANSI/resize、插件MCP/Apps/hooks生命周期、Worktree pin/archive/移除保护及原版同状态视觉继续待办，整体目标active。

## 2026-10-09 本机Cron与真实页面链路派发

四文件合同 `reverse-engineering/scheduled-cron-page-enable-contract-2026-10-09.md` 已派给同一工作者并明确 `SHUAIAPI/gpt-6.1-sol` model override。正式启动接线与测试共用小型Desktop装配；允许在HostId/协议/Local验证下启用本机Cron，页面真实创建暂停任务/运行/启停到Room精确终态及实际Thread导航。

父线程已核对readonly-r2基线2,213输入与工作树一致，四文件before SHA、main副本和完整manifest保存于 `/tmp/harness-scheduled-cron-page-enable-baseline-20261009/`。工作者已确认只实施/静态检查、不build/test/截图，待释放后父线程冻结和串行验收。本批尚无通过结论；Heartbeat与整体剩余范围保持。

## 2026-10-09 本机Cron页面R3失败，真实输入待修复

同一SHUAIAPI/gpt-6.1-sol / opencodex工作者已交四文件，候选main明确启用本机Cron并共用DesktopScheduledExecution。R1生产编译通过、测试编译失败；R2生产及测试编译通过，但新增六项全在进入页面时等待超时，未产生PNG，后19类未运行。两个失败批次完整保留，不能记作本批功能通过。

R2根因为HarnessApp外壳和ScheduledManagementPage内页均有scheduled-page标签，而测试要求原始同名节点只有一个。R3只修测试唯一选择器；其余三文件SHA未变。父线程冻结2,216输入后实际运行：生产及测试编译通过，六项已进入页面，但均在键盘填写名称后断言失败，字段仍为空，失败6、错误/跳过0，PNG0，后19类未运行。完整证据保留于 `verification/scheduled-cron-page-enable-2026-10-09/cron-page-r3-failed/`。下一步查清鼠标焦点与字符事件，交同一指定模型窄修；10秒期限、真实输入、业务/几何断言及12张Paused/Accepted/Completed宽窄浅深PNG矩阵保留。本批尚未验收通过。

终端依赖分析已补充15份固定JediTerm API源码证据，见 `reverse-engineering/terminal-foundation-evidence-2026-10-09/manifest.json`。持续流EOF、UTF-8跨块、快照深复制、解除阻塞清理及write结果语义已写入 `reverse-engineering/terminal-dependency-analysis-2026-10-09.md`。PTY基础仍未派发、未构建或native运行；后续在Cron验收后交同一指定模型。整体目标active，用户必做与排除清单保持。

## 2026-10-09 Cron 页面 R4 实际结果与 R5 窄修

R4 字符输入修复后，父线程冻结 2,216 输入，生产及测试编译通过。首类实际 6 项：3 通过、3 失败、错误/跳过 0；创建暂停任务、手动运行、离开页面 owner 持续及真实会话历史已通过。12 张宽窄浅深 Paused/Accepted/Completed PNG 与 matrix 已生成，SHA 和控制几何已核验；后 19 类未执行，不能称 134 项通过。完整失败证据及四份冻结源码在 `verification/scheduled-cron-page-enable-2026-10-09/cron-page-r4-failed/`。

三项失败为两类测试前提：Accepted 保存先于模型流启动，检查流次数需要有界等待 started；连接恢复或同 Host 替换会生成未选任务的新仓库，详情中的失效提示需要通过真实鼠标重新选任务后检查。R5 合同在 `reverse-engineering/cron-page-r5-test-preconditions-contract-2026-10-09.md`，仅修现有页面测试。其余三份交付源码保持 R4 SHA；永久栅栏、零副作用、次数、终态及真实输入断言保留。仍交同一 SHUAIAPI/gpt-6.1-sol / opencodex 工作者实施，不并发构建。

main 当前是本机 Cron 启用候选，尚未完成本批验收；Heartbeat 仍 Unavailable。父线程在写集释放后串行验证 20 类 134 项并检查 12 图。Cron 验收后推进 PTY/ANSI 基础；用户排除清单和整体 active 目标保持。

## 2026-10-09 Cron 页面 R5失败与R6最终验收

R5实际10项中8通过、0 failure、2 error、0 skipped；页面6/6，装配2/4的两个error均为夹具重复登记api.host，后18类未执行。12 PNG的SHA与geometry矩阵通过；失败证据完整保留在 `verification/scheduled-cron-page-enable-2026-10-09/cron-page-r5-failed/`。

同一SHUAIAPI/gpt-6.1-sol / opencodex工作者R6仅修改装配测试：通过公开Runtime.uninstall卸载夹具唯一HostApiPlugin，确认HostApiKey已释放，再登记测试服务；真实Host describe corrected断言及永久失效、取消传播、零副作用、finally清理保持。其余三源码SHA及后两个装配用例和helpers保持不变，释放写集后父线程冻结2,216输入。

最终同一manifest串行完成20类 **134/134**，失败/错误/跳过均0；页面6、装配4及后18类回归全部实际运行。12张暂停/接收/完成状态、1440×900/600×800、浅深主题PNG的SHA、尺寸、identity及control geometry已复核，最终候选逐张目视检查。全量工作树与冻结输入一致，完整XML/日志/源码manifest/交付/截图和目视记录归档于 `verification/scheduled-cron-page-enable-2026-10-09/cron-page-r6-final/`，归档文件SHA再次逐项核验。

main的verifiedCronExecution=true与DesktopScheduledExecution共用正式接线已通过本机Cron合同门槛；页面真实输入/操作到持久终态与Thread导航已验收。受控模型端点只提供回答和gate，服务/Room/Runtime/Gateway/LocalConnection/账本为真实临时实例。Heartbeat仍Unavailable，应用退出后后台执行未纳入；原版同状态像素、原生窗口和远端仍单独待验收。下一步先在/tmp独立小项目验证PTY/ANSI依赖的实际resolver与当前Kotlin compiler，再给同一指定模型有限基础合同。整体目标active，必做与排除清单保持。

## 2026-10-09 终端 PTY 基础实施与后续接线分析

独立 native 探针 R1/R2/R3 已验证 pty4j 0.14.0 与 JediTerm core 3.76 在当前 macOS x86_64 工具链中的解析、编译、真实 TTY、24×80 到 30×100 的 kernel resize、退出及 owned 线程清理；22 份输入和日志固定于 `reverse-engineering/terminal-native-probe-evidence-2026-10-09/manifest.json`。小探针不证明正式应用兼容。

五文件基础合同 `reverse-engineering/terminal-pty-foundation-contract-2026-10-09.md` 已派给同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者。保存全部 2,216 输入基线及五文件 before SHA；目标是完整 owner/terminal 身份、持续 UTF-8 与 ANSI parser、深复制 screen/history、真实 write+flush/Unknown、模型与 kernel resize，以及有界真实清理。工作者在上下文恢复后误切回已验收插件任务，父线程已纠正，当前确认只实施终端五文件。基础尚未交付、冻结或正式运行测试，不能列为已验收。

父线程已准备验收脚本，固定新增 common4/JVM12 的方法名，完整核对源码/YAML 输入集合；释放后冻结预计 2,220 输入，串行运行 5 类 38 项，保存正式 runtime 依赖图并构建 JVM 应用。后续分析见 `reverse-engineering/terminal-host-renderer-analysis-2026-10-09.md`：先接真实 Session/Host 权限与 Gateway/LocalConnection，再接 typed 静态 renderer 和页面。方向键模式、bracketed paste、IME 与鼠标输入协议仍需实际设计和验收；本机基础不外推远端或其它平台。

## 2026-10-09 终端 PTY 基础 R1 实际失败与窄修

同一指定模型交付五文件并释放写集后，父线程核对完整 SHA，冻结 2,220 输入。common 合同 4/4 通过；native 类 12 项均在 `StyleState.apply` 初始化发生同一 NPE（0 failure、12 errors、0 skipped），尚未进入 native 启动；后 22 项回归、依赖图及完整 build 未运行。失败证据与五文件冻结源码保留于 `verification/terminal-pty-foundation-2026-10-09/pty-r1/`。

确切根因是 apply 隐式 receiver 的 defaultForeground/defaultBackground 遮蔽 Entry 同名字段，设置默认样式前错误调用未初始化的 getter。已交同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者窄修外层显式引用；固定测试名字、数量与断言保持。交付后父线程冻结 pty-r2，按原五类 38 项及完整 build 验证。Host、renderer 和 remote 仍未派发；整体目标 active。

## 2026-10-09 终端 PTY 基础 R2 环境隔离与 R3 最终验收

同一指定模型仅修默认样式的外层显式引用后释放写集。R2 common4与真实PTY12全部通过；旧ExecProcessContractTest实际9/16通过、5 failures、2 errors。父线程确认 JAVA_TOOL_OPTIONS 被真实子Java进程继承并打印，污染精确输出和等待断言；R2失败证据保留于 `verification/terminal-pty-foundation-2026-10-09/pty-r2/`。只调整父线程环境隔离，生产五文件和测试断言未修改，R2/R3全部2,220输入SHA一致。

R3同一manifest串行完成五类 **38/38**：common4、真实PTY12、Exec16、SessionApi1、Descendants5，失败/错误/跳过均0；完整JVM应用build exit=0。正式exec-jvm/jvm-app runtime graph最终解析pty4j 0.14.0、jediterm-core 3.76、JNA/jna-platform 5.19.1、kotlin-stdlib 2.4.20一致；jvm-app stdlib-jdk7/jdk8为2.3.10。人工核对的是解析图，不把它称作重复JAR扫描。43份证据及归档SHA保存于 `verification/terminal-pty-foundation-2026-10-09/pty-r3/`，全工作树与冻结输入一致。

本次通过基础真实PTY/ANSI/write/resize/cleanup合同；Host/Local、语义按键与Paste、renderer/页面、remote、原版同状态像素及原生窗口仍待。下一批14文件合同已分析好：`reverse-engineering/terminal-host-local-contract-2026-10-09.md`，新增24项、总11类70项门槛，基础五文件保持本次验收字节。继续使用同一SHUAIAPI/gpt-6.1-sol / opencodex工作者。整体目标active，必做和排除范围不变。

Host/Local已保存完整2,220输入基线及14文件before SHA并派发。父线程核对旧回归方法时确认 SessionBackgroundMutationsProcessTest 实际2项，初版误记3；总门槛更正为70，未改任何旧测试，原版合同/baseline另存历史并将新SHA同步工作者。工作者仅实施和静态检查，父线程同步准备串行验收脚本；当前没有Host通过结论。

## 2026-10-09 终端 Host/Local R3 最终验收与语义输入实施

Host/Local 的 host-r1、host-r2 编译失败证据独立保留。同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者仅修跨模块 nullable key 的局部捕获和 Ktor InternalAPI 导入；身份断言、固定测试和其它输入保持。host-r3 冻结 2,229 输入，串行完成 11 类 **70/70**，失败/错误/跳过均 0，完整 JVM app build exit=0；全工作树输入与 manifest 一致。87 份证据及 SHA 位于 `verification/terminal-host-local-2026-10-09/host-r3/`。正式 runtime graph 已核对 pty4j 0.14.0、jediterm-core 3.76、JNA/jna-platform 5.19.1、kotlin-stdlib 2.4.20；这是解析图核对，未做重复 JAR 扫描。

下一批六文件语义输入合同 `reverse-engineering/terminal-semantic-input-contract-2026-10-09.md` 已交同一指定模型实施。范围是 typed Text/Paste/KeyPress、真实 parser 模式与每键独立 encoder、同一 bounded worker 单次 native write、已知拒绝与 Unknown fence。新 common4/native12 加上现有70，父线程验收脚本固定13类86项及2,233输入；工作者释放写集后才冻结运行。本批当前尚无测试/build通过结论。

后续接通语义输入 Host/Gateway/Local，再做 renderer 和真实页面输入。Remote、原版同状态像素及原生窗口独立待验收；本批不宣称原生 IME 或终端页面已经完成。整体目标 active，用户确认的必做及排除范围保持。

## 2026-10-10 终端语义输入 R1 最终验收

指定 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者交付六文件并退出后，父线程冻结2,233输入，串行13类 **86/86**，失败/错误/跳过均0；新增common4与真实PTY12、现有70全部运行。完整JVM app build exit=0，全工作树与冻结manifest一致。84份证据及归档SHA逐项核验，存于 `verification/terminal-semantic-input-2026-10-09/semantic-r1/`；批次目录保留2026-10-09合同日期，最终验收日期为2026-10-10。正式runtime解析图仍为pty4j0.14.0、jediterm-core3.76、JNA5.19.1与stdlib2.4.20。

真实fixture已验证方向键/功能键/keypad模式、Enter/Backspace/Tab/Escape、Control/Alt、Unicode提交、CRLF归一化及bracketed paste、最终字节预算、软硬reset、完整身份拒绝、取消和丢确认fence，以及bounded worker队列/解析/resize进展。实现保持单次真实native write，不增加页面假数据。

下一批12文件合同为 `reverse-engineering/terminal-host-semantic-input-contract-2026-10-10.md`，接通Host/Gateway/Local和独立portable client DTO；新增18、总17类104项门槛。外围Unknown与native已fence分开处理。renderer/页面、Remote、原版像素及原生窗口仍待，整体目标active。

## 2026-10-10 终端 Host/Gateway/Local 语义输入 R2 最终验收

同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者退出并释放写集后，父线程核对2,238输入，R2只改Gateway测试夹具：使用合法Runtime.replace，验证重复注册无副作用以及旧handle不删除新端点。R1两个夹具error的证据保留，生产接口及旧回归断言保持。

R2同一冻结manifest串行完成17类 **104/104**，失败/错误/跳过均0；完整JVM app build exit=0，全工作树输入与冻结manifest一致。110份归档文件SHA逐项独立复核，XML计数与build结果复核通过，路径 `verification/terminal-host-semantic-input-2026-10-10/host-semantic-r2/`。正式exec-jvm/jvm-app解析图确认pty4j0.14.0、jediterm-core3.76、JNA/jna-platform5.19.1、kotlin-stdlib2.4.20；各阶段包含重叠回归，不能相加。

Host/Gateway/Local的Text/Paste/KeyPress及真实PTY输入链路已通过合同，外围丢确认Unknown仍不自动声称native fence。renderer、页面键盘/粘贴/IME、Remote、原版同状态像素和原生窗口尚未验收。下一批分析 `reverse-engineering/terminal-renderer-page-analysis-2026-10-10.md` 将真实列表、新建shell、字符列映射、grid绘制、输入、滚动复制与resize一起交付可操作页面；继续使用用户指定模型。整体目标active，用户必做与排除范围保持。

## 2026-10-10 交互终端真实页面实施派发

页面分析已收敛为合同 R1：`reverse-engineering/terminal-renderer-page-contract-2026-10-10.md`。父线程验证当前完整 2,238 输入与 Host 语义输入 R2 manifest 一致，保存唯一 22 文件写集的 before SHA、合同及基线副本；已通过本机 Codex CLI 启动指定 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者。没有创建新的用户线程。

本批复用既有终端 tab，默认后台输出，新增交互终端子视图；一起交付真实 inventory、显式新建 shell、字符列映射、typed grid、语义键盘与 Paste、history 选择复制、kernel resize、退出保留和显式 close。生产 launch 只对同一 verified LocalConnection 与已验收的 macOS x86_64 提供能力。页面打开或关闭视图均不自动启动或关闭 PTY。

工作者只实施和静态检查，退出释放写集后父线程冻结并串行构建测试。验收脚本与固定方法清单已准备于 `verification/terminal-renderer-page-2026-10-10/`：旧 104 加新 36 为 140 项；四类受影响旧页面回归实际定义 44 项，因此本批总门槛为 29 类 184 项。尚未执行本批测试或完整构建，不记为已验收。至少八张宽窄浅深 Running/Exited 完整页面 PNG，另 Unknown/Unavailable 状态及 geometry/matrix 都须实际生成与复核。原版像素、原生窗口/IME、Remote 和其它平台保留独立验证边界；整体目标 active。

## 2026-10-10 交互终端页面 R5 最终验收

指定 SHUAIAPI/gpt-6.1-sol / opencodex 实施。R1、R2、R3失败证据分别保留于 verification/terminal-renderer-page-2026-10-10/page-r1、page-r2、page-r3。R4功能30类184/184和完整JVM build通过，但10张实际截图发现退出标题仍Running、两张标记light的异常状态图实际dark，视觉拒绝记录保留于page-r4。

R5仅修改PtyTerminalPanel和PtyTerminalPageInteractionTest：selector完整key与selectedKey及真实snapshot匹配时采用最新lifecycle；原宽窄浅深矩阵严格断言Running/Exited (7)，Unknown/Unavailable显式Light。父线程核对2,253输入，其中写集外2,251字节不变；22文件delivery、baseline/contract及固定新增8类36方法不变，history复制方法与其余受保护方法/helper逐字节保留。工作者退出释放写集后才冻结并运行。

父线程独立串行完成30类184/184，失败/错误/跳过均0，完整JVM app build exit0。早期父验收清单误把同文件的Shell8与Draft6归为一类14，R4已纠正为两类；总184与方法全集未改，纠正前证据保留。R5全部重新运行，没有复用旧XML。正式runtime解析图核对pty4j0.14.0、jediterm-core3.76、JNA/jna-platform5.19.1、kotlin-stdlib2.4.20；这是依赖解析图核对。

10张完整HarnessApp离屏图均逐张查看：1440×900/600×800、Light/Dark的真实Running/Exited各8张，受控丢确认Unknown和能力Unavailable两张；完整key/revision、viewport/控件geometry、实际主题背景像素均通过。Running/Exited来自真实LocalConnection/Gateway/PTY，异常回执与能力状态单独标controlled，生产未加入演示数据。可达入口、显式新建、键盘/粘贴字节、history选择与复制、kernel resize、owner切换、退出保留及显式关闭已有本批证据。

本批通过的是本机功能和离屏页面验收。原生窗口/系统IME、Remote、其他平台launch和原版同状态像素仍未验收。证据、XML、10PNG及逐文件SHA在verification/terminal-renderer-page-2026-10-10/page-r5/；旧104和新36及页面回归在184中重叠覆盖，各历史批次不相加。整体目标继续active，用户必做与排除范围不变。


## 2026-10-10 消息展示最终释放

指定 SHUAIAPI/gpt-6.1-sol / opencodex 工作者 exit0 并释放写集。当前基线保留 MessageCard 的 internal 可见性以供子 Agent 复用，LiveConversationSurface 展示逻辑不变；本轮只修正字符布局范围注释，明确 STRUT/TIGHT 不等于 raster glyph ink 或 baseline 测量。父线程核对 2,253 输入、2,252 不变，以及全部可执行测试字节和 14 个消息方法不变。

同一冻结源码串行重新运行 ThreadMessageVisualInteractionTest 14/14、LiveConversationInteractionTest 11/11、HarnessAppThreadPanelsShellTest 8/8，共 33/33，失败/错误/跳过均 0。12 消息 PNG 及 geometry、80 完整壳层 PNG 及 geometry 全部核对；父线程查看全部 10 张 contact sheets 及 3 张原尺寸消息图，正文最终 marker 可见，未见明显裁切。正式证据在 verification/thread-message-2026-10-10/final/；旧 provisional 历史保留，不复用旧 XML。

这是离屏结构、布局和交互验收，字体、672dp 内容列及 85%/560dp 人类气泡仍是 Harness 适配；原版同状态像素、原生窗口、Remote 未验收。前文消息仍 provisional 的记录为历史状态；当前已经最终释放。整体目标继续 active。

## 2026-10-10 本地终端紧凑视觉 R1 最终验收

先保存当前 app.asar 六个 asset 的 size/offset/SHA 与分析、三文件合同，再交指定 SHUAIAPI/gpt-6.1-sol / opencodex 实施。工作者 exit0 并释放写集后，父线程冻结 2,253 编译输入，仅 PtyTerminalPanel、PtyTerminalGrid 与 PtyTerminalPageInteractionTest 变化，其他 2,250 输入逐字节保持。

操作区改为一行真实 selector、新建图标与 typed 更多菜单，复用共享 IconButton/Menu/Tooltip；刷新、关闭、复制选区、粘贴、回到底部保留真实调用与禁用条件。菜单 scope 包含 bindingEpoch/selectionEpoch/selectedKey，切换后关闭 popup 并废弃过期待调度动作。成功回执退出常驻正文，Unknown/Unavailable/error 和真实 Exited 保留；10dp scrollbar 接 Surface/Border/BorderStrong 主题。没有新增生产演示数据。

父线程独立串行完成 6 类 **35/35**，失败/错误/跳过均 0，完整 JVM app build exit0。原六个页面方法保留，新增真实指针/键盘菜单方法；103 处原 assert 调用字面保持，55 处新增，唯一替换的 capture 边界断言改用窗口坐标与完整 viewport，支持 popup 多 root；业务、字节、身份、history、生命周期及 kernel resize 断言保留。

12 张终端完整页面 PNG 全部原尺寸查看：宽窄浅深真实 Running/Exited 八张、controlled Unknown/Unavailable 两张、宽浅/窄深菜单两张。完整 key/revision、geometry、实际主题背景像素均核验。80 张完整壳层 PNG/geometry 与全部 8 张 contact sheets 已检查，采用受控后台输出/子 Agent fixture。1440×900 可见行数 38→42、列数 74 保持；600×800 行数 26→31、列数 35 保持。证据、XML、源码 before/after、文档同步前后及逐文件 SHA 位于 `verification/terminal-local-visual-2026-10-10/local-visual-r1/`。

本批属于 Harness 本机离屏结构、视觉与交互适配；R5 184/184 为独立历史结果，不与本批相加。字体保持合同约定的 13sp/18sp，Appearance 消费者另行接入。原版同状态像素、原生窗口/系统 IME、Remote 和其他平台 launch 未验收；整体目标继续 active，用户确认的必做与排除范围保持。

## 2026-10-10 终端字体消费者 R1 最终验收

完成ASAR字体消费分析和三文件合同后，由指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施并exit0释放写集。父线程冻结2,254输入：PtyTerminalPanel/PtyTerminalGrid两份既有源码变化，新增PtyTerminalFontInteractionTest；其余2,251输入逐字节保持，既有页面/业务测试与真实PTY fixture未改。

空视口和正文复用LocalHarnessCodeTextStyle与共享metrics helper，保存真实IntSize；未知尺寸不提交临时5×2。尺寸、字体、density变化后重新推导viewport，经现有controller同步真实PTY/kernel行列。完整terminal key与bindingEpoch继续隔离过期resize；字体变化不创建/关闭PTY、不重建读取或输入桥。普通字形保留配置的family/weight/style，ANSI Bold/Italic继续覆盖，UI字号不额外缩放代码字号。

同一冻结源码由父线程独立串行完成Font3、Page7、Controller8、Grid4、TextInputBridge4、真实Connection4，共 **6类30/30**，失败/错误/跳过均0；完整JVM app build **exit0**。新增三项分别证明首次创建前保存字体生效、固定bounds内同一运行PTY重新调整行列、普通/ANSI字体样式和字号8/24及UI11→16独立性；真实输入字节、create request和kernel `stty size` 均验证。

4张字体完整HarnessApp离屏PNG全部原尺寸查看：1440×900浅色字号12→20，81列×42行→49列×25行；600×800深色为39列×31行→23列×19行。每对完整key与全部bounds保持；cellWidth7.5→12.5、rowHeight18→30，实际浅249/深32 Surface像素及行列公式独立核验。12张页面回归PNG也全部原尺寸查看，覆盖宽窄浅深Running/Exited、controlled Unknown/Unavailable与共享菜单；尺寸、geometry、rendered revision与主题背景像素通过，Exited保留最终size，不按提示后的grid高度反推行数。

正式证据在 `verification/terminal-font-consumer-2026-10-10/font-r1/`，含ASAR manifest、分析/合同、源码与文档before/after、XML/日志、delivery、冻结输入manifest、font-geometry-review、visual-review、acceptance及逐文件SHA。历史184/184、35/35为独立证据，不累加；前批80张壳层图未在本批重跑。

前文固定13sp/18sp是紧凑视觉批次的历史状态，当前已消费Appearance代码字体。行高沿用Harness共享代码样式；字体before内容为READY_0、after为DRAIN，未作原版同内容/同状态像素比较。原生窗口/系统IME、Remote与其他平台launch尚待，整体目标继续active，用户确认的必做与排除范围保持。

## 2026-10-10 Worktrees 关联聊天固定 pin-r2 最终验收

先核对当前ASAR固定聊天契约并保存manifest与片段，再交指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施11文件（6修改、5新增）。固定元数据使用独立owner分区JSON，完整header身份与version归一、原bytes CAS、OS lease、atomic replace及目录fsync警告；不改Session历史或Git。Controller以独立操作票据、owner/repository/header/revision核验与刷新回执驱动真实共享按钮。

pin-r1在Store测试遇到断言位置错误：非法HostId提前抛出；当轮Controller5/5、Store6/7，其余门槛未运行。指定模型仅窄修新增Store测试并补拒绝后bytes不变检查，其他10文件SHA保持。失败源码、6份before、日志与R1→R2 diff保留在 `verification/worktree-chat-pin-2026-10-10/pin-r1-failed/`，不将其列为通过。

工作者退出释放写集后，父线程冻结2,259输入，11交付文件与其余2,248输入逐字节核验。同一冻结源码独立串行完成10类 **56/56**（21新增、35回归），失败/错误/跳过均0，完整JVM app build **exit0**。真实临时Runtime/Room/Git/JSON的live/durable固定与取消固定、fresh reader和正式bootstrap重读、owner/header/revision/CAS隔离、真实鼠标操作均有本批证据。

4张固定状态与8张观察回归PNG按原尺寸、SHA、geometry及owner核验，覆盖640×800/320×600浅深主题。真实固定revision1与取消固定按钮可见；观察回归分别证明lineage末尾和滚动底部。截图滚动到目标，顶部内容局部在视口外，长单行路径仍截断；本批不证明完整壳层、原生窗口或原版像素一致。正式证据、before/after、XML/日志、ASAR、delivery、冻结manifest和逐文件SHA位于 `verification/worktree-chat-pin-2026-10-10/pin-r2/`。

固定与独立目录读取不提供无会话证明或完整删除保护。下一批先审查同一指定模型的归档/恢复只读设计，处理真实入口共享gate、静止证明、恢复自动retry和持久提交阶段，再确定实施合同；独立归档管理页、删除与清理不加入本批。历史测试数量不相加，整体目标继续active。

## 2026-10-10 Worktrees 聊天归档 A1：Session准入 gate-r3 最终验收

归档只读设计已退出，父线程将广泛A提案收敛为四文件合同。指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施，退出释放写集后，父线程冻结2,261输入；SessionStore/Session两文件修改，新增SessionAdmission及固定8方法测试；其余2,257输入字节保持。

每个Store持有精确实例的协调器，普通目标活动/catalog许可覆盖真实操作与回调，独占reservation在忙碌时立即typed拒绝。reservation关闭目标写入及Store生命周期准入，保留其他既有Session append；取消释放、旧token/handle、同ID replacement及owner dispose都有定向证明。owner mutex和同步observer语义保留，不引入继承上下文授权。

父线程独立串行完成Admission8、Session14、Agent6、Persistence7，共4类 **35/35**，失败/错误/跳过均0；完整JVM app build **exit0**。旧open-turn repair与pending retry重启续跑均保留。gate-r1编译失败（0项执行）的源码和日志独立保留；同一模型仅修复一处接收者，窄修基线中其余2,260输入保持。gate-r2编译通过但8项中2项跨Default等待虚拟时间超时，6项通过；旧三类及build未运行，失败独立保留。随后同一模型只修测试文件两处等待及import，各窄修保持其余2,260输入。gate-r3重新冻结并独立串行验收；XML方法名仅规范化末尾()，原名保留，不复用旧XML。源码before/after、review、冻结manifest、固定方法XML、日志、delivery及逐文件SHA位于 `verification/worktree-chat-archive-session-gate-2026-10-10/gate-r3/`。

本批只证明Session层准入。Agent/Inbox/driver、显式静止恢复、Room/process/PTY静止、持久归档/正式bootstrap、Worktrees与Recent/当前聊天同步仍待后续；归档按钮未启用。无截图、原生窗口或像素验收。不加入独立归档管理页、删除或清理；历史批次不累加，整体目标继续active。

## 2026-10-10 Worktrees 聊天归档 A2a：Inbox inbox-r2 最终验收

先完成A2只读分析，再收敛为Session/Inbox两文件修改和固定8方法新测试。指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施并exit0释放写集；父线程冻结2,262输入，修改2个既有文件、新增1个文件，其余2,259输入字节保持。

Session仅新增精确实例operation许可入口。Inbox insert/claim/replace/clear在mutex、复制及空队列判断前准入，Closing四入口拒绝无变更。最小NonCancellable段覆盖append、projection、version/消息ID及同步通知，nextItemId在回调前推进；验证调用者取消、回调失败、提交前失败和旧Inbox/同ID replacement隔离。正常存活owner下live/replay一致；owner disposal竞争仍由后续有限清理合同处理。

父线程独立串行完成Inbox8、SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7，共7类 **54/54**，失败/错误/跳过均0；完整JVM app build **exit0**。旧open-turn repair、pending retry重启续跑及keepInbox取消语义保留；实际XML固定方法已逐项核对。源码before/after、diff、review、冻结manifest、XML、日志、交付和逐文件SHA位于 `verification/worktree-chat-archive-inbox-2026-10-10/inbox-r2/`。

本批只证明Inbox全段准入与正常owner下的取消一致性；Registry/driver、有限owner清理、显式静止恢复、Room/process/PTY静止、持久归档/正式bootstrap、Worktrees与Recent/当前聊天同步仍待后续。归档按钮未启用；无截图、原生窗口或像素验收。不加入独立归档页、删除或清理。历史测试不累加，整体目标active。

## 2026-10-10 Worktrees 聊天归档 B：Registry registry-r2 最终验收

父线程先复核 Registry 生产源码及生命周期只读报告，收敛为一个生产文件和一个新增测试文件。指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施并 exit0 释放写集；父线程冻结 2,263 输入，修改 1 个既有文件、新增 1 个文件，其余 2,261 输入字节保持。

registry-r1 因新增测试访问跨模块 internal Context.isDisposing 编译失败，尚未执行行为测试；失败证据独立保留。registry-r2 由同一指定模型仅修新增测试，使用真实 cleanup gate 与公开 isDisposed 断言，生产实现字节保持；原基线与两次交付均保留。

create/restore 在 Registry mutex 前获取精确 Store catalog 许可，覆盖 setup/factory/publication/Restore listener/同步通知/最后取消检查/NonCancellable rollback。创建登记与已发布 entry 使用精确 identity，未采用 loaded Session 的失败不关闭该 Session；普通 Handle Closing 拒绝后可重试，旧 identity 不影响 replacement。本批未改变 Registry owner disposal，父 Context 并发关闭的 Runtime lane 等待风险仍由后续合同处理。

父线程独立串行完成 Registry6、Inbox8、SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7，共 8 类 **60/60**，失败/错误/跳过均 0；完整 JVM app build **exit0**。XML 固定方法逐项核对；原 open-turn repair、pending retry 重启与 keepInbox 取消语义回归保留。源码 before/after、diff、父 review、冻结 manifest、XML、日志、交付和逐文件 SHA 位于 verification/worktree-chat-archive-registry-2026-10-10/registry-r2/。

下一批 driver 独立活动；有限 owner 清理依赖 Runtime 生命周期等待边界的独立验收。之后才做显式静止恢复、Room/process/PTY 静止、归档持久化与 Worktrees/Recent 接线。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 C：driver driver-r2 最终验收

父线程先审查 AgentLoopAgent 生产入口及运行、取消、初始化和接力路径，收敛为一个生产文件和一个新增测试文件。指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施并 exit0 释放写集；父线程冻结 2,264 输入，修改 1 个既有文件、新增 1 个文件，其余 2,262 输入字节保持。

初次 driver-r1-static-rejected 在静态审查阶段被拒绝，未运行测试或构建；证据单独保留。r2 只修新增测试的关闭状态断言和清理夹具，生产代码及其余八个新增方法字节保持。

send/cancel/resumeAttempt 与每个 driver 独立取得 Session 活动许可；初始化成功后提交状态，采用、取消和 finally 按精确 identity 清理。通知及 Inbox snapshot 在 Agent mutex 外，接力在旧许可释放前登记候选，并发取消保持各自停止记录。本批只证明正常 owner 下操作及 driver 活动；父 Context 关闭的 Runtime lane 等待风险仍待后续合同验收。

父线程独立串行完成 driver9、Registry6、Inbox8、SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7，共 9 类 **69/69**，失败/错误/跳过均 0；完整 JVM app build **exit0**。固定方法及 XML 逐项核对；旧 open-turn repair、pending retry 和 keepInbox 回归字节保持。源码 before/after、diff、父 review、冻结 manifest、XML、日志、交付与逐文件 SHA 位于 verification/worktree-chat-archive-driver-2026-10-10/driver-r2/。

下一批 Runtime 生命周期等待边界与有限 owner 清理，之后做显式静止恢复、Room/process/PTY 静止、归档持久化及 Worktrees/Recent 接线。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 D：Runtime 生命周期等待边界重构验收

原实现用一把 Mutex 包住整个可挂起生命周期块，并依赖会被 withContext / launch 子协程继承的协程上下文 marker 做重入；当 cleanup 或 apply 在锁内等待另一个协程提交状态时，会形成等待环。已确认的具体环：Context.dispose 持锁执行 managedScope 的 cancelAndJoin；Fiber 持锁执行 plugin.apply 与 scope.dispose。

本批把 RuntimeMutationLane 的临界区改为**非 suspend 的同步状态提交**，锁不再需要重入，也不再依赖任何协程上下文元素判定重入。所有可能挂起的用户回调（plugin.apply、EffectScope 清理）、join 等待与 reconcile 循环移到锁外。保留一个操作标记，但它只用于决定「是否需要等待当前 drain」，不参与加锁，因此即使被 launch / withContext 继承也不会绕过锁——这正是旧设计无法解决的点。Fiber 增加进行中操作信号，并发 stop 先等待进行中的 start/deactivate；失败后仍进入 Disposed，重复 stop 无操作。provide/dispose 触发的刷新只「能接管就执行、否则立即返回」，避免与正在等待它的 apply 形成新的等待环；只有 install/uninstall 顶层等待 drain。

改动 6 个生产文件（RuntimeMutationLane、RuntimeCore、Runtime、Context、plugin/Fiber、effect/EffectScope）与 1 个新增测试 RuntimeLaneBoundaryTest。新增两个用例直接证明修复：另一协程在锁外提交 mutation 时，dispose 与 apply 均不再死锁；旧实现下两者会超时。

验证：runtime 模块 **105/105**（0 失败）；loader 12、agent 20、session 22、session-persistence 27、llm 38 全部 0 失败；9 类验收合同（driver9/Registry6/Inbox8/SessionAdmission8/Session14/Agent6/AgentLoop10/LlmRetry1/Persistence7）共 **69/69**，与 driver-r2 一致；完整 jvm-app build **exit0**。agent-loop 模块整体运行时出现的 1 个失败经对照实验确认为既有后台 driver 异常泄漏（旧实现同样 26 成功/1 失败），与本次改动无关；验收按类运行时干净。

边界：本批只证明锁不跨可挂起回调持有，未实现更早设计中的整棵 Context 子树 CAS 认领；child()/effect() 在 dispose 的锁外窗口内仍靠 disposing 标志拒绝新登记。Runtime.replace 现于锁外先校验 owner 再替换，存在窄竞态窗口。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。下一步为有限 owner 清理与显式静止恢复，再进入 Room/process/PTY 静止、持久归档及 Worktrees/Recent 接线。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 F：归档持久链（controller + repository + bootstrap）验收

在 E 批次独立归档存储之上，本批把归档元数据接入与 Pin 相同的读取/写入链，未启用任何页面按钮。源码写集 6 个文件：ManagedWorktreeModels.kt（新增 setArchived 默认不可用、ManagedWorktreeInventory.archiveRead、ManagedWorktreeState.pendingArchive/archiveReceipt/archiveBusy/canArchive）、ManagedWorktreeController.kt（新增 setArchived）、DesktopManagedWorktreeRepository.kt（新增 archiveStore、setArchived、readArchives）、main.kt（注入真实 managed-chat-archive.json），以及两个新增测试 ManagedWorktreeChatArchiveControllerTest.kt、DesktopWorktreeChatArchiveIntegrationTest.kt。apps/*/…/git/ 整包此前未纳入 git，故本批按 SHA 记录 after 值，无 before 基线。

setArchived 与 setPinned 同构：提交前核验真实本地 owner、绑定 identity == null、仓库指纹、目标工作树非 missing/prunable/bare、expectedRevision，以及观察者给出的精确 header 身份（id + headerFingerprint）；Git 复核后再次核验 header 身份与 owner。仅有限的最终确认与文件 CAS 位于 NonCancellable；提交后目录 fsync 失败仍返回 Applied + durability warning。Rejected 覆盖核验失败与存储冲突，其余为 Unknown；错误/未知只允许显式刷新，不自动重投。控制器沿用 read epoch 与 binding/operation ticket 分离、busy 重拒、回执归属核对与成功后重读磁盘发布状态，不本地翻转归档位。归档键 ChatArchiveKey(id, headerFingerprint) 与 PinKey 身份同源但类型独立，不写入 SessionHeader，不改变 PinKey。

新增验收：controller **5/5**；integration **6/6**。回归：Pin controller 5/5、归档存储 4/4、Pin 存储 7/7、Pin integration 6/6、managed integration 8/8、managed store 5/5、观察者 11/11、WorktreePin 交互 3/3，均 0 失败；完整 JVM app build **exit0**。integration 用真实临时 Runtime/Room/Git 与正式重开 bootstrap，断言 Room 行/事件、live session、managed 登记与 Git 跟踪文件未变，且归档/取消归档不产生模型请求或 Git 写。逐文件 SHA、review 与日志位于 verification/worktree-chat-archive-chain-2026-10-10/chain-f1/。

边界：本批只接持久元数据链，尚不改 Session/Agent/Room 生命周期，不持有共享准入门槛，不做恢复路径；归档按钮未启用，页面无归档入口。生命周期门禁独占（Busy 拒绝）、静止恢复（0 模型请求）、Room/process/PTY 静止、Worktrees 与 Recent/当前聊天接线均待后续批次。无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 G：忙碌拒绝门禁（同 Runtime）验收

在 F 的持久提交前加入同 Runtime 的忙碌拒绝门禁，未启用任何页面按钮。源码写集 3 个文件：DesktopWorktreeSessionObserver.kt（新增内部 ArchiveReservation 与 reserveArchive，复用模块已有 SessionAdmission.reserve）、DesktopManagedWorktreeRepository.kt（setArchived 提交前取精确 live 会话的独占 reservation，Busy/Rejected 直接拒绝，finally 释放）、DesktopWorktreeChatArchiveIntegrationTest.kt（新增 2 方法，合计 8）。

reserveArchive 先做与 observe 相同的 binding/owner/host/home/服务 fence，再按 id 取精确 live Session 并要求 header 指纹等于归档键，随后调用 SessionAdmission.reserve：Reserved 持牌提交；Busy 返回 Rejected（"仍有在途操作"）；Rejected 返回对应 reason；NotLive（durable-only 聊天）无 live 实例、无在途活动，直接提交不持牌。reservation 仅在最终确认与文件 CAS 期间持有，Git/observer 复核在持牌之前，模块 admission 状态锁不跨 IO、事件通知或 join 持有，取消路径也在 finally 释放。不排队、不代替用户 cancel、不清 inbox、不停进程、不 load/flush/repair。

新增验收：integration **8/8**（含 liveChatWithInFlightOperationRejectsArchiveAsBusy：持牌 live 会话归档被拒且不写文件，释放后可归档；archiveReservationIsReleasedAfterCommitAndNeverBlocksLaterWork：提交后门禁释放，可再取活动许可并取消归档到 revision 2）。回归：Pin controller 5/5、归档 controller 5/5、观察者 11/11、Pin integration 6/6、managed integration 8/8、归档存储 4/4、WorktreePin 交互 3/3，均 0 失败；完整 JVM app build **exit0**。证据位于 verification/worktree-chat-archive-chain-2026-10-10/chain-f2/。

边界：本批只证明**同一 Runtime 内**的忙碌拒绝与身份有效性。独立 JSON 的 CAS/文件锁不证明另一进程 live Agent 已静止；已归档身份的**持久入口检查**（SessionStore.load 与 AgentRegistry.create/restore 主动拒绝已归档 key）仍未完成，直接模块调用仍可重新接入，属后续模块层批次。Room/process/PTY 静止、显式静止恢复（0 模型请求）、Worktrees 与 Recent/当前聊天接线仍待后续。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 H：入口层持久门禁与独占归档能力验收

把归档检查下沉到拥有 Session/Agent 入口点的模块层，并提供模块级独占归档能力。源码写集 9 个文件：SessionError.kt（新增 ARCHIVED）、SessionArchive.kt（新增 SessionArchiveGate 与 SessionArchiveKey）、SessionAdmission.kt（新增 SessionArchiveToken/SessionArchiveAcquire 与 acquireArchive/releaseArchive）、SessionStore.kt（load/attach 入口层查询 gate）、AgentRegistry.kt（新增 beginArchive 与 AgentArchivePreparation）、DesktopWorktreeChatArchiveGate.kt（新增生产门禁）、main.kt（正式 bootstrap 注入 gate），以及新增 SessionArchiveContractTest.kt、AgentArchiveContractTest.kt。

入口层门禁在取得许可前查询可选 SessionArchiveGate；未提供 gate 时行为与之前逐字节一致。生产门禁按 observer 的完整 header 指纹比对归档键（id + headerFingerprint），坏文档升级为拒绝加载（fail closed）。独占归档资格在同一 mutex 决策内关闭目标 Session 的新 operation 许可，并在有在途活动/既有 reservation/catalog 活动时返回 Busy，不排队；catalog 与 detach 许可保持可用，使 owner 仍能停止 Agent；stopOwner 清空资格。AgentRegistry.beginArchive 按 id + 实例 identity 取独占资格，commit() 复用既有 detach（取消 Agent、释放 owner Context、dispose 触达 Session detach），失败返回 warning 而不抛出；dispose() 只释放资格、绝不取消。durable 提交与 detach 分开报告。

新增验收：SessionArchiveContractTest **5/5**、AgentArchiveContractTest **4/4**。回归：SessionAdmissionContractTest 8/8、AgentRegistryAdmissionContractTest 6/6、InboxAdmissionContractTest 8/8、AgentDriverAdmissionContractTest 9/9、DesktopWorktreeChatArchiveIntegrationTest 8/8、DesktopWorktreeSessionObserverTest 11/11，均 0 失败；完整 JVM app build **exit0**。证据位于 verification/worktree-chat-archive-entry-2026-10-10/entry-h1/。

边界：本批提供并激活入口层门禁与模块级独占归档能力；App 层 setArchived 仍用 F/G 的 reservation 路径，尚未切换到 beginArchive 做「提交后 quiesce」。Room/process/PTY 静止、显式静止恢复（0 模型请求）、Worktrees 与 Recent/当前聊天接线仍待后续。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 I：提交后 quiesce 验收

把 App 层 setArchived 从 F/G 的 reservation 路径切到 H 的模块独占归档资格，并在 durable 提交后对真实 Agent 做 best-effort quiesce。源码写集 5 个文件：AgentRegistry.kt（beginArchive 的 commit 从 detach 改为新的私有 quiesceArchivedAgent）、DesktopWorktreeSessionObserver.kt（ArchiveFence 替换 ArchiveReservation/reserveArchive，改走 SessionAdmission.acquireArchive）、DesktopManagedWorktreeRepository.kt（新增 archiveAgents 参数与 LiveFence/AgentFence/SessionFence；setArchived 优先 beginArchive，提交后 NonCancellable 内 commit）、main.kt（注入 archiveAgents）、DesktopWorktreeChatArchiveIntegrationTest.kt（新增 2 方法，合计 10；fixture 增加可选 archiveAgents）。

旧的 quiesce 复用 detach，而 detach 会先调用 Agent.cancel；cancel 入口即 session.acquireOperationPermit()，但归档门禁已关闭该 Session 的全部 operation 许可，于是 cancel 必然抛异常，被计为 quiesce warning，durable 提交仍成功却给出「未能完全停止」提示。本批的 quiesceArchivedAgent 不再调用 cancel：dispose owner Context 触发 AgentLoopAgent.shutdown()（取消 driver job 与 scope），移除 registry 记录并发出 Dispose 通知；Session detach 走门禁刻意保留的 catalog 级许可。App 层在 archiveAgents 存在时走 beginArchive（Granted→AgentFence、Busy→Rejected“仍有在途操作”、Rejected→对应 reason、NotRegistered→无 fence 直接提交）；否则回落 observer.acquireArchive。durable CAS 与 quiesce 都在 NonCancellable 内，quiesce 失败返回 warning 且与目录 fsync 的 durability warning 分开报告。

新增验收：AgentArchiveContractTest **4/4**、SessionArchiveContractTest **5/5**、DesktopWorktreeChatArchiveIntegrationTest **10/10**（含 archivingALiveAgentChatStopsAndDetachesIt：真实 Agent 支撑聊天归档 warning 为 null、registry 记录移除、Session detach、文件正确；agentBackedArchiveIsBusyWhileTheAgentHasInFlightActivity：持牌在途时 Busy 拒绝、不写文件、Agent 保留）。回归：AgentRegistryAdmissionContractTest 6/6、InboxAdmissionContractTest 8/8（agent 18/18）；SessionAdmissionContractTest 8/8（session 13/13）；DesktopWorktreeSessionObserverTest 11/11、DesktopWorktreePinIntegrationTest 6/6、DesktopManagedWorktreeIntegrationTest 8/8、WorktreePinInteractionTest 3/3（jvm-app 38/38），均 0 失败；完整 JVM app build **exit0**。证据位于 verification/worktree-chat-archive-quiesce-2026-10-10/entry-i1/。

边界：本批只证明同一 Runtime 内的 Agent 停止与 Session detach。独立 JSON 的 CAS 不证明另一进程 live Agent 已静止；Room/process/PTY 静止、显式静止恢复（0 模型请求）、Worktrees 与 Recent/当前聊天接线、移除保护（删除）仍待后续。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 J：页面归档按钮与回执验收

在 F–I 的归档链与 Agent 门禁之上，把 Worktrees 页面的归档入口接上。源码写集 3 个文件：GitPages.kt（每个已观察会话新增 managed-session-archive-status 与 managed-session-archive-toggle，Managed 区新增 managed-archive-receipt，页脚边界文案改为「删除与清理不可用」）、DesktopWorktreePinIntegrationTest.kt（WorktreePinFixture.repository 增加可选 archiveStore 与 chatArchiveRequest 辅助）、WorktreePinInteractionTest.kt（新增 realMouseArchiveAndUnarchivePersistToFreshReader 与 awaitArchive、PinPage.archiveSnapshot）。

归档控件与固定同构：仅当会话观察为 Observed、观察 owner 与 inventory owner 一致、pinKey 指纹有效且 archiveRead 为 Available 时展示；按钮启用走 state.canArchive(root, ChatArchiveKey)。归档与固定两个按钮并到同一行，以满足观察批次冻结的两阶段几何证明——卡片每多一行都会把「父会话标记→观察边界」的跨度撑出列表视口（实测卡片 +128 时 640×800 边界被裁掉）。归档回执仅在 owner/repository/root/会话观察与归档键一致时渲染，Applied 带 durability warning 时优先显示 warning，成功后的显示状态来自磁盘刷新。归档按钮由此启用。

新增验收：WorktreePinInteractionTest **4/4**（含真实鼠标归档→fresh reader 读到归档键、状态「聊天已归档」、按钮「取消归档聊天」，再取消归档 revision=2 清空，registry/Room/Git/live 逐字节不变）、WorktreeSessionObservationInteractionTest **4/4**（4 种尺寸/主题两阶段几何，回归证明卡片高度未破坏可对齐性）、ManagedWorktreeInteractionTest **3/3**、DesktopWorktreeChatArchiveIntegrationTest **10/10**、DesktopWorktreePinIntegrationTest **6/6**、DesktopManagedWorktreeIntegrationTest **8/8**，均 0 失败；完整 JVM app build **exit0**。证据位于 verification/worktree-chat-archive-page-2026-10-10/entry-j1/。

边界：本批只启用 Worktrees 页面归档按钮与回执，Recent/当前聊天同步未接线。归档只提交元数据并在同一 Runtime 内 quiesce Agent；独立 JSON 的 CAS 不证明另一进程 live Agent 已静止，Room/process/PTY 静止与显式静止恢复（0 模型请求）仍待。按钮位置变动使观察批次（pin-r2/observation-captures）与固定批次捕获的几何工件对当前页面而言已过期，本批未重新生成这些图片，仅以交互与几何断言通过为准；无原生窗口或像素验收；不移除 managed-archive 只读保护标签，不增加删除或清理。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 K：静止恢复能力验收

按分析第 4 项与父线程约束 5、6 建立显式静止恢复能力。源码写集 6 个文件：RoomSessionPersistence.kt（新增 SessionQuiescentRejection、SessionQuiescentLoad 与 SessionPersistence.loadQuiescent，Room 实现只读判定后复用 store.load）、Agent.kt（新增 QuiescentRestorableFactory 声明接口）、AgentRegistry.kt（新增 AgentQuiescentRestore 与 restoreQuiescent）、AgentLoopPlugin.kt（默认 loop 工厂改由 LoopAgentFactory : QuiescentRestorableFactory 注册），以及新增 SessionQuiescentRestoreContractTest.kt（5 方法）与 AgentQuiescentRestoreContractTest.kt（2 方法）。

loadQuiescent 先读快照并做与 load 相同的结构性校验，再在任何修复之前判定：pending llm retry → PENDING_RETRY；开放回合（有 TURN_START 无 TURN_END）→ OPEN_TURN；Inbox 重放仍有 NEXT_TURN/NEXT_STEP → PENDING_INBOX；行不存在 → NOT_FOUND；全部通过才发布 live Session。与默认 load 的差别是**不调用 repairOpenTurn、不 flush、不清 Inbox、不触发 retry**，因此不满足条件的会话不会被修复成可恢复；默认实现返回 UNSUPPORTED，未实现能力的后端不会被静默当作支持。restoreQuiescent 在同一 mutex 决策内检查工厂是否声明 QuiescentRestorableFactory，未声明直接拒绝、不挂接，声明则复用既有 restore；loop Agent 只在显式 send 或尝试续跑时才启动 driver，故默认工厂声明支持。既有 load/restore 语义逐字节保留。

新增验收：SessionQuiescentRestoreContractTest **5/5**、AgentQuiescentRestoreContractTest **2/2**。关键证据：恢复完整聊天后模型调用计数仍为 1（0 新请求），恢复会话事件与 durable 事件逐元素相等，两个 Inbox 队列为空，重开 Room 后事件行数不变；开放回合被拒后 durable 行没有补写的 TURN_END；pending retry 被拒后计数不变且无新 live 会话。回归：SessionPersistenceContractTest 7/7（session-persistence 合计 12/12）、agent 26/26（含 Archive 4/4、RegistryAdmission 6/6、InboxAdmission 8/8、AgentContractTest）、jvm-app 24/24（ChatArchive 10/10、Pin 6/6、Managed 8/8），均 0 失败；完整 JVM app build exit0。agent-loop 按类运行干净（AgentLoopContractTest 10/10、AgentInstructionsContractTest 7/7、AgentLoopContextualToolsTest 1/1）；整模块连跑的 1 个 UncaughtExceptionsBeforeTest 在两类之间漂移且单独运行通过，与 H 记录的后台 driver 异常泄漏一致，属既有现象。证据位于 verification/worktree-chat-archive-restore-2026-10-10/entry-k1/。

边界：本批提供模块级静止恢复能力与声明式工厂门槛，但尚未接线到 SessionApi/Host API 与页面——取消归档仍只删除持久记录，Recent/当前聊天仍未消费归档过滤，恢复未产生用户可见的“静止可读”状态。跨进程、Room/process/PTY 静止、Recent 同步与删除保护仍待后续。无截图、原生窗口或像素验收。各批证据独立，整体目标 active。

## 2026-10-10 Worktrees 聊天归档 L：取消归档恢复接线验收

把 K 的静止恢复接到生产路径，取消归档在持久提交后按聊天自身历史重新挂接 Agent。源码写集 7 个文件：RoomSessionPersistence.kt（`Loaded` 改携带 `SessionHandle`，保留 `session` 便捷属性）、ManagedWorktreeModels.kt（新增 `ManagedWorktreeChatRestore`，`Applied` 增加默认空值 `restore`）、GitPages.kt（归档回执渲染恢复结论）、DesktopWorktreeChatRestoreService.kt（新增生产恢复服务）、DesktopManagedWorktreeRepository.kt（新增 `restoreChat` 参数，插在 `archiveStore` 之前以避免尾随 lambda 重载歧义；取消归档在 NonCancellable 内调用）、main.kt（注入恢复服务），以及 DesktopWorktreeChatArchiveIntegrationTest.kt（fixture 增加 `restoreChat`，新增 3 方法）。

恢复顺序在持久 CAS 之后：先提交取消归档，再调用 `restoreChat`，结果放进 `Applied.restore`，与 `durabilityWarning` 分开报告，恢复失败不改写已提交结果、不重投。恢复服务先判 `AlreadyLive`，否则 `loadQuiescent` 只接受无开放回合/无待执行 Inbox/无 pending retry 的快照（无历史为 `NoHistory`，其余为 `Failed(reason)`），provider/model 取自会话自身 `requestContext()`（该聊天上次真正用过的模型，而非设置页当前选择）。挂接被拒时 dispose 刚发布的 Session，避免留下 live 但无 Agent 的泄漏态。新增参数插在 `archiveStore` 之前是必需的：最后一个构造参数若为函数类型，`DesktopManagedWorktreeRepository(backend, store) { owner }` 会在主构造与保留的 owner-validator 次构造之间产生重载歧义，本次已实测复现并修正。

新增验收：DesktopWorktreeChatArchiveIntegrationTest **13/13**（含 unarchivingRestoresTheChatWithoutRepairOrANewModelRequest：归档后 Agent 与 live Session 均消失，取消归档后 `restore == Restored`、Agent 与 Session 重新存在、归档集合清空、恢复后事件与归档前 durable 事件逐元素相等、`requestContext().provider` 仍为历史模型；unarchivingWithoutARestoreServiceStillCommitsAndReportsNoRestore：未接线恢复服务时 `restore` 为 null 且记录照常删除；restoreWithoutAModelOriginIsReportedFailedAndLeavesNoAgentlessSession：无模型来源恢复报 Failed 且无 agentless live Session）。回归 0 失败：jvm-app 32/32（PinInteraction 4/4、ManagedInteraction 3/3、PinIntegration 6/6、ManagedIntegration 8/8、SessionObserver 11/11）、WorktreeSessionObservationInteractionTest 4/4、session-persistence 12/12、shared 10/10（归档 controller 5/5 + 固定 controller 5/5）；完整 JVM app build exit0。证据位于 verification/worktree-chat-archive-unarchive-restore-2026-10-10/entry-l1/。

边界：取消归档现在真正恢复为可运行会话，因此会在下一次 Recent 刷新时自动重新出现（Recent 列 live Agent）；但**隐藏已归档聊天**这一侧仍未做，Recent/主会话未消费归档过滤，独立归档管理页不在范围。需要模型来源（该聊天有过模型请求）才能恢复为可运行；从未发起模型请求的聊天如实报 Failed。跨进程、Room/process/PTY 静止、删除保护仍待。本批修改了 RoomSessionPersistence.kt，K 批次为该文件记录的 SHA 已被取代，K 的其余文件与结论不变。无截图、原生窗口或像素验收。各批证据独立，整体目标 active。
## 2026-10-10 Worktrees 聊天归档 M：归档后 live 会话列表失效（Recent 同步）验收

把归档回执接到会话列表失效，使归档的聊天立即从 Recent 消失、取消归档的聊天立即回到 Recent，不需要用户手动点“刷新”。源码写集 2 个文件：ConversationController.kt（新增公开 `invalidateSessions(retired: SessionId? = null)`，重读 live 列表并在 retired 仍是当前选择时先取消选择）、HarnessApp.kt（订阅托管控制器 state 的 `archiveReceipt`，收到 Applied 时调用，归档传 retired）。测试写集 2 个文件：ConversationControllerTest.kt（新增 2 个契约用例，合计 33）、新增 WorktreeArchiveRecentSyncInteractionTest.kt。

新增验收：ConversationControllerTest **33/33**（含 externalArchiveInvalidationRelistsWithoutTouchingAnUnrelatedSelection、retiredArchiveDeselectsItsSelectionAndFencesStaleHistory）、WorktreeArchiveRecentSyncInteractionTest **1/1**（真实 HarnessApp：Settings → Worktrees 归档后 list 被重读、回到 Recent 时卡片消失并进入 empty；取消归档后卡片恢复；受控仓库收到 1 次归档与 1 次取消归档）。回归 0 失败：jvm-app 定向 **59/59**（GitSettingsRoute、WorktreePin 4/4、WorktreeSessionObservation 4/4、ManagedWorktree 3/3、ProjectsRecent、LiveHarnessConversation、DesktopWorktreeChatArchiveIntegration 13/13、GitWorktreesScreenshot）；shared **47/47**（ConversationController 33 + 归档 controller 5 + 固定 controller 5 + managed controller 4）；完整 JVM app build **Build successful, exit 0**。证据位于 verification/worktree-chat-archive-recent-sync-2026-10-10/entry-m1/。

反向对照：临时把 HarnessApp 的 `invalidateSessions` 调用换成空表达式后，同一用例失败于 `waitUntil { listCalls > beforeArchive }`（live 列表从未被重读），随后恢复接线并重跑通过；证明该用例由本批接线决定而非偶然。

边界：唯一归档入口在 Settings → Worktrees，进入设置时路由已经把选择清空，所以 `retired` 分支在今天的界面上不会被命中，它由契约测试覆盖并供将来“从 Recent 直接归档当前聊天”入口直接生效。本批**不**处理归档后回退栈仍可回到该聊天 Thread（Route 对不在 live 列表中的 id 是权威的，该页面应显示什么需要单独决定）。若某次归档的静止未完成（durabilityWarning / busy），该聊天仍在 live 列表，Recent 仍会显示它；本批不伪造 Recent 的 archived 字段、不做 Recent 侧归档过滤。独立归档管理页、删除与清理保护、跨进程与 Room/process/PTY 静止、原版同尺寸像素与原生窗口仍待。本批修改了 HarnessApp.kt，I 批次为该文件记录的 SHA 已被取代，I 的 quiesce 结论与其余文件不变。无截图、原生窗口或像素验收。各批证据独立，整体目标 active。


## 2026-10-10 Worktrees 移除保护 N：受保护删除与守卫式工作树移除验收

按 reverse-engineering 冻结的删除契约（`git-worktrees-implementation-contract` §3 W03/W05/W09–W12、§5 Delete plan/receipt、§6；`managed-worktree-protection-implementation` W12/W14）实现"可信 plan → 重验全部保护 → typed 回执 → 重扫"，并启用 Worktrees 卡片的"删除工作树"。源码写集 6 个文件：GitModels.kt（`GitWorktreeContent`/`GitWorktreeContentProbe`/`GitRemoveWorktreeRequest`/`GitRemoveWorktreeReceipt`/结果与 `GitWorktreeRemover`）、ManagedWorktreeModels.kt（`ManagedRemovalBlockKind` 含新增 `Detached`、`ManagedWorktreeRemovalPlan/Read/Request/Receipt`、`ManagedWorktreeEntry.removal`、`ManagedWorktreeState.pendingRemoval/removalReceipt/removalBusy/canRemove`）、ManagedWorktreeController.kt（`remove(plan, pageReady)`）、GitPages.kt（卡片末尾 `managed-removal-status`+`managed-remove`、`managed-remove-dialog`、页脚条件收紧）、DesktopGitBackend.kt（真实内容探测与守卫式删除）、DesktopManagedWorktreeRepository.kt（`removalEvidence`/`computeRemovalBlocks` 与全维度复查式 `removeWorktree`）。测试写集 2 个文件：ManagedWorktreeControllerTest.kt（新增 2，合计 6）、新增 DesktopWorktreeRemovalIntegrationTest.kt（6）。

保护维度：归属、Git 存在、bare/prunable/locked、detached（无分支指纹）、主工作树、与另一 linked worktree 嵌套、会话观察可用、运行中 Agent、持久历史、固定聊天、归档聊天，以及仅对无前置阻断的候选才执行的内容探测（未提交/子模块/嵌套仓库/忽略内容/扫描不完整）。分发前重算 blocks 并重读一次 HEAD/分支指纹；backend 再复核并执行 `git worktree remove -- <path>`，**不带 --force、不带 prune**，只有重新读取的工作树列表不再含该路径才发 `Removed`，随后才在 `NonCancellable` 内回收 managed intent；控制器 busy 重拒、回执不匹配降级 `UnknownOutcome`、仅 `Removed` 触发重读，绝不本地翻转"已消失"。

新增验收：ManagedWorktreeControllerTest **6/6**（含单飞与回执不匹配）、DesktopWorktreeRemovalIntegrationTest **6/6**。回归 0 失败：jvm-app 4 类真实链路 **36/36**（Removal 6、Pin integration 6、ChatArchive integration 13、SessionObserver 11）、jvm-app 8 类定向回归 **34/34**（WorktreePin、WorktreeSessionObservation、ManagedWorktree、WorktreePage、WorktreeCreation、WorktreeArchiveRecentSync、GitSettingsRoute、GitWorktreesScreenshot）、shared 4 类 **49/49**（Conversation 33、ChatArchive controller 5、Pin controller 5、ManagedWorktree controller 6）；完整 JVM app build **Build successful, exit 0**。证据位于 verification/worktree-removal-protection-2026-10-10/entry-n1/。

本批首次运行即暴露并修复两个真实缺陷（日志 harness-n1-removal-first-failure.log 为前后对照）：其一，`--ignored=matching` 与 `--untracked-files=no` 在 git 2.50 是非法组合（exit 128），被吞成 `ContentUnavailable`，使任何工作树都永远无法删除；其二，删除后对已不存在路径调用 `toRealPath()` 抛异常，把成功删除误报为 `UnknownOutcome` 且 intent 永不回收。另修正一处布局回归：删除块若放在会话观察边界之前会拉长"父会话标记→边界"跨度而使观察几何用例裁剪 6px（J 批次已记录同类约束），已移到卡片最后一个元素。

边界：只在 `LocalReadOnly` 绑定可用，remote 直接 `Unavailable`；依赖 F–L 的会话/固定/归档服务，任一缺失即整条删除证据不可用、卡片不出现删除块、页脚如实显示不可用。last-read 与 Git 进程启动之间仍有窄 TOCTOU 窗口，不声称原子。未实现 snapshot/auto-cleanup、批量删除、force 解锁路径与跨进程静止；本批无原生窗口、同尺寸像素或截图验收。本批修改了 J 记录的 GitPages.kt 与 L 记录的 DesktopManagedWorktreeRepository.kt 等文件，其旧 SHA 失效；各批证据独立，整体目标 active。

## 2026-10-10 定时任务 会话跟进 O：Heartbeat 真实投递验收

把 `ScheduledTarget.Heartbeat`（会话跟进）从“处处 Unavailable”改成向既有活动会话真实投递，与手动发送共用同一条 `SessionApi.prompt` 路径（`agent.followup` → 同一 `SessionAdmission` 原子准入）。源码写集 8 个文件：ScheduledLedgerRules.kt（运行目标允许 Cron 或 Heartbeat；Heartbeat 的 target.sessionId 必须等于 candidateSessionId；游标接受两种目标；Claimed 对 Heartbeat 额外允许直接进 SessionCreated）、ScheduledExecutor.kt（claim 的候选会话取目标会话；deliver 对 Heartbeat 跳过 CreatingSession、sessionRef 即目标会话、从不 create；settleUncalledPrompt 对 Heartbeat 无创建副作用时落 Rejected）、ScheduledSessionAccess.kt（create 对 Heartbeat 明确报错、prompt 要求 sessionRef 恰为目标会话、unavailableReason 不再短路）、ScheduledExecutionModels.kt（删除 ScheduledHeartbeatUnavailable）、ScheduledModels.kt（canActivate 接受 Cron 或 Heartbeat）、ScheduledRepository.kt（taskExecutionReason 只要求目标在目录内，模型仅非空时校验）、ScheduledManagementPage.kt（删除 scheduled-heartbeat-unavailable 文案，改为仅 Heartbeat 显示的 scheduled-heartbeat-target 说明）、DesktopScheduledSessionEvidence.kt（Heartbeat 要求模型为 null 且目标会话是本机当前活动会话，否则给出不能创建或恢复的中文原因）。测试写集 4 个文件：ScheduledRepositoryTest.kt、ScheduledExecutorTest.kt 覆盖 Heartbeat 的激活/认领/投递与拒绝；LocalServiceTransportInteractionTest.kt、ScheduledManagementScreenshotTest.kt 对齐页面迁移后的真实 tag。

语义：Heartbeat 的持久 intent 直接指向任务绑定的既有会话，跳过创建步骤，绝不调用 create，也不覆盖模型；只有正式接线（main.kt 的 verifiedCronExecution=true）下才可被认领。settleUncalledPrompt 对 Heartbeat 在 prompt 前停止且未提交投递意图时落 Rejected，因为没有创建副作用需要核对。

新增验收：ScheduledRepositoryTest.heartbeatActivatesAndClaimsItsBoundSessionWithoutEverCreatingOne（激活 + runNow → Accepted，candidate/sessionRef 等于目标会话，0 create、1 prompt）、heartbeatIsRefusedWhenItsBoundSessionIsNoLongerAvailable（目标会话消失后激活与 runNow 均拒绝，无副作用）；ScheduledExecutorTest.heartbeatDeliversIntoItsBoundSessionWithoutCreatingOne（无 CreatingSession 提交，0 create、1 prompt）、directAdapterRejectsUnsupportedOrInvalidTargetAndMismatchedSessionBeforeEffects（Heartbeat 的 create/prompt 在副作用前失败）。回归 0 失败：shared 6 类 83/83（含上述 Heartbeat 用例）、jvm-app 16 类 90 通过；完整 JVM app build Build successful, exit 0。证据位于 verification/worktree-heartbeat-delivery-2026-10-10/entry-o1/。

本批暴露并修复一个真实缺陷：claim 中新增的会话候选变量与既有快照变量同名 candidate，构成重复声明（编译错误），已重命名为 candidateSessionId。另对齐两处页面迁移遗留的陈旧测试：LocalServiceTransportInteractionTest 改用 scheduled-task:local-only / scheduled-new-task；ScheduledManagementScreenshotTest 按状态分阶段校验（landing 标签 → 进入 ledger → 历史标签），状态区无滚动祖先时改为能滚则滚、否则直接断言可见。

边界：只做投递到既有活动会话；不自动创建、不自动恢复、不覆盖模型。未纳入应用退出后的后台执行与跨进程 Heartbeat 投递。jvm-app 定向运行 2 个失败与本批无关且为既有陈旧用例：ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks（600dp 下 plugins-navigator 未挂载，shell 紧凑布局）、ShortcutsInteractionTest.ordinarySearchEditingAndOpenMenuKeepTheirOwnKeyboardBehavior（shortcuts-search 把占位符暴露为 Text）。无原生窗口、同尺寸像素或新截图验收；各批证据独立，整体目标 active。

## 2026-10-10 定时任务 导航器视觉 P：按原版截图实测对齐验收

本批只改视觉，且只改 1 个生产源文件：`ScheduledManagementNavigator.kt`（未改任何测试）。修正来自对原版截图的重新测量。

先纠正上一批 handoff 的换算：`docs/desktop-ui/reference/captures/2026-10-06-user-pages/scheduled-tasks.png` 的密度实测为 1（1 raw px = 1 css px），不是 handoff 里写的 0.5。判据是图标 rail 实测宽 52 px，恰等于 `HarnessTokens.Control.RailWidth = 52.dp`；导航器左边界 x=52、右边界 x=340，宽 288 px，正是 `workspace-navigator-measurement-2026-10-06.md` 已记录的用户手改宽度。若按密度 2 换算，rail 会变成 26 css、导航器 144 css，与仓库既有 token 和记录都矛盾。

三处有实测依据的差异（归一化到导航器左边界 / 应用内容顶）：

1. 任务行此前是"整行居中按钮 + 缩进状态"，原版是左对齐两行（任务名在上、状态在下，左内缩相同）。实测我们任务名内缩 24 css、参考 17 css。改用共享 `HarnessRow`（标题 + `bodySmall` 描述）后落在 16 css。
2. "新建任务"此前是整行填满的居中按钮，原版是左对齐一行（`Plus` 图标 + 文本）。改为 `NavigatorNewTaskRow`，内缩 `Spacing.Two`；testTag `scheduled-new-task` 与 disabled 语义逐字保留，8 处 `assertIsNotEnabled()` 与鼠标点击用例不受影响。
3. 小节标题此前是"任务"，原版是"即将执行"（Codex 对该任务列表的组名），已对齐，样式 `labelLarge` + `TextMuted`。

另两处：去掉 header 与列表之间的 `HorizontalDivider`（原版该区间无任何水平分割线，x=200 列在 y 74..300 恒为 `#FCFCFC`）；外层间距 10→14 dp、header 内 10→12 dp，使新建任务/小节标题/任务名的基线落到参考位置。

实测对照：标题 x 17 vs 16、新建任务图标 18 vs 19 / 文本 41 vs 40、小节标题 17 vs 17、任务名 17 vs 16、状态 17 vs 17；纵向相对内容顶的差在 1~4 px，剩余差来自字形（参考是 CJK、我们是拉丁），基线已对齐。

验收 0 失败：shared 9 类 **103/103**（PageShellSpec、PageNavigatorMatrixAudit、ScheduleEngine、Executor、ExecutorOwner、ExecutorCapacity、LedgerRules、Repository、ScheduledTasksRepository）；jvm-app 8 类 **47/47**（调度页面交互/路由/截图、Cron 页面集成、Picker 迁移、LocalServiceTransport）；完整 JVM app build **Build successful, exit 0**。截图重新生成到新目录 `docs/desktop-ui/captures/scheduled-page-2026-10-10/`（144 PNG + 144 geometry json），未覆盖 2026-10-08 旧产物。证据位于 verification/scheduled-navigator-parity-2026-10-10/entry-p1/，含可重跑的 `measurement/measure-navigator.py`。

边界：参考图"即将执行"右侧的漏斗筛选图标**未加**——本机没有真实的筛选语义（列表恒为全部任务），加一个不改变任何结果的图标就是死控件。参考图导航器右边界竖线未复刻，因为测试载体只渲染导航器 + 页面，不含窗口 chrome。本批是几何对齐 + 目视核对，不是像素级比对，也无原生窗口；页面主体与模板卡片本批不动。唯一失败是既有无关用例 `ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks`（600dp 下 `plugins-navigator` 未挂载，shell 紧凑布局）。过程中 `ScheduledRoutingInteractionTest.editingBoundTaskPersistsAcrossStoreInstancesWithoutExecutingSession` 出现一次 `scheduled-detail-prompt` 偶发失败，单跑与整套重跑均通过，记为 flake。各批证据独立，整体目标 active。


## 2026-10-10 插件管理 导航器视觉 Q：按原版截图实测重建并接入真实已登记库存

本批交付两件事：(1) 插件页导航器按原版截图实测重建几何；(2) 导航器列出 Host 真实已登记的插件，与页面共用同一个查询。

生产源码写集 5 个文件：新增 `apps/shared/src/im/hikaru/harness/client/plugins/PluginsManagementNavigator.kt`；`AppShell.kt` 新增 `pluginsNavigatorContent` 参数（`NavigatorKind.Plugins` 优先用它，旧 `PluginsNavigator` 退化为回退）；`HarnessApp.kt` 构造并注入导航器（筛选回调同时收起紧凑抽屉，旧回调也是这个行为）；`HarnessTokens.kt` 新增 `Control.PageNavigatorWidth = 289.dp` 并把 `SettingsNavigatorWidth` 改为其别名；`NavigatorLayout.kt` 的 `pageNavigatorWidth` 把 Settings/Plugins/Hosts/Scheduled 统一为 289（原先 Plugins 走 275）。测试写集 3 个文件：`PluginRoutingInteractionTest.kt`(+3 用例)、`PluginManagementInteractionTest.kt`（"已安装插件"计数 2→1，该文案只在页面标题出现）、`PluginsPageTestSupport.kt`（新增默认不写盘的 `pluginsShellCapture`）。

几何来自对原版截图的直接测量，不是凭印象改动。`plugins-marketplace.png` 密度为 1：rail 宽 52、导航器 x 52..340 = 289。实测对照（都归一到各自导航器左边界 / 导航器顶；参考底 74，我们底 44）：导航器宽 289 vs 289；主导行 y 60..74 vs 60..73、行距 31 vs 31；主导行图标 x 17..30 vs 18..29、标签 x 40 vs 40；小节标题 x 17 vs 17、y 138..150 vs 136..147；条目行行距 31 vs 31、标签 x 41 vs 41；选中高亮 y 52..81 vs 52..81、x 8..279 vs 8..280；导航器右边界竖线 x 340（lum 228）vs x 340（lum 239）。结构锚点全部一致，剩余差是字形量级（参考标题是 CJK"自定义"，我们是"插件"）。

本批纠正两处此前记录：

1. 上一批 handoff 写"参考选中高亮 x 66..323（相对 14..271）"，本次直接测量为 x 60..331（相对 8..279）。我们的实现（相对 8..280）本就正确，因此没有按错误数据去改。
2. P 批次边界写"参考图导航器右边界竖线未复刻，因为测试载体不含窗口 chrome"。用生产 Shell 复核后确认真正原因是截图时 `titleBarInset = 0`，而 `TitleBarInsetColumn` 只在 inset > 0 时才画右边缘 hairline；真实窗口有 44dp 标题带，这条竖线在生产中存在。截图改用 44dp inset 后竖线即出现在 x=340，未改任何源码。P 批次的代码结论不受影响。

验收 0 失败：shared 4 类 **28/28**（NavigatorLayout、PageShellSpec、PageNavigatorMatrixAudit、AppShellState）；jvm-app 4 类 **28/28**（PluginManagement/PluginRouting/PluginLocalUpdate/PluginsPage 交互）；完整 JVM app build **Build successful, exit 0**。截图落在新目录 `docs/desktop-ui/captures/plugins-navigator-2026-10-10/`（8 PNG + 8 几何 JSON：1440x900 与 2560x1440 × 浅深），未覆盖 `plugins-page-2026-10-08` 与 `plugins-visual-2026-10-07`。证据位于 verification/plugins-navigator-parity-2026-10-10/entry-q1/，含可重跑的 `measurement/measure-plugins-navigator.py`。

边界：参考导航器的三个分区（自定义 / 已安装 / 技能）与右侧市场网格属于用户已排除的云端/市场范围，本批只分组本机真实已登记库存，不引入市场发现，也不渲染 Host 没报过的条目。唯一失败是既有无关用例 `ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks`（600dp 下 plugins-navigator 未挂载，shell 紧凑布局）。`AppShell.kt` 的 `ScheduledTasksNavigator` 分支自 P 批次起已是死代码，本批不动，留待清理。本批是几何对齐 + 目视核对，不是像素级比对，也无原生窗口组合；各批证据独立，整体目标 active。


## 2026-10-10 基础设置 导航器视觉 R：按原版截图实测对齐

参考图 docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png 已实测（密度 1，导航器 x=52..340）。分组标题与带图标条目在此前批次已实现；本批实测确认行几何本就一致，只修了 3 处有实测依据的差异。

生产源码写集 3 个文件：`HarnessSearchField.kt`、`HarnessTokens.kt`、`AppShell.kt`；测试写集 1 个文件：`GeneralSettingsInteractionTest.kt`（新增默认不写盘的 `settingsShellCapture` 与截图用例）。

1. 导航器右边界竖线缺失。参考图 x=340 有 1px 竖线（lum 228），我们渲染 249、看不到。原因是页面导航器用不透明背景铺满整列，盖住了外壳画在内容之下的 hairline；插件页导航器不加背景，所以那条线一直可见，掩盖了这个缺陷。改为由外壳在内容之上绘制，且只在 Pushed 布局绘制（浮动抽屉靠阴影分隔）。
2. 共享 `HarnessSearchField` 水平内边距 8 → 12。参考图放大镜 ink 在相对 x 22..33、占位文本在 45；改前是 18..29 / 41，整段偏左 4px。推导：内边距 + 图标 16 + 间距 8 = 36，与参考文本 ink 45 一致。
3. 设置行占位图标溢出单元格。`SettingsRowIconScale = 1.17` 的推导假设共享图标是 0.64 幅面，实际用于占位的却是满幅网格图标：实测 ink 20px，越过 16px 图标槽（相对 x 14..33，而已编目图标是 17..30）。按实测回标到 0.82。

实测对照（都归一到各自导航器原点；参考底 74，我们底 44）：导航器宽 289 vs 289；右边界竖线 x 340（228）vs x 340（239）；标题 ink y 17..33 vs 18..34；搜索框 y 52..87、x 8..279 vs y 52..87、x 8..280，放大镜 ink 22..33 vs 22..33；分组标题 y 109..121 vs 107..119；首行（选中）y 142..156、图标 17..30、标签 41..67 vs y 141..154、图标 17..30、标签 41..67；行距 31 vs 31；选中行底色 y 134..163、x 8..279 vs y 133..162、x 8..280。

另修正一处旧文档结论：2026-10-06 的 settings-navigator-measurement 写"没有同状态截图，几何只能结构推断"，该截图后来已归档；其中"宽 275、行高 36/行距 40"是当时实现值，现实现为 289 宽、30 行高/31 行距，与参考一致。

跨批次影响：本批改了共享搜索框内边距与竖线层级，Q 批归档的插件导航器截图因此不再逐像素一致。同测试重渲染比对后，插件导航器区域只有 x 340..341、y 0..44 不同（竖线现在只覆盖内容区，参考图标题带本就没有竖线），内容区差异来自插件页自己的搜索框右移 4px。Q 的证据目录与其 files.sha256 保持批次快照不变。

验收 0 失败：shared 5 类 **33/33**（NavigatorLayout、PageShellSpec、PageNavigatorMatrixAudit、AppShellState、SettingsNavigatorState）；jvm-app 8 类 **56/56**（GeneralSettings/Appearance/ModelSettings/Connections/GitSettingsRoute/PluginRouting/PluginManagement/PluginsPage 交互）；完整 JVM app build **Build successful, exit 0**。截图落在新目录 `docs/desktop-ui/captures/settings-navigator-2026-10-10/`（8 PNG + 8 几何 JSON：1440x900 与 2560x1440 × 浅深）。证据位于 verification/settings-navigator-parity-2026-10-10/entry-r1/，含可重跑的 `measurement/measure-settings-navigator.py`。

边界：参考图分组里包含本机已排除的条目（通知、导入、个人资料、家长控制、受信任联系人、语音、配置、个性化、Mini 与虚拟宠物、使用情况和计费、账户、电脑操控、应用快照、浏览器、Cloud computer、Codex 云端、代码审查、已归档的聊天），我们只列范围内条目（个人：常规/外观/模型与 Provider/键盘快捷键；集成：插件；编码：连接/Git/Worktrees），行几何与分组结构对齐、内容按已确认范围裁剪。"模型与 Provider"是本机自己的行，图标仍是共享占位图形（已修到与编目图标同宽同高，没有 Codex 原图）。共享搜索框内边距取 12 的依据只来自设置导航器；参考图的页面级搜索框是描边白底样式、与导航器填充药丸不是同一控件，页面级用法没有同源实测，本批不动其结构。rail 与导航器的边界过渡在参考图里是软的（x 44..51 从 243 渐变到 235），我们是 x=51 单像素 234，接近但不是同一画法，未改。本批是几何对齐 + 目视核对，不是像素级比对，也无原生窗口组合；各批证据独立，整体目标 active。


## 2026-10-10 基础设置 正文视觉 S：按原版截图实测对齐

参考图 docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png 已实测（密度 1）。正文三处有实测依据的差异已修正，生产源码只改 1 个文件 `SettingsGeneralPage.kt`，测试只改 `GeneralSettingsInteractionTest.kt`。

1. 内容列宽 696 → 728。页面自身把 728 上限又减了左右各 16 内边距，而外壳（MainSurface）已经把设置内容列的上限设为 728，于是 `narrowContent` 在恰好 728 时误判为窄屏、走了"内缩 16"分支而不是既有的 -8 偏移。改为 `maxWidth < ContentWidth` 才判窄屏后，卡片落在 x 1079..1806（宽 728），与参考完全一致。
2. 行分隔线内缩 17。参考卡片 x 1079..1806、分隔线 x 1096..1789，即分隔线与行内容同为内缩 17；此前是整卡满宽。现在两者共用 `RowInsetX = 17`。
3. 页标题 24 → 28px、行盒 34。参考标题"常规" ink 是 53×26；同一套字体 24px 渲染成 45×23（参照：同批参考图里插件页标题"插件"实测 46×23，正是 24px）。改到 28px 后 ink 53×27、位置完全对上；自然行盒 39px 会让下方整体下移 5px，故固定 34px 行盒。

实测对照（归一到各自内容顶；参考 74，我们 44）：页标题 y 79..104 x 1081..1133 vs y 80..106 x 1081..1133；分节标题「权限」y 157..170 x 1079..1106 vs 完全相同；首卡上下边 y 189 / 359 vs 完全相同；首卡分隔线 y 266 vs 266；第二卡上边 y 446 vs 446；卡片边线 1090..1795 / 1096..1789 vs 1089..1796 / 1096..1789；行内容内缩 17 vs 17；第二行标签 y 282..294 x 1097..1173 vs 完全相同；分节标题「常规」y 414..427 vs 完全相同。

仅剩差异是内容驱动：参考的偏好行是"标题+说明"两行（行高 61），我们为未接入的服务额外显示第三行「尚未接入…」（行高 73），因此第二张卡片更高（参考 789、我们 925）。首卡不受影响——它的两行与参考一一对应，行高 77/93 与参考相同。另有一处小差：行内标题与说明之间的 ink 间距参考约 4px、我们约 7px。

跨批次影响：本批改了设置页正文，重渲染 R 批设置导航器截图后，导航器区域（x < 341）逐像素完全相同，只有页面区域不同；R 的证据目录与 files.sha256 保持批次快照不变。

验收 0 失败：shared 4 类 **21/21**（GeneralSettingsRepository、DesktopGeneralSettingsStore、NavigatorLayout、SettingsNavigatorState）；jvm-app 5 类 **39/39**（GeneralSettings/Appearance/ModelSettings/Connections/GitSettingsRoute 交互）；完整 JVM app build **Build successful, exit 0**。截图落在新目录 `docs/desktop-ui/captures/settings-page-2026-10-10/`（8 PNG + 8 几何 JSON：1440x900 与 2560x1440 × 浅深），用持久化 store 渲染，因此页面没有"仅本次运行"提示。证据位于 verification/settings-page-parity-2026-10-10/entry-s1/，含可重跑的 `measurement/measure-settings-page.py`。

边界：参考图偏好行都是可用控件，我们多数行是未接入状态并额外显示一行说明，这是如实反映本机能力，不去为了对齐而隐藏。参考图正文右侧控件（开关、下拉、按钮）的颜色与形状本批未逐项测量，只对齐了位置与整页几何。本批是几何对齐 + 目视核对，不是像素级比对，也无原生窗口组合；各批证据独立，整体目标 active。

## 2026-10-10 设置表单页框架 T：6 个设置页统一到「常规」页实测几何

参考图只有 `settings-general.png` 一张设置页截图（2560×1440，密度 1）。S 批把「常规」页对齐到内容列 728、卡片 x 1079..1806、-8 偏移、标题 28px；其余走 `SettingsFormFrame` 的设置页（外观、模型与 Provider、键盘快捷键、连接、Git、Worktrees）当时仍是旧框架：外壳已把设置内容列上限设为 728，页面自己又减左右各 16，实际列宽 696，且卡片圆角 16、填充 Surface、描边 Border、标题 24px。本批把这 6 个页面统一到「常规」页已实测的几何与卡片样式。

生产源码写集 3 个文件：`HarnessTokens.kt`（新增 `Control.SettingsPageTopPadding = 73.dp`、`SettingsCardRadius = 12.dp`、`SettingsRowInsetX = 17.dp`，与既有 `SettingsContentMaxWidth = 728.dp` 并列成为设置页几何的唯一来源）、`SettingsGeneralPage.kt`（`SettingsGeneralModel` 的 ContentWidth/CardRadius/PageTopPadding/RowInsetX 改为引用 token，取值不变，`const val` 变 `val`）、`SettingsFormComponents.kt`（`SettingsFormFrame` 改为 `BoxWithConstraints` 加满 728px 列并与「常规」页同样 -8 偏移，不足 728px 时退回 16 内缩；上内边距取 token、下内边距 Spacing.Seven；页标题 24sp → 28sp 固定 34px 行盒；内容列挂 `<testTag>-column` 几何锚点。`SettingsFormCard` 圆角 16 → 12、填充 Surface → SurfaceElevated、描边 Border → BorderSubtle、横向内边距 16 → 17）。测试写集 2 个文件：新增 `SettingsFormFrameInteractionTest.kt`（6 页 × 2 窗口 × 2 主题几何契约、600dp 窄屏回退、默认不写盘的截图用例），`ConnectionsInteractionTest.kt`（外壳截图的 `assertFits` 锚点由 728px 页面盒换成更宽的 `main-surface`，因为框架有意落在页面盒左外 8px）。

新增验收：shared 6 类 **36/36**；jvm-app 9 类 **64/65**，唯一失败是既有陈旧用例 `ShortcutsInteractionTest.ordinarySearchEditingAndOpenMenuKeepTheirOwnKeyboardBehavior`（`shortcuts-search` 把占位符暴露为 Text）；另有 10 类 Worktrees/Git 回归 **40/43**，3 个失败均为既有（`ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks` 紧凑布局缺口，以及 `GitBranchCheckoutInteractionTest` 的 2 个 `menu:1` 用例——为确认非本批回归，把 `SettingsFormComponents.kt` 临时整体还原为改动前实现后单独重跑，仍是同样 2 个失败）；`build -m shared` 与 `build -m jvm-app` 均 **Build successful, exit 0**。截图落在新目录 `docs/desktop-ui/captures/settings-form-pages-2026-10-10/`（14 PNG + 14 几何 JSON），未覆盖 R/S 批目录。证据位于 verification/settings-form-frame-parity-2026-10-10/entry-t1/，含可重跑的 `measurement/measure-settings-form-pages.py`（PROBLEMS: 0）。

实测：14 张截图（6 页 × 1440×900/2560×1440 × 浅深）一致给出列宽 728、列左 = 外壳主列左 − 8、列右 = 主列右 − 8；像素上 `appearance-2560x1440-light` 的卡片带 1079..1806（宽 728）与参考 `settings-general` 的 1079..1806（宽 728）完全一致；页标题 ink（两个 CJK 字形）参考相对内容顶 y 79..104、高 26，我们 y 80..105、高 26。

边界：参考图只覆盖「常规」页，其余 6 页没有同尺寸参考图，本批是 Harness 内部一致性而非这些页面的像素级比对。行内部仍是两套实现（`SettingsFormRow` 最小高 40 加上下各 8；「常规」页行是 61/77/93），卡片自身 8px 纵向内边距使表单卡首行距卡片顶 16px 而「常规」页为 8px，本批不改行样式。圆角/填充/描边是 token 级对齐，不是像素测量。既有 `ConnectionsInteractionTest` 的截图用例不受环境变量控制、直接写 `docs/desktop-ui/captures/connections/`，因此那组 PNG 被刷新为对齐后的框架。本批是几何对齐 + 目视核对，不是像素级比对，也无原生窗口组合；各批证据独立，整体目标 active。

## 2026-10-10 紧凑 More 入口契约 U：判定并修正唯一遗留失败用例

本批只改一个测试文件，不动生产源码。

背景：O/P/Q/T 四批都把 `ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks` 记为"既有陈旧用例"，但都没有判定到底是测试错还是外壳错。它与 `PluginsPageInteractionTest.compactMoreRouteDismissesDrawerAndExplicitOpenSurvivesRefresh` 在同一场景（600dp、More→插件）上互相矛盾，二者必有一错。

实测：临时探针在 600dp 挂载真实 `HarnessApp`（`connection = null`）并枚举 navigator/overlay tag，跑完即从工作树删除。结果是 Workspace、Settings、Scheduled、Hosts 四条路由进入即让浮动抽屉打开（`navigator-overlay` 与 `navigator-scrim` 均在），只有 Plugins 进入只暴露页面内容、`plugins-navigator` 不挂载。这不是偶然：来自 `HarnessApp.kt` 的 `dismissFloatingPluginsNavigator` 与 `LaunchedEffect(page, shellWidthPx != null)`（注释即"Expose plugin content on route entry … an explicitly opened drawer stays open"），也正是 `PluginsPageInteractionTest` 逐条覆盖的契约；`shell-width-classes.md` 对紧凑外壳的描述一致——内容之上的抽屉，由底栏入口或标题栏开关打开，而不是进入即盖住内容。

判定：`PluginsPageInteractionTest` 是更新（`main-path-and-plugins-2026-10-08`）、更具体、专门撰写的契约；`ScopeNavigationInteractionTest` 是 2026-10-07 范围批写的更早断言。Plugins 行为是有意的且有专门测试，所以陈旧预期属于 `ScopeNavigationInteractionTest`。就地改写该用例并保留原意：More→插件 断言 `plugins-search` 可见、`plugins-navigator` 不存在，再由 `bottom-nav-drawer` 开合并关闭；More→定时任务 断言 `scheduled-navigator` 可见、`scheduled-toggle-search` 展开 `scheduled-search`、`rail-overflow-menu` 不存在。

验收：定向 8 类 **43 项 41 通过 2 失败**，`compactMoreReachesPluginsAndScheduledTasks` 通过。2 个失败是既有的 `scheduled-detail-prompt` 顺序相关用例（`ScheduledPageRoutingInteractionTest` 与 `ScheduledRoutingInteractionTest`）：同一 8 类重跑仍是同样 2 个失败；把本批改动的类从集合里去掉（7 类 40 项）仍是同样 2 个失败，证明与本批无关；2 类单跑 **5/5**。本批无生产改动，故不重新生成任何截图。证据位于 `verification/compact-more-route-contract-2026-10-10/entry-u1/`，含可重跑的 `measurement/measure-compact-more-route.py`（PROBLEMS: 0）、探针源码与 5 份日志。

边界：只消解两条用例的矛盾，不改变任何页面渲染。紧凑外壳是本项目自定（桌面尺寸以下没有 Codex 参考图），Plugins（内容优先）与其余四条（进入即开抽屉）的不对称记为既定行为，而非对着参考图断言。2 个 `scheduled-detail-prompt` 失败为既有、多类连跑时顺序相关、单跑通过，本批不动，仍需单独排查。各批证据独立，整体目标 active。

## 2026-10-10 定时任务保存用例竞速 V：让两个路由用例等待落盘

U 批次暴露的两个失败（`ScheduledPageRoutingInteractionTest.harnessRouteEditsBoundTaskAndPersistsPausedConfigurationWithoutExecution`、`ScheduledRoutingInteractionTest.editingBoundTaskPersistsAcrossStoreInstancesWithoutExecutingSession`）在同一 JVM 多类连跑时必现、单跑必过，且把 U 改动的类从集合里去掉仍必现，与本批无关。

根因：两个用例点完 `scheduled-save` 就立刻断言 `scheduled-detail-prompt`。点击只是启动保存（`ScheduledManagementPage.kt`：`onSave = { coroutineScope.launch { repository.save() } }`），`ScheduledRepository.save()` 经 `store.compareAndSet` 落盘，而 `DesktopScheduledStore.compareAndSet` 是 `withContext(Dispatchers.IO)` 加 sidecar 文件锁。Compose 测试时钟不跟踪 `Dispatchers.IO`，断言与磁盘提交竞速：单跑够快就赢，多类负载下就输。保存进行中 draft 仍非空、界面停在编辑器，根本没有 detail 节点；提交时清 draft 与发布新快照在同一次状态更新里完成，所以不存在"短暂渲染旧提示词"的中间态。

改法（仅测试）：两处断言前加 `waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("scheduled-detail-prompt") and hasText(<期望提示词>)).fetchSemanticsNodes().isNotEmpty() }`，原 `assertTextEquals` 保留。保存若不落盘仍会在 10 秒超时失败，因此不会掩盖真实缺陷；其后的 `runOnIdle` 块仍校验持久化（revision 2、Paused、新提示词）与 0 次执行。

写集：`ScheduledPageRoutingInteractionTest.kt`、`ScheduledRoutingInteractionTest.kt` 两个测试文件，无生产改动。

验收：改动前 8 类 41/43、7 类 38/40（同样 2 个失败）、两类单跑 5/5；改动后 8 类 **43/43**、8 类重跑 **43/43**、7 类 **40/40**，连续三次全绿。证据位于 `verification/scheduled-detail-save-wait-2026-10-10/entry-v1/`。

边界：仅测试加固，无生产改动，页面行为/几何/截图不变；只是把此前每个批次都要解释的 2 个假失败从共享验收命令中去掉，不新增 UI 覆盖。各批证据独立，整体目标 active。

## 2026-10-10 定时任务落地页正文 W：按原版截图实测对齐模板网格与空状态

P 批次只对齐了定时任务导航器，并明确把「页面主体与模板卡片」留到后续。本批按 `scheduled-tasks.png`（密度 1、2560×1440；页面区自 x 341 起、内容顶 y 74）实测对齐正文。

改动只有一个生产文件 `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledPageLayout.kt`：

1. 模板网格外宽 720 → 738、列间距 12 → 20、行间距 12 → 20（行间距放进自己的 Column，空状态那一段仍保留 12dp 节奏）。参考实测：卡片 1 x1080..1438（359）、卡片 2 起于 1459（间距 20）、网格 1080..1817（738）。
2. 时钟 44 → 48，并把圆环画在盒子边缘（半径 size/2 − 1dp），使 ink 等于盒子；此前 44dp 盒加 3dp 内缩只画出 40px。
3. 空状态标题 16sp（titleLarge）→ 18sp/26，即 ASAR 提取出的 `--font-heading-sm`。
4. 卡片内边距 16 → 20、标题 14sp（titleSmall）→ 16sp/24（`--font-heading-xs`）、说明 12sp（bodySmall）→ 14sp/18（`--font-text-sm`）。字号取自 `reference/app-asar-geometry-evidence.md` 的类型刻度。
5. 每个模板行改为 `height(IntrinsicSize.Min)`、卡片 `fillMaxHeight()`，说明折行成两行时同排卡片仍保持等高（第 3 排：左卡 90、右卡 109，已统一）。参考网格每排等高。

实测对照（参考 vs 我们，归一到各自页面区左边界；空状态比 ink 尺寸，网格比结构）：时钟 ink 48×47 vs 48×48；空状态标题 ink 69×17 vs 70×17；说明 ink 高 13 vs 13；卡片宽 359 vs 359；列间距 20 vs 20；网格外宽 738 vs 738；行间距 20 vs 20。可重跑脚本 `measurement/measure-scheduled-landing.py` 报 **PROBLEMS: 0**。

验收：`ScheduledPageScreenshotTest` + `ScheduledPageInteractionTest` + `ScheduledManagementInteractionTest` **17/17**；再加 `ScheduledManagementScreenshotTest` **23/23**；均无失败。截图写入新目录 `docs/desktop-ui/captures/scheduled-page-2026-10-10-w1/`；P 批的 `scheduled-page-2026-10-10/` 与硬编码的 `captures/scheduled-execution/` 都未被改写（mtime 未变）。

边界：卡片高 90（说明折行时为 109）vs 参考 110 属内容驱动（参考卡片带前导图标且说明两行，我们只有标题加一行说明），脚本记为 KNOWN 而非几何缺陷，不用伪造内容去凑高。标题到说明的 ink 间距我们 19 vs 参考 12：标题块保持单一 12dp 节奏，这一对因此偏松，要对齐需要单张截图无法确定的逐对间距，作为残余如实记录。只实测了浅色 1440×900 落地页；紧凑 600×800 与深色由截图矩阵覆盖，但参考图没有对应状态。本批是几何对齐加目视核对，不是像素一致；空状态文案与模板列表按设计本就与原版不同。证据位于 `verification/scheduled-landing-parity-2026-10-10/entry-w1/`。
## 2026-10-10 既有红测与负载抖动 X：判定真实原因并修复（生产无改动）

此前 U/V/W 把两个用例记为「既有红测、未动」。本批不沿用单行结论（「menu:1 找不到」「搜索把占位符当文本」），而是用临时探针定位真实原因后修测试；探针用完全部删除。

全量 `jvm-app`（在 Git/Shortcuts 两处修复之后）：647 项、跳过 5、通过 639、失败 3，分别是 `LocalProjectsInteractionTest`、`WorktreeSessionObservationInteractionTest`、`DesktopProductionBoundaryTest` 各一项。三者在同类内单跑：LocalProjects 与 DesktopProductionBoundary 仍失败（真实缺陷），Worktree 转通过（仅全量连跑超时）。

修复：

1. 分支切换 picker 是禁用而非标签错：`pageReady` 要求 repository/status/branches/worktrees/diff 以及 `commitSnapshot` 全部脱离 Loading，用例只等了 branches 与 status；点击时 picker 仍禁用，点击是空操作，从不挂载菜单（探针实测：`checkoutAvailable=true, commitSnapshot=Loading, pickerDisabled=true, menuTags=[]`）。同一原因还让脏切换后的 `git-refresh` 处于禁用，点击无效、工作树一直脏、`canSwitchBranch` 永不满足。改为在每个控件前等待其真正可用（新增 `awaitEnabled(tag)`）。
2. 快捷键页「确认关闭」菜单是控件禁用而非缺失：`closePolicyAvailable` 默认 false，生产 `main.kt` 传 true，用例未提供 general settings 仓库；补齐与生产一致的接线后菜单可达。同用例另有 `assertTextEquals("")` 误把占位符当字段文本，改为断言 EditableText 为空（与共享 `HarnessSearchField` 用例一致）。
3. 本地项目 picker 条目按 id 打标签（`local-project-picker-item-<id>`），用例仍用项目名作标签，改为 id 标签。
4. Worktree 观察就绪在全量连跑下 10s 超时（`commitSnapshot=Loading`），该项等待放宽到 30s，断言不变。

未改动并上报：`DesktopProductionBoundaryTest` 失败是真实生产回归——`DesktopScheduledSessionEvidence.kt` 引入了禁用的 `im.hikaru.harness.llm.llm` 导入（边界测试 2026-09-12、该文件 2026-10-10 15:55，即违规晚于不变式）。共享层已按真实目录校验模型（`ScheduledRepository`/`ScheduledManagementPage`；`HarnessApp` 以 modelChoices 构建），所以这是执行准入处的第二道、且与 provider 耦合的校验；删除会改变已验收的定时执行行为，迁出又需要由可导入 llm 的模块注入中性解析器。属于需要边界所有者的设计决策，本批只记录不改。

验收：两个修复类 8/8；相关八类 46/46；三者在修复后单跑 9/10（仅边界用例仍红）；全量在 LocalProjects/Worktree 修复前为 639/647。证据位于 `verification/preexisting-red-tests-2026-10-10/entry-x1/`。

边界：只改测试；无生产改动、无截图重生成、不主张像素一致；探针已删除，其观测只记录在证据里。就绪等待仍有界（10s，提交快照门 30s），真实回归仍会失败而非静默通过。定时执行准入处的 provider 耦合与边界允许表是否调整，留给后续批次。

## 2026-10-10 Desktop 生产边界回归 Y：把模型解析移入 bundle 层（X 的上报项落地）

X 批次认定 `DesktopProductionBoundaryTest` 的失败是真实生产回归：`DesktopScheduledSessionEvidence.kt` 引入了边界禁止的 `im.hikaru.harness.llm.llm` 导入（边界测试 2026-09-12 早于该文件 2026-10-10 15:55）。本批落地修复，且不放宽边界允许表。

做法：app 模块本就依赖 `modules/bundle/desktop`，而该模块允许接触 provider 内部（`DesktopProviderSettings` 已调用 `context.llm.resolveModelInfo`）。因此把单模型查找下移到 bundle：新增 `DesktopModelCatalog`（`suspend fun requireResolvable(providerId, modelId)`，未知时抛错）与 `requireDesktopModelCatalog(context)`（先 require 本地 LLM runtime 再返回句柄）；`DesktopScheduledSessionEvidence` 去掉 llm 导入，`val llm = context.llm` 改为 `requireDesktopModelCatalog(context)`，解析调用改为 `modelCatalog.requireResolvable(...)`。

等价性：runtime 仍在同一位置被 eager require（缺少该服务的 Context 仍返回同一条通用不可用消息，例如 `LocalScheduledSessionAccessIntegrationTest` 里裸 `Runtime()` 的用例）；未知 provider/model 仍抛错并被同一 catch 映射成同一条消息；`resolveModelInfo` 为 suspend，门面方法同为 suspend。

验收：边界用例 + 定时访问/证据消费者 24/24（`productionSourceShouldDependOnProfileBundleInsteadOfProviderInternals` 转绿）；定时执行消费者 11/11；bundle 模块 29/29；全量 `jvm-app` 见证据。证据位于 `verification/desktop-boundary-fix-2026-10-10/entry-y1/`。

边界：不改边界允许表、不给 app 源码新增 provider 导入；耦合移到边界测试指定的层。属生产改动，故重跑了真实的定时执行消费者而不只是边界用例。X 的证据保留其「只记录不改」的原状，本批为后续落地。

## 2026-10-10 终端字体用例超时余量 Z：真实 IO 就绪门 10s 放宽到 30s（无生产改动）

Y 批（以及更早的 X 批）的全量 `jvm-app` 唯一未过用例是 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`：`ComposeTimeoutException: Condition still not satisfied after 10000 ms`，卡在 `PtyTerminalFontInteractionTest.kt:177` 那个等真实 PTY 回写 `size-trigger.hex`/`kernel-size` 的 `waitUntil`。同类单跑必过，全量连跑在持续高负载（Clash、mobileassetd、多个 Codex 渲染进程）下必现，是环境余量不足而非行为缺陷。

改法（仅测试）：把该文件里所有真实 IO 就绪门从 `timeoutMillis = 10_000` 放宽到 `30_000`，共 11 处——两个 `runSkikoComposeUiTest` 体内的 7 个就绪门（行 96/102/106/116/150/164/177）以及 4 个共享助手 `awaitTag`/`awaitGrid`/`awaitFittedSnapshot`/`command`（行 352/362/369/389）。与 X 批把 Worktree 观察门、Git/Shortcuts `awaitEnabled` 上界放到 30s 的做法一致。只抬上界，门后断言逐条不变，真实回归仍会在 30s 失败而非静默通过。

写集：`apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalFontInteractionTest.kt` 一个测试文件，无生产改动。

验收：改动后同类单跑 **3/3**（含此前失败的 `savedFontChangeRefitsSameRunningPtyAtFixedBounds`）；全量 `jvm-app` **647 找到、跳过 5、通过 642、失败 0**（104 容器成功、0 失败，exit 0），此前 X/Y 证据里携带的单个 PTY 字体环境抖动不再复现。`measurement/verify-timeout-margin.py` 复核文件为 11 个 30s 门、0 个 10s 门（PROBLEMS: 0）。证据位于 `verification/pty-terminal-timeout-margin-2026-10-10/entry-z1/`。

边界：只改超时值；无生产改动、无截图重生成、不主张像素一致。等待仍有界（30s），PTY 路径真坏时仍会失败。各批证据独立，整体目标 active。

## 2026-10-11 基础设置右侧控件 AA：下拉触发器、紧凑内边距与控件圆角按原版截图实测对齐

S/T 两批把设置页的列宽、卡片与行内缩对齐了，并明确把「正文右侧控件的颜色与形状」留作未做。本批按同一张 `settings-general.png`（密度 1、Display-P3 ICC，先转 sRGB 再读色）实测对齐三类行级控件。

实测（参考 vs 我们改前）：行级下拉（Default app / 自动检测 / Close shortcut only）在参考里是 28px 高、白底、1px 细描边（实测 #eeeeef）、标签加 10×5 手绘箭头的药丸，带图标那种宽 138、四字那种宽 96；我们改前是实心 `#f3f3f3` 的 Secondary 按钮，没有描边，箭头是用文本字符「⌄」拼在标签后面。行内「更改」按钮参考宽 44（两侧各约 10px 内边距），我们改前宽 58（16px 内边距）。圆角用左缘剖面拟合：参考 9.5–10.0，我们改前 8。

改法（生产 4 个文件）：`HarnessTokens.kt` 新增 `Corners.Control = 9.dp`（拟合值；asar 令牌表里 `--radius-lg` 是 8–10）、`Control.CompactPaddingX = 10.dp`、`Control.SelectChevron = 14.dp` 与 `SelectChevronScale = 1.5f`；`HarnessActionControls.kt` 的 `HarnessActionSurface` 增加可选 radius 参数（默认不变），`HarnessActionButton` 在 Compact 时用控件圆角与 10px 内边距、Regular 保持 8/16，并新增 `HarnessSelectTrigger`（28px 高、控件圆角、白底、1px `BorderSubtle` 描边、标签加手绘箭头，语义与 testTag 契约与原按钮一致）；`SettingsFormComponents.kt` 的 `SettingsFormSelect` 改用该触发器；`SettingsGeneralPage.kt` 的关闭策略下拉与两个未接线下拉行改用它，标签里不再拼「⌄」。

实测对照（可重跑脚本 `measurement/measure-controls.py`，输出 `measurement/control-measurements.txt`）：「更改」按钮 参考 w44/h28/半径 9.50 → 我们 46/28/9.75；行级下拉 参考 w138 与 96、h28、半径 10.00、白底、1px 描边、手绘箭头 → 我们 124/28、白底、1px 描边、10×5 手绘箭头；开关 参考 32×20 全圆 → 我们 32×19（未改）。

验收：`ui-foundation` 模块 **53/53**；覆盖这些共享控件的 14 个 jvm-app 类 **127/127**；设置截图类 **6/6**；全量 `jvm-app` 见证据；build 通过。新截图落在 `docs/desktop-ui/captures/settings-controls-2026-10-11/`（1440×900 与 2560×1440 × 浅深），S 批截图目录未被覆盖。证据位于 `verification/settings-control-geometry-2026-10-11/entry-aa1/`。

边界：三项残余如实记录而非掩盖——「更改」按钮我们 46 对参考 44，两者标签 ink 为 22 对 24 且内边距同为 10，差在 UI 字体的 CJK 步进；下拉描边我们 #f5f5f5 对参考 #eeedee，同一 7 值差也出现在设置卡片描边与行分隔线上（S 批只对了几何位置没对颜色），我们的 `BorderSubtle` 是 `--alpha-05` 而 `--alpha-10` 又偏深 4，判不准参考用的是哪个 asar 令牌，故共享描边令牌不动、只如实上报；开关高 19 对参考 20，令牌注释引的是 app-shared CSS 的 32×19，CSS 与渲染差 1px，令牌不改。参考图只覆盖「常规」页，所以紧凑圆角与内边距会作用到全 app 的紧凑按钮（插件/Git/Worktrees/对话框），这些页没有参考图；Regular 尺寸按钮几何未动。本批是几何与结构对齐，不是像素一致，也无原生窗口组合；各批证据独立，整体目标 active。

## 2026-10-11 终端页面用例超时余量 AB：同类真实 IO 门 10s 放宽到 30s（无生产改动）

AA 批的全量 `jvm-app` 唯一失败是 `PtyTerminalPageInteractionTest.focusedTypingPasteKeysAndResizeUseRealController`：`ComposeTimeoutException: Condition still not satisfied after 10000 ms`，卡在 `PtyTerminalPageInteractionTest.kt:122` 那个等真实 PTY 回写 `size-trigger.hex`/`kernel-size` 的 `waitUntil`——与 Z 批在同类 `PtyTerminalFontInteractionTest` 修的是同一个门。该类单跑通过，说明是满载下的余量问题而非行为缺陷；该用例不渲染任何设置页，与 AA 的生产改动无关。

改法（仅测试）：把该文件所有真实 IO 就绪门从 `timeoutMillis = 10_000` 放宽到 `30_000`，共 23 处，沿用 Z 批先例。只抬上界、门后断言不变，真实回归仍会在 30s 失败而非静默通过。

验收：改动前该类单跑 **7/7**（即 AA1 证据里的隔离日志）；改动后单跑 **7/7**、0 失败；`measurement/verify-timeout-margin.py` 复核 23 个 30s 门、0 个 10s 门（PROBLEMS: 0）。本批未重跑全量：AA 的全量日志早于本批且已隔离出同一个门，且本批只是抬等待上界、断言未变。证据位于 `verification/pty-page-timeout-margin-2026-10-11/entry-ab1/`。

边界：只改超时值；无生产改动、无截图重生成、不主张像素一致。等待仍有界（30s），PTY 路径真坏时仍会失败；本批只是放宽既有的环境边际门，不新增覆盖。各批证据独立，整体目标 active。

## 2026-10-11 设置页细节描边 AC：浅色 BorderSubtle 由 245 收敛到 238（生产改动）

AA 之后仍留一个跨页面残留：设置卡片外框、行分隔线、控件描边在原版截图里实测 `#efeeef`（238），我们是 `#f5f5f5`（245），差 7；三处共用同一个 `BorderSubtle = Color(0x147F7F7F)`。

参考图带 Display-P3 ICC。实测该三面在原始 P3 下都是 `#eeedee`，perceptual / relative / absolute 三种意图转 sRGB 后都是 `#efeeef`/`#eeeeef`，中性灰在该转换下不偏移，所以 238–239 是真实目标而不是转换伪影。当前安装版 app.asar（SHA-256 `797be85c…c360`、561986433 字节；与 AA 记录的基线不是同一 SHA，即 asar 已更新）里 `--color-border-subtle:var(--lightningcss-light,var(--alpha-05))`，而 `--alpha-base` 在浅色是 `#0d0d0d`（与我们的 `Text` 同值）、`--alpha-06:color-mix(in oklab, var(--alpha-base) 6%, transparent)`：原版按近黑 5–6% 淡染，不是 gray-127 的 8%。按 `brightness = 255 - (255 - base) * alpha`：gray-127 @8.0% → 245（旧），gray-127 @13.3% → 238（新），参考 → 238。

改法（生产 1 个常量）：浅色 `BorderSubtle` 由 `0x147F7F7F` 改为 `0x227F7F7F`。深色令牌与自定义主题路径不动。

实测对照（`measurement/measure-borders.py`，输出 `border-measurements.txt`）：卡片顶边 / 行分隔线 ×2 / 卡片左边 / 控件描边 参考均 238–239、本批后均 `#eeeeee`（Δ≤1，改前 Δ7）；轨道端发丝线 参考 `#e5e5e5`（229）、本批后 `#e8e8e8`（232，改前 239）。同一令牌在卡片（白底）复合成 245、在轨道端边（`#f9f9f9` 底）复合成 239，是 alpha 合成随底色变化；原版轨道端边 229 更接近更强的令牌（`AppShell.kt` 注释也写 Codex 用 `border-e-hairline border-default`），故本批不拆令牌，只按更深的共享强度收口。

圆角 9 vs 10：直接对照同型控件后维持 9。我们的行级下拉标称圆角 9 拟合 10.00–10.25，参考同型下拉也拟合 10.00、左缘剖面逐行一致；同一拟合在已知标称值上偏高约 1（我们的开关标称 9.5 也拟合 10.00）。故参考真实圆角 ≈9，`Corners.Control = 9.dp` 不变，asar 的 `--radius-lg:10px` 不适用于 28px 行级控件。

开关高度 19 vs 20：AA 记的残留是拿参考 ON（实心 32×20）对我们 OFF/禁用（浅底描边）比，口径不同。同口径复核：参考 8 个开启开关全 32×20，帽剖面与「高 20、半径 10」自洽（x=1773 自 y292 到 y311 恰 20 行满色、上下邻行纯白）；我们布局盒 32×19、像素 19 行；当前 app.asar 令牌 switch-track-width:32px / switch-track-height:19px / switch-thumb-offset:3px 与我们的 HarnessToggleGeometry（32/19/3/13）逐项一致。故我们的值忠于当前上游令牌，参考的 20 属上游版本差异；本批维持 19 并记录分歧，不按参考像素改（开关是全应用共享控件且会与上游令牌相悖）。

验收：设置页捕获类 6/6（新截图 `captures/settings-border-token-2026-10-11/`，8 PNG+JSON）；`ui-foundation` 53/53；全量 `jvm-app` **647 找到 / 跳过 5 / 通过 642 / 失败 0**（exit 0，104/105 容器成功）。该次全量同时覆盖 AA、AB、AC（AB 此前无全量）。重跑 measure-borders.py PROBLEMS 0。证据位于 `verification/settings-border-token-2026-10-11/entry-ac1/`。

边界：生产改动只有 1 个常量；该令牌全仓 29 处使用（菜单分隔线、终端分隔线、标题栏内衬等），本次只对设置页三面做了参考对照，其余面未逐面验收（方向与原版语义令牌一致，更深）。轨道端边未拆令牌（参考 229 / 我们 232），轨道底色本身也不符（`#f9f9f9` vs `#fcfcfc`，疑似窗口 vibrancy），无原生窗口截图权限无法继续。自定义主题路径 `lerp(canvas, ink, 0.05f)` 未同步；深色令牌未实测。AA 的紧凑按钮宽 46 vs 44（字体 advance）本批未动。各批证据独立，整体目标 active。

## 2026-10-11 设置页下拉触发器 AD：内容内缩与手绘箭头按参考实测收口（生产改动）

AA 建立了 `HarnessSelectTrigger`，但它的内容内缩用了 `Spacing.Three`（12），而同一条注释写「inset … about 16px a side」；此外注释里的「radius-6」与同批实测（AC 已复核为 9）矛盾。本批按同一张 `settings-general.png` 把这两处收口。

实测（参考图 Display-P3 先转 sRGB）：行级下拉 "Automatic"（盒 w96）标签 ink 左内缩 15、箭头 ink 10×5、箭头右内缩 15；"Default app"（盒 w138）前导图标 ink 左内缩 16、标签 ink 左内缩 37、箭头 10×5 右内缩 15。我们改前：标签 12、箭头 12×6、箭头右 13。

推导：参考的三种 ink 内缩是图标 16 / 标签 15 / 箭头右 15；`HarnessIcon` 与 `HarnessText` 在各自布局盒内还带约 1–2px 侧向 bearing，所以能同时复现这三者的布局内缩是 14。同一 ChevronDown 路径只占盒面 46%，14dp 盒在 `glyphScale` 1.5 画 12×6、1.25 画参考的 10×5。

改法（生产 2 个文件）：`HarnessTokens.kt` 新增实测令牌 `Control.SelectPaddingX = 14.dp`、`SelectChevronScale` 1.5f → 1.25f；`HarnessActionControls.kt` 触发器内容内缩改用 `SelectPaddingX`，并更正触发器文档注释（radius-6 → radius-9，16 → 14）。

实测对照（`measurement/measure-select-trigger.py`，输出 `select-trigger-measurements.txt`）：标签 ink 左内缩 参考 15 / 改前 12 / 内缩 16 得 16 / 内缩 14 得 **14**；箭头 ink 参考 10×5 / 改前 12×6 / 内缩 16 得 10×6 / 内缩 14 得 **10×6**；箭头 ink 右内缩 参考 15 / 改前 13 / 内缩 16 得 18 / 内缩 14 得 **16**。内缩 16 与 12 的三项 |Δ| 合计同为 4，14 压到 1/0/1，故取 14 并固化为令牌。

验收：设置页捕获类 6/6；`ui-foundation` 53/53；全量 `jvm-app` **647 找到 / 跳过 5 / 通过 642 / 失败 0**（exit 0）。证据位于 `verification/settings-select-inset-2026-10-11/entry-ad1/`。


## 2026-10-11 定时任务模板卡虚线描边 AE：虚线外框、22dp 圆角与标题 ink 间隙按原版截图实测对齐（生产改动）

W 批把落地模板栅格几何对齐了（卡 359 宽、gutter 20、grid 738），但把卡外观记为「内容驱动」而没做描边本身。本批按 `scheduled-tasks.png`（密度 1、Display-P3 ICC，先转 sRGB 再读）收口两处外观：参考的模板卡是 **1px 虚线**外框而不是实心发丝线，且角比我们共享的 8px 圆角明显更圆；顺带修掉落地标题块比参考松 8px 的行距。

实测（参考 vs 我们改前）：卡盒都是 359 宽；参考顶行 dark 196/367（`####.####..###..###…`）是虚线，我们 267/367 是实线；参考顶边角内缩 18、左边 19/18，我们 13。落地标题块：参考 时钟 48 → 16 → 标题 17 → **11** → 副标题 13，我们改前最后一段是 **19**（外层 12dp rhythm 的 lineHeight 余量）。

改法（生产 3 个文件）：`HarnessComponents.kt` 的 `HarnessSurface` 新增 `dashed: Boolean = false` 与 `radius: Dp = Corners.Medium`——实心分支（`Surface` + `BorderStroke`）逐行不变，虚线分支用 `Box.clip(shape).background(fill).drawBehind` 画 1px 3-on/2-off 的 `Path` 圆角矩形描边并补 `CompositionLocalProvider(LocalContentColor provides Text)`；`HarnessTokens.kt` 新增实测令牌 `Corners.Card = 22.dp`；`ScheduledPageLayout.kt` 模板卡改用 `dashed = true, radius = Corners.Card`，落地标题块包进 `spacedBy(Spacing.One)`。

圆角怎么定的：把角弧按「与两条边相切」约束拟合 `(x-r)²+(y-r)²=r²`，只取落在弧上的点、取各点正根的中位数，**对虚线相位不敏感**（相位只删样本、不改 min-x）。参考两个角读到 22.94 / 22.21（中位 22.6），我们 22dp 的路径读到 21.94，相差 <1px，落在该方法的 <1px 分辨率内；22dp 也等于 asar 记录的 `--composer-radius: 22px`，故取 22（顶边 dark 宽度代理法相位敏感，读出的 18 vs 17 与之一致）。

验收：定时页捕获类 `ScheduledPageScreenshotTest` **3/3**（288 文件落在 `captures/scheduled-card-outline-2026-10-11/`）；`ui-foundation` 模块 **53/53**；全量 `jvm-app` **647 found / 5 skipped / 642 started / 641 passed / 1 failed**（exit 1）。唯一失败是 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 在 30s 门超时——Z / AB 记录过的同类真实 PTY 环境余量抖动，该用例不渲染定时页；隔离重跑该类 **3/3** 通过（exit 0）。证据位于 `verification/scheduled-card-outline-2026-10-11/entry-ae1/`。

边界：参考卡内容驱动更高（110 vs 66，带前导 emoji 图标 + 两行描述，我们无图标、单行；`HarnessIconName` 无对应字形，前导图标本批不做），副标题文案也不同，我们另多两行——都是内容差异不是描边缺陷。顶行 dark 比例 259/367 vs 196/367 是 Skia 与 Chromium 对同标称 3-on/2-off 描边的抗锯齿差，不按它反推改标称 dash。`HarnessSurface` 加参数后旧 scheduled 捕获目录（`scheduled-page-2026-10-10/`、`-w1/`、`scheduled-execution/`）相对当前代码过期，新目录取代它们。本批是几何/结构对齐不是像素一致，无原生窗口组合（本机无屏幕录制权限）。各批证据独立，整体目标 active。

## 2026-10-11 本地插件 inline Apps 消费 AF：详情页接入真实名称与清单 schema 迁移（生产改动）

插件详情页已经有 Apps 区域，但本地服务始终返回「Apps 尚未接入」，即使 manifest 已声明 Apps；导入器也把 manifest 的 `apps` 字段当作未支持能力。本批先把可以安全枚举的 inline Apps 名称接通。

改法（生产 2 个文件）：`DesktopUserPlugins` 的 `DesktopUserPlugin` 增加受限 `applications` 名称列表；`.codex-plugin/plugin.json` 的 `apps` 数组读取字符串名，`apps` 对象只读取键名，不保存值；名称限制为最多 64 项、单名 256 字节。清单写入 schema 2，schema 1 读取时迁移为空列表，下一次 mutation 生成 schema 2。目录型 `apps/`、`.app.json`、MCP、hooks、scripts 和其他配置仍列为未支持资源，不复制正文也不执行脚本。`DesktopInstalledPluginRepository` 详情读取改为消费该列表；没有 Apps 时返回 `Available(emptyList())`。

验收：`DesktopUserPluginsTest` 22/22；`DesktopInstalledPluginRepositoryTest` 11/11；`PluginManagementInteractionTest` 3/3；`PluginsPageInteractionTest` 9/9；`PluginLocalUpdateInteractionTest` 10/10；`PluginsPageScreenshotTest` 4/4；`PluginsScreenshotTest` 15/15。全量 `jvm-app` 为 647 found / 5 skipped / 642 started / 641 passed / 1 failed，唯一失败仍是 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 的 30s PTY 就绪门；本批结束后两次隔离重跑同样各为 2/3 passed / 1 failed，未出现插件相关失败。focused 证据与完整日志位于 `verification/plugin-apps-inline-2026-10-11/entry-af1/`。

边界：本批只消费 manifest 内可枚举的 Apps 名称。MCP server 配置、hooks、scripts、文件型 app 包和授权/连接生命周期仍需独立适配器；市场发现、云端项目和 Codex 云端环境继续排除。本批是行为验收，没有原版同状态像素一致性声明。

## 2026-10-11 线程标题栏死按钮清理 AG：移除内置分享空处理器（生产改动）

扫描生产 UI 后发现 `DesktopClientCatalog` 向 `ThreadTitlebarActions` 注入了 `desktop-client-share`，渲染中文「分享」按钮，但 `onClick = {}` 没有任何服务。该按钮属于原型残留，会让视觉页面看起来已支持分享而实际没有动作。

改法：保留 `ThreadTitlebarActions` slot 供真实插件使用，移除内置 catalog 的 `desktop-client-share` 注入；更新 `ClientUiCatalogTest`，明确内置 catalog 不再注册这个死按钮。

验收：`ClientUiCatalogTest` 1/1；`ScheduledPageScreenshotTest` 3/3，jvm-app 重新编译并运行通过。证据位于 `verification/shell-noop-share-2026-10-11/entry-ag1/`。

边界：工作区菜单、通知、附件、Agent 和 Model 仍有独立服务缺口，后续按真实服务逐项接入；本批只清理确认的标题栏空处理器。

## 2026-10-11 工作区选择器 Host 路由 AH：接入现有 Hosts 页面并移除通知死按钮（生产改动）

生产侧边栏的工作区选择器原来使用空 `onClick`，通知 Bell 也没有通知服务或页面。本批把选择器接到已有 `PageId.Hosts` 路由，并移除通知死按钮。

改法：`AppShell` 新增 `onOpenHosts` 回调并沿 `WorkspaceNavigator` / `SidebarHeader` 传递；`HarnessApp` 绑定到 `selectPage(PageId.Hosts)`。工作区选择器点击后现在进入 Host 页面，侧边栏不再暴露没有处理器的通知按钮。

验收：`PageShellSpecTest` 7/7；`ScreenshotTest` 15/15；证据位于 `verification/workspace-header-host-route-2026-10-11/entry-ah1/`。

边界：附件、Agent、Model 等旧版测试 composer 仍有独立能力边界；生产 `LiveConversationComposer` 已将附件和权限显示为禁用语义，并使用真实模型菜单。

## 2026-10-11 本地插件能力清单 AI：MCP / Hooks / Scripts 名称只读接入（生产改动）

本地插件详情此前只显示「未支持的资源」，即使 manifest 已声明 MCP server、hooks 或 scripts，也无法看到真实名称。本批接入安全的只读能力清单：读取 manifest 中对象键或字符串数组的名称，保存名称和数量；配置正文、凭据、脚本正文和执行入口不进入 Harness 清单。

改法（生产 4 个文件）：`DesktopUserPlugins` 新增 `DesktopPluginCapabilities`，支持 `mcpServers`/`mcp`、`hooks`、`scripts` 的名称解析；清单 schema 升为 3，schema 1/2 读取时迁移空能力列表；`PluginModels` 新增 `PluginCapabilitySummary`；本地 repository 将能力传给详情页；详情页显示 MCP Servers、Hooks、Scripts 的名称和数量，并明确配置与执行服务尚未接入。目录型 `.mcp.json`、hooks、scripts 和无法枚举的值继续标记为未支持。

验收：`DesktopUserPluginsTest` 22/22；`DesktopInstalledPluginRepositoryTest` 11/11（三类能力名称真实 fixture）；`PluginManagementInteractionTest` 3/3；`PluginsPageInteractionTest` 9/9；`PluginsPageScreenshotTest` 4/4；`PluginsScreenshotTest` 15/15；全量 `jvm-app` 647 found / 5 skipped / 642 started / 642 passed / 0 failed，exit 0。证据位于 `verification/plugin-capability-inventory-2026-10-11/entry-ai1/`，8 个证据文件校验通过。

边界：本批完成本地 manifest 能力清单，不宣称 MCP 连接、授权、hook 配置写入、脚本执行、文件型 Apps 或远端插件服务已实现；市场发现、云端项目和 Codex 云端环境继续排除。

## 2026-10-11 定时任务执行模型披露与 Unknown 运行完整处理 AJ：UI 明示「只在应用运行期间执行」并补齐阻塞计数说明（生产改动）

执行契约（`reverse-engineering/scheduled-implementation-contract-2026-10-07.md` §7.2）规定：本机没有 OS daemon 时，关闭应用即停止本机调度，且必须在 UI 明示「只在应用运行期间执行」，不得承诺退出后照常运行。readiness 门槛 7 还要求这一点的**完整用户处理与视觉验收**。检查当时代码：全仓没有任何执行模型披露文案；详情页对阻塞运行只有一句「任务有在途或 Unknown 运行；不能启用或重新投递。」，没有条数与原因。

本批只补 UI 缺口，不改执行器、不改调度语义、不加后台进程。生产 2 个文件：`ScheduledModels.kt` 新增 `ScheduledLocalExecutionModel`（`本机定时任务只在本应用运行期间执行；关闭应用后不再运行，也没有系统后台守护进程替代执行。`）；`ScheduledManagementPage.kt` 在页面顶部常驻一行 `scheduled-execution-model`（落地/详情/编辑器都可见），并把详情页阻塞段扩成 `scheduled-blocked-runs`——按真实账本统计「进行中 N 条，结果未知 M 条」，写清「结果未知的运行没有精确终态证据，不会自动重新投递；请先打开对应会话人工核对」。

实测（`measurement/measure-execution-model.py`，bundled Python + Pillow）：披露行所在坐标带在批次 AE 旧捕获里是 **0** 明暗墨迹像素，本批 **2495**（bbox y11..22），节点 bounds x268..1420、y8..25；Unknown 详情 `scheduled-blocked-runs` 墨迹 **5392**、三行。`PROBLEMS: 0`。

验收：`ScheduledPageScreenshotTest` **3/3**（288 文件落在 `captures/scheduled-execution-model-2026-10-11/`）；`ScheduledPageInteractionTest` **12/12**；`ScheduledManagementInteractionTest` + `ScheduledPageRoutingInteractionTest` + `ScheduledRoutingInteractionTest` + `ScheduledPickerMigrationInteractionTest` **21/21**；`ScheduledCronPageIntegrationTest` + `ScheduledManagementScreenshotTest` **12/12**；全量 `jvm-app` **647 found / 5 skipped / 642 started / 642 passed / 0 failed**（exit 0）。证据位于 `verification/scheduled-execution-model-2026-10-11/entry-aj1/`。

边界：离屏证据，无原生窗口组合，不主张与原版同状态像素一致；披露文案是依据本仓契约的设计决定，非逆向原版字符串。没有新增任何退出后后台执行机制——「应用退出后继续后台执行」仍属未做；不可恢复 Unknown 仍没有应用内确认/清除通道，本批只把状态与后果说清。`captures/scheduled-execution/`（未被 git 跟踪）在全量运行后按 AE 先例还原到批次前字节（组合 sha `a842ee6a…`）。各批证据独立，整体目标 active。

## 2026-10-11 本地插件文件型 Apps AK：manifest 字符串路径的 Apps 名称只读接入（生产改动）

AF 让详情页消费 manifest **inline** 的 Apps 名称，AI 追加了 MCP/hooks/scripts 名称，但 Apps 的**文件型**声明一律进 `apps (unsupported)`。逆向契约（`reverse-engineering/plugins-package-consumption-contract-2026-10-07.md` §1 表 / §3.3）已记录一个可安全枚举的观测形态：manifest 的 `apps` 可以是**字符串路径**（实测 `./.app.json`），其顶层 `apps` object 的 **key 就是 App ID**（`openai-templates → default_templates`、`spreadsheets → connected_documents`），value 内含 `id`/`required` 等配置。

本批只接入这一种有实测证据的形态。生产 2 个文件：`DesktopUserPlugins.kt` 的 `apps` 分支新增字符串路径处理——用既有 `relativeDirectory` 校验后于 source 内解析，`readAppsFile` 只读顶层 `apps` object 的**键**并并入 `applications`（沿用排序/`MAX_APPS`/`checkText`），记录为“已消费”后 `ASSET_PATHS` 不再为同一文件追加 `apps (unsupported)`；缺失/非普通文件/非 JSON/无 `apps` object/超限一律回落描述符，不猜也不部分读取。新增 `MAX_APPS_FILE = 64 KiB`。`DesktopInstalledPluginRepository.kt` 更新 Apps 不可用文案与注释（`.app.json` 现在会读）。

安全边界：只保留名称；端点、密钥、配置正文不进入清单；路径逃逸被拒；不连接、不鉴权、不执行任何 App；`apps/` 目录型无观测约定，仍 unsupported。

验收：`desktop` 模块 `DesktopUserPluginsTest` **24/24**（本批前 22，新增 2 个：路径形态名称排序且无 unsupported 描述符；缺失/非 JSON/无 apps object/目录/逃逸路径五种回落且不读 root 外文件）；`jvm-app` 定向 `DesktopInstalledPluginRepositoryTest` + `PluginsPageInteractionTest` + `PluginManagementInteractionTest` + `PluginsPageScreenshotTest` + `PluginLocalUpdateInteractionTest` **38/38**（含新增文件型 Apps 到详情页 Available 名称的端到端断言）；全量 `jvm-app` **648 found / 5 skipped / 643 started / 643 passed / 0 failed**（exit 0）。证据位于 `verification/plugin-file-apps-2026-10-11/entry-ak1/`。

边界：只读名称，不证明 tool inventory、已授权或已连接；无 UI 新增元素（仅 fallback 文案），故无截图、不主张像素；MCP 真实连接/授权、hooks 配置写入与启停、scripts 执行生命周期仍未接。各批证据独立，整体目标 active。

## 2026-10-11 插件页原生窗口组合 AL：插件清单页在真实 macOS 窗口的 dark/light/system 捕获（证据批次，无生产改动）

原生验收文档此前只有 Settings 页的真实窗口捕获（`c2_1-settings-native-*`，2026-10-07），插件清单页只有离屏 Compose 捕获，计划里一直挂着「原生窗口待验收」。本批在已登录 GUI 会话下，对 `--destination plugins` 以 `1440x900` 抓取三种主题的真实窗口。

捕获：主机外观为 **Light**（`defaults read -g AppleInterfaceStyle` 键缺失），显示 2560x1440。窗口 id 5658（dark）/5668（light）/5678（system），CG bounds 均 `x=48,y=78,w=1440,h=900`，PNG 均 1440x900 RGBA。`light` 与 `system` 字节相同（sha `c205d15d…`），`dark` 不同（sha `97082a6d…`）。

实测（`measurement/measure-plugins-native.py`，bundled Python + Pillow，`PROBLEMS 0`）：三张都检出红黄绿交通灯簇（352 彩色像素，bbox `x8..59,y8..19`）与 44px 标题分隔行，证明是真实窗口捕获；rail/分隔/navigator/边界列在 y=400 扫描下均为 `51, 52, 340, 341`，与 Settings 验收捕获完全相同，说明共享 shell 几何跨页一致；dark 内容模态面色 `(44,44,44)`（暗色确实生效），light/system 为 `(255,255,255)`；三张的内容区（深色 6594 / 浅色 7010 墨迹像素）与 navigator 列（3370 / 3532 墨迹，bbox `(69,104,304,270)`）都有内容，说明页面真的渲染而非空壳。

验收：三次捕获脚本 exit 0；几何检查 `PROBLEMS 0`。本批无生产改动，故不重跑测试套件、不宣称测试通过。证据位于 `verification/plugins-native-window-2026-10-11/entry-al1/`（注：捕获脚本的临时 app 日志被自身 trap 清除，`logs/capture.log` 记录的是运行 stdout）。

边界：只证明插件页在真实窗口 `1440x900` 下渲染；无同状态原版捕获可 diff，故不主张与原版像素一致（与 Settings 验收同一限制）；仅抓了 `1440x900`，其余尺寸仍是离屏证据。各批证据独立，整体目标 active。

## 2026-10-11 本地插件文件型 MCP AM：manifest 字符串路径的 MCP server 名称只读接入（生产改动）

AI 让详情页消费 manifest **inline** 的 MCP/hooks/scripts 名称，但 MCP 的**文件型**声明（逆向契约 §1 表 `mcpServers` 行 / §3.3：manifest 写字符串 `./.mcp.json`，其顶层 `mcpServers` object 的 key 即 server ID）仍一律进 `MCP (unsupported)`，server 名称不可见。本批只接入这一种有实测证据的形态，复用 AK 引入的受限读取。

改法（生产 1 个文件）：`DesktopUserPlugins.kt` 的 MCP 分支不再走 `capabilityNames`，改为与 apps 同构——inline object/array 语义不变，字符串路径经 `relativeDirectory` 校验后在 source 内解析，由新增 `readMcpFile` → 泛化的 `readNamesFile(path, "mcpServers", MAX_CAPABILITIES)` 只读顶层 `mcpServers` object 的 key；缺失/非普通文件/非 JSON/无该 object/逃逸路径一律回落 `MCP (unsupported)`。已消费的声明文件从 `ASSET_PATHS` 跳过，不再重复标 unsupported；私有常量 `MAX_APPS_FILE` 重命名为 `MAX_DECLARATION_FILE`（同为 64 KiB）。

安全边界：只保留 server ID；`command`/`args`/`env`/端点/凭据不进入快照，不连接、不授权、不启动进程。无 UI、无 schema 改动（`capabilities` 仍为 schema 3），名称经既有 `PluginCapabilitySummary` 行显示。

验收：`desktop` 模块 `DesktopUserPluginsTest` **27/27**（本批前 24，新增 3：路径形态 server 名排序且无 unsupported 描述符；`mcp` 别名；缺失/非 JSON/无 mcpServers/目录/逃逸五种回落且不读 root 外文件）；`jvm-app` 定向 5 类 **39/39**（含新增文件型 MCP 到详情页 MCP Servers 名称的端到端断言）；全量 `jvm-app` **649 found / 5 skipped / 644 started / 644 passed / 0 failed**（exit 0）。首次全量仅 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 因 30s Compose 超时（已知负载抖动，单独重跑 3/3 通过），第二次全量干净。证据位于 `verification/plugin-file-mcp-2026-10-11/entry-am1/`。

边界：只读名称，不证明已连接、已授权或工具清单；hooks/scripts 无观测文件形态，仍为描述符；`captures/scheduled-execution/`（未跟踪）全量后已按 AE 先例还原到批次前字节（`diff -r` 与备份一致）。各批证据独立，整体目标 active。

## 2026-10-11 插件详情「未支持的资源」本地化与排序 AN：内部声明描述符映射为用户文案（生产改动）

AI/AM 已让详情页显示 Apps 与 MCP/hooks/scripts 名称，但解析器内部的声明描述符（`apps (unsupported)`、`MCP (unsupported)`、`hooks (unsupported)`、`scripts (unsupported)`、`skill attachments (unsupported)`、`other capabilities (unsupported)`）仍原样打印在中文界面里，且「未支持的资源」列表排在 Skills/Apps/能力 section 之前，读起来像主内容而非补充说明。

改法（生产 1 个文件）：`InstalledPluginsPage.kt` 新增 `unsupportedAssetLabel` 把 6 个规范 token 映射为中文说明，未识别描述符原样透传（不隐藏任何信息、不发明内容）；该 block 从 Apps section 之前移到能力 section 之后，成为对上方具体 section 的限定说明；仅当 `apps` 为 `Unavailable`（此时仓库已把 `apps (unsupported)` 渲染为中文不可用理由）才过滤掉该描述符，避免重复且混语的提示，其余描述符照常显示。无解析器/schema/仓库改动，不新增能力主张。

验收：`shared` 模块 **453/453**；`jvm-app` 定向插件 6 类 **45/45**（较 AM 的 39/39 新增 `PluginDetailUnsupportedLabelTest`）；全量 `jvm-app` **650 found / 5 skipped / 645 started / 645 passed / 0 failed**（exit 0）。证据位于 `verification/plugin-unsupported-labels-2026-10-11/entry-an1/`。

边界：仅文案与排序；不接入 MCP 连接/授权、hooks 配置写入、scripts 执行、目录型 apps、附件，这些仍是描述符；`desktop` 模块本批无改动故不重跑；截图套件随全量重跑并重新生成未跟踪的 `captures/plugins-page-2026-10-08/`（fixture 的未知描述符仍原样渲染，透传路径仍被覆盖）；`captures/scheduled-execution/` 全量后已按先例还原到批次前字节（`diff -r` 一致，合并 sha `ac8ebb54…`）。无像素一致主张；各批证据独立，整体目标 active。

## 2026-10-11 Git 提交「包含未暂存变更」AO：真实暂存步与提交后校验（生产改动）

原版提交弹窗含 Include unstaged changes（`git-action-include-unstaged-changes`，mutation 发送 `includeUnstaged`），worker 在该值为真时先跑 stage helper（fallback `add -A`）再 `commit -m`（C02/C03/C04；现网 bundle 仍可见 `includeUnstaged:!0`）。C1 明确收缩为「只提交已有 index」，该复选框一直无消费者。

改法（生产 4 个文件）：`GitCommitRequest` 增 `includeUnstaged`；`GitCommitReceipt` 增 `stagedByRequest` 与 `stagedIndexDigest`（提交 tree 必须等于本次提交所依据的 index 摘要；未勾选时仍必须等于已接受快照摘要）；`GitState.includeUnstaged` 与 `commitDisabledReason`（index 为空但工作树有变更时，勾选后才可提交）；`GitController.setIncludeUnstaged` 只读切换并把该值写入请求；`DesktopGitBackend.commit` 在勾选时于 dispatch 前跑一次 `git add -A -- .`，并以暂存后的 index 作为提交与校验基线；`GitPages` 提交弹窗新增真实复选框、状态提示与提交按钮标签。

安全顺序：暂存前先以已接受快照复核 index（防止覆盖竞争暂存，测试断言 index 字节不变）；暂存步在 dispatch 之前，失败只判 Rejected、控制器重读真实 index，不谎称 Unknown；暂存后 index 为空则拒绝且不调用 `git commit`；固定 hooks/signing 策略与无 amend/empty/push 不变。

验收：`shared` 模块 **455/455**（本批前 453，新增 2）；`jvm-app` 定向 Git 5 类 **32/32**（DesktopGitCommitTest 10（+3）、DesktopGitCommitProcessTest 6、GitCommitInteractionTest 3（+1）、GitPageInteractionTest、GitSettingsRouteInteractionTest）；全量 `jvm-app` 首次 **654 found / 5 skipped / 649 started / 648 passed / 1 failed**（唯一失败为 Z/AB/AE/AF/AM 记录过的 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 真实 PTY 就绪门 30s 超时，与本批改动无关，隔离重跑 2/3 亦命中同类抖动），第二次全量 **654 found / 5 skipped / 649 started / 649 passed / 0 failed**（exit 0）。证据位于 `verification/git-include-unstaged-2026-10-11/entry-ao1/`。

边界：不做按路径 stage/unstage、不单独区分 includeUntracked、不 push、不生成提交消息、不做归属 trailer；不主张原版弹窗视觉与默认勾选一致（原版默认勾选，本批默认未勾选，属产品决定）；`git add -A -- .` 作用于工作树根，ignored 不进入 index。各批证据独立，整体目标 active。

## 2026-10-11 Git 新建分支并切换 AP：真实 create-and-checkout 新能力（生产改动）

ASAR 证据一直记录着真实 mutation `git-create-branch → create-branch{branch,cwd,failIfExists,mode,startPoint}`（G03/G07/G10），G01 的 `createAndCheckoutDisabled = hasNoCommits || disabled` 说明它是「新建并切换」入口；但本仓自「已有分支切换」批次起就把「新分支」列为未做，页面也只有切换与提交。本批补上这一有完整证据的形态：在真实 HEAD 或真实已有本地分支上创建并切换一个新本地分支，`failIfExists` 固定为真。

改法（生产 4 个文件）：`GitModels` 新增 `GitCreateBranchRequest/Receipt/Result`、`GitBranchCreation`、`GitBranchCreator` 与共享名称规则 `validLocalBranchName`，`GitState` 增加 `branchCreate*` 与 `branchCreateDisabledReason`/`canCreateBranch`，并让切换/创建/提交/新建分支的禁用原因互相包含彼此的 busy/rescan 门；`GitController` 新增只读草稿 `setNewBranchName`/`setNewBranchStart` 与 `createBranch()`，与切换/创建/提交共用同一 admission 槽，回执要求新 ref 为 current 且 HEAD/状态/工作树一致，未知结果进入独立 `uncertainBranchCreates` rescan 门（需状态+分支+工作树三类新读取一致才解除，`worktrees` 单独成功不解除）；`DesktopGitBackend` 在 `git switch --no-guess --no-overwrite-ignore --no-recurse-submodules --create <name> <start>` 之前完成本机 Host/scope 复验、名称+起点校验、`check-ref-format`、同名存在即拒绝、两次一致快照，调用之后失败/取消一律 Unknown 且复读校验；`GitPages` 新增「新建分支并切换」小节并让已有分支 picker 在 `branchCreateNeedsRescan` 时禁用。

安全边界：不做 force/reset/stash/上游，`mode` 其他取值与 `failIfExists=false` 未观察故不实现；未跟踪/已暂存/未暂存/冲突一律阻止创建（沿用既有切换的干净前提，属 Harness 安全决定）；不含 ignored 覆盖；远端 Host 不提供；回执与外部 Git 并发之间不保证原子锁。

验收：`shared` 模块 `im.hikaru.harness.client.git.*` **63/63**（本批前 57，新增 `GitBranchCreateControllerTest` 6）；`jvm-app` 定向 `DesktopGitBranchCreateTest` + `GitBranchCreateInteractionTest` **7/7**（真实临时仓库创建并切换、起点形态、switch 前拒绝计数 0）；Git 页面/截图回归 **19/19**；`im.hikaru.harness.desktop.git.*` **120/120**；全量 `jvm-app` **661 found / 5 skipped / 656 started / 656 passed / 0 failed**（exit 0，未命中历史 PTY 字体抖动）。证据位于 `verification/git-create-branch-2026-10-11/entry-ap1/`；未跟踪的 `captures/scheduled-execution/` 全量后已按先例还原到批次前字节（`diff -r` 一致，合并 sha `ac8ebb54…`）。

边界：无截图对照、无原生窗口（本机 Screen Recording 权限当前不可用，`screencapture` 返回 `could not create image from display`），不主张原版像素；`mode`/`failIfExists=false`/非干净工作树/自动 stash/上游/detached 入口/远端 Host 仍未做；契约文件 `reverse-engineering/git-create-branch-contract-2026-10-11.md`。各批证据独立，整体目标 active。

## 2026-10-11 Git Diff 逐文件卡片 AQ：把整块原文改为按文件渲染（生产改动）

此前 Git 页面把后端返回的 patch 当作一整块原文（旧 `GitPatch` 直接 `HarnessText(patch)`）。ASAR 证据 D01/D02/D03 显示原版是「头部计数行 + 逐文件可折叠块」：头部 `codex.diffView.filesChanged` 复数文案 + `linesAdded/linesRemoved` 徽标（D01），`D = fileCount <= Ae && linesAdded + linesDeleted <= je`，逐文件块传 `hunkSeparators:'line-info'`/`stickyHeader:!0`/`diffViewWrap:!1`/`initialOpen: D && metadata.type !== 'deleted'`（D02），`ke` 内部用 `useState(initialOpen)` 让每块独立可折叠（D03）。

改法（生产 2 个文件）：新增 `GitDiffFiles.kt` 纯文本解析器（`parseGitDiff` 按 `diff --git ` 切块、统计 hunk 内 `+`/`-`、按 mode/rename/copy 归类 Added/Deleted/Renamed/Copied/Modified、解 Git 引号含八进制 → UTF-8 字节路径、`leadingContent` 标记，无平台依赖、不发明内容）；`GitPageComponents` 的 `GitPatch` 改为解析后调用 `GitDiffView`，新增 `git-diff-summary` 计数行、`git-diff-leading` 提示、`git-diff-file` 卡片（标题/明细/正文三 tag），删除型文件永不自动展开、其余按 Harness 阈值 `files.size<=10 && added+deleted<=400` 展开，保留 288dp 视口与 `SelectionContainer`（文本可选中复制）。

边界：不实现工具栏统一/分栏/自动布局选择器、rich preview、行内评论、sticky header 吸附；阈值 `Ae`/`je` 与 `metadata.type` 其余取值未观察，Harness 阈值属自定；不主张原版像素/原生窗口。

## 2026-10-11 Git Diff 布局选择器与分栏渲染 AR：unified/split/auto（生产改动）

ASAR 证据 D01/D02 显示 diff 工具栏有一个**页面级** unified/split/auto 三选一布局选择器（`codex.diffView.switchToUnified/switchToSplit/auto`，auto tooltip 明确「split for additions and removals, inline for narrow panes or one-sided changes」），选中值来自持久 store 并作用于所有文件块；同排另有 rich preview 开关。AQ 只做了单栏统一渲染，本批补上选择器与真实分栏。

改法（生产 3 个文件）：新增 `GitDiffLayout.kt` 纯逻辑（`GitDiffLayout`、`GitDiffSplitRow/Hunk/Kind`、`splitDiffBody` 把删除 run 与新增 run 配对成左右行、上下文两侧同文、缺侧留空、去行首标记、忽略 `\ No newline`；`effectiveGitDiffLayout` 实现 auto = 宽面板且两侧都有变化才 Split）；`GitPageComponents` 顶部新增工具栏 `git-diff-toolbar`（左计数 `git-diff-summary`、右选择器 `git-diff-layout` 三态），`BoxWithConstraints`（`< 520.dp` 视为窄面板）驱动每个文件卡片选择统一（`git-diff-file-patch`）或分栏（`git-diff-split/-hunk/-row/-left/-right`）；`GitDiffFiles` 计数循环去掉 `+++`/`---` 前缀排除（hunk 内每行都自带标记，内容以 `++`/`--` 开头的增删行此前被漏计）。

边界：默认保持 unified（原版持久默认不可观察）；选择器用「统一/分栏/自动」文字标签代替原版图标字形；不实现 rich preview、分栏行内评论/吸附头、`hunkSeparators:'line-info'` 在分栏中的精确排布；不主张原版像素/原生窗口。

验收：`shared` 模块 `im.hikaru.harness.client.git.*` **73/73**（本批前 68，新增 `GitDiffLayoutTest` 4 与计数回归 1）；`jvm-app` 定向 `GitDiffViewInteractionTest` + `GitWorktreesScreenshotTest` **9/9**；全量 `jvm-app` **664 found / 5 skipped / 659 started / 659 passed / 0 failed**（exit 0，本批未命中历史 PTY 字体抖动）。证据位于 `verification/git-diff-layout-2026-10-11/entry-ar1/`；未跟踪的 `captures/scheduled-execution/` 全量后已按先例还原到批次前字节（`diff -r` 一致，合并 sha `ac8ebb54…`）；契约文件 `reverse-engineering/git-diff-layout-contract-2026-10-11.md`。各批证据独立，整体目标 active。

验收：`shared` 模块 `im.hikaru.harness.client.git.*` **68/68**（本批前 63，新增 `GitDiffFilesTest` 5）；`jvm-app` 定向 `GitDiffViewInteractionTest` + `GitWorktreesScreenshotTest` **8/8**；全量 `jvm-app` **663 found / 5 skipped / 658 started / 657 passed / 1 failed**（exit 1；唯一失败为 AE/AF/AM/AO 记录过的 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 真实 PTY 就绪 30s 超时，本机当前负载下全量两次、隔离三次稳定命中，与本批改动无关）。证据位于 `verification/git-diff-file-view-2026-10-11/entry-aq1/`；未跟踪的 `captures/scheduled-execution/` 全量后已按先例还原到批次前字节（`diff -r` 一致，合并 sha `ac8ebb54…`）；契约文件 `reverse-engineering/git-diff-file-view-contract-2026-10-11.md`。各批证据独立，整体目标 active。


## 2026-10-11 Thread 摘要面板「环境」小节 AT：独立摘要 island + 本机 Git 只读投影（生产改动）

ASAR 证据 S01–S04：右侧摘要面板是独立于 tab 面板的 island（`thread-summary-panel-layout-*.js`，默认宽 300，`overlay`/`shift`/`gutter` 三种宽度策略）；`Environment` 小节仅当前 cwd 为 git 检出时渲染，依次为 Changes（真实 `+additions/−deletions`，点击打开该 checkout 的本地变更）、分支（可复制分支名）、`Uncommitted: {fileCount}`（仅大于 0 时）。本仓此前完全没有该面板。

改法（生产 7 个文件）：`GitModels` 新增 `GitDiffStats(fileCount,additions,deletions)` 与独立的 `GitState.summaryStats`（与页面 `diff` 分离）；`GitDiffFiles` 新增 `gitDiffStats(patch)`（复用 diff 卡片同一计数）；`GitController` 新增 `Query.SummaryStats`/`refreshSummaryStats()`（复用只读 `diff(HEAD)`，unborn HEAD 置 `Unselected` 不伪造 0，与页面 `selectDiff` 互不覆盖，仓库切换复位）；新增 `summary/ThreadSummaryModels.kt`（`threadBranchDisplayName` 只剥 `refs/heads/`；`ThreadSummaryEnvironment` 投影分支/detached/unborn/root/真实统计/已跟踪未提交数，untracked 排除；`ThreadSummaryLoad`）与 `summary/ThreadSummaryPanel.kt`（摘要 island：标题 + 环境小节 + Changes/分支/未提交行 + 不可用/加载真实状态）；`AppShell` 新增 `summaryPanel`/`summaryPanelWidth`/`onDismissSummaryPanel`（宽则 300dp 右列，窄则带 scrim 右侧浮层，`null` 时既有布局零影响）；`HarnessApp` Thread 页标题栏「摘要」开关（`thread-summary-toggle`）+ 打开且 repo/status 就绪时才触发一次统计读取，Changes 行跳 `PageId.Settings(Git)`（本仓无原版 Review panel，属 Harness 取舍）。

验收：`shared` 全模块 **482/482**（exit 0，生产改动所在模块）；`jvm-app` 定向 5 类 **21/21**（`ThreadSummaryPanelInteractionTest` 3、`GitDiffViewInteractionTest` 3、`HarnessAppThreadPanelsShellTest` 8、`git.DesktopGitBackendTest` 6、`git.DesktopGitProcessIntegrationTest` 1，含真实仓库 `gitDiffStats(HEAD)==GitDiffStats(1,1,1)` 且 untracked 不计）；全量 `jvm-app` **667 found / 5 skipped / 662 started / 659 passed / 3 failed**（exit 1，3 个失败全是真实 `git add`/`commit` 15s 与 `PtyTerminalFontInteractionTest` 30s 超时，与 AE/AF/AM/AO/AQ 一致；隔离重跑两个 Git 类 **13/13** 通过、PTY 复现 1 次）。证据位于 `verification/thread-summary-environment-2026-10-11/entry-at1/`；契约文件 `reverse-engineering/thread-summary-environment-contract-2026-10-11.md`。

边界：未实现原版 Changes 行打开的 Review panel 内部、摘要面板 Sources/Artifacts/Usage/Scheduled/Outputs 小节、`overlay`/`shift`/`gutter` 精确位移曲线、`metrics.bytesEstimate` 等未观察字段；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量后已按先例还原到批次前字节（`diff -r` 一致，合并 sha `ac8ebb54…`）。各批证据独立，整体目标 active。

## 2026-10-11 Thread 摘要面板「背景活动」小节 AU：子智能体/后台进程只读小节（生产改动）

ASAR 证据 B01–B04：摘要 island 除「环境」外还有两条只在有真实数据时才渲染的后台活动小节 `background-subagents`（子智能体）与 `background-tasks`（后台进程），二者共用行渲染器 `$b`；子 Agent 行 = 名称 + 活跃时 meta「运行中」（+ 可选 `diffStats`），点击打开子 Agent；后台终端行 = 命令首个非空行（不可用时回退「后台终端」），点击打开终端，另带「停止所有后台终端」批量操作；各带 `SectionCount`，列表为空时整节返回 `null`。

改法（生产 4 个文件）：新增 `summary/ThreadSummaryActivity.kt`（`ThreadSummaryActivityKind`、`ThreadSummaryActivity`，以及两个纯投影 `threadSummarySubagents`/`threadSummaryProcesses`：只取 `Ready`、运行中优先再按 recency、真实 label / 命令首行 / 回退「后台终端」/ running 判定，非 Ready 或空 → 空列表）与 `summary/ThreadSummaryActivityController.kt`（只读控制器，复用 `ThreadPanelsRepository.readOverview` 与 `readTerminalSelection`，owner 身份 + 请求代数双重围栏，提供 `bind`/`isBoundTo`/`refresh`/`close`，打开摘要不打开 tab、不建进程、不 spawn agent）；`ThreadSummaryPanel` 新增 `subagents`/`processes`/`onOpenActivity` 与 `thread-summary-section-subagents`/`-processes`（合并语义标题 + `thread-summary-section-<slug>-count`）及行 tag `thread-summary-subagent-<id>`/`thread-summary-process-<id>`；`HarnessApp` 建控制器、绑 owner、摘要打开时只读刷新一次、行点击打开 `OpenTab(Subagents|BackgroundOutput)` 并在面板已知该行时尽力深选。

验收：`shared` 全模块 **492/492**（exit 0，生产改动所在模块，本批前 482）；`jvm-app` 定向 8 类 **53 found / 53 started / 52 passed / 1 failed**（唯一失败为 `PtyTerminalPageInteractionTest.wideAndCompactLightDarkMappedGridRemainUsable` 真实 PTY 输出等待 30s 超时，隔离重跑该类 **7/7 通过**）；全量 `jvm-app` **669 found / 5 skipped / 664 started / 659 passed / 5 failed**（exit 1，5 个失败全是真实 worktree 注册 / PTY 就绪 10s/30s 超时，与 AE/AF/AM/AO/AQ/AT 同类，非本批路径；隔离重跑这 4 类 **12/13**，仅 PTY 字体复现）。证据位于 `verification/thread-summary-background-activity-2026-10-11/entry-au1/`；契约文件 `reverse-engineering/thread-summary-background-activity-contract-2026-10-11.md`。

边界：未实现子 Agent 行 `diffStats` 徽标、`showInlineActivity` 去重、`autoCollapse`（无活跃项且全部完成时折叠为「{count} 完成」）、「停止所有后台终端」批量停止、摘要其它小节（Sources/created-tasks/plan/pull-requests/side-chats/triggers/environment-creation）、子 Agent 行精确图标字形；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量后已按先例还原到批次前字节（`diff -r` 一致，合并 sha `ac8ebb54…`），经复核该批次前字节早于 `ScheduledModels.kt` 的披露文案，还原后与该文案不一致，本批不据此改动。各批证据独立，整体目标 active。

## 2026-10-11 Thread 摘要面板「停止所有后台终端」AV：进程行停全部动作（生产改动）

AU 已把摘要 island 的「子智能体」「后台进程」小节接上真实只读数据，并明确未做 terminal 行上的停止动作。ASAR 证据 C01–C04：该动作挂在**每一个** terminal 行（`Q.ItemActionButton`，`visibility:'interaction'`，label = `codex.localConversation.backgroundTerminals.stop` =「停止所有后台终端」），点击执行 `cleanBackgroundTerminals(conversationId)`——即停止该会话的**所有**后台终端而非被点击的那一个；在途时 `disabled` 且行内换 spinner，失败走 `onStopError` toast；行 trigger 仍单独打开该终端输出。

改法（生产 4 个文件）：`ThreadSummaryActivity` 新增 `ThreadSummaryStopAllLabel`；`ThreadSummaryActivityController` 状态加 `stoppingProcesses`/`stopNotice` 并新增 `stopAllProcesses()`——只取消**上一次成功读取里 lifecycle 为 Running** 的进程（清单来自控制器自己的只读读取），每个 key 各发一次既有 `operateTerminal(owner, key, Cancel)`，`Applied`/`Unknown` 记入 `stoppedKeys` 同一 incarnation 内不重发、`Rejected` 可重试，非 `Applied` 的服务原文去重后进 `stopNotice`，结束后 `refresh()` 重读清单；未绑定/已关闭/无 running/已在途一律 no-op，绑定与关闭清空清单与 `stoppedKeys` 并取消在途任务；`ThreadSummaryPanel` 新增 `processesStopping`/`processesNotice`/`onStopProcesses`，`onStopProcesses != null` 时每个 process 行右侧渲染 `thread-summary-process-stop-<id>` 图标按钮（在途 disabled，与行点击分离），notice 渲染 `thread-summary-processes-notice`；`HarnessApp` 接线；`HarnessAppThreadPanelsShellTest` 追加两个真实进程行的按钮断言。

验收：`shared` 全模块 **496/496**（exit 0，生产改动所在模块，本批前 492）；`im.hikaru.harness.client.summary.*` 定向 **21/21**（新增 4）；`jvm-app` 定向 `ThreadSummaryActivityPanelInteractionTest` + `ThreadSummaryPanelInteractionTest` + `HarnessAppThreadPanelsShellTest` **16/16**（新增 3）；全量 `jvm-app` **672 found / 5 skipped / 667 started / 667 passed / 0 failed**（exit 0，本批未命中历史 worktree/PTY 抖动）。证据位于 `verification/thread-summary-stop-all-2026-10-11/entry-av1/`；契约文件 `reverse-engineering/thread-summary-stop-all-contract-2026-10-11.md`。

边界：与原版三处已知差异——hover 才可见 → 始终可见、失败 toast → 小节内服务原文 notice、单次批量 RPC → 按进程各发一次 Cancel（部分成功只在 notice 里按去重原文展示，未合并语义）；未实现 subagent 行 `diffStats`、`showInlineActivity` 去重、`autoCollapse`「{count} 完成」、摘要其余小节（Sources/created-tasks/plan/pull-requests/side-chats/triggers/environment-creation）；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量后已按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-11 Git diff 富文本预览 AW：diff 工具栏富文本开关 + markdown 子集预览（生产改动）

AR（diff 布局选择器）验收时把「rich preview 开关与其渲染」列为未实现；本批补上。ASAR 证据 E01–E07：diff 工具栏 pressed 图标按钮（`codex.diffView.richPreviewToggle`，`aria-pressed`，`ghost/ghostActive`，`onClick set(ve,!g)`）；`initialOpen: O && e.metadata.type !== 'deleted'`；`fOr({filePath,imagePreviewMode,isDeletion,richPreviewEnabled})` = 开关 ∧ 非删除 ∧ markdown 才 `'markdown'`（`hOr`/`mOr`/`pOr`，`_Or=Set(['markdown','md','mdown','mdx','mkd'])`、`gOr=Set(['avif','bmp','gif','ico','jpeg','jpg','png','tif','tiff','webp'])`）；`D1` basename 扩展名规则（`i>0 && i<len-1`）；`Pt` 的 markdown 分支渲染 `Markdown{path, fallback: diffView}`；zh-CN 文案「切换富文本预览」「正在加载预览…」「无法渲染 PDF 预览」。

改法（生产 7 个文件）：新增 `git/GitRichPreview.kt`（`GitMarkdownExtensions`、`GitDiffPreviewKind`、`gitFileExtension` 镜像 `D1`、`gitDiffPreviewKind` 只保留 markdown 分支、`gitPreviewPathIsSafe`、`GitPreviewTextLimit=256 KiB`）与 `git/GitMarkdown.kt`（子集解析：块级标题/段落/围栏代码/引用/列表/分隔线 + 行内粗体/斜体/行内代码/链接，未匹配标记原样保留）；`GitModels` 增 `GitPreviewFile{Loading,Ready,Unavailable}`、`GitState.previewFiles`、`GitBackend.readPreviewFile` 默认 `Unavailable`（不可读后端显式回退）；`GitController` 增 `previewJobs`/`refreshPreviewFiles(paths)`（按路径 `distinct`、终态不重读、与其它查询同一 `epoch` 围栏、LAZY 任务、`invalidate`/`close` 取消清空），`refreshDiff()` 仅在补丁文本真的变化时清缓存；`GitPageComponents` 工具栏加 `GitDiffRichPreviewToggle`（`git-diff-rich-preview`），`LaunchedEffect(document, previewPaths)` 在开关打开或 diff 变化时重新请求，markdown + `Ready` 渲染 `git-diff-rich-preview-<path>`，否则 `git-diff-rich-preview-notice-<path>` 并继续显示 diff 文本；`GitPages` 接线；`DesktopGitBackend` 实现 `readPreviewFile`（本机只读 binding + 路径安全 + `isRegularFile(NOFOLLOW_LINKS)` 不跟随符号链接 + 有界读取 + `throwOnInvalidSequence` 非 UTF-8 回退，全程不调用 git 命令）。

验收：`shared` 全模块 **506/506**（exit 0，生产改动所在模块，本批前 496，新增 10）；`im.hikaru.harness.client.git.*` 定向 **87/87**（新增 7+3）；`jvm-app` 定向 `GitDiffRichPreviewInteractionTest` + `DesktopGitBackendTest` + `GitDiffViewInteractionTest` + `GitPageInteractionTest` **20/20**（新增 3+2）；全量 `jvm-app` **677 found / 5 skipped / 672 started / 672 passed / 0 failed**（exit 0，本批未命中历史 worktree/PTY 抖动）。证据位于 `verification/git-rich-preview-2026-10-11/entry-aw1/`（`files.sha256` 6 文件校验通过）；契约文件 `reverse-engineering/git-rich-preview-contract-2026-10-11.md`。

边界：只做 markdown，图片/SVG/PDF 沿用 diff 文本（原版 `image`/`pdf` 分支与 `codex.diffView.pdfPreviewError` 存在，本批不主张）；markdown 渲染器是子集而非 CommonMark（无表格/任务列表/嵌套强调/引用内块/HTML，`snake_case` 下划线强调可能误判）；markdown 失败用可见 notice 代替原版的静默 diff 回退；开关文案「富文本预览」、`GitPreviewTextLimit`、选中样式为本仓选择；未实现 `imagePreviewMode` 持久来源与设置入口、开关持久 store 初值、原版 `Markdown` 组件本体、图标字形；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 跑后已按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-11 统一 Diff 逐行着色 AX：新增/删除行底色 + `@@` 分隔头（生产改动）

AR（布局选择器）/AQ（逐文件卡片）/AW（富文本预览）之后，统一视图仍把整个补丁正文当成一块无着色文本；本批按原版行级规则补齐。ASAR 证据 L01–L05：`codex-diff-css` 对 `[data-line-type='change-addition'|'change-deletion']:is([data-line],[data-no-newline])` 设 `--diffs-computed-diff-line-bg: var(--diffs-bg-addition|deletion)`，行号单元格另有数字底色；`@@` 头是 `[data-separator='line-info']` 分隔行（统一视图 `grid-template-columns: number-width minmax(0,1fr)`，分隔面 `--codex-diffs-separator-surface`）；`code-diff` 透传 `hunkSeparators:'line-info'`；zh-CN 文案沿用。

改法（生产 2 个文件）：新增 `git/GitDiffLines.kt` 纯分类器（`GitDiffLineKind{Context,Added,Removed,HunkHeader,NoNewline,Meta}`、`GitDiffLine(kind,text,oldNumber?,newNumber?)`、`parseGitDiffLines(body)`：`@@` 头重置两侧行号，hunk 内每行按首字符分类并剥掉一个标记，`\` 记 `NoNewline`，其余记 `Meta`，绝不丢字）；`GitPageComponents` 统一视图从整块 `HarnessText` 改为 `GitUnifiedDiff(body)` + 纯函数 `gitUnifiedDiffAnnotatedString`（新增行 `DiffAddedBackground`、删除行 `DiffRemovedBackground`、`@@` 头 `BorderSubtle` 底 + `TextMuted` 字、`\ No newline`/`Meta` 置灰），保持单 `testTag("git-diff-file-patch")` 文本块与水平滚动语义不变，`AnnotatedString.text` 与 body 逐字符相等，可选中文本不变。

验收：`shared` 全模块 **513/513**（exit 0，本批前 506，新增 7）；`im.hikaru.harness.client.git.*` 定向 **94/94**（新增 7）；`jvm-app` 定向 `GitDiffViewInteractionTest` + `GitDiffRichPreviewInteractionTest` + `GitPageInteractionTest` **12/12**；全量 `jvm-app` **677 found / 5 skipped / 672 started / 669 passed / 3 failed**（exit 1，3 个失败为既有无关节奏抖动：`GitCommitInteractionTest` + `git.DesktopGitCommitTest` 真实提交 15s 超时、`PtyTerminalFontInteractionTest` PTY 就绪超时；隔离重跑同三类 **16/16** exit 0）。证据位于 `verification/unified-diff-lines-2026-10-11/entry-ax1/`（`files.sha256` 7 文件校验通过）；契约文件 `reverse-engineering/unified-diff-lines-contract-2026-10-11.md`。

边界：原版独立的不可选中行号 gutter 列未复刻（本仓渲染为单文本块）、`@@` 分隔行的上下文展开按钮未实现、无语法高亮 token；色值为本仓既有 `HarnessTokens` 而非原版 `color-mix` 字面值；未实现 sticky header 吸附、行内评论、分栏与统一共用列宽；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 跑后已按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-11 Worktrees「工作树根目录」设置 AY：Host 级 root 字段 + 创建路径默认值（生产改动）

AW（富文本预览）/AX（统一 diff 逐行着色）之后，Worktrees 页仍缺原版首行的「工作树根目录」设置。ASAR 证据 R01–R04：该行在「General」小节，当前值取自 host 查询 `re(kt,r)?.worktreesRoot`，提交走 `Nt(i, r, De.worktreeRoot, e)`，成功/失败各出 `settings.git.worktreeRoot.save.success|error` toast；受控输入只在「有改动且未在提交」时可保存，提交 `if (!changed || pending) return; const e = draft.trim(); (e === '' || isSafePath(e) && fs.isAbsolute(e)) && mutate(e)`——**空串合法**（回默认位置），否则须**绝对路径**；还绑定 `CmdOrCtrl+S` 并在失焦时提交；zh-CN 文案「工作树根目录」「ChatGPT 创建托管工作树的目录。留空则使用默认位置」。

改法（生产 6 个文件）：新增 `settings/WorktreeSettings.kt`（`@Serializable WorktreeSettings(root)`、`configuredRoot`、纯函数 `worktreeRootValue`（trim + 反斜杠归一）、`worktreeRootError`（空合法 / 绝对合法 / NUL 或相对报错）、`worktreeDefaultPath(root,branch)`、`branchSlug`）与 `settings/WorktreeSettingsRepository.kt`（`WorktreeSettingsStore`/`MemoryWorktreeSettingsStore`/`WorktreeSettingsState`/`WorktreeSettingsRepository`，镜像 `GeneralSettingsRepository` 的 load/save/retry、失败写不替换在用值、`canEdit` 仅在可写且已加载时为真）与 `settings/DesktopWorktreeSettingsStore.kt`（shared jvm 源集，落盘 `client-worktrees.json`，临时文件 + 原子替换）；`git/GitPages.kt` 的 WorktreesPage 顶部新增「工作树根目录」小节（受控输入 + 行内错误 + 保存按钮 + 成功/失败/加载失败提示），`WorktreeCreationForm` 增 `worktreeRoot` 参数——根目录已配置且分支为真名时把「新工作树绝对路径」默认填成 `<root>/<slug>`（用户一旦编辑即不再覆盖）；`app/HarnessApp.kt` 增参数 `worktreeSettingsRepository`，与 `GeneralSettings` 同层加载一次并透传；`jvm-app/src/main.kt` 构造并注入 `WorktreeSettingsRepository(DesktopWorktreeSettingsStore(home/client-worktrees.json))`。

验收：`shared` 全模块 **524/524**（exit 0，生产改动所在模块，本批前 513，新增 11）；`im.hikaru.harness.client.settings.*` 定向 **52/52**（新增 5+4+2）；`jvm-app` 定向 `WorktreeRootSettingInteractionTest` **2/2**（新增 2）；`jvm-app` 回归 6 类 **27 found / 26 passed / 1 failed**（唯一失败 `WorktreePinInteractionTest` 时序抖动，两次隔离重跑：**2/4** → **4/4** 全绿）；全量 `jvm-app` **679 found / 5 skipped / 674 started / 672 passed / 2 failed**（exit 1，2 个既有真实 git 超时抖动 `GitCommitInteractionTest` + `git.DesktopGitCommitTest`，隔离重跑 **13/13** exit 0）。证据位于 `verification/worktree-root-setting-2026-10-11/entry-ay1/`（`files.sha256` 10 文件校验通过）；契约文件 `reverse-engineering/worktree-root-setting-contract-2026-10-11.md`。

边界：与原版两处已知差异——`CmdOrCtrl+S` 快捷键与失焦提交 → 显式保存按钮 + 行内校验、原版失焦非法静默不提交 → 可见行内错误；未实现同页「环境」小节的自动删除旧工作树／自动删除限制／创建前获取上游更新三行（自动删除是破坏性操作，本批不接线）、原版 host 级存储与 `worktreesRoot` 默认值来源、`isSafePath` 完整规则；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 跑后已按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-11 Worktrees「环境」小节三项设置 AZ：自动删除开关／保留数量／上游刷新（生产改动）

AY 补了 Worktrees 页首「工作树根目录」，但同页「环境」小节三行仍缺。ASAR 证据 N01–N06：三项都是按 host 持久化的设置——`worktree-auto-cleanup-enabled`（boolean，默认 `true`，关闭前弹 `autoCleanup.confirm.*` 确认框）、`worktree-keep-count`（`int().min(1)`，默认 `15`，`Enter` 触发保存）、`worktree-upstream-refresh-mode`（enum `never`|`best-effort`，默认 `never`，即「创建前始终获取上游更新」）；三条各有成功/失败 toast；zh-CN 小节标题「常规」「环境」，自动删除关闭时保留数量描述切换为「自动删除已停用…」。

改法（生产 3 个文件）：`settings/WorktreeSettings.kt` 扩展为三项——新增 `WorktreeUpstreamRefreshMode`（`@SerialName("never"/"best-effort")` + `wire`）、常量 `WorktreeAutoCleanupDefault=true`/`WorktreeKeepCountDefault=15`、`WorktreeSettings` 增 `autoCleanupEnabled`/`keepCount`/`upstreamRefreshMode`（带默认值，AY 旧文档仍可读）、纯函数 `keepCountValue`（trim 后 ≥1 的整数）与 `keepCountError`；`git/GitPages.kt` 的 WorktreesPage 参数改 `onSaveWorktreeSettings: (WorktreeSettings)->Unit`，根目录小节改为 `settings.copy(root=…)`（不再是整体替换，避免清空新字段），新增 `WorktreeEnvironmentSection`（自动删除开关 + 关闭确认框、保留数量输入 + 行内 `min(1)` 错误 + 保存按钮、上游刷新开关，附加载/保存错误与「已保存工作树设置」提示）；`app/HarnessApp.kt` 透传改名并改为整份 `save(next)`。键位与原版一致（`git-worktree-root` 沿用 AY）。

验收：`shared` 全模块 **526/526**（exit 0，生产改动所在模块，本批前 524，新增 2）；`im.hikaru.harness.client.settings.*` 定向 **54/54**（新增 2）；`jvm-app` 定向 `WorktreeRootSettingInteractionTest` **3/3**（新增 1）；`jvm-app` 回归 5 类 **22/22**；全量 `jvm-app` **680 found / 5 skipped / 675 started / 675 passed / 0 failed**（exit 0，本批未命中历史抖动）。证据位于 `verification/worktree-env-settings-2026-10-11/entry-az1/`（`files.sha256` 7 文件校验通过）；契约文件 `reverse-engineering/worktree-env-settings-contract-2026-10-11.md`。

边界：只实现可观察的持久化层——三项是真实存储的偏好，**破坏性的自动裁剪**与**创建前的上游 fetch 实际执行未接线**；未复刻原版逐条独立 mutation 的并发/失败重试、`CmdOrCtrl+S`/`Enter` 快捷键、逐条 toast（改为小节内联提示）；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 跑后已按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-11 定时任务列表「节奏 + 下次运行」摘要 BA：真实数据投影（生产改动）

AZ 收敛 Worktrees 后回到定时任务。原版列表每行 = 节奏 + 下次运行/状态两段，本仓此前任务行只有标题与简短状态。ASAR 证据 B01–B03：`table-92b84c469d71.js` 的 `At`（下次运行单元格——空为 `—`；今天/明天 → `automations.scheduled.table.today|tomorrow`「今天/明天 {time}」；否则 `formatDate`；非活动 → `inactive`「{paused 已暂停 / 其它 已完成}」；云端 → `alwaysOn`「始终开启」）与 `jt`（节奏单元格——`condition_watch` → `monitoring`「监测」；`webhook` → `events`；否则 `frequency`「一次/每小时/每天/每周/…」）；zh-CN 印证「下次运行」「正在运行」「下次运行：{time}」等 timing 分支。

改法（生产 5 个文件）：`scheduling/ScheduleEngine.kt` 的 `ScheduleCalendar` 增带默认实现的 `displayZoneId`（默认空串）与 `nextRunLabel(next, now, zoneId): String?`（默认 null）——默认实现让既有测试替身无需改动即为「只显示节奏」；新增 `scheduling/ScheduledTaskSummary.kt` 纯投影（`ScheduledRule.cadenceLabel()`、`zoneIdOrNull()`、常量 `ScheduledPausedLabel`「已暂停」/`ScheduledRunningLabel`「正在运行」、`scheduledTaskSummary(task, cursor, running, now, calendar)`：暂停 →「已暂停」；活动且有阻塞运行 →「节奏 · 正在运行」；否则「节奏 · 下次运行：<平台标签>」，无 cursor/无日历/无法格式化时退化为「节奏」）；`scheduling/ScheduledManagementNavigator.kt` 增可选 `calendar`/`nowEpochMillis` 并把任务行 `description` 改为摘要；`app/HarnessApp.kt` 传 `scheduleCalendar` 与随快照 `revision`/阶段重算的 `now`；`jvm-app/.../scheduling/DesktopScheduleCalendar.kt` 用 `java.time` 实现 `displayZoneId`（系统默认时区）与 `nextRunLabel`（今天/明天/M月d日/含年份，`HH:mm`，捕获 `DateTimeException` → null）。

验收：`shared` 全模块 **532/532**（exit 0，生产改动所在模块，本批前 526，新增 6）；`im.hikaru.harness.client.scheduling.*` 定向 **89/89**（新增 6）；`jvm-app` 定向 `DesktopScheduleEngineTest` + `ScheduledManagementInteractionTest` **8/8**（新增 3）；全量 `jvm-app` **683 found / 5 skipped / 678 started / 678 passed / 0 failed**（exit 0，本批未命中历史抖动）。证据位于 `verification/scheduled-next-run-2026-10-11/entry-ba1/`（`files.sha256` 6 文件校验通过）；契约文件 `reverse-engineering/scheduled-next-run-contract-2026-10-11.md`。

边界：只投影**可观察**字段——`lastRun`（上次运行）、`starting`/`executing` 运行中动画、事件/条件型「监测」「始终开启」、云端 schedule 形态均未实现（本仓只有本机 Cron/Heartbeat）；「现在」非逐秒刷新，只在一次快照 `revision`/阶段变化时读一次时钟，跨天不会自动从「今天」翻到「明天」；`formatDate` 精确本地化与年份省略规则未逐字对齐（本仓用中文 `M月d日`、同年省略年份）；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 跑后已按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-12 定时任务详情「上次运行」时机行 BB：真实运行终态时间投影（生产改动）

BA 补齐了列表行的「节奏 + 下次运行」，原版**详情**页还有一条「上次运行」时机行仍未做。ASAR 证据 BB01–BB04：`update-content-bc3a4545935a.js` 的函数 `A`——取 `automation.last_run_time`，先算相对标签（函数 `M`：未来或 ≥1 天 → null，<1h → 分钟、否则小时，均 `max(1, round(...))`）；相对为 null 时算日差——0/1 → `at`（今天/昨天 + `HH:mm`）、2..7 → `on`（星期几）、>7 → `on`（含年份日期），渲染 `automations.scheduled.detailRunTiming`（该 footer 调用把 `nextKind` 固定为 `none`、`separator` 为空串）。

改法（生产 5 个文件）：`scheduling/ScheduleEngine.kt` 的 `ScheduleCalendar` 增带默认实现的 `relativeRunLabel` 与 `runDayLabel`（默认 null，既有替身即「不显示上次运行」）；`scheduling/ScheduledTaskSummary.kt` 新增 `ScheduledLastRunPrefix`（「上次运行于 」）、`lastRunInstant(runs)`（最近终态 `endedAt`，否则最近 `claimedAt`，无运行 null）、`scheduledLastRunLabel(lastRunAt, now, calendar)`（相对优先、否则日分支，无运行/无日历/无查看者时区/无法格式化 → null）；`scheduling/ScheduledManagementPage.kt` 的页面与详情新增可选 `calendar`/`nowEpochMillis` 并在「已保存配置 · {status}」下渲染 `scheduled-detail-last-run`；`app/HarnessApp.kt` 把一次快照 `revision`/阶段重算的 `scheduledNow` 上提共用并传 `calendar = scheduleCalendar`；`jvm-app/.../scheduling/DesktopScheduleCalendar.kt` 用 `java.time` 实现两方法（相对：未来或 ≥1 天 null，`<1h` 分钟前否则小时前；日分支：今天/昨天 `HH:mm`、`星期X`、含年份日期，坏时区 → null）。

验收：`shared` 全模块 **538/538**（exit 0，生产改动所在模块，本批前 532，新增 6）；`im.hikaru.harness.client.scheduling.*` 定向 **95/95**（新增 6）；`jvm-app` 定向 `DesktopScheduleEngineTest` + `ScheduledManagementInteractionTest` **11/11**（新增 3）；全量 `jvm-app` **686 found / 5 skipped / 681 started / 681 passed / 0 failed**（exit 0，本批未命中历史抖动）。证据位于 `verification/scheduled-last-run-2026-10-12/entry-bb1/`（`files.sha256` 6 文件校验通过）；契约文件 `reverse-engineering/scheduled-last-run-contract-2026-10-12.md`。

边界：只实现可观察的 lastKind 分支——原版同一条消息里的 `nextKind`（`正在运行`/`下次运行于 …`）在该 footer 调用被固定为 `none`，失败态外层「上次运行未成功」标签未实现；`last_run_time` 的写入时机未观察（本仓用真实运行账本投影）；「现在」非逐秒刷新；`formatRelativeTime`/`formatDate(weekday:long)` 精确本地化未逐字对齐（本仓用 `N 分钟前`/`N 小时前`/`星期X`）；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；因截图测试的日历替身未实现本批新增方法（默认 null），`captures/scheduled-execution/` 内容未变，仍按先例在跑后从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-12 定时任务导航器状态筛选 BC：全部/已启用/已暂停（生产改动）

BB 补齐详情「上次运行」后回到导航器：原版有一个「筛选和排序任务」入口，其「状态」子项可收敛列表到 全部/已开启/已暂停/已完成，本仓此前只有搜索。ASAR 证据 BC01–BC02：`table-92b84c469d71.js` 的函数 `_t`——筛选入口是 `variant:'ghost'` 图标按钮（aria-label `table.filters`「筛选和排序任务」，图标 16/20px），菜单含 SectionLabel `table.filterBy`「筛选条件」与子项 `table.status`「状态」，RadioItem 列表 `['all','active','paused','finished']`，默认 `n.status ?? 'active'`；`table.empty`「没有符合筛选条件的任务」。owner 筛选与排序属团队/云端范围、`finished` 态本仓没有，均不实现。

改法（生产 3 个文件）：`ui-foundation/HarnessIcons.kt` 新增 `HarnessIconName.Filter`（`filter_list` 三条递减横杠，逐条 `drawLine`）；`scheduling/ScheduledManagementNavigator.kt` 新增可选 `statusFilter`/`onStatusFilterChange`，标题行在传入回调时多渲染筛选图标按钮（`scheduled-toggle-filter`）、展开后一行 全部/已启用/已暂停（`scheduled-status-filter` + `scheduled-filter-*`），列表按 query 与状态组合过滤，空结果文案改为原版「没有符合筛选条件的任务」；`app/HarnessApp.kt` 新增 `scheduledStatusFilter` UI 状态并透传。

验收：`ui-foundation` 全模块 **53/53**（新增图标所在模块）；`shared` 全模块 **538/538**（导航器改动所在模块，本批未新增 shared 测试）；`jvm-app` 定向 `ScheduledManagementInteractionTest` + `DesktopScheduleEngineTest` **13/13**（新增 2）；全量 `jvm-app` 第 1/2 次均因既有真实进程用时门 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（第 177 行 `waitUntil(30_000)`）各 **682 passed / 1 failed**（exit 1，该类隔离重跑 **3/3 exit 0**，与本批 UI 改动无关），第 3 次 **688 found / 5 skipped / 683 started / 683 passed / 0 failed**（exit 0，本批门槛以此为准）。证据位于 `verification/scheduled-status-filter-2026-10-12/entry-bc1/`（`files.sha256` 9 文件校验通过，含三次全量日志与 PTY 隔离重跑）；契约文件 `reverse-engineering/scheduled-status-filter-contract-2026-10-12.md`。

边界：只实现可观察的状态筛选——原版筛选菜单的 owner（所有者）筛选与排序子菜单、默认选中「已开启」（本仓默认「全部」）、`finished`（已完成）态、精确几何/弹出层交互未实现；owner/sort 属团队/云端范围；新 `Filter` 图标是 Harness 内部 `filter_list` 形态，不主张与原版逐像素一致；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 三次跑后均按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；壳层截图为挂载 `HarnessApp` 的截图测试按既有机制重写，未单独备份。各批证据独立，整体目标 active。

## 2026-10-12 定时任务导航器排序 BD：默认/下次运行/名称（生产改动）

BC 做了「筛选和排序任务」菜单的状态筛选，同一菜单里的排序子菜单仍未做。ASAR 证据 BD01–BD02：`table-92b84c469d71.js` 的函数 `_t`——排序与状态筛选同菜单、用 `Separator` 分隔；子菜单标题 `table.sort`「排序方式」，RadioGroup value = `n.sort ?? 'automatic'`，选项 `['automatic','next_run_asc','updated_at_desc','title_asc', ...(搜索时 'relevance')]`，`automatic` 写回 `undefined`；描述明确 Default 在浏览时把 active 排在 paused/completed 之前、搜索时用相关性排序。`updated_at_desc`（需更新时间戳）与 `relevance`（需远端评分）本仓没有，不实现。

改法（生产 3 个文件）：`scheduling/ScheduledModels.kt` 新增 `ScheduledTaskSort { Automatic, NextRun, Title }`；`scheduling/ScheduledManagementNavigator.kt` 把状态行包进 Column 并加「排序方式」行（默认/下次运行/名称，tag `scheduled-sort` + `scheduled-sort-*`），列表先按 query 与状态过滤再排序（`Automatic` 用稳定 `sortedBy` 让 active 先于 paused、组内保序；`NextRun` 按 cursor `nextNominalAt` 升序、无 cursor 最后；`Title` 按名称），筛选按钮在只传 `onSortChange` 时也出现；`app/HarnessApp.kt` 新增 `scheduledSort` UI 状态并透传。

验收：`shared` 全模块 **538/538**（生产改动所在模块，本批未新增 shared 测试）；`jvm-app` 定向 `ScheduledManagementInteractionTest` **7/7**（新增 1）；全量 `jvm-app` **689 found / 5 skipped / 684 started / 684 passed / 0 failed**（exit 0，本批未命中历史抖动）。证据位于 `verification/scheduled-sort-2026-10-12/entry-bd1/`（`files.sha256` 5 文件校验通过）；契约文件 `reverse-engineering/scheduled-sort-contract-2026-10-12.md`。

边界：只实现可观察的三种排序——原版 `updated_at_desc`（最近更新，需任务更新时间戳）与搜索时的 `relevance`（相关性，依赖远端评分）未实现；排序是页面内 UI 状态、不写用户偏好；原版菜单弹出层/键盘导航未复刻；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；壳层截图为挂载 `HarnessApp` 的截图测试按既有机制重写，未单独备份。各批证据独立，整体目标 active。

## 2026-10-12 定时任务写入时间戳 BE：最近更新排序 + 详情「上次更新」（生产改动 + 持久化 schema 3）

BD 做了「筛选和排序任务」菜单的状态筛选与三种排序，同一菜单还有 `updated_at_desc`（最近更新），详情页另有一行写入时间戳；两者都依赖任务的更新时间戳，本仓模型此前没有该字段。ASAR 证据 BE01–BE04：`table-92b84c469d71.js` 的排序比较器 `n.sort==='updated_at_desc'?t.updatedAt-e.updatedAt:…`（最近更新降序）、`zh-CN-b05cfd55c534.js` 的 `table.sortOption`「{… updated_at_desc 最近更新 …}」与 `automations.scheduled.detailUpdateTimestampWithLabel`（`{showLastUpdateLabel, select, yes {上次更新 · } other {}}{recent, select, yes {{relativeTime}} other {{date}…{time}}}`）、`update-content-bc3a4545935a.js` 函数 `j` 的 `showLastUpdateLabel = a==null?'no':'yes'`（前缀只在约一天内的相对分支）。原版时间戳来自远端服务、`relevance`/`createdAt` 默认序/owner 筛选属远端范围，均不实现。

改法（生产 8 个文件）：`scheduling/ScheduledModels.kt` 给 `ScheduledTask` 加可空 `updatedAt`、给 `ScheduledTaskSort` 加 `Updated`；`ScheduledRepository.kt` 新增可注入 `clock: ScheduledClock`，新建/编辑保存与无执行器的持久暂停写入时间戳；`ScheduledExecutor.kt` 的 `activate` 变更状态时一并写；`ScheduledValidator.kt` 加 `updatedAt < 0` 非法；`ScheduledTaskSummary.kt` 新增 `ScheduledLastUpdatedPrefix` 与 `scheduledLastUpdatedLabel`（只有相对分支带前缀）；`ScheduledManagementNavigator.kt` 加「最近更新」项（tag `scheduled-sort-updated`，按 `updatedAt` 降序、无时间戳最后）；`ScheduledManagementPage.kt` 详情渲染 `scheduled-detail-updated`；`desktop/scheduling/DesktopScheduledStore.kt` 文档 `schemaVersion` 升到 3、读取接受 1–3、提交时统一写当前版本（旧文档在下一次写入升级）。

验收：`shared` 定向 **34/34**（新增 4）、`shared` 全模块 **542/542**（较上批 538 增 4）；`jvm-app` 定向 **25/25**（新增 3、改 1）、全量 `jvm-app` **692 found / 5 skipped / 687 started / 687 passed / 0 failed**（exit 0，较上批 689 found 增 3，本批未命中历史 PTY 抖动）。证据位于 `verification/scheduled-task-timestamp-2026-10-12/entry-be1/`（`files.sha256` 6 文件校验通过）；契约文件 `reverse-engineering/scheduled-task-timestamp-contract-2026-10-12.md`。

边界：时间戳是本地写入时钟而非远端服务时间；详情非相对分支复用上次运行的日期标签，不逐字复刻原版 `{date}`/`{time}`/`includeAt` 拼接；`relevance`、`createdAt` 默认序与 owner 筛选不实现；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；冻结的 `paused-detail` 截图夹具 `updatedAt` 为 null，故该图不含「上次更新」行（与 BB「上次运行」行同）。各批证据独立，整体目标 active。

## 2026-10-12 统一 Diff 行号 Gutter BF：不可选中且带逐行底色的行号列（生产改动）

AX 做了统一 Diff 的逐行着色与 `@@` 分隔头，同一份契约里原版每行还有一个独立行号单元格，本仓此前把正文渲染为单个可选中文本块、没有行号列（`GitPageComponents` 注释原文「the original keeps a separate, unselectable line-number gutter ... no such gutter exists yet」）。ASAR 证据 G01–G04：`app-initial-6350d04672d0.js` 的 `Awt(type,number,lineIndex)` 输出 `div[data-line-type][data-column-number][data-line-index]` 内含 `span[data-line-number-content]`，无行号处用 `Fx` 的 `div[data-gutter-buffer][data-buffer-size]` 占位；`codex-diff-css-a6773575cd64.js` 的统一视图 hunk 分隔行网格为「行号列 + 内容列」（`--diffs-column-number-width` + `minmax(0,1fr)`），`[data-column-number]`/`[data-gutter-buffer]` 参与行级底色合成。行号语义：新增/上下文取新文件行号、删除取旧文件行号、hunk 头/`\ No newline`/meta 为空白缓冲。

改法（生产 2 个文件）：`git/GitDiffLines.kt` 新增纯函数 `gitDiffGutterNumber(line)`（新增/上下文取新行号、删除取旧行号、hunk 头/`\ No newline`/meta 空白）、`gitDiffGutterText(lines)`（逐行右对齐、按公共宽度补齐、行数等于 `parseGitDiffLines`、空 body 返回空串）与 `gitDiffGutterAnnotatedString(lines, added, removed, separator)`（单 `AnnotatedString`，逐行单元格按 Added/Removed/HunkHeader 加底色，其余不着色）；`git/GitPageComponents.kt` 的 `GitUnifiedDiff` 改为 `Row { 行号 Text（SelectionContainer 之外，带逐行底色） ; SelectionContainer { 补丁 Text } }`，并把视口级 `SelectionContainer` 下移到文件卡头部列/富文本预览/预览回退提示/分栏正文，使行号列落在选区之外；补丁 `gitUnifiedDiffAnnotatedString` 未改，`git-diff-file-patch` 标签与横向滚动语义不变。单元格填充用非断行空格（U+00A0）——普通空格会被行尾裁剪、使 gutter 色带止于最后一位数字并与正文留白缝，实测像素确认换 U+00A0 后色带连续到正文且等宽字距下仍右对齐。

验收：`shared` 定向 `GitDiffLinesTest` **11/11**（新增 4）、`shared` 全模块 **546/546**（较上批 542 增 4）；`jvm-app` 定向 `GitDiffViewInteractionTest`+`GitDiffRichPreviewInteractionTest` **6/6**（改 1）、`GitWorktreesScreenshotTest` **6/6**（重生成 git-worktrees 截图）；全量 `jvm-app` r1 **692 found / 5 skipped / 687 started / 686 passed / 1 failed**（exit 1，唯一失败为历史真实进程抖动类 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，隔离重跑 **3/3** exit 0）、r2（单色行号列版本）**692 found / 5 skipped / 687 started / 687 passed / 0 failed**、r3（最终逐行底色版本）**692 found / 5 skipped / 687 started / 687 passed / 0 failed**（exit 0，本批门槛以此为准）。证据位于 `verification/unified-diff-gutter-2026-10-12/entry-bf1/`（`files.sha256` 校验通过，含三次全量日志与 PTY 隔离重跑、像素核验说明）；契约文件 `reverse-engineering/unified-diff-gutter-contract-2026-10-12.md`。

边界：原版 `color-mix` 合成的行号**字色**（`--codex-diffs-*-number`）与 `--diffs-column-number-width` 精确列宽、`--diffs-gap-inline` 内缩未逐像素复刻（本批行号字色沿用 `TextMuted`、列宽由最大位数 + 固定间隙推出，底色已按行类型与正文同色）；hunk 分隔行的 `data-expand-up/down` 展开按钮、分栏视图两侧行号列未做；上下文行显示旧号还是新号未逐像素比对（本批按统一 Diff 惯例取新号）；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`，本批仅有离屏自渲染像素与颜色采样说明）；`captures/scheduled-execution/` 各次全量跑后均按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；`captures/git-worktrees/` 因正文新增行号列由截图测试按既有机制重写。各批证据独立，整体目标 active。

## 2026-10-12 分栏 Diff 行号 Gutter BG：两侧行号 + 变更侧同色（生产改动）

BF 做了统一 Diff 的单行号列，同一份 Diff 契约里分栏视图两侧的行号列还是空白（BF 边界原文「分栏视图两侧行号列未做」）。ASAR 证据 H01–H06：`app-initial-6350d04672d0.js` 的分栏渲染器给每个 pane（`[data-deletions]`/`[data-additions]`）只生成 `[gutter, content]` 两个子元素，并断言 `gutter.children.length === content.children.length`（即每行一个 gutter 单元格），`pre` 上写 `data-diff-type: 'split'`；`codex-diff-css-a6773575cd64.js` 注释原文 `Pierre duplicates split separators into each gutter and content cell.`，全文件只有一处 `--diffs-column-number-width`（两 pane 共用列宽），变更行底色分落正文单元格（`--diffs-bg-*`）与行号单元格（`--codex-diffs-*-number`）。

改法（生产 3 个文件）：`git/GitDiffLayout.kt` 给 `GitDiffSplitRow` 加 `oldNumber`/`newNumber`，`splitDiffBody` 按 `@@` 头重置并推进两个计数器，`HUNK_HEADER` 提为 `internal` 供两个解析器共用；`git/GitDiffLines.kt` 新增 `gitDiffSplitGutterNumber`/`gitDiffSplitGutterWidth`（两 pane 共用列宽）/`gitDiffSplitGutterText`/`gitDiffSplitGutterAnnotatedString`（单侧单元格 + 底色，被补空的一侧留同宽空白缓冲）/`gitDiffSplitPaneText`（pane 正文，变更侧整行着色、字符不变）与共享判定 `gitDiffSplitSideBackground`；`git/GitPageComponents.kt` 的 `GitDiffSplitView` 由「外层 `SelectionContainer` 包整块」改为逐行 `Row { 左行号 ; SelectionContainer{左正文} ; 右行号 ; SelectionContainer{右正文} }`，hunk 头各自包 `SelectionContainer`，行号落在选区之外且与所属行同一 `Row`（折行时行号停在该行首行）。

验收：`shared` 定向 `GitDiffLayoutTest`+`GitDiffLinesTest` **21/21**（新增 6、改 4 期望）、`shared` 全模块 **552/552**（上批 546 增 6）；`jvm-app` 定向 `GitDiffViewInteractionTest`+`GitDiffRichPreviewInteractionTest` **6/6**（改 1）、`GitWorktreesScreenshotTest` **6/6**（重生成 git-worktrees 截图）；全量 `jvm-app` **692 found / 5 skipped / 687 started / 687 passed / 0 failed**（exit 0，本批未命中历史 PTY 抖动）。证据位于 `verification/split-diff-gutter-2026-10-12/entry-bg1/`（`files.sha256` 校验通过，含像素核验与分栏行级几何提取）；契约文件 `reverse-engineering/split-diff-gutter-contract-2026-10-12.md`。

边界：原版 `--codex-diffs-*-number`（`color-mix` 合成）与 `--diffs-column-number-width` 精确列宽/`padding-inline` 内缩未复刻（行号字色沿用 `TextMuted`，列宽由最大位数 + 固定间隙推出，底色沿用正文同色）；分栏 hunk 分隔行展开按钮与两 pane 分隔行圆角/内缩未做；上下文行旧号/新号取用未逐像素比对（本批左旧号、右新号）；pane 底色只覆盖正文文本与行号单元格（与统一视图一致），未铺满整条 pane 宽度的色带；分栏正文选区由「整块」变为每行每侧一个 `SelectionContainer`（行号必须在选区之外），跨行拖选与原版差异未核对；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；`captures/git-worktrees/` 因分栏新增行号与变更侧底色由截图测试按既有机制重写。各批证据独立，整体目标 active。

## 2026-10-12 Diff「变更选项」菜单 BQ：展开/折叠全部 + 自动换行（生产改动）

BG 做完统一/分栏两侧行号后，同一份 Diff 契约里头部还有一组控件：一次开合所有文件卡片、以及把长行折行而不是横向滚动。ASAR 证据 E01–E06：`pull-request-code-review-be17c7c34414.js` 的本地 Diff 头部菜单（`aria-label = codex.review.header.moreOptions`「“变更”选项」）依次是 Refresh、Word wrap 复选项（`CheckboxItem{checked: i.wrap, closeOnSelect: true, onCheckedChange: i.onToggleWrap}`）、Diff 模式项、展开/折叠全部项（`onSelect: i.onToggleExpanded`）、`Separator`；展开/折叠项的**文字与同一个 `expanded` 状态同源**（`i.expanded ? 折叠全部差异 : 展开全部差异`，图标随之切换）；同头部另有独立按钮 `aria-pressed: i.wrap` 的换行开关（`codex.review.wrap.label`「自动换行」）与 `label: I` 的展开/折叠按钮；`inline-file-diff-review-76525498732c.js` 的内联文件 Diff 也在 `wrapLines ? wrap.disable(禁用自动换行) : wrap.enable(启用自动换行)` 间切换同一状态。菜单后半段的 `wordDiffs`（词级差异）/`whitespace.hide`（隐藏空白字符）/`richPreview`/`hideImports`/`loadFullFiles` 复选项不在本批范围。

改法（生产 2 个文件）：`git/GitDiffLines.kt` 把列宽计算提为 `internal gitDiffGutterWidth`，新增 `gitDiffGutterLineAnnotatedString`（逐行单元格 + 底色）并让整块 gutter 复用同一底色判定；`git/GitPageComponents.kt` 给 `GitDiffView` 加 `wrap` 状态，并把每个卡片的开合**上提到视图**（`expandedHeaders: Set<String>`，菜单标签由 `allExpanded` 决定，点击整体置空/填满），`GitDiffFileCard` 改为受控 `expanded` + `onExpandedChange`，新增 `GitDiffOptionsMenu`（紧凑按钮「变更选项」+ `HarnessMenu`，项「自动换行」「展开全部差异/折叠全部差异」），`GitUnifiedDiff(body, wrap)` 在换行开时改走逐行 `GitWrappedUnifiedDiff`（每行一个 `Row{gutter; SelectionContainer{正文}}`，与分栏同构、不再横向滚动），并抽出 `gitDiffLineText`/`gitDiffLineSpan`/`gitDiffUnifiedLineAnnotatedString` 让整块与逐行两条渲染共用同一套判定。

验收：`shared` 定向 `GitDiffLinesTest`+`GitDiffLayoutTest` **26/26**（新增 5）、全模块 **557/557**（上批 552 增 5）；`jvm-app` 定向 `GitDiffViewInteractionTest`+`GitDiffRichPreviewInteractionTest` **8/8**（新增 2）、`GitPageInteractionTest`+`GitSettingsRouteInteractionTest` **13/13**、`GitWorktreesScreenshotTest` **6/6**（新增换行截图并断言 600px 视口内控件不越界、换行正文无横向滚动轴）；全量 `jvm-app` **694 found / 5 skipped / 689 started / 689 passed / 0 failed**（exit 0，上批 692 found 增 2，本批未命中历史 PTY 抖动）。证据位于 `verification/diff-options-menu-2026-10-12/entry-bq1/`（`files.sha256` 8 文件校验通过）；契约文件 `reverse-engineering/diff-options-menu-contract-2026-10-12.md`。

边界：只实现换行与展开/折叠——`wordDiffs`（词级差异，需词级比对）与 `whitespace.hide`（隐藏空白字符，需按空白过滤）未实现；原版工具条上那两个独立按钮与本菜单里的 Refresh/Diff 模式项未按原版复刻（本仓分别是菜单项与工具条「统一/分栏/自动」按钮）；换行只作用于统一视图（分栏本来就按 pane 宽度折行），换行模式下正文选区由「整块」变为每行一个 `SelectionContainer`（与 BG 批分栏同一取舍），跨行拖选与原版未比对；页面顶部 Harness 提示文案未随换行状态改写（换行时「可上下与左右滚动」一句不再适用，已知不一致）；原版 `i.expanded` 的宿主推导与折行后的精确缩进/列宽、`white-space` CSS 未逐条比对，也未复刻 `@container_review-header_(min-width:625px)` 的窄容器折叠；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；`captures/git-worktrees/` 因新增换行截图与正文渲染由截图测试按既有机制重写。各批证据独立，整体目标 active。

## 2026-10-12 后台终端 / 子智能体面板文案 BT：面板中文化（生产改动）

改法（生产 3 个文件）：`panels/ThreadPanels.kt` 把两个标签页标题（后台终端 / 子智能体）、`HarnessTabs` 的 `closeLabel`（改传「关闭{标题}」）、标签容器 label、后台进程生命周期（运行中 / 已退出 · 代码 N / 状态未知）、进程选择占位、刷新/重试按钮、进程输入控件、后台输出各状态、子代理总览与详情各状态、分区标题/空态（进行中 / 已完成 / 没有活跃的子智能体）、更多按钮、详情返回与只读提示全部改为中文，状态徽标与 `stateDescription` 改用新增的 `ChildAgentStatus.label`；`panels/ThreadPanelModels.kt` 为 `SubagentSection` / `ChildAgentStatus` / `ChildTranscriptRole` 新增 `label`（`name` 仍是稳定身份）；`panels/PtyTerminalPanel.kt` 把交互终端选择器的 `TerminalLifecycle` 文案（运行中 / 已退出 (N) / 未知）与退出提示改中文。文案取自 `zh-CN-b05cfd55c534.js` 的 `backgroundTerminalTab.title|noOutput`、`backgroundTasks.title.*`、`subagents.panel.*`、`subagents.status.*`、`backgroundAgents.activeLabel`、`summaryPanel.card.showMore`；无 key 的只读/加载/按钮文案按本仓语气书写并显式标注。

验收：`shared` 定向 `client.panels.*` **77/77**，`shared` 全模块 **557/557**（与上批持平，本批不新增单测）；`jvm-app` 定向 `ThreadPanelsInteractionTest`+`SubagentHistoryInteractionTest`+`TerminalReadingInteractionTest` **28/28**（首轮 26/28，补两处漏改期望）、`HarnessAppThreadPanelsShellTest`+`LiveHarnessConversationInteractionTest` **15/15**、`PtyTerminalPageInteractionTest` **7/7**、`ThreadPanelsScreenshotTest`+`ThreadSummaryActivityPanelInteractionTest` **10/10**；全量 `jvm-app` **694 found / 5 skipped / 689 started / 688 passed / 1 failed**（exit 1，唯一失败为历史抖动类 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，隔离重跑 **3/3** 通过）。证据位于 `verification/panel-localization-2026-10-12/entry-bt1/`（`files.sha256` 11 文件校验通过）；契约文件 `reverse-engineering/panel-localization-contract-2026-10-12.md`。

边界：Codex 的分区标题是「已开启 · {count}」/「完成 · {count}」，本仓保持「进行中 (N)」/「已完成 (N)」的括号结构，属结构性差异未逐字复刻；面板里仍有多条无 Codex key 的 Harness 专有文案（只读输出、各加载/失败态、刷新/重试按钮，`EOF` 保留 ASCII）；`PtyTerminalGrid` 的结构化 `stateDescription` 与跨页面既有的 `Unknown：` 状态前缀有意未改；只覆盖 `client/panels` 生产文件与交互终端选择器，未系统盘点面板/摘要岛之外的同类英文文案；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-12 定时任务删除确认改为模态对话框 BU：删除改弹窗（生产改动）

BT 把面板中文化后，同一份调度页契约里「删除」的二次确认还是详情页内联 `HarnessSurface` 卡片（原文案「删除此任务配置？」/「只删除配置，不取消已运行的任务，也不清理会话历史。」/「确认删除」/「取消」），而 Codex 用的是模态确认对话框。ASAR 证据 E01–E04（`zh-CN-b05cfd55c534.js`）：`inbox.automations.deleteConfirm.title`=`删除 {name}？`、`.confirm`=`删除定时任务`、`.descriptionNoPeriod`=`这将永久删除定时任务，并停止日后的运行`、`.cancel`=`取消`；同资源另有同名却不同命名空间的 `home.artifactTemplates.deleteConfirm.*`（模板删除）与 `settings.dataControls.archivedChats.deleteConfirm.*`（归档聊天删除），本批不采用。

改法（生产 1 个文件）：`scheduling/ScheduledManagementPage.kt` 新增 `deleteDialogFence`（与 `pageFence` 同源但不含 `mutation`），`deleteId`/`onDeleteRequest` 改用它，使删除提交期间确认框不被 fence 关闭；详情页删除确认由内联卡片改为共享 `HarnessConfirmDialog`（标题「删除 {任务名}？」、说明在 Codex 原句后追加「已开始的任务不会被取消，会话历史也不会被清理。」、确认「删除定时任务」、取消「取消」、`HarnessConfirmIntent.Destructive`、`testTag=scheduled-delete-confirmation`），`submitState` 由 `mutation == Delete`（`Submitting`）/ `mutationError`（`Failed`）/ 其余 `Ready` 驱动；`scheduled-delete` 启用条件与 `ScheduledRepository.delete` 语义不变（仍只移除任务配置）。

验收：`jvm-app` 定向 `ScheduledPageInteractionTest` **13/13**（新增 1、改 1）；`shared` 全模块 **557/557**（与上批持平）；全量 `jvm-app` **695 found / 5 skipped / 690 started / 689 passed / 1 failed**（exit 1，唯一失败为真实 git 进程抖动类 `DesktopGitCommitTest.includeUnstagedStagesTrackedAndUntrackedKeepsIgnoredOutThenCommitsVerified`，`Timed out waiting for 15000 ms`，隔离重跑 **10/10** exit 0；本批未命中历史 PTY 抖动）。证据位于 `verification/scheduled-delete-dialog-2026-10-12/entry-bu1/`（`files.sha256` 6 文件校验通过）；契约文件 `reverse-engineering/scheduled-delete-dialog-contract-2026-10-12.md`。

边界：只改确认框的呈现与文案，删除仍只移除任务配置（不取消在途运行、不清理会话历史），说明文案是 Codex `descriptionNoPeriod` 原句 + 本仓诚实补充，未逐字复刻 Codex 是否另有句号/换行变体；对话框在提交期间按钮 loading 的像素细节未与码版比对；本仓其它二次确认（工作树删除、快捷键重置、窗口关闭）未统一改造；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`。各批证据独立，整体目标 active。

## 2026-10-12 Diff「变更选项」隐藏空白字符 BV：忽略空白补丁（生产改动）

BQ 做了 Diff「变更选项」菜单的自动换行与展开/折叠全部，同一菜单里 Codex 还有 `codex.review.whitespace.hide`（隐藏空白字符）。ASAR 证据 E01–E05：`zh-CN-b05cfd55c534.js` 的 `codex.review.whitespace.hide`=`隐藏空白字符`、英文 defaultMessage `Hide white space`、description `Menu item to hide whitespace-only diff changes`；`pull-request-code-review-be17c7c34414.js`/`app-initial-6350d04672d0.js` 显示该状态进入 `method:'review-diff'` 的参数 `hideWhitespace`（Host 端 Diff 输入），另有独立偏好键 `hideDiffWhitespace`。关键判断：它是 Diff 输入而非客户端对已返回文本的过滤。

改法（生产 3 个文件）：`git/GitModels.kt` 给 `GitDiffRequest` 加 `hideWhitespace: Boolean = false`（默认值保持两参构造与既有相等断言不变，`GitDiffSnapshot.request` 原样回显）；`desktop/git/DesktopGitBackend.kt` 的 `diff()` 在标准参数与可选基准 commit 之后、`--` 之前追加 `--ignore-all-space`；`git/GitPageComponents.kt` 给 `GitPatch`/`GitDiffView` 加 `hideWhitespace`/`onToggleHideWhitespace`，`GitDiffOptionsMenu` 新增第三项（key `hide-whitespace`、文案「隐藏空白字符」、`selected` 反映当前值）；`git/GitPages.kt` 切换时 `controller.selectDiff(state.diffRequest.copy(hideWhitespace = it))` 触发重读。

验收：`jvm-app` 定向 `DesktopGitBackendTest`+`GitPageInteractionTest`+`GitDiffViewInteractionTest` **21/21**（新增 2）、`shared` 全模块 **557/557**（与上批持平）、全量 `jvm-app` **697 found / 5 skipped / 692 started / 692 passed / 0 failed**（exit 0，上批 695 found 增 2，本批未命中历史 PTY/真实 git 抖动）。证据位于 `verification/diff-hide-whitespace-2026-10-12/entry-bv1/`（`files.sha256` 校验通过）；契约文件 `reverse-engineering/diff-hide-whitespace-contract-2026-10-12.md`。

边界：`--ignore-all-space`（等价 `-w`）与 Codex 实际送往 Host 的开关是否逐字一致未取证（原版是否用 `-b` 未证实）；未接 `hideDiffWhitespace` 偏好持久化，开关状态仅在页面级 `GitState.diffRequest`，重开页面复位；只实现隐藏空白字符，同一菜单的词级差异/隐藏导入/加载完整文件/Refresh/Diff 模式项与远端 Diff 未实现；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；`captures/git-worktrees/` 由截图用例按既有机制重写（沿用 BF/BG/BQ 先例保留重生成版本）。各批证据独立，整体目标 active。

## 2026-10-12 子 Agent 面板详情节与区块标题 BW：状态 / 委派的任务 + 计数标题（生产改动）

BT 把子 Agent 面板中文化时留下结构性边界：区块标题是「进行中 (N)」「已完成 (N)」（还有「· 等待中 N」），详情视图只有 `model · reasoningEffort` 元信息 + 历史。ASAR 证据 E01–E05：`chatgpt-subagents-panel-e608a4ff5e56.js` 里列表标题 `subagents.panel.active`（进行中，**不带**计数）、`subagents.panel.done`（已完成 · {count}，值=已完成代理数）、Active/Done 的 `visibleItemLimit` 为 4/10；详情组件按序渲染 `subagents.panel.status`（状态）、`subagents.panel.delegatedTask`（委派的任务，`whitespace-pre-wrap` 保留换行）、`subagents.panel.response`（最终回复，正文来自 `statusMessage` 或 `chatgpt-subagent-final-response-query`）；小节结构 `F(e)` = muted 小标题(h2 text-sm font-medium text-tertiary) + 默认色正文(div text-sm leading-6)。

改法（生产 2 个文件）：`panels/ThreadPanelModels.kt` 给 `SubagentSection` 加 `heading(count)`（Active→「进行中」、Done→「已完成 · $count」，`require(count >= 0)`，`label` 保留）；`panels/ThreadPanels.kt` 的 `SubagentSectionView` 标题改用 `section.heading(all.size)`（删掉只服务旧标题的 `waiting` 统计），`ChildDetailPanel` 头部下新增两个 `DetailSection`（「状态」取 `summary.status.label`，「委派的任务」取 `summary.objective` 且仅非空白时渲染、保留换行，tag 分别为 `subagent-detail-status`/`subagent-detail-task`），并新增私有 `DetailSection(title, tag, body)`。两节只读已有 summary 字段，lineage-checked 历史仍在下方。

验收：`shared` 定向 `ThreadPanelsStateTest` **13/13**（新增 1）、`shared` 全模块 **558/558**（上批 557 增 1）；`jvm-app` 定向 `SubagentHistoryInteractionTest`+`ThreadPanelsInteractionTest`+`ThreadPanelsScreenshotTest` **25/25**（新增 1）、全量 `jvm-app` **698 found / 5 skipped / 693 started / 693 passed / 0 failed**（exit 0，上批 697 found / 692 started 增 1，本批未命中历史 PTY/真实 git 抖动）。证据位于 `verification/subagent-detail-sections-2026-10-12/entry-bw1/`（`files.sha256` 校验通过）；契约文件 `reverse-engineering/subagent-detail-sections-contract-2026-10-12.md`。

边界：**「最终回复」小节未实现**——Codex 的正文来自子代理 `statusMessage` 或专门的最终回复查询，本仓 `SessionDescendant`/`ChildHistoryResult` 无该数据源，不渲染以免虚构完成响应；Codex 详情只有状态/委派的任务/最终回复三节、没有完整会话记录，本仓在其上仍渲染 lineage-checked 历史（结构性差异，未做同一滚动容器）；状态体在 Codex 里 `failed` 标红而本仓 `ChildAgentStatus` 无 Failed，故无红色态；只覆盖子智能体面板标题与详情节，后台终端页/线程摘要岛不改；无原生窗口/像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；`captures/scheduled-execution/` 全量跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`；`captures/git-worktrees/` 全量运行重写但字节未变（组合 sha 仍 `c55cad68…`）。各批证据独立，整体目标 active。

## 2026-10-12 Worktrees「已知对话」只读列表 BX：按 cwd 匹配已加载会话并打开历史（生产改动）

BW 处理子 Agent 面板后，同一份 Worktrees 契约里 Codex 还有 `settings.worktrees.row.conversations`（「已知对话」）。ASAR 证据 E01–E04：`zh-CN-b05cfd55c534.js` 的 `settings.worktrees.row.conversations`=「已知对话」、`.conversations.empty`=「未找到关联到此工作树的对话」、`.conversations.loading`=「正在加载对话…」、`settings.worktrees.conversation.untitled`=「无标题对话」；`worktrees-settings-page-ad5cbb37d745.js` 里该块是 `div.flex.flex-col.gap-1` 的「标签 + 三态（加载 spinner / 空态文本 / 列表）」，列表项为整宽 `button`（`rounded-lg px-row-x py-row-y text-start text-sm`，hover 背景），内容为 `span.truncate{ e.title 或 无标题对话 }`，点击以该工作树的 `hostId` 打开对话；该 key 的英文自述明确口径是「conversations linked by execution directory or already loaded attachments; other attachment owners may be missing」；行组件 `En({worktree, nativeConversations, visibleConversations, hostId, isConversationsLoading})` 渲染的是 `visibleConversations`，而 `Dn(e)=e.isPinned` / `On(e)=e.isPinned||!e.isSubagent` 只用于「新建聊天」源与删除禁用原因。

改法（生产 3 个文件）：`git/GitModels.kt` 新增纯函数 `worktreeConversations(root, sessions)`（根与每条 cwd 都 `trimEnd('/')` 后精确匹配、空根返回空列表、按 `createdAt` 降序再按 id 升序）；`git/GitPages.kt` 的 `WorktreesPage` 新增 `sessions`/`onOpenSession` 参数，新增私有 `WorktreeConversations` 渲染「已知对话」标签（`worktree-conversations-<dir>`）、空态（`worktree-conversations-empty-<dir>`）与逐条 `HarnessRow`（`worktree-conversation-<id>`），并同时接入 Git 工作树卡片与 Managed 登记卡片；`app/HarnessApp.kt` 把当前 Host 的 `liveSessions` 与既有 `selectPage(PageId.Thread(id))` 传入。

验收：`shared` 定向 `WorktreeConversationsTest` **3/3**、全模块 **561/561**（上批 558 增 3）；`jvm-app` 定向 `WorktreePageInteractionTest` **8/8**（新增 1）、13 个类合集 **41/41**；全量 `jvm-app` **699 found / 5 skipped / 694 started / 693 passed / 1 failed**（exit 1，唯一失败为历史 PTY 抖动类 `PtyTerminalPageInteractionTest.focusedTypingPasteKeysAndResizeUseRealController`，隔离重跑 **7/7** exit 0，上批 698 found 增 1）。证据位于 `verification/worktree-known-conversations-2026-10-12/entry-bx1/`（`files.sha256` 校验通过）；契约文件 `reverse-engineering/worktree-known-conversations-contract-2026-10-12.md`。

边界：本仓 `SessionSummary` 无 title/`isPinned`/`isSubagent`，列表项以会话 id 起头、cwd 作副标题，无「无标题对话」回退、无 `isSubagent` 过滤、无加载态（会话列表本就是已加载数据），只覆盖「执行目录关联」这一半口径（不主张「已加载附件关联」）；「在此工作树中新建聊天」、删除按钮与 `settings.worktrees.row.pinned` 禁用原因不在本批；`captures/git-worktrees/` 因卡片新增该块由全量截图用例重写 416 个文件（几何 JSON 新增 `worktree-conversations-*` 标记节点，属实质变化，保留重生成版本），`captures/scheduled-execution/`（20）与 `captures/plugins-page-2026-10-08/`（64）按先例从批次前字节还原并逐文件 sha256 校验字节一致（后者差异仅为每次运行都会变的 Compose 节点 id）；本批无原生窗口证据（见 BY 批）。

## 2026-10-12 定时任务页与插件页原生窗口截图 BY：screencapture 恢复（证据）

复刻计划第 4 项（定时任务）与第 5 项（插件管理）长期携带「原生窗口待验收」边界，原因是本机 `screencapture` 自 2026-10-11 13:03 起持续返回 `could not create image from display`。本批开始时复测：`screencapture -x -t png /tmp/probe-shot.png` 成功写入一张 2560x1440 实屏图（该探针是屏幕内容，未入库），据此判定阻塞解除。

本批不改产品代码：用仓库既有 `scripts/capture-desktop-window.sh`，以 `--size 1440x900 --destination plugins|scheduled --theme dark|light|system`、每次独立临时 Harness home 各跑一次，按 CoreGraphics window id 截出 6 张图（均为 1440x900 RGBA）：`scheduled-native-{dark,light,system}-1440x900.png` 是本批新增；`plugins-native-{dark,light,system}-1440x900.png` AL 已有，本次重跑的 sha256 与 AL 记录**逐字节一致**（`97082a6d…`/`c205d15d…`），说明该链路可复现。主机外观 Light，`system` 与 `light` 字节完全相同。新增纯标准库校验脚本 `measure-native-pages.py`（不依赖 Pillow，输出 `PROBLEMS 0`）：交通灯彩色像素 bbox `(8,8,59,19)`、标题条分隔线 `y=44`、Rail/分隔/nav/边界 `51,52,340,341`，与 C1.5B 的 Settings 及 AL 的插件库存原生截图逐像素一致；Navigator 内容像素 1969–3873、正文 6628–18821，说明两页确实渲染了内容而不是空窗口。证据位于 `verification/native-pages-scheduled-plugins-2026-10-12/entry-by1/`（`files.sha256` 校验通过），并已写入 `native-window-acceptance.md` 的 Batch BY 小节。

顺带发现（本批未修改）：插件页在隔离空 home 下的「库存不可用」状态仍渲染英文 `Local plugin inventory or source is invalid, unsupported, or unavailable.` 与 `Retry` 按钮，同页其余文案为中文——错误态与共享 `HarnessRow` 的默认 `retryLabel` 尚未本地化，列为后续批次候选。

边界：6 张截图都是**空/不可用状态**（每次独立临时 home），不代表已登记库存或已建任务的状态，因此第 4/5 项的「原版同状态像素」仍未关闭；未做与仓库内 2560x1440 整屏原版参考图的同状态像素比对（两者坐标系不同，本批只与本仓自己的 Settings/插件原生截图核对壳层几何）；`system` 在本机等于 Light，未覆盖第二种系统外观；未覆盖 `--destination settings` 与其它页面的原生窗口；截图中的示例模板与空状态不构成功能证据。各批证据独立，整体目标 active。

## 2026-10-12 插件页错误态与能力标题本地化 BZ：中文错误句 + 重试 + MCP 服务器/钩子/脚本（生产改动）

BY 批顺带记录了一处用户可见缺陷：插件页在隔离空 home 下的「库存不可用」错误态渲染英文整句 `Local plugin inventory or source is invalid, unsupported, or unavailable.` 与 `Retry` 按钮，同页其余文案为中文。定位到文字来源后本批关闭它。ASAR 证据（`zh-CN-b05cfd55c534.js`）：官方 zh-CN 把通用重试动作 `Retry` 译作 `重试`（表中出现 1438 次，引导片段 `n=\`重试\``）；能力类术语译作 `MCP 服务器`（44 次）/`钩子`（50 次）/`脚本`（36 次），而 `MCP Servers`、`Scripts` 各 0 次、`Hooks` 仅 7 次（均为代码标识）。计数与引导片段见证据目录。

文字流向（本批定位）：`DesktopUserPlugins.invalid()` → `DesktopUserPluginsException(默认 sanitizedMessage)` → `DesktopInstalledPluginRepository.guarded` 的 `catch (error: DesktopUserPluginsException)` → `PluginRepositoryError(error.message)` → `InstalledPluginsController` → `PluginLoadState.Error` → `InstalledPluginsPage.PluginReadFeedback` → `HarnessErrorState(message, retryLabel)`。

改法（生产 2 个文件）：`modules/bundle/desktop` 的 `DesktopUserPluginsException` 默认 `sanitizedMessage` 由英文整句改为「本机插件库存或来源无效、不受支持或不可用。」（仍是唯一脱敏异常，不保留原始消息/内容/原因；显式中文消息不变）；`apps/shared` 的 `InstalledPluginsPage.PluginReadFeedback` 错误分支显式传 `retryLabel = "重试"`（不再落到共享默认 `Retry`），并把 `capabilityLabel` 的 `MCP Servers`/`Hooks`/`Scripts` 改为 `MCP 服务器`/`钩子`/`脚本`。

验收：`desktop` 定向 `DesktopUserPluginsTest` **28/28**（新增默认消息断言）；`jvm-app` 定向 `*Plugin*Test` **61/61**、`PluginsPageInteractionTest` **9/9**（新增「error-state 内存在重试、全页无 Retry」）；`shared` 定向 `client.plugins.*Test` **20/20**、全模块 **561/561**（与 BX 持平）；全量 `jvm-app` **699 found / 5 skipped / 694 started / 693 passed / 1 failed**（exit 1，唯一失败为历史 PTY 字体抖动类 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，隔离重跑 `PtyTerminal*Test` **10/10** exit 0——同族的 `PtyTerminalPageInteractionTest`（BX 曾抖动）本次亦通过）。证据位于 `verification/plugin-error-localization-2026-10-12/entry-bz1/`（`files.sha256` 10 文件校验通过）；契约文件 `reverse-engineering/plugin-inventory-localization-contract-2026-10-12.md`。

边界：共享默认值未改——`HarnessErrorState`/`HarnessRow`/`HarnessContentState.Error` 的 `retryLabel` 默认仍是 `Retry`，本批只在插件页调用点显式传中文（`SettingsConnectionsPage` 错误态无重试按钮，不涉及）；`DesktopUserPluginsConflict` 的英文消息未本地化（它不会到达界面，仓储层 `catch` 已映射为中文，已核实为非用户可见项）；未做原版同状态像素比对（本仓插件页与原版插件页不是同一族界面，本批只证明文案语言与本仓一致性）；无新增原生窗口截图（BY 已覆盖原生壳层）；`captures/plugins-page-2026-10-08`（64）与 `captures/scheduled-execution`（20）在全量跑后被重写，与本批快照逐文件 sha256 比对仅这 84 个文件字节变化（前者为本地化内容已在快照内的运行间非确定 id 抖动，后者与本批无关），已按先例从快照还原，还原后 captures 与快照差异 **0**；插件页失败态截图 JSON 现已含「重试」且不含 `"Retry"`（见证据 `plugins-capture-localization-check.txt`）。各批证据独立，整体目标 active。

## 2026-10-12 线程摘要岛「定时任务」小节 CA：只读列出绑定会话的定时任务（生产改动）

计划第 6 项长期把摘要岛的其它小节（含「定时任务」）列为未做。ASAR 证据 E01–E05（`zh-CN-b05cfd55c534.js` / `summary-section-258c26458664.js` / `compact-cadence-c1690916d81f.js`）：小节标题取 `automations.scheduled.destination.label`=「定时任务」、计数用 `M.SectionCount`、错误时追加 `chatgptConversations.summaryPanel.scheduled.retry`=「重试加载已安排的任务」的重试行；每行是 `M.ItemButton`（图标 + 任务名 + `compact-cadence` 的 `variant:"summary"` 元信息），点击打开该自动化；cadence token 为 已暂停 / 每小时 / 每 N 小时 / 每天 · {time} / 周一至周五 / 每周X。抄录见 `verification/thread-summary-scheduled-section-2026-10-12/entry-ca1/logs/asar-evidence.txt`。

改法（生产 5 个文件）：`scheduling/ScheduledTaskSummary.kt` 新增纯函数 `scheduledSummaryCadence(task)` 映射上表 token；`summary/ThreadSummaryActivity.kt` 新增常量 `ThreadSummaryScheduledTitle`/`ThreadSummaryScheduledRetryLabel`；`summary/ThreadSummaryModels.kt` 新增 `threadSummaryScheduledTasks(tasks, sessionId)`（只保留 `ScheduledTarget.Heartbeat` 且 sessionId 相等者、null/cron 返回空、保序）；`summary/ThreadSummaryPanel.kt` 新增 5 个参数与私有 `ScheduledSection`（加载态「正在读取定时任务…」、标题+计数、逐条 `SummaryRow`、错误重试行），仅在「有条目/加载/出错」时出现；`app/HarnessApp.kt` 由真实 `scheduledState.effective.tasks` + 当前 Thread SessionId 过滤并传入，`onOpenScheduled`/`onRetryScheduled` 仅在 `scheduledBindingReady` 时非 null。

验收：`shared` 定向 `ThreadSummaryModelsTest` **8/8**、`ScheduledTaskSummaryTest` **16/16**，全模块 **563/563**（上批 561 增 2）；`jvm-app` 定向 `ThreadSummaryPanelInteractionTest` **5/5**（新增 2）、全量 `jvm-app` **701 found / 5 skipped / 696 started / 695 passed / 1 failed**（exit 1，唯一失败为历史 PTY 字体抖动 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，隔离重跑 `PtyTerminal*Test` **14/14** exit 0，上批 699 found 增 2）。证据位于 `verification/thread-summary-scheduled-section-2026-10-12/entry-ca1/`（`files.sha256` 9 文件校验通过）；契约文件 `reverse-engineering/thread-summary-scheduled-section-contract-2026-10-12.md`。

边界：计数只统计**绑定到该会话**的定时任务（无原版 local automations 合并、无 `showCollapsedCount` 折叠）；无 `display_schedule` 透传，`everyMinutes`/`monthly`/`weekends`/`monitoring`/`completed`/一次性 `date` 等 token 未实现（本仓 `ScheduledRule` 只有 Hourly/Daily/Weekdays/Weekly）；行内图标用既有 `HarnessIconName.Clock`（字形未复刻），点击打开本仓「定时任务」页（无原版侧边面板），重试重载整个存储（原版逐查询 refetch），任何读取错误都显示重试（原版区分 404）；无原生窗口/同状态像素证据；摘要岛其余小节（Sources/Artifacts/Usage/Outputs/Plan/created-tasks/pull-requests/side-chats/triggers/environment-creation）仍未做。`captures/plugins-page-2026-10-08/`（64，仅 id 抖动）与 `captures/scheduled-execution/`（20，故意冻结的历史基线，早于 `ScheduledLocalExecutionModel` 披露文案）在全量跑后按先例从批次前字节还原，`diff -rq` 与快照差异 **0**，组合 sha `ac8ebb54…`（与既有记录一致）。各批证据独立，整体目标 active。

## 2026-10-12 Worktrees「在此工作树中新建聊天」 CB：绑定工作树目录并回到 Home 新建会话（生产改动）

CA 之后，同一份 Worktrees 契约里 Codex 还有 `settings.worktrees.row.newChat`（「在此工作树中新建聊天」）——BX 批已实现同行的「已知对话」，本批收口这一同族动作。ASAR 证据 E01–E04：`zh-CN-b05cfd55c534.js` 的 `settings.worktrees.row.newChat`=「在此工作树中新建聊天」、`.row.newChatDescription`=「使用相同文件和分支开始全新聊天」、`.row.title`=「工作树」、`.row.delete`=「删除」、`.row.pinned`=「请先取消置顶此工作树中的聊天，再删除此工作树。」；`worktrees-settings-page-ad5cbb37d745.js` 的行组件 `En({worktree, nativeConversations, visibleConversations, hostId, isConversationsLoading})` 里 `g = nativeConversations.find(On)`、`v = nativeConversations.some(Dn)`，`On(e)=e.isPinned||!e.isSubagent`、`Dn(e)=e.isPinned`；按钮可见性 `w = (host.kind === 'local' && !(record?.projectKind === 'local' && mt(record.projectId)))`（仅本机 Host 且源对话非本机项目会话时可见），`disabled = f`（忙碌/加载态），`onClick = () => ht(s, {sourceConversationId: g?.id, workspaceRoot: n.dir})` —— 以**同文件同分支**开启全新聊天，源对话取 `find(isPinned || !isSubagent)` 的 id，工作目录取该工作树目录，不携带会话历史。

改法（生产 2 个文件，均在 `apps/shared`）：`git/GitPages.kt` 的 `WorktreesPage` 新增 `onNewChatInWorktree: (String) -> Unit` 参数，并在**本地 Git 工作树卡片**内（`WorktreeConversations(...)` 之后、「读取此工作树」之前）新增 `HarnessButton("在此工作树中新建聊天", { onNewChatInWorktree(worktree.path) }, enabled = state.pageReady && !worktree.bare && !worktree.prunable, testTag = "git-worktree-new-chat")`；`app/HarnessApp.kt` 新增 `pendingWorktreeChat` 状态，`onNewChatInWorktree = { path -> pendingWorktreeChat = path; selectPage(PageId.Home) }`，Home 组合器在 `page == PageId.Home` 时渲染待定提示（`local-worktree-chat-pending`）+「取消」（`local-worktree-chat-cancel`），`LocalProjectPicker.onSelect` 与取消清除待定；提交时若存在待定目录则 `CreateSessionOptions(cwd = 该目录)` 并在 `onSessionCreated` 清除待定，不走项目关联分支——只在用户于 Home 发送第一条消息时才创建会话。

验收：`shared` 全模块 **563/563**（与 CA 持平）；`jvm-app` 定向 `WorktreePageInteractionTest` **9/9**（新增 1）、`GitSettingsRouteInteractionTest` **8/8**（新增 1）；全量 `jvm-app` **703 found / 5 skipped / 698 started / 697 passed / 1 failed**（exit 1，唯一失败为历史 PTY 字体抖动 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，隔离重跑 **3/3** exit 0，上批 701 found 增 2）；`shared` 全量与 jvm-app 全量分别以 `--build-dir=/tmp/harness-cb-targeted -p jvm -m <module>` 运行。证据位于 `verification/worktree-new-chat-2026-10-12/entry-cb1/`（`files.sha256` 9 文件校验通过）；契约文件 `reverse-engineering/worktree-new-chat-contract-2026-10-12.md`。

边界：只在本地 Git 工作树卡片上加动作（Managed 卡片未加，范围收窄）；可见性门控用 `pageReady && !bare && !prunable`，未复刻 `projectKind` 判断；本仓 `SessionSummary` 无 `isPinned`/`isSubagent`/`projectKind`，未做「用置顶或首个非子代理对话作源」也不携带 `sourceConversationId`；未复刻按钮图标与行内 `newChatDescription` 说明行；无原生窗口/同状态像素证据。`captures/git-worktrees/` 因卡片新增该按钮由全量截图用例重写 432 个文件（几何 JSON 含 `git-worktree-new-chat` 标签的文件 0 → 220，属实质变化，保留重生成版本）；`captures/plugins-page-2026-10-08/`（64，仅 id 抖动，43 个差异叶全为 id 命名、图片 sha 不变）与 `captures/scheduled-execution/`（20）按先例从批次前快照还原，`diff -rq` 差异 **0**，组合 sha `ac8ebb54…`（与既有冻结基线一致）。各批证据独立，整体目标 active。

## 2026-10-12 Managed 工作树「在此工作树中新建聊天」 CC：把 CB 的动作扩展到 Managed 卡片（生产改动）

CB 把 `settings.worktrees.row.newChat`（「在此工作树中新建聊天」）接到**本地 Git 工作树**卡片，并明确把 Managed 卡片列为范围收窄。原版行组件 `En({worktree, nativeConversations, visibleConversations, hostId, isConversationsLoading})` 为**每个工作树行**渲染该动作，不区分「本地 Git 工作树 / Managed 登记」——该拆分是本仓自己的呈现（沿用 CB 证据，见 `verification/managed-worktree-new-chat-2026-10-12/entry-cc1/logs/asar-evidence-reused.txt`）。本批把同一动作扩展到 Managed 卡片。

改法（生产 1 个文件，`apps/shared`）：`git/GitPages.kt` 的 `ManagedWorktreeSection` 新增 `onNewChatInWorktree: (String) -> Unit` 参数并由 `WorktreesPage` 透传；在 Managed 卡片的「已知对话」块之后新增 `HarnessButton("在此工作树中新建聊天", { onNewChatInWorktree(entry.root) }, enabled = verified && pageReady && entry.worktree?.let { !it.bare && !it.prunable } == true, testTag = "managed-worktree-new-chat")`。`HarnessApp` 无需再改（CB 已把 `onNewChatInWorktree` 接为 `pendingWorktreeChat = path; selectPage(PageId.Home)`，对 Managed 卡片同样生效）。

验收：`shared` 全模块 **563/563**（与 CB 持平）；`jvm-app` 定向 `WorktreePageInteractionTest` **10/10**（新增 1 个 Managed 用例）、`ManagedWorktreeInteractionTest` **3/3**；全量 `jvm-app` **704 found / 5 skipped / 699 started / 699 passed / 0 failed**（exit 0，上批 703 found 增 1；CB 的历史 PTY 抖动本次未复现）。证据位于 `verification/managed-worktree-new-chat-2026-10-12/entry-cc1/`（`files.sha256` 7 文件校验通过）；契约文件 `reverse-engineering/managed-worktree-new-chat-contract-2026-10-12.md`。

边界：归属门控要求 `verified` 且有可用 Git 条目（非 bare/prunable），`Missing` 或无 Git 条目/未核验的行按钮禁用（原版是否有等价门控未观察到）；与 CB 相同未携带 `sourceConversationId`、未复刻按钮图标与 `newChatDescription` 说明行；无原生窗口/同状态像素证据。`captures/git-worktrees/` 因 Managed 卡片新增该按钮由全量截图用例重写 504 个文件（几何 JSON 含 `managed-worktree-new-chat` 标签的文件 0 → 188，属实质变化，保留重生成版本）；`captures/plugins-page-2026-10-08/`（64，仅 id 抖动，43 个差异叶全为 id 命名）与 `captures/scheduled-execution/`（20）按先例从批次前快照还原，`diff -rq` 差异 **0**，组合 sha `ac8ebb54…`（与既有冻结基线一致）。各批证据独立，整体目标 active。

## 2026-10-12 Diff「词级差异」强调 CD：把默认开启的 wordDiffsEnabled 开关接到统一/分栏 diff 行内（生产改动）

CC 之后，diff 显示设置里只剩最后一项**默认开启**的开关没接。ASAR 证据 E01–E04：`app-initial-6350d04672d0.js` 里 `sja = 'wordDiffsEnabled.2'`、`mja = Fu(sja, !0)`（同区 `wrapCodeDiff.2`/`hideDiffWhitespace`/`diffRichPreview` 都是 `!1`，只有词级差异默认开）；`pull-request-code-review-be17c7c34414.js` 的 diff 选项菜单组件 `jr({…, wordDiffsEnabled, onToggleWordDiffs})` 用 `be.CheckboxItem` 呈现它，文案 `codex.review.wordDiffs.label`、`defaultMessage: 'Word diffs'`、`description: 'Toggle for highlighting individual changed words in the diff view'`；`zh-CN-b05cfd55c534.js` 译为 `"词级差异"`。抄录见 `verification/word-level-diff-2026-10-12/entry-cd1/logs/asar-worddiffs.txt`。

改法（生产 3 个文件，均在 `apps/shared/src/im/hikaru/harness/client/git/`）：新增 `GitWordDiff.kt`——`GitWordSpan`/`GitWordDiff`、`gitWordTokens`（词/空白/单字符三类 run）、`gitWordDiff(old,new)`（公共词前缀 + 后缀 trim，中间整体标为变化；相等为空、整行不同为整行；相邻 span 合并）、`gitUnifiedWordDiff(lines)`（同一 hunk 内按序配对 `-`/`+` 行，context/header/meta flush，未配对尾部行不产生行内 span）、`gitSplitWordDiff(rows)`（只处理 `Change` 行）、`appendWordSpans`（强调段用 `SpanStyle(background = emphasis)`，**逐字符仍只出现一次**），强调 alpha `WORD_DIFF_EMPHASIS_ALPHA = 0.35f`（Harness 取值，强于行背景 `0x1A`/`0x2E`）；`GitDiffLines.kt` 的 `gitDiffSplitPaneText(...)` 新增可选 `wordDiff`/`wordAddedBackground`/`wordRemovedBackground`，在既有整行背景**之内**叠加词级强调，默认参数保持旧行为；`GitPageComponents.kt` 的 `GitDiffView` 新增 `wordDiffs`（默认 `true`），`GitDiffOptionsMenu` 新增第 4 项 `HarnessMenuEntry.Item(key = "word-diffs", text = "词级差异", selected = wordDiffs)`，`GitDiffFileCard`/`GitUnifiedDiff`/`GitUnifiedDiffScroll`/`GitWrappedUnifiedDiff`/`GitDiffSplitView` 全链路传参，并新增 `gitDiffLineMarker` + `appendWordDiffLine` 把整行拆成「marker（不强调）+ 文本（可强调）」。

验收：`shared` 全模块 **573/573**（CC 563 增 10，正是新增 `GitWordDiffTest`）；`jvm-app` 定向 `GitWorktreesScreenshotTest` **7/7**（新增 1 个用例 `GitWordLevelDiff`）；全量 `jvm-app` **706 found / 5 skipped / 701 started / 700 passed / 1 failed**（exit 1，CC 704 found 增 2；唯一失败仍是历史 PTY 用例 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，本次隔离重跑 7 次里 **1** 次 3/3 通过、其余 6 次同一断言超时，故记为**未决的间歇性抖动**而非环境结论，另附 `terminal.PtyTerminalConnectionIntegrationTest` **4/4** 证明终端栈可用）。证据位于 `verification/word-level-diff-2026-10-12/entry-cd1/`（`files.sha256` 校验通过）；契约文件 `reverse-engineering/word-level-diff-contract-2026-10-12.md`。

边界：强调色、分词边界与配对算法均为 Harness 取值（原版 token/算法未观察到，未做像素比对）；原版是 `CheckboxItem`，本仓沿用既有「变更选项」菜单的 `✓` 选中写法；开关未持久化（原版写 `wordDiffsEnabled.2`），只存在页面组合状态；「变更选项」菜单其余项（复制 `git apply` 命令、隐藏 import、加载完整文件、refresh）仍未做；无原生窗口/同状态像素证据。本批**没有改动任何既有截图**：`captures/git-worktrees/` 相对批次前快照 `/tmp/cd-captures-before-Z1ulD1` 逐文件 sha256 为 `added 48 / removed 0 / changed 0`（48 = 新用例 24 PNG + 24 JSON）；全量 `jvm-app` 重写的所有截图目录跑前先快照 `/tmp/cd-full-before-0XfOfM`、跑后 `rsync -a` 还原并放回本批 24 个 PNG/JSON，还原后与跑前快照只差新用例的 24 个 JSON 节点 id（0 个 PNG 变化），新用例 24 个 PNG 在三次独立运行间逐字节相同；像素层面 on/off 两张图**只有一个色带不同**（例：light 1440x900 `x 704..749, y 730..746`，`rgb(229,246,236)` → `rgb(166,223,188)`，见 `logs/word-emphasis-pixels.txt`）。另注：全量运行在该卷 `captures/rail/` 留下 2 个无法 stat 的陈旧目录项 `.ghost-0000000000000000509{3,4}`（文件系统残留，非本批产物，unlink 无法清除），已如实记录。各批证据独立，整体目标 active。

## 2026-10-12 Diff「隐藏导入内容」 CE：把 codex.review.imports.hide 开关接到统一/分栏 diff（生产改动）

CD 之后，「变更选项」菜单里 ASAR 明确存在、尚未实现的下一项是「隐藏导入内容」。ASAR 证据 E01–E04（抄录见 `verification/hide-imports-2026-10-12/entry-ce1/logs/asar-hide-imports.txt`）：`app-initial-6350d04672d0.js` 里 `rja = 'hideDiffImports'`、`pja = Fu(rja, !1)`（默认**关闭**；同区 `hideDiffWhitespace`/`wrapCodeDiff.2`/`diffRichPreview` 也是 `!1`）；`pull-request-code-review-be17c7c34414.js` 的 diff 选项菜单用 `be.CheckboxItem` 呈现它，文案 `codex.review.imports.hide`、`defaultMessage: 'Hide imports'`、`description: 'Menu item to hide top-level import-only diff changes'`，`checked`/`onCheckedChange` 绑 `hideImports`/`onToggleHideImports`；`zh-CN-b05cfd55c534.js` 译为 `"隐藏导入内容"`；同一对 prop 也出现在 `thread-side-panel-tab-content-e9ce64182d53.js`。**负结果**：逐 asset 扫描整个解包 bundle，`hideImports` 只有「设置键」与「透传 prop」两类用法，没有任何 import 正则 / 行分类函数 / 后端参数——判定规则在原版不可见。

改法（生产 2 个文件，均在 `apps/shared/src/im/hikaru/harness/client/git/`）：新增 `GitDiffImports.kt`——`gitDiffIsImportOnlyContent(text)` 要求已去 marker 的行**无前导缩进（顶层）**且命中 `import …`/`from … import …`/`#include …`/`use …`/`using …`/`require(…)`/`@import …`；`gitDiffHiddenImportLines(lines)` 丢掉 `Added`/`Removed` 且为顶层 import 的行（保留 context/hunk 头/meta），`gitDiffHiddenImportRows(rows)` 对分栏行同规则（`Change` 行须**两侧都是** import），`gitDiffHiddenImportHunks(hunks)` 逐 hunk 应用并保留 hunk 头；`GitPageComponents.kt` 的 `GitDiffView` 新增 `hideImports`（默认 `false`），`GitDiffOptionsMenu` 新增第 5 项 `HarnessMenuEntry.Item(key = "hide-imports", text = "隐藏导入内容", selected = hideImports)`，`GitDiffFileCard`/`GitUnifiedDiff`/`GitUnifiedDiffScroll`/`GitWrappedUnifiedDiff`/`GitDiffSplitView` 全链路传参，`gitUnifiedDiffAnnotatedString(...)` 末尾新增带默认值的 `hideImports`。过滤为**逐行省略**：被隐藏行既不出现在可选中文本也不出现在行号栏，幸存行保留各自记录的 `oldNumber`/`newNumber`，故行号栏不错位；hunk 头始终保留（统一/分栏一致）。

验收：`shared` 定向 `GitDiffImportsTest` **7/7**、全模块 **580/580**（CD 573 增 7）；`jvm-app` 定向 `GitDiffViewInteractionTest` **7/7**（新增 1）、`GitWorktreesScreenshotTest` **8/8**（新增 1）；全量 `jvm-app` **708 found / 5 skipped / 703 started / 702 passed / 1 failed**（exit 1，CD 706 found 增 2；唯一失败为 Managed 工作树**真实鼠标**用例 `WorktreePinInteractionTest.realMousePinAndUnpinPersistToFreshReader`，隔离重跑 7 次里 **6** 次 4/4 通过、1 次失败的是**同族另一条** `realMouseArchiveAndUnarchivePersistToFreshReader`——失败者身份每次运行都可能不同，故记为**未决的间歇性抖动**而非本批回归；本批符号只被 git 页面/测试引用）。证据位于 `verification/hide-imports-2026-10-12/entry-ce1/`（`files.sha256` 19 文件校验通过）；契约文件 `reverse-engineering/hide-imports-contract-2026-10-12.md`。

边界：import 判定规则是 Harness 取值（原版分类器未观察到；只认「无缩进的 import 形式」，不区分文件头部区域，也不按语言分支）；原版是 `CheckboxItem`，本仓沿用既有「变更选项」菜单的 `✓` 选中写法；开关**未持久化**（原版写 `hideDiffImports`），只存在页面组合状态；「变更选项」菜单其余项（`refresh`、加载完整文件、复制 `git apply` 命令）仍未做；顺带观察到 ASAR 对「隐藏空白字符」的描述是 `hide whitespace-only diff changes`，与本仓「host 侧 `--ignore-all-space` 重读」口径不一致，已记入契约待后续核对，本批不改。截图：`captures/git-worktrees/` 新增 **48** 文件（24 PNG + 24 JSON，`git-hide-imports-*`）；既有点击菜单对比图 `git-word-level-diff-*-options-menu-word-diffs-{on,off}`（8 PNG + 8 JSON）因新菜单项发生**实质变化**而保留重生成版本；全量运行重写的其余 **252** 文件（20 个与本批无关的 `scheduled-execution/*.png` + 232 个仅 Compose 节点 id 抖动、去数字后完全一致的 `.json`）按先例从批次前快照 `/tmp/ce-captures-before-1791792006` **逐字节还原**，还原后与该快照差异为 `new 48 / changed 16 / removed 0`；像素层面 `imports-shown` vs `imports-hidden` 差异只落在 diff 正文区（例：light 1440x900 `x 360..1075, y 568..802`，12480 像素，见 `logs/hide-imports-pixels.txt`），菜单开/关为 200x188 整块差异（13678 像素）。各批证据独立，整体目标 active。

## 2026-10-12 Diff「加载完整文件」 CF：把 codex.review.loadFullFiles.enable 开关接到 diff 视图（生产改动）

CE 之后，「变更选项」菜单里 ASAR 明确存在、尚未实现的下一项是「加载完整文件」。ASAR 证据 E01–E04（抄录见 `verification/load-full-files-2026-10-12/entry-cf1/logs/asar-load-full-files.txt`）：`zh-CN-b05cfd55c534.js` 的 `"codex.review.loadFullFiles.enable"`=「加载完整文件」；`pull-request-code-review-be17c7c34414.js` 的 diff 选项菜单用 `be.CheckboxItem` 呈现它，`defaultMessage: 'Load full files'`、`description: 'Menu item to load full file contents for partial diffs'`，`checked`/`onCheckedChange` 绑 `loadFullFilesEnabled`/`onToggleLoadFullFiles`；`code-diff-38f339f0a601.js` 的缓存键构造 `Gt({…, loadFullFilesEnabled, …})` 把该开关**编入 diff cache key**，升级门控 `qe = … && diff.metadata.isPartial && !isBinary && !isGitlink && !type-new && !type-deleted && !renamePure && …` 说明它**只对被截断的 partial 文件**尝试加载整文件内容。**负结果**：逐 asset 扫描整个解包 bundle，`loadFullFiles` 只有「组件 prop」「cache key」两类用法；原版「按 per-file OID 取整对象 → 客户端 differ → 重算」所依赖的 per-file OID 与客户端行级 differ 在**本仓不存在**，无法照抄。

改法（生产 4 个文件）：`apps/shared/.../client/git/GitModels.kt` 的 `GitDiffRequest` 新增 `loadFullFiles: Boolean = false`（带 ASAR 依据注释）；`apps/jvm-app/.../desktop/git/DesktopGitBackend.kt` 的 `diff()` 在 `--ignore-all-space` 之后、路径分隔 `--` 之前插入 `if (request.loadFullFiles) args.add("--unified=$FULL_FILE_CONTEXT_LINES")`，文件末尾新增 `private const val FULL_FILE_CONTEXT_LINES = 100_000`；`apps/shared/.../client/git/GitPageComponents.kt` 的 `GitPatch`/`GitDiffView` 新增 `loadFullFiles`+`onToggleLoadFullFiles`（默认 `false`），`GitDiffOptionsMenu` 在「展开」之后、「隐藏空白字符」之前插入第 3 项 `HarnessMenuEntry.Item(key = "load-full-files", text = "加载完整文件", selected = loadFullFiles)`，`onSelect` 增 `"load-full-files" -> onToggleLoadFullFiles(!loadFullFiles)`，全链路传参；`apps/shared/.../client/git/GitPages.kt` 的 `GitPatch(...)` 接线为 `loadFullFiles = state.diffRequest.loadFullFiles, onToggleLoadFullFiles = { controller.selectDiff(state.diffRequest.copy(loadFullFiles = it)) }`。开关是**请求级**的：切换后控制器用新 `GitDiffRequest` 重读一次快照，Host 以 `--unified=100000`（大于任何现实文件行数）把 hunk 上下文补满，从而展开为整文件上下文。

验收：`shared` 全模块 **580/580**（与 CE 持平）；`jvm-app` 定向 `DesktopGitBackendTest` **10/10**（新增 1）、`GitDiffViewInteractionTest` **8/8**（新增 1）、`GitWorktreesScreenshotTest` **9/9**（新增 1）；全量 `jvm-app` **711 found / 5 skipped / 706 started / 705 passed / 1 failed**（exit 1，CE 708 found 增 3；唯一失败为历史 PTY 字体抖动 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，与 BZ 批全量的唯一失败同一条，隔离重跑 **3/3** exit 0，另注 CE 的历史抖动是 `WorktreePinInteractionTest`，本批未复现）。证据位于 `verification/load-full-files-2026-10-12/entry-cf1/`（`files.sha256` 12 文件校验通过）；契约文件 `reverse-engineering/load-full-files-contract-2026-10-12.md`。

边界：原版「per-file OID + 客户端 differ」路径未实现，本仓用 Host 全上下文重算代替（结果可观察一致、机制不同，已如实记入契约 §2.1）；`isPartial` 门控未实现（本仓无 partial 标记，开关对请求内所有文件统一加宽）；`maxObjectBytes`/`too-large` 回退未实现；开关**未持久化**（只存在页面组合状态，重开复位）；菜单顺序与 ASAR（`loadFullFiles → richPreview → wordDiffs → hideWhitespace → hideImports → copyGitApplyCommand`）略有出入；「变更选项」菜单其余项 `refresh`、`richPreview`、复制 `git apply` 命令仍未做；无原生窗口/同状态像素证据。截图：`captures/git-worktrees/` 新增 **48** 文件（24 PNG + 24 JSON，`git-load-full-files-*`）；既有点击菜单对比图 `git-word-level-diff-*-options-menu-word-diffs-{on,off}` 与 `git-hide-imports-*-options-menu-hide-imports-{on,off}`（16 PNG + 16 JSON）因菜单新增第 3 项发生**实质变化**（切换后含 `menu:load-full-files` 节点）而保留重生成版本；全量运行重写的其余 **264** 文件（20 个与本批无关的 `scheduled-execution/*.png` 历史冻结基线 + 244 个仅 Compose 节点 id 抖动、去数字后完全一致的 `.json`）按先例从批次前快照 `/tmp/cf-captures-before-1791795676` **逐字节还原**，还原后与该快照差异为 `new 48 / changed 32 / removed 0`；新用例 24 个 PNG 在隔离（`HARNESS_GIT_WORKTREES_CAPTURE_DIR=/tmp/cf-captures`）与全量两次运行间逐字节一致（`identical=24 / differing=0`，24 个 JSON 仅节点 id 抖动）；像素层面开关前后差异只落在 diff 正文区（例：dark/light 1440x900 `x 360..1075, y 568..802`，3544/3553 像素；dark 600x800 `x 28..583, y 468..702`，3224 像素，见 `logs/load-full-files-pixels.txt`）。各批证据独立，整体目标 active。

## 2026-10-12 Diff「刷新」 CG：把 codex.review.refreshGitQueries 动作项接到只读刷新（生产改动）

CF 之后，「变更选项」菜单里只剩最后一项普通动作没接。ASAR 证据 E01–E03（抄录见 `verification/git-refresh-2026-10-12/entry-cg1/logs/asar-refresh.txt`）：`zh-CN-b05cfd55c534.js` 的 `"codex.review.refreshGitQueries"`=「刷新」，紧邻 `"codex.review.refreshGitQueries.inProgress"`=「正在刷新更改」；`pull-request-code-review-be17c7c34414.js` 里 `defaultMessage: 'Refresh'`、`description: 'Menu item to refresh git queries for the current repository'`，菜单组件 `Er(...)` 的 props 为 `onRefreshGitQueries` / `refreshGitQueriesDisabled` / `showRefreshGitQueries`——是**普通动作项**（不是 `onToggle*` 勾选项），读取进行中禁用。

改法（生产 2 个文件，均在 `apps/shared`）：`client/git/GitPageComponents.kt` 的 `GitPatch` 新增 `onRefresh: () -> Unit = {}`、`refreshDisabled: Boolean = false`，`GitDiffView` 同名透传，私有 `GitDiffOptionsMenu` 新增同两名参数并追加第 7 项 `HarnessMenuEntry.Item(key = "refresh", text = "刷新", enabled = !refreshDisabled)`、`onSelect` 增 `"refresh" -> onRefresh()`；`client/git/GitPages.kt` 两处 `GitPatch(...)` 调用点接线 `onRefresh = controller::refresh`、`refreshDisabled = !state.pageCanRead`，并新增 `lastReadyDiff`（`remember(state.binding, state.selectedCwd, state.diffRequest)` + `SideEffect`）使刷新期间保留上一次完成的 patch 可见。刷新走既有 `GitController.refresh()`（`GitController.kt:397`，只做读：不取消创建、不释放准入槽），`GitState.pageCanRead` 在读取中为 false 故刷新项自动禁用。

验收：`shared` 全模块 **580/580**（与 CF 持平）、定向 `GitControllerTest` **11/11**；`jvm-app` 定向 `GitDiffViewInteractionTest` **9/9**（新增 1），`GitWorktreesScreenshotTest` **9/9**（隔离输出到 `/tmp/cg-captures`）；全量 `jvm-app` **712 found / 5 skipped / 707 started / 707 passed / 0 failed**（exit 0，CF 711 found 增 1；本批 **0 失败**，CF 的 PTY 字体抖动未复现）。证据位于 `verification/git-refresh-2026-10-12/entry-cg1/`（`files.sha256` 11 文件校验通过）；契约文件 `reverse-engineering/git-refresh-contract-2026-10-12.md`。

边界：原版进行中文案 `codex.review.refreshGitQueries.inProgress`（「正在刷新更改」）**未接入**（只做禁用态，不新增进度 UI）；刷新范围复用既有 `GitController.refresh()`（状态/分支/工作树/提交快照/diff 一并刷新），原版是否区分「只刷新 diff」与「刷新全部查询」未观察到；「刷新期间保留旧 patch 可见」是 Harness 取值；菜单顺序与 ASAR 不同（本仓把 `refresh` 放最后一项）；`richPreview` 移入菜单、复制 `git apply` 命令、`autoSwitchToUnified` 仍未做；无原生窗口/同状态像素证据。截图：本批**未新增**截图目录；`git-word-level-diff-*` / `git-hide-imports-*` / `git-load-full-files-*` 三组 `options-menu-*` 截图（24 PNG + 24 JSON）因菜单新增第 7 项发生**实质变化**（切换后含 `"menu:refresh"` 节点）而保留重生成版本；全量运行重写的其余 **280** 文件（20 个与本批无关的 `scheduled-execution/*.png` 历史冻结基线 + 260 个仅 Compose 节点 id 抖动、去数字后完全一致的 `.json`）按先例从批次前快照 `/tmp/cg-captures-before-1791799682` **逐字节还原**，还原后与该快照差异为 `new 0 / changed 48 / removed 0`；隔离重跑 `GitWorktreesScreenshotTest` 后 `docs/desktop-ui/captures/` 未被增删，且这 48 个变化文件中 24 个 PNG 与隔离运行**逐字节一致**（`identical=24 / differing=0`）；像素层面四个抽样菜单弹窗一致地由 `200x224` 变为 `200x260`（`+36px`，正好一行），新增行有 607 个非背景像素（既有末行 845），见 `logs/menu-pixels.txt`。各批证据独立，整体目标 active。

## 2026-10-12 Diff「复制 git apply 命令」 CH：把 codex.review.copyGitApplyCommand 动作项接到剪贴板（生产改动）

CG 之后，「变更选项」菜单里 ASAR 明确存在、尚未实现的普通动作只剩「复制 git apply 命令」。ASAR 证据 E01–E03（抄录见 `verification/git-apply-command-2026-10-12/entry-ch1/logs/asar-copy-apply.txt`）：`zh-CN-b05cfd55c534.js` 三条相邻文案 `"codex.review.copyGitApplyCommand":"复制 git apply 命令"`、`"…patchError":"未能生成 git apply 命令"`、`"…toast":"已将 git apply 命令复制到剪贴板"`；`pull-request-code-review-be17c7c34414.js` 的菜单组件 `jr(...)` props 为 `onClickCopyGitApplyCommand`/`copyGitApplyCommandDisabled`/`showCopyGitApplyCommand`——是**普通动作项**（同菜单的 `richPreview` 是 `be.CheckboxItem`，本批不动）。**负结果**：命令的具体形式、失败判定条件、toast 渲染位置在 bundle 中均不可见。

改法（生产 3 个文件）：`apps/shared/.../client/git/GitDiffApply.kt`（新增）——`gitApplyCommand(patch): String?` 生成 quoted heredoc（`git apply <<'HARNESS_GIT_APPLY_EOF'` + 完整 patch + 分隔符，冲突时加 `_1`/`_2`…），patch 为空/全空白或无 `diff --git ` 行时返回 `null`（均为 Harness 取值）；`client/git/GitPageComponents.kt` 的 `GitPatch` 新增 `copyGitApplyCommandDisabled`，内部用 `LocalClipboardManager` + `remember(patch) { gitApplyCommand(patch) }` 计算命令与反馈，**把反馈透传进 `GitDiffView` 并在其末尾渲染**（`testTag = git-diff-copy-git-apply-feedback`），菜单追加第 8 项 `HarnessMenuEntry.Item(key = "copy-git-apply", text = "复制 git apply 命令", enabled = !copyGitApplyCommandDisabled)`；`client/git/GitPages.kt` 两处 `GitPatch(...)` 接线 `copyGitApplyCommandDisabled = !state.pageCanRead`。实现要点：反馈**不**用外层 `Column` 包 diff——那样会把 `GitDiffView` 原本贡献给调用方布局作用域的子节点（提示语/工具栏/视口）重新挂到新 `Column` 下，将其间距从 `SettingsFormSection` 的 16dp 变成 8dp，造成与 ASAR 无关的整页位移；改为在 `GitDiffView` 末尾渲染后，无反馈时子节点集合与该功能加入前逐字节一致（本批先由 6.1-sol 子 Agent 实现，主线程修正了这一处并补齐测试）。

验收：`shared` 全模块 **584/584**（CG 580 增 4，正是新增 `GitDiffApplyTest`）；`shared` 定向 `GitDiffApplyTest` **4/4**；`jvm-app` 定向 `GitDiffViewInteractionTest` **10/10**（新增 1）、`GitWorktreesScreenshotTest` **9/9**（隔离输出到 `/tmp/ch-captures-park`）；全量 `jvm-app` **713 found / 5 skipped / 708 started / 708 passed / 0 failed**（exit 0，CG 712 found 增 1；本批 **0 失败**，CF 的 PTY 字体抖动与 CG 记录过的 `GitCommitInteractionTest` 超时均未复现）。证据位于 `verification/git-apply-command-2026-10-12/entry-ch1/`（`files.sha256` 11 文件校验通过）；契约文件 `reverse-engineering/git-apply-command-contract-2026-10-12.md`。

边界：原版用 toast 提示，本仓无全局通知组件，改为 diff 区域内联 `HarnessText`（成功/失败文案与原文一致，位置不同）；命令形式（heredoc）与失败判定（无 `diff --git ` 行）均为 Harness 取值（未观察到）；**未在真实仓库执行生成的命令**（只有命令格式与 patch 完整保留的证据）；提示在切换 patch/重开后消失（`remember(patch)`），未复刻 toast 自动消失时长；`richPreview` 移入菜单、`autoSwitchToUnified` 仍未做；无原生窗口/同状态像素证据。截图：本批**未新增**截图目录；`git-word-level-diff-*` / `git-hide-imports-*` / `git-load-full-files-*` 三组 `options-menu-*` 截图（24 PNG + 24 JSON）因菜单新增第 8 项发生**实质变化**而保留重生成版本；像素层面四个抽样菜单弹窗一致地由 `200x260` 变为 `200x296`（`+36px`，正好一行），新增带 `rows 260..295` 有 1171/1175 个非背景像素（见 `logs/menu-pixels.txt`）；全量运行重写的其余 **280** 文件（20 个与本批无关的 `scheduled-execution/*.png` 历史冻结基线 + 260 个仅 Compose 节点 id 抖动、去数字后完全一致的 `.json`）按先例从批次前快照 `/tmp/ch-captures-before-1791803364` **逐字节还原**，还原后与该快照差异为 `new 0 / changed 48 / removed 0`；隔离重跑 `GitWorktreesScreenshotTest` 两次逐字节一致（`logs/captures-reproducibility.txt`）。另有一处截图文案外的可见差异：菜单多一行后弹层变高，测试合成指针在关闭菜单后的落点改变，`git-hide-imports` 两例 600x800 视口截图会带上 Diff 来源选择控件的**悬停底色**（`SurfaceSelected` 55 vs `SurfaceElevated` 40，差异 bbox `rows 343..382 / cols 16..583`）；在该 `focus()` 截图前调用既有 `parkGitPointer()` 后两张图回到基线逐字节一致且**仅影响这 2 张图**（`logs/park-pointer.txt`）。各批证据独立，整体目标 active。

## 2026-10-12 Diff「渲染预览」勾选项 CI：把 codex.review.richPreview.label 接入「变更选项」菜单（生产改动）

CH 之后，「变更选项」菜单里 ASAR 明确存在、尚未实现的勾选项只剩 `richPreview`。ASAR 证据 E01–E03（抄录见 `verification/rich-preview-menu-2026-10-12/entry-ci1/logs/asar-rich-preview.txt`）：`zh-CN-b05cfd55c534.js` 的 `"codex.review.richPreview.label"`=「渲染预览」，另两条相关 `"codex.diffView.richPreviewToggle"`=「切换富文本预览」、`"codex.diffView.richPreviewLoading"`=「正在加载预览…」；`pull-request-code-review-be17c7c34414.js` 的菜单用 `be.CheckboxItem{checked,onCheckedChange,…}` 呈现它（勾选项），由 `showRichPreview` 门控；`editor-diff-page-86fa0f0062f3.js` 的 diff 页工具栏另有同名开关（tooltip），即原版**工具栏 + 菜单两个入口都有**。本仓 AW 批已有工具栏按钮 `GitDiffRichPreviewToggle`（保留不动），本批补菜单项。

改法（生产 1 个文件，`apps/shared`）：`client/git/GitPageComponents.kt` 的私有 `GitDiffOptionsMenu` 新增 `richPreview: Boolean` + `onToggleRichPreview: (Boolean) -> Unit`，`entries` 在 `load-full-files` 之后插入第 4 项 `HarnessMenuEntry.Item(key = "rich-preview", text = "渲染预览", selected = richPreview)`，`onSelect` 增 `"rich-preview" -> onToggleRichPreview(!richPreview)`，`GitDiffView` 调用处与 AW 批已有 `richPreview` 状态同源接线；沿用 CH 教训，无外层 `Column`、无布局结构变化（本批先由 deepSeek v4.1 flash 子 Agent 实现，主线程核对并补齐测试）。

验收：`shared` 全模块 **584/584**（与 CH 持平）；`jvm-app` 定向 `GitDiffRichPreviewInteractionTest` **4/4**（新增 1，上批 3）、`GitDiffViewInteractionTest` **10/10**、`GitWorktreesScreenshotTest` **9/9**（隔离输出 `/tmp/ci-captures-park`，两次运行 `diff -rq` 逐字节一致）；全量 `jvm-app` **714 found / 5 skipped / 709 started / 709 passed / 0 failed**（exit 0，CH 713 found 增 1）。证据位于 `verification/rich-preview-menu-2026-10-12/entry-ci1/`（`files.sha256` 18 文件校验通过）。

边界：原版是 `be.CheckboxItem`，本仓沿用既有「变更选项」菜单的 `✓ selected` 写法；`richPreview` 未持久化（只存在页面组合状态）；本仓既有的 `hide-whitespace`/`word-diffs` 相对次序与 ASAR 仍有出入、`refresh` 置于末段（遗留，本批不改）；「变更选项」菜单其余项（图片/SVG/PDF 预览、行内评论、sticky 吸附、上下文展开、`autoSwitchToUnified`、远端 Diff、大 diff 自动折叠的实际执行、工作树列表/移除、上游 fetch）仍未做；无原生窗口/同状态像素证据。截图：本批**未新增**截图目录；`git-word-level-diff-*` / `git-hide-imports-*` / `git-load-full-files-*` 三组 `options-menu-*` 截图（24 PNG + 24 JSON）因菜单中段插入第 4 项发生**实质变化**而保留重生成版本（PNG 由 `200x296` 变为 `200x332`，+36px 正好一行；插入点在「加载完整文件」之后，故新项以下整体下移一行，最佳对齐 new=base+36，228/296 行匹配）；全量运行重写的其余 **548** 文件（20 个与本批无关的 `scheduled-execution/*.png` 历史冻结基线 + 528 个仅 Compose 节点 id 抖动、去数字后完全一致的 `.json`，其中 `git-worktrees` 464、`plugins-page-2026-10-08` 64）按先例从批次前快照 `/tmp/ci-captures-before` **逐字节还原**，还原后与该快照差异为 `new 0 / changed 48 / removed 0`。另附 PTY 归属：修复上界前的一次全量 `jvm-app` 有 4 个失败，其中 3 个是本批必须修的弹层上界断言（`GitWorktreesScreenshotTest` 的 `captureGitHideImports`/`captureGitLoadFullFiles`/`captureGitWordLevelEmphasis`），第 4 个是历史 PTY 抖动 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（30s 超时）；修复上界后的全量该用例通过（全量 0 失败），但单独跑 3 次均 2/3（同一条 30s 超时，与 CD/CE/CF 记录同族，且本批生产符号与终端栈无关）。各批证据独立，整体目标 active。
