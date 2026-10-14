# Inbox A2a 身份断言窄修交付

日期：2026-10-10。model：SHUAIAPI/gpt-6.1-sol；provider：opencodex（用户指定合同元数据）。

状态：仅实施与静态检查完成；原 inbox-r1 仍为 7 通过、1 失败，修复后未执行，待父线程退出后独立冻结、串行重验。

## 确认的原因

父线程 command.log 与 JUnit XML 的唯一失败是 callbackFailureCannotReuseCommittedItemId 第 351 行 assertSame：两个 IllegalArgumentException 类型与消息相同、实例不同。父线程真实 java-exec / junit-platform-console-standalone telemetry 第 4 行均记录 -ea，JVM 参数和 XML 属性未设置 debug/recovery 覆盖；XML 与所引用 JAR 的版本文件均为 kotlinx.coroutines 1.11.0。

精确路径：Agent.followup → 本测试 TestAgent.send → Inbox.insert → mutate → commit → withContext(NonCancellable) → Session.append → projection/version/nextItemId → onChanged。应用路径直接传播错误，外层 finally 释放许可。协程同 dispatcher 的 undispatched 完成路径调用 recoverStackTrace；标准异常经 tryCopyException 复制。额外实例字段使 ExceptionsConstructor 放弃复制，StackTraceRecovery 返回原对象。

依据为已有验收证据与静态应用/库实现核对；没有独立运行复现，也没有在新运行中观察 caught cause/stacktrace。源文件位置、上游固定版本来源与 telemetry 路径见 JSON diagnosis。

## 实际修改

- 唯一源码写集：modules/agent/test/im/hikaru/harness/agent/InboxAdmissionContractTest.kt。
- 增加私有普通 CallbackFailure(val marker: Any) : IllegalArgumentException，实例使用 CallbackFailure(Any())；marker 是独立、非静态实例字段，适用于 common 测试代码。
- 原 assertSame(failure, assertFailsWith<IllegalArgumentException> { ... }) 整行保持不变。
- 原 8 方法及全部 queue/version/ID/notice/admission 断言、helper、fixture 字节保持不变；仅归一化新增声明与构造后，整个文件等于冻结 inbox-r1 原测试。
- Session.kt 与 Inbox.kt SHA 保持原交付 after 值。

## 基线与文件 SHA256

- baseline_sha256：58cc581edc9b2fe18e98d7b771b7b7470b0c5da4ceba9c60e0f56432fcc0a6da
- contract_sha256：817de26117cb4bf6d995404f9830036928ec4be4521809bad581f268a5517925
- repair-entry test SHA256：ddf3790c8d44a68634d267011984bc2857756daeefa29437ba40e043864781c4

三文件 before_sha256 均保留原 baseline；测试 before 仍为 null，不替换为修复入口 SHA。

| 文件 | 原 baseline before_sha256 | 当前 after_sha256 |
| --- | --- | --- |
| modules/session/src/im/hikaru/harness/session/Session.kt | 7d57a17ff730445dfd96fb9e4050424bc4f6ab6ca058c38b200a8a7d8b9ba4cc | fa30f4724722bd07cd7ca4649c5f6eff319e41d37f231df7a59e471ea6f74d8e |
| modules/agent/src/im/hikaru/harness/agent/Inbox.kt | e826ec08013a5ea45a0c1ea009ab36910f35a005cb051acedd7b109c2ff04e5f | 01210800fbf92ff92cfd51cc336e9c1932074c7a96eeff7065f4847aa501b3ba |
| modules/agent/test/im/hikaru/harness/agent/InboxAdmissionContractTest.kt | None | 9564a80fbf21434ab1321564044a467e349ecb8a45dba75cc90eceece17c22f9 |

## 静态检查

- 8 个新增方法及 6 个旧回归类方法集合与原交付完全一致；旧类计数 8 / 14 / 6 / 10 / 1 / 7。
- 原 assertSame 整行、全部原断言与 helper/fixture 的字节保持检查通过。
- 2,261 baseline 编译输入中排除原生产写集后，2,259 个受保护输入 SHA 未变；Session 去掉原新增 API 后仍匹配其原 baseline SHA。
- 修复入口与交付检查之间，11084 个受保护仓库路径内容指纹相同；6899 个受保护 dirty/untracked 状态记录指纹相同。原工作者历史指纹另保留于 original_delivery_static_checks。
- git diff --check 退出 0。冻结原测试到修复测试的 git diff --no-index --check 退出 1（源文件存在差异），stdout/stderr 为空；直接全文件空白/control-byte 检查通过。
- 禁止模式检查通过；未使用反射、生产钩子、skip、sleep、debug/recovery 关闭或配置修改。

## 执行与释放

- tests_run / builds_run / screenshots_run / native_execution_run / pty_execution_run / resource_execution_run：全部 false。
- write_set_released / source_writes_stopped：全部 true。源码写入已停止，写集释放。
- 没有 test/build/screenshot/PTY/运行资源、用户配置修改、进程终止、Git 破坏操作或其它工作者。

## 尚未通过的验收

原 inbox-r1 callbackFailureCannotReuseCommittedItemId 的 assertSame 失败仍是唯一已观测结果；该方法后续断言在原失败运行中未到达。此交付不宣称修复后通过，也未编译。父线程须在工作者退出后重新冻结当前 SHA，独立串行验收固定 Inbox 8 方法、6 类旧回归及完整 JVM app build。

原 owner/lifecycle/resource 静止限制继续有效；无截图或 UI 验收声明。

JSON：/tmp/harness-worktree-chat-archive-inbox-repair-delivery-20261010.json
