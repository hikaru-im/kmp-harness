# Worktree 会话目录前置分析

状态：父线程已分析，尚未派发或实施。插件本地更新切片仍由唯一 SHUAIAPI/gpt-6.1-sol 工作者处理。

## 当前证据

2026-10-09 通过 CodeGraph 读取 SessionApi、SessionStore、RoomSessionPersistence、SessionHeader、ManagedWorktreeModels、DesktopManagedWorktreeStore 及现有 persistence 测试。

- DefaultSessionApi 的会话列表来自 live agents；SessionStore.list 也仅返回当前存活对象。两者不能证明历史会话不存在。
- Room 的 harness_sessions 表保存会话 header，包括 id、createdAt、cwd 和 parentSession。schema 为 1，没有 host/member、pin 或 archive 字段。
- SessionPersistenceDao 只有按 id 查 header、查 events、计数及写入方法，没有 header 目录查询。
- persistence.load 会将对象加入 SessionStore，并可能修补中断 turn 和 flush。它不能用于只读目录扫描。
- ManagedWorktreeProtection 的会话保护仍未知，pin/archive 能力仍不可用。目前没有基于完整会话目录的删除消费者。

## 建议的最小实施切片

先在持久层提供独立的只读 header 目录，再沿真实 Host 绑定接入保护。首个切片只涉及 RoomSessionPersistence.kt 和新增的真实 Room 目录测试，不改 schema、不解析事件、不 load 会话。

目录接口必须区分可用空目录与服务不可用；旧实现保持兼容且默认不可用。查询全部 header，固定 createdAt、id 排序，返回独立集合。某一行无法构造合法 SessionHeader 时整次查询失败，不能跳过坏行后声称目录完整。查询遵守 provider mutex/disposed/cancellation 边界，不写入 header/events，也不发 Created/Appended/Flush。

真实临时 Room 验收需要覆盖：空目录、flush 后目录、重启后尚未 load 的历史 header、多个 cwd 和父子 header、排序、调用者修改返回集合不影响下次读取、坏 header 整体失败，以及读取前后事件和 live store 不变。既有 persistence contract suite 验证 flush/load/repair 未受影响。

下一切片才把持久目录与 live 会话合并为明确 Host 范围的观察；历史 header 没有 member 信息，因此不能把 member 过滤后的子集当作完整 Host 目录。cwd 规范化、未知路径、连接替换、权限撤销和迟到响应均须保守表示。目录完整只证明这次观察，不提供防止后续新会话建立的删除锁。

pin/archive 和 destructive Worktree 操作仍需单独的持久字段、迁移、完整保护及提交前重验合同。本分析不授权或启用 Worktree 删除，也不代表相关功能完成。
