# Registry 全段准入实施交付

- model: `SHUAIAPI/gpt-6.1-sol`
- provider: `opencodex`
- 状态：实施及静态审查完成；父线程冻结后独立验收待执行。
- baseline_sha256: `29fb2f59a3e7eef41298c9a9bd2dfcb936041061e64e73bb549e2056e03165dd`
- contract_sha256: `e02f5e99ed4c0b22177bf1e430428dbeb75cae26bcf1c45bb8e42878e6a34909`

## 两文件写集

| path | before_sha256 | after_sha256 |
| --- | --- | --- |
| `modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt` | `1bceddcb0e85e63e24f5d0bdc87c5791ac29376b2d56321ab832e02e29df2def` | `d101c3bed7463b3ebed6999e7e4fc315054af4be6099ad5fe34582494f50ed6a` |
| `modules/agent/test/im/hikaru/harness/agent/AgentRegistryAdmissionContractTest.kt` | `None` | `a48cd61859c09aa3a96522af23722a7c67918695e551b079600cf71b68dc36c1` |

新增文件 before_sha256 为 null，表示基线不存在。

## 实施

- 入口保留精确 SessionStore；create 无 target、restore 精确 Session target，均在首次 Registry mutex 前取得 catalog permit，并直接 try/finally dispose。
- permit 覆盖 setup、factory、entry publication、Restore listener、同步通知、最终 ensureActive 及 NonCancellable rollback；内层 Session/Inbox 入口仍各自准入。
- creating 使用唯一 operation identity；显式和自动 ID 均记录已发布 entry identity。rollback 在 Registry mutex 内精确撤销，解锁后独立清理未安装 handle 与 child；清理异常附到原错误。
- restore 的 child/replay/attach 均在 try 内；attach 前失败保留 loaded Session，采用后由 child effect 中精确 SessionHandle 清理；同步转移安装失败保留本地 fallback。
- 普通 Handle 先锁内判断旧 identity，再解析 Store、取得无 target catalog permit 并复核后移除；移除后 NonCancellable 完成全部 cleanup/notices，按值保留原错误后在外侧抛出；Handle 仅在 detach 成功后置 disposed。

## 固定六方法及实质断言

- `closingRejectsCreateAndRestoreBeforeFactoryOrReplay`：Closing 下显式和自动 create、restore 均早于 factory/replay 被拒绝；真实 Session mutex 被持有；释放后同 ID 可重试。
- `catalogPermitCoversCreateFactoryPublicationAndNotices`：无 factory 分支释放 permit；自动 ID creating 阶段拒绝 duplicate；factory、已发布 entry 和 Created/SessionStart/Status 通知各阶段均为 CATALOG_ACTIVITY；正常退出后释放。
- `catalogPermitCoversRestoreListenerAndRollback`：Restore listener 失败原样返回；回滚先撤销 entry，再在 child cleanup 期间保持 CATALOG_ACTIVITY；cleanup 错误附为 suppressed；采用的 loaded Session 被关闭；同 ID restore 再次可用。
- `cancelledCreateAndRestoreRemoveOnlyTheirOwnPublishedIdentity`：自动 create 和 restore 均在最后 Status 通知期间取消，真实已发布 entry 被撤销；取消 identity 与 suppressed cleanup 保留；无关 Agent 保留，同 ID 可再次 create/restore；create/restore 的无关 factory Agent 不被取消或 dispose。
- `restoreSetupCancellationReleasesIdWithoutClosingUnadoptedSession`：通过真实 Session replay mutex 和真实 Store attach mutex 分别取消 setup；factory 未运行，creating ID 可重用；attach 前 loaded Session 仍保留；后续 restore 成功。
- `closingHandleRemainsRetryableAndStaleIdentityCannotDetachReplacement`：Closing 多次拒绝保持 Handle 可重试；精确 Session 已关闭时仍清理 Agent Context；移除后取消不跳过 cancel/context/notices，错误聚合；旧 Handle 在 replacement reservation 下不取得许可、不触碰 replacement；并发 disposal 只 cancel 一次。

## 静态检查

- 校验完整基线 2262 个输入；其中 Registry 按授权改变，2261 个写集外输入 SHA 不变。
- 六个 @Test 方法名称和顺序与合同完全一致。
- Registry owner dispose 字节、既有公共方法签名及 Agent 错误类型保持。
- 两文件 LF/no trailing whitespace、词法括号检查通过；词法检查不等于编译。
- Registry 的 git diff --check 为 exit0；新增测试 no-index --check 无空白诊断（exit1 表示新增文件差异）。

## 执行与写集释放

```json
{
  "tests_run": false,
  "builds_run": false,
  "screenshots_run": false,
  "native_execution_run": false,
  "pty_execution_run": false,
  "resource_execution_run": false,
  "write_set_released": true,
  "source_writes_stopped": true
}
```

## 保留事项

- 只改合同 Registry 源文件并新增合同测试文件；既有测试、fixture 和写集外基线输入 SHA 均不变。
- 未新增公共 API；未改 Registry owner dispose、driver 或 Runtime。
- 未 reset/stash/clean/checkout/commit/push、修改用户配置、杀既有进程或派其他工作者；未重跑 Comet probe。
- 理解代码先 CodeGraph，核对 modules 下生产路径；不以 verification 同名快照替代生产源码。

## 明确限制

- 仅完成实施和静态检查；未编译，也未运行新旧测试。六个新增方法的编译、并发行为和断言结果均待父线程冻结后独立验收。
- 合同的普通 owner 存活边界保持：父 Context 并发 dispose、Registry owner drain、SessionStore 永久 stop、Runtime mutation lane handoff 及 driver 独立活动不在本批证明范围。
- SessionHandle 返回到 effect 安装之间无挂起点；安装失败的本地 handle fallback 已静态审查。正常 API 无法在这一同步间隙插入确定性暂停，因此未添加生产测试钩子、反射或父 Context 并发 disposal 场景。
- 未运行 test/build/screenshot/native app/PTY/资源；未验证原生窗口、像素一致性、归档持久化或归档接线。父线程仍需串行验收 8 类 60 个固定方法及完整 JVM app build。

交付后源写入已停止、写集已释放；父线程可以冻结并串行验收。
