# Session Persistence Room 完整规格

本规格把 `session-persistence` 的唯一持久化 Provider 定义为 Room 3 KMP 实现。Session 核心仍只提供
append-only event log；Room 负责持久化，不拥有消息、Inbox 或 retry 的第二份事实。

## Storage boundary

`SessionPersistence` 是后端无关接口。Room 实现通过平台注入的数据库工厂获得一个 `RoomDatabase`，并在
`harness_sessions` 与 `harness_session_events` 中保存 Header 和 `SessionEventEnvelope`。公共接口不暴露
`java.nio.file.Path`、Android `Context` 或 RuoYi `AppDatabase`。

默认 Host 使用独立数据库文件。可选 App 集成只能通过中立的 DAO/transaction adapter；若 App 选择复用同一
RoomDatabase 实例，App 必须拥有组合后的 entities、schema version 和 migration。两个 RoomDatabase 实例
不得指向同一 SQLite 文件。

## Event and transaction semantics

Room 持久化必须保持事件 seq 连续、`(sessionId, seq)` 唯一、Header 与事件同批提交，并对重复 flush
幂等。读取时必须先完成 envelope metadata、serializer shape、provenance 和 unknown-event policy 校验，
再调用 `SessionStore.load`，不得把半个 Session 放入 live store。未知 required event 必须稳定拒绝，未知
ignorable event 必须保留。

## Platform construction

Desktop、Android 和未来的 iOS launcher 负责数据库位置、Room builder、SQLite driver、查询 coroutine context
和生命周期。Plugin 只把 `SessionPersistence` 注册进 Context，并监听 `session/flush`。它不读取环境变量、
不选择系统目录、不创建平台 Context，也不依赖 RuoYi-KMP 包。

## Scenario: Room flush/load preserves the event log

WHEN a Session appends messages, request metadata, tool events and durable retry events, then flushes and a new
Runtime loads the same id, THEN events, derived messages, request folds, Inbox replay and pending retry recovery are
logically equal and no JSON session file is written.

## Scenario: Room flush is transactional and idempotent

WHEN repeated or concurrent flushes target one Session, THEN one contiguous event sequence exists without duplicate
`(sessionId, seq)` rows, and a failed transaction does not replace the previous committed snapshot.

## Scenario: Room recovery repairs an interrupted turn once

WHEN load sees an open turn/step without pending retry, THEN it appends one interrupted repair and persists it; a second
load adds no second repair. WHEN pending retry exists, THEN repair waits for retry recovery.

## Scenario: App database integration is explicit

WHEN default storage is selected, THEN Harness uses a dedicated Session Room database. WHEN an App supplies the
optional adapter, THEN Harness can use the adapter without importing the AppDatabase class, and no two database
instances share one file.

## Scenario: Platform factory owns database construction

WHEN a supported platform starts the Host, THEN platform code supplies the database construction facts and the Plugin
only installs the persistence service.

## Compatibility and migration

Room schema changes require explicit migrations and migration tests; destructive migration is forbidden. Existing JVM
JSON files are outside this Provider's storage contract and are not migrated by this change; Room starts with a new
database. JVM and Android are required acceptance platforms. iOS source-set/factory work may be included, but a current
Room/KSP toolchain blocker is recorded rather than treated as a failed implementation. Dependencies are pinned to the
`ruoyi-kmp` baseline: Room 3.0.0, SQLite 2.7.0, and KSP 2.3.10.
