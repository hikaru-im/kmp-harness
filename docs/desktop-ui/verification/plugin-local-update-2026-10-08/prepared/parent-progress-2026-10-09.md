# 插件更新父线程验收进度

日期：2026-10-09，Asia/Shanghai。状态：部分验收，尚未最终归档。

- 冻结候选 `/tmp/harness-plugin-local-update-parent-candidate-20261008-initial`，2,205 输入。工作者已释放九文件写集。
- DesktopUserPluginsTest：21 tests，0 failures/errors/skipped，exit 0。
- DesktopInstalledPluginRepositoryTest：11 tests，0 failures/errors/skipped，exit 0。
- 真实 next request 测试源码经 CodeGraph 查询遗漏后的定向读取复核；捕获 GenerateOptions.system 与持久 REQUEST_HEADER，检查四次请求的旧/新/停用/再次启用、base system 和不累积。该测试实际通过。
- 四组剩余 33 tests 的串行 runner 两次提升权限审核超时，均未创建进程。
- 沙箱 fallback 实际启动 runner，但 Kotlin 初始化在 `/Users/hikaru/Library/Caches/JetBrains/Kotlin/telemetry/` 写入时被拒绝；PluginLocalUpdateInteractionTest 为 0 tests，exit 1，没有新的 XML。后面三组未启动。
- 服务层与仓库层 XML/summary/source-manifest 位于 `/tmp/harness-pages-verified-20261008/plugin-local-update/r1/` 下各自 suite 目录。沙箱失败同目录保留 command.log 和 summary.json。
- `/tmp/harness-plugin-local-update-parent-remaining-20261009.py` 仅按顺序调用既定 verifier。后续重跑须使用独立 run label 或保留失败目录后再运行，禁止覆盖失败记录。最终归档必须合并同一 manifest 的六组真实成功 XML，合计 65 tests，并审查 8 PNG 和 proof/geometry。
- Worktree 目录合同已准备，尚未派发；不因当前测试启动阻塞改变生产 Cron 或启用 destructive Worktree 操作。

## 后续实际运行：r2 至 r4

- Kotlin CLI 的 `KOTLIN_CLI_JAVA_OPTIONS=-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009` 使遥测及依赖缓存落在可写临时目录；r2 完成全量所需编译，但子测试 JVM 未继承该参数。Skiko 默认缓存锁写入失败，10 tests/10 errors；保留 environment-r2。
- 本机已下载 Skiko 0.144.6 字节码确认 `skiko.data.path` 是支持的缓存配置项。通过 `JAVA_TOOL_OPTIONS` 指向 `/tmp/harness-plugin-skiko-data-20261009` 后 r3 不再报缓存错误，但 macOS AWT RegisterApplication 触发 SIGABRT，0 tests；保留 environment-r3 和诊断路径。
- 追加 `-Djava.awt.headless=true` 后 r4 子测试 JVM 正常运行；PluginLocalUpdateInteractionTest 10 tests 中 9 successful，1 error：截图矩阵第263行真实 wheel 未能 reveal plugin-skill-5-name。不能把这个源码/交互失败当作环境限制。
- r4 既有 PluginManagementInteractionTest 3/3、PluginManagementControllerTest 9/9、InstalledPluginsControllerTest 11/11 通过；与 r1 的32项合计55项完整类级通过，UI9项只是未完整通过类中的部分结果，不汇总作最终65/65。证据见 partial-r4。
- 同一 SHUAIAPI/gpt-6.1-sol / opencodex 工作者已收到唯一 UI 测试文件的修复任务，禁止构建测试或修改生产文件。新 turn 01a11e32-c76c-7f50-8e4f-f5677423c3bc 正在分析。父线程准备独立 one-file overlay 冻结脚本；只有释放、复核和重跑后才出最终结论。
