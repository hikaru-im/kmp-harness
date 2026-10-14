# Worktrees 聊天归档 A1 实施交付

日期：2026-10-10。状态：四文件实施完成，等待父线程独立冻结并串行验收。

宿主请求路由：SHUAIAPI/gpt-6.1-sol / opencodex。写集已释放，源码写入已停止；交付后停止所有写入。

## 基线与文件 SHA-256

- baseline_sha256: 23393055347d0400f1a5eb212903fad3ab3cd225fb9d4c1a890c3dfa40c045e9
- contract_sha256: c73b3a935e48143c4758bf202b10c8a6d6a152fdfc9c8f03bfd1d4f7c38b1df1

| 文件 | before_sha256 | after_sha256 |
| --- | --- | --- |
| modules/session/src/im/hikaru/harness/session/SessionStore.kt | 19212beb16c002b424f5ac01c429f7624b18f64c0c72664f449debee7e8d030e | d1f702cb0ffcaf2f9b1e53c6f7419a9798223b7fe4d472cbaf29950546124cec |
| modules/session/src/im/hikaru/harness/session/Session.kt | 6a2048efc21b926d7bad60ff2a30b693af4f448df1af7e72211b685f5fd2a958 | 7d57a17ff730445dfd96fb9e4050424bc4f6ab6ca058c38b200a8a7d8b9ba4cc |
| modules/session/src/im/hikaru/harness/session/SessionAdmission.kt | null（新增） | c0c0696ebd1f37b64b16b12aab031ff3f26549af315ca32804e37ccb8b798018 |
| modules/session/test/im/hikaru/harness/session/SessionAdmissionContractTest.kt | null（新增） | f50c6fe7526aca78f14198663f7d52dd60af4be908d4082e10eeb13c0e9ae508 |

## 实现 API 与行为

- coordinator: SessionStore.admission: one SessionAdmission per exact Store instance; every constructed Session receives a distinct internal SessionAdmissionIdentity.
- reservation: SessionAdmission.reserve(session): SessionReservationResult.Reserved(reservation), Busy(reason), or Rejected(reason). Busy reasons: RESERVATION_ACTIVE, TARGET_ACTIVITY, CATALOG_ACTIVITY. Rejected reasons: OWNER_STOPPED, FOREIGN_SESSION, CLOSED_SESSION.
- closing: Ordinary admission under reservation throws SessionAdmissionException(reason = CLOSING) before encoding, validation, ID allocation, log mutation or lifecycle mutation.
- explicit_permits: acquireOperation(session) and acquireCatalog(session = null) return SessionAdmissionPermit. Activity and catalog membership are counted under a short state mutex; each permit releases only its own membership in NonCancellable. No CoroutineContext authorization is inherited.
- reservation_token: SessionReservation exposes session, suspend isActive(), and suspend dispose(). Exact-token and exact-Store checks make stale or duplicate release idempotent without reopening another reservation or stopped owner.
- entry_coverage: prepare, create, load, enter, announce, attach, detach and flush use explicit catalog/lifecycle admission. Store.create/load retain one permit through all internal work, cancellation checks and rollback. Session.append/restore acquire operation admission before real work; append retains it through synchronous notices and failure reporting.
- internal_helpers: Session internal restore/enter/beginAnnounce/completeAnnounce/close require an explicit permit; Store private admitted helpers reuse the caller permit within try/finally. No callbacks or owner work run under the admission state mutex.
- owner_stop: Store.dispose uses a NonCancellable special owner-stop path, invalidates reservation, rejects new activity and completes the existing reverse exact-instance cleanup. Existing ordinary permits can release late without reopening admission.
- compatibility: Original Store/Session owner mutexes and synchronous observer semantics remain. Closed handles are idempotent and cannot detach a same-ID replacement; read-only Session projections remain available.

## 静态证据

- 核对全部 2,259 个已有基线输入：仅 Session.kt 与 SessionStore.kt 的 SHA 改变；无缺失输入，无写集外已有输入变化。两个新增文件属于合同写集。完整新增输入清单由父线程独立冻结核对。
- 四文件 UTF-8、LF、末尾换行、行尾空白、控制字符与词法括号检查通过；已有两文件 git diff --check 通过。词法检查不等同于编译。
- 新测试恰为基线冻结的八个 @Test 方法，名称及顺序一致。测试代码尚未执行，覆盖意图需父线程动态确认。
- tests_run=false / builds_run=false / screenshots_run=false / native_execution_run=false / pty_execution_run=false。未启动其他 worker，未改旧测试、配置或 Git 状态。

## 固定新增测试方法

- inFlightAppendMakesReservationBusyThroughNotice
- reservedSessionRejectsAppendRestoreAndLifecycleWithoutMutation
- catalogPermitProtectsCreateLoadAndFlush
- callbackReservationAndIndependentMutationDoNotHoldStateLock
- cancellationAndFailuresReleaseOnlyOwnPermits
- releasedParentPermitCannotAuthorizeInheritedChild
- staleHandleOrReservationCannotAffectSameIdReplacement
- ownerDisposeInvalidatesReservationAndKeepsOldReferencesClosed

## 未覆盖范围与验收边界

- Dynamic behavior and compilation are unverified in this worker. Parent must freeze the delivered hashes and independently run the fixed 35 cases across 4 classes, inspect actual XML for zero failure/error/skip, and run the full JVM app build with exit code 0.
- Reservation covers Session-layer admission only; Agent, Inbox, driver, Room and background activity are not fenced or proven quiet.
- No archive metadata, archive UI enablement, persistent archive implementation or quiescent recovery is delivered.
- Original same-object reentry restrictions while holding owner mutex remain; no ordered outbox or notification semantic redesign is included.
- SessionKey.kt / Context.createSession are outside this write set and continue composing separately admitted prepare/enter/announce calls. Whole-function catalog coverage is provided for Store.create/load as required by this contract.
- Independent asynchronous work must acquire its own explicit permit; this implementation does not grant inherited child authorization.

## 父线程验收

先独立核对完整输入集合、逐文件 SHA 与 Pin R2，再冻结串行执行：SessionAdmissionContractTest 8、SessionContractTest 14、AgentContractTest 6、SessionPersistenceContractTest 7，共四类 35 项；实际 XML 必须无 failure/error/skip，完整 JVM app build 必须 exit0。

本工作者未宣称动态通过或归档/恢复完成。真实失败应另存；修复必须重新授权窄写集。

write_set_released=true；source_writes_stopped=true。交付后停止写入并退出。
