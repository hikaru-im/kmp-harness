# Outcome

让 `modules/llm-koog` 的物理源码和测试目录与现有包命名空间一致：源码根目录统一带有 `im/hikaru/harness/llm/koog`，便于 IDE 浏览、代码搜索和后续按职责分层维护。

# Scope

- 移动 `modules/llm-koog/src` 下现有 Kotlin 源文件到 `src/im/hikaru/harness/llm/koog/`，保留当前 `adapter`、`catalog`、`config`、`error`、`lifecycle`、`plugin`、`semantics` 等职责子目录。
- 移动 `modules/llm-koog/test` 下现有 Kotlin 测试文件到 `test/im/hikaru/harness/llm/koog/`。
- 保持所有文件顶部的 `package im.hikaru.harness.llm.koog` 声明不变；本轮不把职责目录转换成新的 Kotlin 子包。
- 更新必要的文档、路径引用和测试发现配置，使 Amper/Kotlin 仍能发现全部源码与测试。

# Non-goals

- 不修改 `im.hikaru.harness.llm.koog` 或其他现有包名。
- 不迁移 `modules/llm-koog-openai`、其他模块、Contracts 或 RuoYi 后端。
- 不新增或删除 provider、mapper、API 或运行时行为。
- 不在本轮把 `config`、`plugin`、`adapter` 等职责目录改成对应的 Kotlin 子包。

# Acceptance examples

### Scenario: 源码目录镜像 Harness 包路径

WHEN 检查 `modules/llm-koog/src` 下的全部 Kotlin 源文件，THEN 每个文件都位于
`src/im/hikaru/harness/llm/koog/` 目录树内，且 `adapter`、`catalog`、`config`、`error`、`lifecycle`、
`plugin`、`semantics` 等职责子目录保持不变。

### Scenario: 测试目录镜像 Harness 包路径

WHEN 检查 `modules/llm-koog/test` 下的全部 Kotlin 测试文件，THEN 每个文件都位于
`test/im/hikaru/harness/llm/koog/` 目录树内，测试包声明保持不变。

### Scenario: 包声明和旧路径保持稳定

WHEN 对比移动前后的源码与测试，THEN 所有 `package im.hikaru.harness.llm.koog` 声明保持不变，且旧的
`modules/llm-koog/src/<role>` 和 `modules/llm-koog/test/<file>.kt` Kotlin 文件路径不再存在。

### Scenario: 模块行为不因目录移动改变

WHEN 编译并运行 `llm-koog` 模块既有测试，THEN Amper 能发现全部源码和测试，编译、测试及既有行为保持通过。

- **A1**：`modules/llm-koog/src` 下所有 Kotlin 源文件均位于 `im/hikaru/harness/llm/koog/` 目录树内，职责子目录结构保持不变。
- **A2**：`modules/llm-koog/test` 下所有 Kotlin 测试文件均位于 `im/hikaru/harness/llm/koog/` 目录树内，测试包声明保持不变。
- **A3**：源码和测试的 `package im.hikaru.harness.llm.koog` 声明与移动前完全一致，旧的扁平 Kotlin 文件路径不再存在。
- **A4**：`llm-koog` 模块的编译、测试发现和既有行为不受目录移动影响。

# Constraints and invariants

- 只进行物理文件移动和必要的路径引用修正，不做语义重构。
- 保留当前工作区中的其他未提交改动，不执行 reset、clean、revert 或批量格式化。
- Harness 包名继续使用 `im.hikaru.harness.*`；Contracts 和 RuoYi 包名保持现状。
- 物理目录以 `src`/`test` 为 source root，source root 下的相对目录镜像包路径。

# Decisions

- 工作区方式：当前目录（用户已选择），不创建新分支或 worktree。
- 本轮只移动 `llm-koog` 的源码和测试目录；包名与职责子包拆分留在后续 change。

# Open questions

无。

# Verification expectations

- 检查源码/测试路径与包声明的镜像关系。
- 运行 `./kotlin test --include-module=llm-koog --platform=jvm`。
- 运行 `./kotlin build --module=llm-koog --platform=android`（若当前环境可用）。
- 运行 `git diff --check`，并确认无旧路径 Kotlin 文件残留。
