# Outcome

在保留已完成的 131 个 flat source-root 文件迁移成果的基础上，完成三项后续整理：修正 `CodegenEngine.rootPath` 生成的点号路径、将 `apps/shared` 的 4 个 Kotlin 文件迁移到完整 package 镜像路径、将 `modules/runtime` 的全部 28 个 Kotlin 源码/测试文件整理到完整 package 镜像路径。文件迁移只改变物理位置，不改变 Kotlin `package`、源码内容、模块依赖或运行时行为；`CodegenEngine` 只修正生成路径字符串，不改变生成包声明或业务逻辑。

# Scope

## Follow-up scope

### `CodegenEngine.rootPath`

修正 `backend/features/infra/src/im/hikaru/ruoyi/module/infra/service/codegen/inner/CodegenEngine.kt` 中的 `rootPath`：

```text
错误：src/im.hikaru.ruoyi/module/$module
正确：src/im/hikaru/ruoyi/module/$module
```

生成的 Kotlin 文件路径必须使用斜杠分隔的 package 镜像目录，例如 `src/im/hikaru/ruoyi/module/infra/dal/...`；生成文件内容中的 `package im.hikaru.ruoyi...` 保持不变。

### `apps/shared`

整理以下 4 个源码文件：

| 当前路径 | 目标路径 |
| --- | --- |
| `apps/shared/src/client/connection/Connection.kt` | `apps/shared/src/im/hikaru/harness/client/connection/Connection.kt` |
| `apps/shared/src/client/connection/HostApi.kt` | `apps/shared/src/im/hikaru/harness/client/connection/HostApi.kt` |
| `apps/shared/src/client/connection/handshake/HandshakeNegotiationResult.kt` | `apps/shared/src/im/hikaru/harness/client/connection/handshake/HandshakeNegotiationResult.kt` |
| `apps/shared/src/client/connection/handshake/HandshakeNegotiator.kt` | `apps/shared/src/im/hikaru/harness/client/connection/handshake/HandshakeNegotiator.kt` |

### `modules/runtime`

整理 `modules/runtime/src` 与 `modules/runtime/test` 下全部 28 个 Kotlin 文件。最终每个文件都必须位于其 `package` 声明对应的 source-root 镜像目录；已经正确的 9 个文件保持不动，需要补齐的 19 个文件移动到 `im/hikaru/harness/runtime/...`。

迁移审计识别出的 131 个 flat source-root 文件，覆盖以下模块：

| 模块 | 文件数 |
| --- | ---: |
| `apps/android-app` | 2 |
| `bundles/desktop` | 3 |
| `modules/agent` | 9 |
| `modules/agent-loop` | 4 |
| `modules/credentials` | 4 |
| `modules/credentials-local` | 3 |
| `modules/home` | 2 |
| `modules/include` | 8 |
| `modules/llm` | 20 |
| `modules/llm-retry` | 2 |
| `modules/loader` | 8 |
| `modules/logger` | 5 |
| `modules/profile` | 3 |
| `modules/profile-file` | 6 |
| `modules/runtime` | 9 |
| `modules/session` | 15 |
| `modules/session-api` | 3 |
| `modules/session-persistence` | 9 |
| `modules/settings` | 5 |
| `modules/settings-file` | 3 |
| `modules/timer` | 6 |
| `modules/tools` | 2 |

对每个文件，以其所在的 `src`、`test`、`src@<platform>` 或 `test@<platform>` 为 source root，将 package 的点号路径转换为目录路径。例如：

```text
modules/session/src/Session.kt
package im.hikaru.harness.session
```

迁移到：

```text
modules/session/src/im/hikaru/harness/session/Session.kt
```

# Non-goals

