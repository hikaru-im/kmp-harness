# Worktrees 聊天归档 B：Registry 全段准入合同

## 目标与依据

在已经验收的 Session A1 与 Inbox A2a 上，补齐 AgentRegistry create/restore 及普通 Handle detach 的活动登记。指定实施模型为 **SHUAIAPI/gpt-6.1-sol / opencodex**。父线程先审查生产源码和只读报告，再独立冻结及验收。

只读分析 `/tmp/harness-worktree-chat-archive-owner-followup-analysis-20261010.md` 已 exit0；六个受保护生产文件 SHA 均保持。父线程已核对 AgentRegistry create/restore/detach、SessionAdmission acquireCatalog 和现有 AgentContractTest。其 Runtime lane handoff、driver 和有限 owner 清理建议仍为提案。

当前事实：create 的 factory/publication/notices 未有外层 catalog permit，发布后取消缺少精确 entry 回滚；restore 的 child/replay 在 try 外；普通 Handle 在 detach 前置 disposed，Closing 拒绝后无法重试。

## 精确写集

仅修改：

- `modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt`

仅新增：

- `modules/agent/test/im/hikaru/harness/agent/AgentRegistryAdmissionContractTest.kt`

不新增公共 API。使用既有 `SessionStore.admission.acquireCatalog`，并直接 try/finally dispose permit；`useAdmission` 是 session 模块 internal，不扩大可见性。所有既有测试、fixture 和其它生产文件字节保持。

## create 与 restore

1. 入口解析并保留精确 SessionStore。create 在第一次 Registry mutex 前取得无 target catalog permit；restore 在第一次 Registry mutex 前取得该 Store、该 Session 的 catalog permit。Closing 必须早于 creating、child、replay、factory 或通知。
2. permit 覆盖整个登记、child/Session/Inbox、factory、发布、Restore listener、同步通知、最后 `currentCoroutineContext().ensureActive()` 与失败/取消回滚。不得借助 CoroutineContext 继承活动权限。嵌套普通 Session/Inbox 入口继续各自准入。
3. child 创建、events/Inbox replay、attach 都放入 rollback try。所有已取得许可的分支均 finally 释放，未有 factory、duplicate 或取消也不能漏许可。
4. creating 登记使用唯一操作 identity。回滚只撤销本操作的 creating 记录及已发布 entry identity，不删除其它创建、同 ID replacement 或其它 factory 的 Agent。自动生成 ID 的 create 同样记录其已发布 identity。
5. rollback 用最小 `NonCancellable` 完成精确 Registry 撤销与 child dispose；Registry mutex 只处理登记，解锁后才调用 dispose。清理错误附到原错误，不覆盖原失败或取消。factory 返回无关 Agent 时不 dispose 该无关 Agent。
6. restore 在 attach 前失败只清理自身 child 和登记，保留尚未采用的 loaded Session；attach 后失败由已安装的 owner SessionHandle 关闭精确 Session。attach handle 取得到 effect 安装的转移间隙须取消安全，安装失败也要释放该 handle。
7. preserve 已有 created/session-start/status、Restore listener 与 handle 语义；contained listener 错误本身不会传播，不用此方式假造 create 通知失败。

## 普通 Handle detach

1. 先 Registry mutex 检查 entry identity。已失效 identity 直接无操作，无须读取正在关闭的 Context 或取得新许可。
2. 解锁后解析并保留 Store，取得普通无 target catalog permit，再锁内复核同一 identity 并移除自己的 entry。
3. Closing 拒绝必须早于 entry removal、cancel、Context disposal 和 Handle disposed。拒绝后保持可重试。
4. permit 覆盖整个 cancel/ownerContext.dispose/同步 Disposed 通知及错误整理。移除 entry 后以最小 NonCancellable 完成这段清理，任一清理错误不得跳过其余清理或 permit 释放。
5. Handle 仅在 detach 成功或确认旧 identity 已失效后置 disposed。并发正确性由 Registry identity 检查承担；不以不安全的布尔标记取代锁。
6. 已关闭 Session 不应阻止尚未释放的 Agent Context 清理，所以普通 detach 外层 catalog 不绑定 Session target；内层 SessionHandle 仍执行精确 detach。

## 固定新增测试

类 `AgentRegistryAdmissionContractTest` 固定六个 @Test 方法：

1. `closingRejectsCreateAndRestoreBeforeFactoryOrReplay`
2. `catalogPermitCoversCreateFactoryPublicationAndNotices`
3. `catalogPermitCoversRestoreListenerAndRollback`
4. `cancelledCreateAndRestoreRemoveOnlyTheirOwnPublishedIdentity`
5. `restoreSetupCancellationReleasesIdWithoutClosingUnadoptedSession`
6. `closingHandleRemainsRetryableAndStaleIdentityCannotDetachReplacement`

使用真实 Runtime/SessionPlugin/AgentPlugin、最小 TestAgent、Deferred 和真实 dispatcher 的有界同步。无 sleep、反射、生产测试钩子、网络或模型请求。验证 factory/通知/listener/rollback 期间 reservation 为 CATALOG_ACTIVITY；退出后许可可释放。取消检查需证明真实已发布 entry 被撤销、同 ID 后续 create/restore 可用，及旧 identity 不误删 replacement。

普通 owner 存活是本批边界，不把父 Context 并发 dispose 纳入本批已证明范围。若某项无法用正常 API 确定性构造，报告具体限制，不改固定方法、不弱化断言、不扩大生产写集。

## 验收与交付

工作者只实施与静态检查，不运行 test/build/screenshot/native app/PTY；交付后停止写入并 exit。父线程收取 exit0，检查两文件写集和其它源码输入字节，冻结源码后串行运行：

- AgentRegistryAdmissionContractTest：6
- InboxAdmissionContractTest：8
- SessionAdmissionContractTest：8
- SessionContractTest：14
- AgentContractTest：6
- AgentLoopContractTest：10
- LlmRetryContractTest：1
- SessionPersistenceContractTest：7

共 8 类 60 个固定方法，零失败/错误/跳过，然后完整 JVM app build。逐项保存当前 XML、日志、diff、before/after SHA、源码 manifest 和父 review。

交付 JSON/MD 必须含 model/provider、baseline_sha256、contract_sha256、两项 files(path/before_sha256/after_sha256)、tests_run=false、builds_run=false、screenshots_run=false、native_execution_run=false、write_set_released=true、source_writes_stopped=true 和明确限制。

## 后续边界

本批不更改 Registry owner dispose、SessionStore 永久 stop、driver 或 Runtime lane。driver 独立活动下一批；有限 owner 清理依赖 Runtime 生命周期等待边界的独立设计与验收。之后才做显式静止恢复、Room/process/PTY 静止、归档持久化和 Worktrees/Recent 接线。归档按钮未启用；不增加独立归档页、删除或清理；不证明原生窗口或原版像素一致。
