# RuoYi Harness Integration 完整目标规格

本规格已于 2026-09-11 获用户确认。验收 ID 统一定义在 brief.md，本文件表达其完整目标行为，不重复生成额外 Scenario 验收。文中 integration 指本文件。

## 基线与范围

对应 A1、A2、A11、A20。选择性迁入 ruoyi-kmp 本地固定版本 b4877d495c3479bf3632f4117a3c62b62c32d209，以本仓库目录和工具链承载。每项引入保留来源/许可证并记录同步、保留或排除原因；构建、启动不得隐式拉取或更新来源。后续同步重新固定 commit、归一化目录/换行、审查行为 diff 并执行受影响回归。

首期提供账号、租户、个人资料、Host 与最小 Agent 会话。既有后端会员/支付等功能保留，但不要求增加相关客户端入口；管理后台维持本仓库管理方式。上游工具目录、未跟踪工作、开发密钥、数据库重置脚本不直接迁入。UI Slot 独立推进。

## 公共契约

对应 A2、A3。所有 RuoYi 公共契约采用 im.hikaru.contracts；以 package/FQCN 和序列化行为审查，路径不同不视为需要两份定义。InfraContracts 与原 FileContracts 合并后只有一个 FileInfo，保留上游文件、配置、Redis 数据语义和既有调用兼容性。测试覆盖 null/default、字段名与实际 JSON 往返。

contracts/harness-protocol、WebSocketMessage 与 Harness 数值错误、requestId、HostId、事件序号保持独立边界。产品源码、测试和构建配置不能留下旧契约 import；历史文档中的旧名仅作历史说明。

签到沿用上游纯在线行为，删除签到的 sync 注册和处理路径并更新相应测试；旧客户端提交签到同步命令稳定拒绝，不执行副作用。地址和个人资料仍遵守其有效 sync contract，不能因移除签到而失效。

## 后端与身份

对应 A4、A7、A14。应用身份为 Member /app-api；Admin 使用原管理后台认证域。登录身份由服务端认证信息决定，以 tenantId、userType、userId 加 HostId 隔离 Host。客户端 payload 不具备覆盖身份或扩大 ACL 的能力。

OAuth2 接口/实现、安全过滤、全局错误和 WebSocket 必须配套合并，保留 raw content 解码、会话生命周期 listener 以及租户上下文的进入和清理。后端能在隔离测试配置启动并处理登录、刷新和拒绝错误租户的请求；已有 Admin 路由不得因引入 Member 客户端而放宽。

本次 Relay 使用单后端实例语义；跨实例 lease/CAS/路由不在范围。在线 Host 列表仅返回同三元身份的可见 Host，离线、替换和失效状态可观察。

## 客户端基础

对应 A5–A8。Ktor client 管理后端地址、认证 header、租户 header、JSON、超时、业务错误和连接可用性；仅向当前受信任且匹配身份作用域的后端发送令牌。Repository 对 UI 暴露领域结果，不吞掉 CancellationException。

密码登录先明确租户，合法响应建立 AppSession。刷新按身份作用域串行化，共享并发过期请求的刷新结果。登录态使用世代/等价的失效机制，确保刷新或登录结果在用户已登出、切换后端或租户后不能恢复旧状态。刷新被服务端拒绝时清理认证状态；网络失败显示可恢复错误；用户取消只取消所属操作。

身份作用域包含后端地址、租户、用户类型与用户 ID。切换任一身份维度前取消旧网络工作、同步、订阅与远程连接，清理或隔离缓存与队列，再激活新作用域；不能把旧任务转换为新用户任务。

登出优先确保本地状态清理；远程注销失败不保留可恢复登录态。普通网络失败不影响 Desktop 本地模式。SMS/社交认证可保留复用接口，但没有配置的能力不成为可误用的首期操作。

## 存储与依赖边界

对应 A6、A8、A9。AppSession 表示服务登录身份；Harness Session 表示 Agent 事件日志，二者无生命周期绑定。客户端 Koin 创建应用依赖，Runtime/Loader 继续管理 Host Plugin，不跨容器接管所有权。

登录令牌留在应用专属平台安全存储，Android 使用 Keystore 支持的加密，iOS 使用 Keychain，Desktop 使用平台安全存储适配。租户等非秘密偏好允许普通应用存储。应用标识/目录不得复用上游 ruoyi-kmp 的账号文件或 Host 模型凭据命名空间。

Desktop 无安全存储时仍允许本次进程内登录，界面明确告知无法记住登录；不回退为明文文件。损坏或不可解密记录不能被当作有效会话，读取失败有可理解的状态；持久化失败不声称已保存成功。日志只记录脱敏错误。

