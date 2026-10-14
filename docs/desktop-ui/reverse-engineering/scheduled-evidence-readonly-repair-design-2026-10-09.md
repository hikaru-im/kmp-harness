# 定时任务恢复证据只读化：待复现后的最小修复方案

状态：真实Accepted开放回合已复现生产误判，首轮3/5。实施授权已定稿于 scheduled-evidence-readonly-repair-contract-2026-10-09.md；本文以下保留派发前的设计历史。

## 已确认的调用路径

- `DesktopScheduledSessionEvidence.inspect` 在 live store 中无会话时调用 `context.persistence.load`。
- `RoomSessionPersistence.load` 先读取并验证持久 snapshot，再 `store.load`，随后 `repairOpenTurn` 追加 Interrupted TurnEnd 并 flush。
- `terminalEvidence` 可将这个新补写事件识别为 Interrupted；事件的精确 inputMessageId 匹配不能证明该结果来自崩溃前的进程。
- 当前 `SessionPersistence` 公共 API 无只读事件能力；内部已有 `readSnapshot`、`validateSnapshot` 和 `toEnvelope`，可以复用，不需要另建数据库或事件解析器。

## 若第四窗口实际失败，修复只改两个生产文件

1. `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt`
   - 给 `SessionPersistence` 增加可选的只读持久事件能力，默认明确表示不支持，以保持旧适配器兼容。
   - Room 实现复用现有 mutex、disposed 检查、snapshot 读取与校验，核对 header 的 id。
   - 返回事件快照；不调用 store.load、repairOpenTurn、flush、Agent 恢复或 lifecycle 发布。
   - 取消按原异常传播；读失败保留原因。保持 schema、既有 load 的恢复语义和 header catalog 不变。
2. `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidence.kt`
   - 对历史会话只调用新的只读能力；不支持时返回 Unavailable，不能 fallback 到 load。
   - live 会话仍通过现有真实 flush 将终态持久化，再认可精确持久事件。
   - 没有对应 TurnEnd 保持 Pending；原 exact input、turn、seq、reason 检查保留。

具体 API 名称、缺失会话返回和 live flush 的最终顺序在实际失败证据确认后定稿。不得为了通过用例直接排除全部 Interrupted：真实旧进程已经持久化的 Interrupted 仍应被认可。

## 必须新增或保留的证据

- 当前 crash 五窗口预期保持原合同：首四 Unknown，第五只修复账本，零次恢复 create/prompt/provider。
- 只读能力对真实开放回合返回已有 UserMessage/TurnStart 且不补 TurnEnd；读取前后 DAO 行内容、live sessions 和 lifecycle 不变。
- 已有真实持久 Completed/Interrupted 仍被读取；错误输入不能误认。
- malformed 事件和无能力适配器不能变为空的成功结果；取消保持取消语义。
- 既有普通 load 的开放回合修补继续成立，避免修复扩散到正常会话恢复。

派发前父线程保存新的 before SHA 和完整候选。工作者仍使用 SHUAIAPI/gpt-6.1-sol / opencodex，不 build/test；释放后由父线程冻结并串行验收。生产 Cron 开关保持 false。
