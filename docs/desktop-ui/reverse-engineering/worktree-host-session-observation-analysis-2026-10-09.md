# Worktrees 的本机 Host 会话观察：接线分析

状态：父线程完成源码分析；持久 header 目录尚未实现，本文不作为实施授权或验收结论。

## 当前可直接复用的接线

2026-10-09 通过 CodeGraph 读取 DesktopManagedWorktreeRepository.kt、ManagedWorktreeModels.kt、main.kt、LocalConnection.kt、SessionStore.kt。

- main 已持有同一个 harnessHost.runtime.context、LocalConnection、profile.hostDescription.hostId 和 managedHome，能注入绑定于这个 Host 的只读目录消费者，无需让 UI 直接读取数据库。
- managed repository 的 ownerValidator 当前只接受 LocalReadOnly、匿名 identity 和指定本机 Host；调用真实 connection.host.describe 并校验协议 major。read 在返回前再次验证 owner，Git 指纹和登记依旧由实际服务读取。
- SessionStore.list 返回当前 live 对象的独立 list；持久 provider 的目录是另一个服务。当前 LocalConnection.session.list 基于 SessionApi 只能提供 live agent summary，不能代替完整 live store 与持久 headers 的合并。
- 当前 ManagedWorktreeProtection 只有 Unknown/Unavailable 和 reason，页面没有会话已确认存在/本次未观察到的 typed 结果。只加数字或把 Unknown 改文案不足以表示读取失败、能力不支持和路径无法判定。

## 下一合同需要明确的决策

1. 使用 default-off 的可选只读消费者；旧 repository/fixture 不受影响。消费者依赖同一 Runtime 的 SessionStore 与 SessionPersistenceKey，不通过 persistence.load 枚举，不修改 SessionApi.list 现有语义。
2. 观察结果保留 Host 归属及持久/活跃来源，不暴露完整历史正文。持久 null、读错、disposed、连接/owner 变化、无法判定 cwd 必须保持 Unknown/Unavailable，不能构造零会话成功。
3. 同 id 的 live 和 durable header 只计一次；冲突字段不能静默用一边覆盖并声称完整。需要明确 parent 和 cwd 的归属规则，cwd 子目录用 Path 边界判定，禁止字符串 startsWith 误把 tree-2 当 tree 子目录。
4. header 无 member 字段：只针对验证的匿名本机 Host 使用全库；远端或 member 分区仍不可用。先确认 Runtime 和数据库路径属于该 Host/home，HostId 相同本身不能证明数据库归属。
5. 两个服务各有 mutex，没有跨服务共享读事务。结果应称本次只读观察，并记录开始/完成时间；不能描述为线性化的完整无会话证明。未来删除消费者还需要与会话建立共用的 gate 和提交前重验。
6. 当前无 cwd 的历史会话、不存在或无法规范化的 cwd、symlink/目录变化怎样影响全局完整性必须写入合同。即使可确认某条关联，也不能从其余无法归属行推出安全删除。
7. 对照真实 Runtime/临时 Room/真实 Git 创建：历史未 load、尚未 flush 的 live、父子及重复 id、多 cwd、缺失路径、读错/取消、owner 迟到与切换。页面用真实鼠标刷新并断言 typed 状态；pin/archive/删除不并入本切片。

首个持久目录切片验收后，再确定有限写集。生产 Cron 仍 false；本文没有增加远端、云端或排除范围中的页面。
