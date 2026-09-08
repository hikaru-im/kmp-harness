# Outcome

将当前 `session-persistence` 从 JVM JSON 文件实现改为 Room 3 KMP 持久化 Provider。Session 继续以
append-only event log 为唯一事实来源，Agent、AgentLoop、Tools 和 LLM Retry 不感知底层数据库类型。

默认给 Harness Host 使用独立的 Session Room 数据库；RuoYi-KMP 的 AppDatabase 不作为默认依赖，但通过
中立的数据库访问接口保留可选复用能力。

# Scope

- 把 `SessionPersistence` 变为 KMP/Room 可用的公共边界，移除公共 API 中的 JVM `Path`。
- 在 `modules/session-persistence` 内实现 Room 3 entity、DAO、数据库、迁移和 Plugin。
- 以 `harness_sessions`、`harness_session_events` 等带 Harness 前缀的表保存 Session Header 和事件 Envelope。
- 在 Room transaction 中实现 flush、load、事件校验、Inbox replay、未闭合 turn/step 修复和 llm-retry 恢复。
- 由平台启动层注入数据库工厂、SQLite driver、查询 coroutine context 和数据库位置；Plugin 不读取环境变量，
  不创建 Android Context，也不决定 App 数据库路径。
- 提供独立数据库与 AppDatabase 复用的适配 seam；当前仓库不依赖 `ruoyi-kmp`，也不把两个
  `RoomDatabase` 实例指向同一个文件。
- 复用 `ruoyi-kmp` 已验证的 Room 3/SQLite 技术栈和迁移测试方法，先验证 JVM，再验证 Android；记录 iOS
  Room/KSP 工具链限制。

# Non-goals

- 不保留 `modules/session-persistence-file`，不继续维护 JVM JSON 文件 Provider。
- 不让 `Session`、`AgentLoop` 或 `LlmRuntime` 直接依赖 Room 类型。
- 不把 AppDatabase 的业务实体、Koin 模块或 RuoYi 包名引入 Harness modules。
- 不允许两个独立 `RoomDatabase` 实例共享同一个 SQLite 文件。
- 不在本 change 中实现 RuoYi-KMP 跨仓库代码修改；复用模式只提供接口和适配边界。
- 不在 Session 表中复制一份并行的 Message、Inbox 或 Retry 状态；这些仍从事件日志派生。
- 不因 iOS 编译暂时受 Room Compiler/KSP 版本问题阻塞 JVM/Android 实现，但不能把 iOS 标记为已验收。

# Acceptance examples

### Scenario: Room flush/load preserves the event log

WHEN a Session appends header-dependent events, messages, request context, tool events and retry events, then flushes
and a new Runtime loads the same id, THEN the event envelopes, derived messages, request folds, Inbox replay and pending
retry recovery equal the original logical state, and no JSON session file is created.

### Scenario: Room flush is transactional and idempotent

WHEN concurrent or repeated flushes target the same Session, THEN the database commits one contiguous event sequence,
does not duplicate `(sessionId, seq)` rows, and a failed transaction leaves the previous committed snapshot readable.

### Scenario: Room recovery repairs an interrupted turn once

WHEN a persisted Session ends with an open turn/step and has no pending durable retry, THEN load appends one structured
interrupted repair and a second load does not append another repair; WHEN a pending retry exists, THEN load leaves retry
continuation for `llm-retry` recovery.

### Scenario: App database integration is explicit

WHEN the Host is configured with the default storage, THEN it opens a dedicated Harness Session Room database with its
own file, schema and migration version; WHEN an embedding App supplies the optional Session database adapter, THEN the
same adapter can be used without making `session-persistence` depend on the AppDatabase class, and two RoomDatabase
instances are never opened against one file.

### Scenario: Platform factory owns database construction

WHEN Desktop or Android starts the Host, THEN the platform supplies the Room builder/driver/query context and the
Plugin only installs `SessionPersistence`; the Plugin does not read environment variables, choose a global home, or
access an Android `Context`.

- **A1**：Room flush/load preserves the complete Session event log, derived messages, request folds, Inbox replay and
  pending retry recovery; no JSON session file is created.
- **A2**：Repeated or concurrent flushes are transactional and idempotent; `(sessionId, seq)` remains unique and a
  failed transaction leaves the previous committed snapshot readable.
- **A3**：Load repairs an interrupted turn/step exactly once, while a pending durable retry defers repair to retry
  recovery.
- **A4**：Default Host storage uses an independent Harness Room database; optional App integration uses only a neutral
  adapter and never opens two RoomDatabase instances against one SQLite file.
- **A5**：Desktop/Android platform factories own Room builder, driver, query context and location; the Plugin only
  installs the persistence service and has no platform or environment lookup.

# Constraints and invariants

- `SessionEventEnvelope.seq` remains contiguous and `(sessionId, seq)` is unique.
- Header and event writes are committed atomically for one flush.
- Unknown required events, invalid envelope metadata, invalid serializer shape and invalid provenance are rejected
  before a partial Session is registered.
- Unknown `ignorable=true` events remain round-trippable.
- Durable Session content remains credential-free and provider-neutral.
- Room schema changes use explicit migrations; destructive migration is forbidden.
- The Session persistence interface remains usable by future non-Room providers even though this change ships only Room.

# Decisions

- 默认存储：独立 `harness-sessions.db`，不并入 RuoYi `ruoyi-kmp.db`。
- App 复用：只通过 `SessionDatabaseAccess`/数据库工厂等中立接口可选接入；复用同一个 RoomDatabase 实例时由
  App 负责把 Harness entities 纳入自己的 schema 和 migration，Harness 不反向依赖 AppDatabase。
- 物理实现：直接改造现有 `modules/session-persistence`，不新建或保留 JVM JSON 文件模块。
- 配置归属：数据库位置和 Room 实例由 Desktop/Android 等平台 launcher 注入，Profile 只选择插件，不携带
  任意平台 Context。
- Desktop 默认 Profile：本 change 先完成可安装 Provider 和独立测试 fixture；在 Session/Agent 链完整接入前，
  不自动把 persistence 行加入当前五项 Desktop 生产 Profile。
- 兼容边界：不迁移已有 JVM JSON 文件；当前 JSON 数据视为原型数据，Room 从新数据库开始。
- 验收边界：JVM 与 Android 为本 change 的必过平台；iOS 若受 Room/KSP 工具链限制则记录为 blocked，不阻塞
  本 change，也不宣称 iOS 已验收。
- 依赖基线：采用 `androidx.room3:room3-runtime:3.0.0`、SQLite `2.7.0` 和 KSP `2.3.10`，与
  `ruoyi-kmp` 保持一致。

# Open questions
暂无。

# Verification expectations

- JVM：Room schema、DAO、transaction、migration、flush/load/recovery 全部通过。
- Android：Room/KSP 编译和最小数据库读写通过。
- iOS：若工具链限制仍存在，明确记录为 blocked，不宣称全平台完成。
- 全仓：现有 Session、Agent、AgentLoop、Tools、Retry 契约不回归。
