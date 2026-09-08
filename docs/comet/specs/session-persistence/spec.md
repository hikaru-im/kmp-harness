# Session Persistence 完整规格

Persistence contract 通过 `session/event` 与 `session/flush` 接入，不修改 Session 核心。首个 provider 为 file-backed append/flush；路径由平台 launcher 注入，模块不读取环境变量、不硬编码系统目录。

flush 必须在所有并行 listener settle 后返回；写入采用临时文件/原子替换或等价的崩溃安全策略。load 时校验 envelope version、seq 连续性、serializer shape 和 provenance；未知 `ignorable=true` 事件可保留，未知 required event 稳定拒绝。

恢复会重建 Session log、Agent Inbox durable splice、未完成 turn/step 和 request folds。普通未闭合生命周期写入一次结构化 interrupted/crash-repair 标记；若日志含有尚未提交下一次 `request/header` 的 durable `llm/retry`，则暂缓该修复，由 `llm-retry` 在 Agent restore 时续跑。恢复后的 `deriveMessages` 与重启前相同。

### Scenario: flush/load/recover

WHEN Session append 多条事件并 flush，随后重新创建 Runtime/Plugin 并 load，THEN events、messages、request header/context 和 Inbox replay 与原实例一致；未知 required event 被拒绝且不产生半个 Session。