- 不处理其他仍有独立设计边界的路径组：`modules/llm-koog` 职责目录、无 package 的应用入口、`backend/server` 中的 `org.jetbrains.amper.spring` 模板包。
- 不修改任何 Kotlin `package` 声明、import、源码逻辑、模块 YAML、Android Manifest、Swift 调用或生成文件内容中的包声明。
- 不把 `modules/runtime` 已正确的 9 个文件重复移动；本次只补齐剩余路径并以全部 28 个文件的最终状态验收。
- 不改变 `CodegenEngine` 的生成内容、包声明、命名规则或业务行为，只修正输出路径中的点号/斜杠错误。
- 不处理无 package 的 `apps/jvm-app/src/main.kt` 和 `apps/ios-app/src/ViewController.kt` 入口文件。
- 不改变 `modules/llm-koog` 当前已完成的完整包根路径，也不把其职责目录转换为 Kotlin 子包。
- 不修改 package 声明、import、模块 YAML、Android Manifest、Swift 调用或业务逻辑。
- 不处理 `backend/server` 中遗留的 `org.jetbrains.amper.spring` 模板包及 `CodegenEngine` 的独立路径生成问题。

# Acceptance examples

- **A1：全量迁移**：审计清单中的 131 个文件全部位于其 source root 下的 package 镜像目录，不再有 flat source-root 文件残留。
- **A2：声明保持不变**：131 个迁移文件的 package 声明、文件内容和 import 与迁移前一致；正式包名仍只使用 `im.hikaru.harness.*`、`im.hikaru.contracts.*` 或 `im.hikaru.ruoyi.*`。
- **A3：非目标边界**：无 package 入口、`apps/shared` 4 个嵌套文件、`modules/runtime` 19 个嵌套文件、`llm-koog` 职责目录和 RuoYi/模板遗留文件均未被本 change 移动或修改。
- **A4：构建与测试**：受影响模块的 JVM/Android/iOS source-set 编译与测试通过；全仓 package/path 复核显示迁移后没有新的路径不匹配。
- **A5：Codegen 路径**：`CodegenEngine.rootPath` 使用 `src/im/hikaru/ruoyi/module/$module`，生成 Kotlin 文件路径不再包含 `src/im.hikaru.ruoyi/`；生成内容中的 package 声明保持正确。
- **A6：apps/shared 完整路径**：4 个 `apps/shared` 源码文件全部位于 `src/im/hikaru/harness/client/...` 对应路径，旧的 `src/client/...` 文件不再存在，声明和内容保持不变。
- **A7：runtime 完整路径**：`modules/runtime/src` 与 `modules/runtime/test` 的全部 28 个 Kotlin 文件均位于 package 镜像路径；无 `src/plugin`、`src/effect`、`src/service`、`src/event`、`src/intercept` 或对应测试路径残留，声明和内容保持不变。
- **A8：扩展后验证**：Codegen 测试、`apps/shared` 与 `modules/runtime` 的可用 JVM/Android 检查通过；全仓严格 package-to-path 扫描只剩已登记的 llm-koog、应用入口和模板边界。

# Constraints and invariants

- 只能移动文件，不能用改 package 的方式掩盖路径问题。
- 保留 `src`、`test`、`src@android`、`src@ios`、`src@jvm` 等 source-set 边界。
- 目标目录由当前 package 声明决定；Kotlin 关键字包段（如反引号包名）按其实际物理目录名处理。
- 保留所有用户已有的未提交修改，不执行 reset、clean、checkout 或批量删除。
- 移动后必须删除空的旧 flat 文件位置，但不得删除包含非目标文件的目录。

# Decisions

- 采用全仓严格 package-to-path 镜像规则，完成用户要求的 131 个 flat source-root 文件迁移。
- 本轮将已明确的 `CodegenEngine.rootPath`、`apps/shared` 4 文件和 `modules/runtime` 28 文件加入同一 change；不扩大到其他未确认的设计边界。
- `modules/runtime` 以全部 28 个文件作为最终验收集合，其中 19 个执行移动，9 个仅复核。
- 使用当前工作区，不创建额外分支或 worktree；物理移动不改公开 Kotlin API。

# Open questions

- [blocking] CONFIRM：确认按上述范围执行：修正 `CodegenEngine.rootPath`、迁移 `apps/shared` 4 个文件、整理 `modules/runtime` 全部 28 个文件，并按 A5-A8 验收；保留原 131 个迁移成果及既有非目标边界。

# Verification expectations

- 使用脚本重新扫描全部 Kotlin source/test roots，统计 flat 文件、package 镜像命中数和非目标残留。
- 对每个受影响模块运行对应的 JVM 测试；对多平台模块至少执行 Android/iOS 编译检查。
- 运行 `git diff --check`，并确认只发生文件移动，没有 package/import 或配置内容变化。
