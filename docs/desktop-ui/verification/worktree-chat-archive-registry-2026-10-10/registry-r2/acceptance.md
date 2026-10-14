# Registry 定向验收通过

## 2026-10-10 Worktrees 聊天归档 B：Registry registry-r2 最终验收

父线程先复核 Registry 生产源码及生命周期只读报告，收敛为一个生产文件和一个新增测试文件。指定 **SHUAIAPI/gpt-6.1-sol / opencodex** 实施并 exit0 释放写集；父线程冻结 2,263 输入，修改 1 个既有文件、新增 1 个文件，其余 2,261 输入字节保持。

registry-r1 因新增测试访问跨模块 internal Context.isDisposing 编译失败，尚未执行行为测试；失败证据独立保留。registry-r2 由同一指定模型仅修新增测试，使用真实 cleanup gate 与公开 isDisposed 断言，生产实现字节保持；原基线与两次交付均保留。

create/restore 在 Registry mutex 前获取精确 Store catalog 许可，覆盖 setup/factory/publication/Restore listener/同步通知/最后取消检查/NonCancellable rollback。创建登记与已发布 entry 使用精确 identity，未采用 loaded Session 的失败不关闭该 Session；普通 Handle Closing 拒绝后可重试，旧 identity 不影响 replacement。本批未改变 Registry owner disposal，父 Context 并发关闭的 Runtime lane 等待风险仍由后续合同处理。

父线程独立串行完成 Registry6、Inbox8、SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7，共 8 类 **60/60**，失败/错误/跳过均 0；完整 JVM app build **exit0**。XML 固定方法逐项核对；原 open-turn repair、pending retry 重启与 keepInbox 取消语义回归保留。源码 before/after、diff、父 review、冻结 manifest、XML、日志、交付和逐文件 SHA 位于 verification/worktree-chat-archive-registry-2026-10-10/registry-r2/。

下一批 driver 独立活动；有限 owner 清理依赖 Runtime 生命周期等待边界的独立验收。之后才做显式静止恢复、Room/process/PTY 静止、归档持久化与 Worktrees/Recent 接线。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。
