# Worktree 历史会话目录：只读持久层合同

状态：父线程已分析；插件本地更新验收完成后交由同一 SHUAIAPI/gpt-6.1-sol 工作者实施。

前置分析：[worktree-session-catalog-analysis-2026-10-09.md](worktree-session-catalog-analysis-2026-10-09.md)。这是 Worktrees 真实会话保护的依赖，不代表完整 Worktrees 功能交付。

## 实施范围

只允许改动以下两文件：

1. `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt`
2. 新增 `modules/session-persistence/test/im/hikaru/harness/session/persistence/SessionPersistenceHeaderCatalogTest.kt`

不得改 schema/version/migration、已有 flush/load/inbox/repair 行为或既有测试；不接入客户端、Host、Git 删除、pin/archive。不依赖新库，不读取用户真实数据库。父线程负责构建测试和验收，工作者不运行构建或测试。

## 类型与查询

- 为 `SessionPersistence` 新增 `suspend fun listHeaders(): List<SessionHeader>? = null`。默认 null 表示旧实现不支持目录；Room 返回非 null，空集合表示可用且当前数据库没有持久 header。异常表示本次查询失败，禁止把失败转成空集合或不完整成功。
- DAO 新增单条 SELECT 全部 `harness_sessions` header 的查询；固定 `created_at ASC, session_id ASC` 排序。不查询 events、不分页、不按 cwd/member 筛选。
- Room 的 listHeaders 复用 provider mutex 和 disposed 检查；只有读取，不进入 write transaction，也不触发 load/flush/repair、SessionStore 或生命周期事件。
- 返回本次独立集合，每个 header 使用已有 toHeader 构造与校验。调用者修改返回集合不得影响后续查询。不共享可变缓存，不持有 Room row 对象。
- 任意 header 行转换失败时整次查询失败，使用已有 INVALID_HEADER 类型与原始 cause；禁止跳过行。保留数据库读取错误，不宽泛捕获取消异常。进入查询和转换循环时尊重协程取消；取消时不返回成功结果。
- 该目录只覆盖此数据库的持久 headers，不包含尚未 flush 的 live 会话，不声称是完整 Host/member 会话目录，不提供删除锁或 pin/archive 状态。

## 必须提供的真实 Room 验证

用临时数据库和现有 SessionPersistencePlugin/Test 平台桥覆盖下列行为；测试数量由独立行为决定，不用镜像实现凑数量：

- 默认能力 null 与 Room 空目录的区别。
- flush 前 live 会话不在目录；flush 后出现，字段与真实 header 一致。
- 关闭和重启 Runtime 后，未 load 的历史会话仍可列出且 live store 仍为空。
- 多 cwd、无 cwd、父子关系、origin/seed/depth/preset 保留；同 createdAt 时以 id 排序。
- 返回集合的调用者更改不污染下次读取。
- 实际写入一个非法 header 行后，整次目录失败；其他有效行不能形成部分成功。
- 持久事件存在中断 turn 或无效 JSON 时，目录仍只列 header，不读取/解析/修补事件；读取前后的事件行完全相同。
- 读取前后 live store 及 Created/Appended/Flush/Disposed 观察计数不变。
- disposed 后查询拒绝；取消的目录请求保持取消，后续正常读取仍可用。
- 与真实 flush 并发的读取只观察完整 header，查询不写数据库；不得用睡眠或返回预设成功的 fake DAO 代替上述集成证据。

## 交付与父线程验收

工作者交付 exact write set、每文件 before/after SHA、新增测试名/数量、模型与 provider、tests_run=false、builds_run=false、write_set_released=true。父线程从已验收的插件候选副本叠加这两个文件冻结，运行新增目录 suite 以及既有 SessionPersistenceContractTest 和 SessionPersistenceDatabaseFactoryTest。保留真实 XML、命令日志、源码输入 manifest 与失败记录。

只有上述验收完成，下一切片才设计 Host 绑定的持久与 live 合并、cwd 归属、连接/权限 fencing。历史 schema 没有 member 字段；成员过滤的子集不能证明 Host 历史会话不存在。
