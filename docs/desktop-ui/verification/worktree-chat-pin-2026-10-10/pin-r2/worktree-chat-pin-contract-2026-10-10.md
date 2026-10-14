# Worktrees 关联聊天固定实施合同

日期 2026-10-10；先读同目录 worktree-chat-pin-analysis-2026-10-10.md 和 evidence manifest。实际模型固定 SHUAIAPI/gpt-6.1-sol / opencodex。

## 唯一源码写集（11）

1. apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt
2. apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeController.kt
3. apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt
4. apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt
5. apps/shared/src/im/hikaru/harness/client/git/GitPages.kt
6. apps/jvm-app/src/main.kt
7. apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreePinStore.kt（新增）
8. apps/shared/test/im/hikaru/harness/client/git/ManagedWorktreePinControllerTest.kt（新增）
9. apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinStoreTest.kt（新增）
10. apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinIntegrationTest.kt（新增）
11. apps/jvm-app/test/im/hikaru/harness/desktop/WorktreePinInteractionTest.kt（新增）

保留全部已有 dirty/untracked。不改任何旧测试、构建描述、Runtime/Room/schema 或其他源码/文档。不 reset/stash/clean/checkout/commit/push，不杀既有进程。Comet probe 已 none，本持续任务不重跑。理解代码先 CodeGraph，核对生产路径，避免证据目录同名符号。

## 行为合同

- 增加 serializable header PinKey(id, headerFingerprint)，observer 对成功无冲突 header 做 Json encodeDefaults=true + version=SESSION_FORMAT_VERSION 编码并 SHA256；缺 key 的兼容模拟数据不可写。完整字段进入摘要，不能仅 id/cwd/parent；格式版本变化不应改变键。
- 独立 owner/revision/pins snapshot；Available/Unknown/Unavailable typed 读取结果。旧键保留，未知不能变空成功。Repository 可选 pinStore 默认 null，保持现有 trailing-lambda/positional 构造兼容及旧 unavailable 行为。接口新增 default unavailable mutation，旧 mocks 不必改。
- immutable 请求捕获 repository/owner/root/key/expectedRevision/desiredPinned。typed Applied/Rejected/Unknown 回执必须含请求身份；Applied 返回真实持久 revision/状态及可选 durability warning。请求、回执、UI 都必须匹配当前 owner/repository/key。
- 每次提交前核验真实 local owner、仓库指纹、当前完整成功 observer、目标 root 下精确 header key、目标非 missing/prunable/bare；核对 expectedRevision。observer/source/owner变化或冲突时不写。提交前最后重验 owner；不得调用 Session load/flush/create/cancel/repair 或 Git mutation。
- Store 严格 schema、重复 JSON 键/owner/key校验，native绝对规范home、非空host/id、SHA256格式、非负revision；坏文件、非普通文件、过大、符号链接、锁冲突 fail closed。每 owner 保留其他 owner，CAS 比较完整原始 bytes，OS短 lease、写临时文件 force、atomic replace、目录 fsync。沿用 DesktopManagedWorktreeStore 的明确限制，不宣称锁住不合作外部writer。dir fsync失败返回 committed warning。CAS必须增revision，禁止默默重试覆盖；相同状态可返回真实当前状态不重复写。
- IO提交阶段有限 NonCancellable；接受请求后页导航/关闭不得虚假回报未提交，不把回执发布给新scope。Controller read epoch 与 binding/operation ticket 分开；revision触发read时保留busy/receipt，重复点击只派一次。成功后重读发布当前磁盘状态，不本地翻转伪造成功。错误/unknown允许明确刷新，禁止自动重投。
- Worktrees关联会话每行显示固定状态与固定/取消固定按钮，使用共享 HarnessButton，窄屏可布局/滚动。仅pageReady+当前非stale inventory+Observed+Available+精确owner/key时可写。缺服务、unknown、stale、busy、scope不匹配禁用。切scope首帧隐藏旧状态。文案不称无固定/可删除；保留归档/删除/清理不可用说明。
- main 注入真实 home 下 managed-chat-pins.json。生产无fixture/示例；测试只临时home。

## 固定新增测试方法（21）

ManagedWorktreePinControllerTest（5）：

- pinWithExactObservedKeyDispatchesOnceAndRefreshesAfterRevision
- unavailableUnknownStaleAndDuplicateClicksNeverDispatch
- lateReceiptCannotLeakAcrossBindingOrClose
- revisionRefreshDuringAcceptedPinKeepsReceiptAndRefreshes
- mismatchedOwnerRepositoryOrKeyReceiptNeverPublishesSuccess

DesktopWorktreePinStoreTest（7）：

- roundTripOwnersAndFreshReader
- completeByteCasRejectsWhitespaceAndForeignOwnerDrift
- badSchemaDuplicateKeysAndDuplicatePinsAreNeverEmpty
- fileAndLeaseSymlinksNeverMutateTargets
- competingLeaseRejectsReadAndWrite
- directorySyncFailureIsCommitted
- headerKeysRemainDistinctAndInvalidKeysRejected

DesktopWorktreePinIntegrationTest（6）：

- realLiveAndDurablePinRoundTripWithoutSessionOrGitWrites
- freshBootstrapReadsDurablePinWithoutLoadingHistory
- staleRevisionAndChangedHeaderRejectBeforeWrite
- ownerOrCapturedServiceReplacementCannotWrite
- diskConflictAndMalformedStoreRetainBytes
- directorySyncFailureReturnsCommittedWarning

WorktreePinInteractionTest（3）：

- realMousePinAndUnpinPersistToFreshReader
- unknownUnavailableAndScopeChangeDisableAndHidePins
- capturesWideCompactLightDarkRealPins

真实成功路径复用只读 WorktreeObservationFixture（旧文件不得改），可新建自己需要的controller/setup/helper。Room rows/events、live sessions、managed registry和Git跟踪文件/refs/index mutation前后保持；freshBootstrap测试正式重开bootstrap，durable-only聊天不load。指纹变化断言明确同时验证原Pin保留；source替换/owner撤销/staleRevision零写入。截图用真实fixture和当前Pin，至少640×800、320×600、Light/Dark四张原尺寸PNG；geometry包含SHA、viewport/button bounds、theme、真实owner和pin状态。通过环境变量 HARNESS_WORKTREE_PIN_CAPTURE_DIR 控制写图，状态/几何断言始终执行。鼠标测试必须真实pointer输入，不以controller直调代替。

父线程另独立跑observer11、managed integration8、旧controller4、observation UI4、managed UI3、managed store5（共35）及完整JVM build。本批总门槛10类56项，数量与方法不得更改或降低，不改旧断言。

## 工作者交付与释放

工作者只实施、静态检查，禁止动态test/build/screenshot/native/PTY执行。父线程负责冻结后串行运行。新增文件也纳入11记录，before_sha256=null。

交付 /tmp/harness-worktree-chat-pin-delivery-20261010.json 及同名 .md：model、provider、baseline_sha256、contract_sha256；files（path/before_sha256/after_sha256）；tests_run/builds_run/screenshots_run/native_execution_run 均false；write_set_released/source_writes_stopped 均true；实现摘要与未验证边界。写完交付停止所有源码写入并exit。
