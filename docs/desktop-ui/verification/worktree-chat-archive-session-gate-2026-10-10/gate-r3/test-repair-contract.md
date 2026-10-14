# A1 跨调度等待测试窄修

宿主指定SHUAIAPI/gpt-6.1-sol / opencodex。唯一源码写集：modules/session/test/im/hikaru/harness/session/SessionAdmissionContractTest.kt

只新增withContext import，并将noticed.await()与reporting.await()的外层withTimeout(10_000)两行包入withContext(Dispatchers.Default)，保持原10秒期限、Deferred屏障、8方法和所有断言，其余2260输入字节不变。精确替换为：

    withContext(Dispatchers.Default) { withTimeout(10_000) { noticed.await() } }
    withContext(Dispatchers.Default) { withTimeout(10_000) { reporting.await() } }

禁止其它改动、test/build/截图/native/PTY、Git状态写入、派其它worker。只实施与静态检查；保留全部dirty/untracked。Comet已none，本持续任务不重跑；理解代码先CodeGraph。

交付 /tmp/harness-worktree-chat-archive-a1-test-repair-delivery-20261010.json 与同名.md：复制原repair-delivery结构及全部原始before SHA，更新测试文件after SHA；保留生产repair字段，增加test_repair_baseline_sha256、test_repair_contract_sha256、test_repair_files单行path/before_sha256/after_sha256，以及其余2260输入核对结果。tests_run/builds_run/screenshots_run/native_execution_run/pty_execution_run均false，write_set_released/source_writes_stopped均true。写入交付后停止写入并退出，父线程独立冻结gate-r3串行35项及完整JVM构建。
