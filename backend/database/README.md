# Database

该目录包含从 Yudao 上游参考工程抽离出的数据库初始化脚本。模块脚本可独立使用；同步校验工具
还会把核心 PostgreSQL 基线与 `references/ruoyi-vue-pro` 的命名空间转换结果进行比较。

默认本地配置使用 PostgreSQL：

```bash
createdb -h 127.0.0.1 -U postgres agent-kmp
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/ruoyi-vue-pro.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/sync/sync.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/member/member.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/mp/mp.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/pay/pay.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/agent/agent.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/quartz.sql
```

`ruoyi-vue-pro.sql` 保持与 `references/ruoyi-vue-pro` PostgreSQL 基线一致，并把上游命名空间
`im.hikaru.ruoyi` 转换为本仓库的 `im.hikaru.ruoyi`；当前转换后 SHA-256 为
`c1cbe8d1a192af7f8cb67053e8ad661951d65387685b965bedfb835e992d3223`。sync、member、mp、pay 的
PostgreSQL 结构分别维护在同名子目录中，并以以下 MySQL 导出为权威来源：

- `mysql/sync-2026-07-26.sql`
- `mysql/member-2026-07-26.sql`
- `mysql/mp-2026-06-26.sql`
- `mysql/pay-2026-07-26.sql`

每个模块目录中的 `<module>.sql` 只包含结构；可选的 `data.sql` 包含对应 MySQL
导出中的历史和演示数据。需要数据时，在结构脚本之后执行：

```bash
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/member/data.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/mp/data.sql
psql -h 127.0.0.1 -U postgres -d agent-kmp -f backend/database/postgresql/pay/data.sql
```

`pay/data.sql` 来自原始导出，可能包含支付配置、证书和密钥，只应在明确需要的
隔离环境中导入，不应作为默认初始化数据。

上述基线和模块脚本都会删除并重建各自已有的表，只应对新数据库或明确允许覆盖的环境执行。
其他数据库的脚本按数据库名称保留在对应子目录中。

结构和数据脚本由无第三方依赖的同步工具生成。修改 MySQL 权威脚本后执行：

```bash
node tools/module-sql-sync.mjs --write
node tools/module-sql-sync.mjs --check
node tools/module-sql-sync.mjs --check sync member
```

本地同时运行 MySQL 和 PostgreSQL 容器时，可进行双库 catalog、索引、序列和逐行数据
校验：

```bash
node tools/module-database-verify.mjs
```

Agent PostgreSQL 的可重复门禁使用：

```bash
node tools/database-gate.mjs
```

它会在隔离数据库中比较 fresh 与 incremental Agent schema，再与实时 `agent-kmp` 的八张
Agent/Koog 表比较，并自动执行官方 Koog JDBC provider round-trip 测试。默认不把既有模块的
历史 live schema 漂移计入 Agent 结果；设置 `MIGRATION_COMPARE_FULL_LIVE_CATALOG=true` 可开启全库严格比较。

已存在的数据库不能直接执行上述会删表的结构脚本。本次 App Sync 升级使用独立增量迁移：

- MySQL：`backend/database/migrations/mysql/2026-07-26-app-sync.sql`
- PostgreSQL：`backend/database/migrations/postgresql/2026-07-26-app-sync.sql`

系统级 Push 设备注册使用后续独立迁移：

- MySQL：`backend/database/migrations/mysql/2026-07-27-app-sync-push.sql`
- PostgreSQL：`backend/database/migrations/postgresql/2026-07-27-app-sync-push.sql`

Agent M1 云端运行时使用 PostgreSQL 专用增量迁移：

- PostgreSQL：`backend/database/migrations/postgresql/2026-07-28-agent-m1.sql`

其中 `agent_chat_history` 与 `agent_checkpoints` 严格匹配 Koog 1.1.1 JDBC provider
schema；其余六张表归 Agent 所有并带非空 `tenant_id`。

迁移会创建 `app_sync_command`、`app_sync_change`、`app_sync_change_retention`，为
`member_address` 增加 `version`，并为 `member_user` 增加只保护昵称、头像、邮箱和性别的
`profile_version`。`app_sync_change_retention` 保存各 scope/resource 已清理日志的
最大 cursor，使旧客户端能够收到明确的全量重建要求。默认 change 保留 30 天，每天 03:30 分批
清理，可通过 `YUDAO_SYNC_CHANGE_RETENTION_*` 环境变量调整。
`app_sync_push_device` 只保存 FCM token、平台和当前 `tenantId + userId` 归属；同一 token
重新登录或切换租户时会原子转移归属，退出登录时客户端会尽力注销。

后续升级仍需按实际版本增加独立 migration。不要再创建 MySQL 中不存在的业务唯一索引；例如
`pay_wallet_transaction` 的 `(biz_id, biz_type)` 在原始数据中存在合法重复值。
