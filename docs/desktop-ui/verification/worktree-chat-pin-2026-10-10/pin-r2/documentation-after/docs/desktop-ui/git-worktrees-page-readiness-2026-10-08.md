# Git / Worktrees 页面验收（2026-10-08）

## 范围与基线

本批复用既有本地 Git 读取、创建、已有分支切换、已有暂存提交和 M1 managed inventory。先核对 ASAR 契约与实际 controller/capability，再委托 **SHUAIAPI/gpt-6.1-sol** 实施页面和定向验收。代理不运行构建测试，主线程串行验证。

排除云端环境、自动代码审查和应用快照。没有 pins/archive/完整移除保护时不增加删除入口；不以空列表推断没有关联会话。缺少服务的动作明确不可用，生产页面不注入测试仓库或虚构结果。

## 本批检查表

| 区域 | 验收内容 | 状态 |
| --- | --- | --- |
| 仓库 | 实际目录选择、刷新；未选择/加载/失败/不可用/就绪 | 实施中 |
| 分支与变更 | 实际分支与变更层级、长路径/分支、checkout 可用条件 | 实施中 |
| 差异 | source 切换、BaseBranch 选择、空/失败/加载、patch 横纵滚动 | 实施中 |
| 创建工作树 | 表单可达；失败/已有路径/真实成功回执；重复操作禁用 | 实施中 |
| 普通工作树 | 路径/HEAD/branch/锁定/prunable；选择只读取目录 | 实施中 |
| managed | 刷新、stale、空/失败/不可用；Verified/UnknownOwnership/FingerprintMismatch/Missing 区分 | 实施中 |
| 布局 | 1440×900 与600×800，浅深主题；内容滚动、按钮可达、长文本 | 实施中 |
| 回归 | 既有checkout/commit/create/managed交互定向回归 | 待主线程验证 |

实际验收结果、命令、报告和截图在运行后填写。历史服务批次不计入本批测试数量。

## 尚未覆盖

原版同尺寸像素对照、原生窗口实机、远端 Git、完整会话目录与归档/恢复/删除保护、本地环境读写与脚本执行。关联聊天固定已于2026-10-10完成下述定向验收；观察与固定能力不提供无会话证明或完整删除保护。这些缺口不由截图或 controller fixture 测试推定完成。

## 2026-10-10 关联聊天固定批次（pin-r2定向验收通过）

原版 Worktrees 以关联聊天固定集形成删除保护；已保存当前 app.asar SHA、asset size/offset 和精确片段。Harness 当前真实 Runtime/Room Header 观察层可复用，本批限定聊天固定/取消固定与 owner 分区持久化、完整 header 指纹、fresh reader/bootstrap 重读、scope/revision 隔离和真实鼠标操作。

分析与合同位于 `reverse-engineering/worktree-chat-pin-analysis-2026-10-10.md`、`reverse-engineering/worktree-chat-pin-contract-2026-10-10.md`。指定模型 SHUAIAPI/gpt-6.1-sol / opencodex 实施11文件（6修改、5新增）；退出释放写集后，父线程冻结2,259输入并独立串行验收。10类56/56（21新增、35回归），失败/错误/跳过均0；完整JVM app build exit0。真实临时Runtime/Room/Git/JSON、fresh reader/bootstrap重读、owner/header/revision/CAS隔离及鼠标固定/取消固定均通过。

4张真实固定状态PNG与8张观察回归PNG已按原尺寸、SHA、geometry及owner核验。截图分别滚动至固定按钮、lineage和列表底部，长单行路径仍按现有布局截断；本批不证明完整壳层、原生窗口或原版像素一致。证据位于 `verification/worktree-chat-pin-2026-10-10/pin-r2/`。

pin-r1保留为失败历史：Controller5/5、Store6/7，非法HostId在进入断言前抛出，后续门槛未运行。同一模型仅修复新增Store测试的断言位置并加强拒绝后bytes检查，其他10文件SHA不变；失败源码、before、日志和修复diff位于 `verification/worktree-chat-pin-2026-10-10/pin-r1-failed/`。

下一批先审查归档/恢复生命周期设计，涵盖真实入口共享gate、静止证明、恢复自动retry和持久提交边界；尚未派实施。完整归档/恢复与移除保护仍未完成，不开放删除或清理。