Host 保留独立 harness-sessions.db 和现有 append-only event 语义。客户端业务缓存如采用 Room，拥有独立文件、schema 和生命周期；不得两个实例共用 SQLite 文件，不得 destructive migration。引入前后旧 Host 事件和派生 history 等价。apps/shared 只依赖协议/客户端库，不依赖 Runtime Service；Mobile 不创建 Host。

## 应用行为

对应 A10–A12。共享 HarnessApp 提供 Harness 首页、账号/租户/个人资料、Host 选择与可观察连接状态。Desktop 启动即提供本地 Connection，登录可选；本地模式不依赖后端或第三方模型目录在线。启动参数 --home/--patch 延续当前行为，Host 仅由 launcher 创建/释放，登录/登出不触发重建。

远程入口未认证时进入登录，成功后返回原目标；无在线 Host、连接中、在线、已断线、认证失效、协议不兼容分别有明确状态。首期主导航不增加会员地址、签到、积分或支付入口。

Connection 提供 host.describe 和 session.list/create/history/prompt/cancel。LocalConnection 直接调用 Gateway，不引入 RPC 序列化。会话界面提供显式 provider/model 选择和发送/取消/历史展示；没有完整模型选择时稳定提示配置错误，不能自动选择目录第一项。prompt 的接收确认不代表模型执行完成，增量与运行状态来自事件流，history 由同一事件日志派生。

关闭窗口与平台 owner 销毁时释放其应用作用域、client、数据库和订阅。Desktop 最后关闭 Host；Mobile 生命周期切换只管理客户端，不终止 Desktop Agent。

## 远程行为

对应 A13–A17、A19。Desktop HostRelayConnection 与 Mobile RemoteConnection 都主动连接 RuoYi；移动端不直连 Desktop。注册建立身份绑定和 Host generation，客户端先发现可见 Host，再核对协议版本和能力，满足授权后通过同一 Gateway 调用会话能力。

每个 pending 绑定请求 ID、客户端连接、身份、目标 Host 和 generation；重复 ID、越权、离线目标和不匹配响应稳定拒绝。有限超时必须结束等待并释放注册项。Host 替换/断线、客户端断线和取消都会完成或取消相应 pending，迟到响应不影响当前世代请求。

订阅有明确客户端连接和身份所有权，并绑定 Host generation、stream 和 Session。Relay 只向拥有者发送，不按 tenant/user 广播。事件保留序号，重复可丢弃，乱序或缺口可检测；需要时通过 history 校准。取消订阅、切 Host、登出、断线均清理服务端与客户端资源。

重连执行有限退避、重新认证/握手并重建有效订阅，恢复期间界面保持可观察状态。恢复连接不能默认重发 prompt/create 等结果不确定的执行命令，不能放入 RuoYi Outbox；超时显示结果未知时允许用户刷新 history 决定后续操作。取消只作用于目标 Agent，不终止其他 Session。

远程端点仍遵守 local-only/remote policy。Credentials set/unset、secret Settings 写入、携带临时 key 的模型发现和本地文件操作默认拒绝；实现不以 UI 隐藏替代 Gateway 拒绝。响应、事件、日志不泄露 secret。当前协议不宣称端到端保密。

## 平台与验证

对应 A18–A20。JVM、Android、iOS 分别注入网络 engine、安全存储、数据库和生命周期适配，入口均显示 HarnessApp。依赖按本仓库工具链显式固定，迁入上游版本前完成解析和受影响目标构建检查。

验收先验证契约/身份/存储/连接状态和真实 Gateway，再运行 JVM/Desktop、Android，以及可用 Apple 环境的编译/模拟器检查。真实后端+Desktop+Android 演练必须覆盖登录、发现、创建、显式模型、prompt、流事件、history 与 cancel，并记录脱敏证据及取消的可观察结果。确定性模型 fixture 是自动回归补充，不能代替真实模型远程演练。

缺少服务、模型凭据、设备或 Apple 环境时保留 blocked，记录缺什么、如何重现与下一步。任何未运行检查不得记 passed。数据库配置先在隔离环境验证，不自动对用户数据库执行升级/清空。交付说明覆盖启动配置、部署、停止/回滚、数据库兼容、来源/许可证及后续同步流程。

## 交付依赖

已确认 Supervisor introduce-ruoyi-kmp 统筹三个 Child：ruoyi-foundation（A1–A9）→ ruoyi-app-shell（A10–A12）→ ruoyi-remote-platform（A13–A20）。应用壳用可注入的远程 fixture 验证导航，最后由远程 Child 替换为真实连接并全量复核。所有 Child 集成后父 change 再验收全部 A1–A20。

用户已选择多会话协作（multi-session）。各 Child 只在 Runtime 创建的工作区按任务包实现，由 Supervisor 统筹；实际执行进度以 Runtime 状态为准。
