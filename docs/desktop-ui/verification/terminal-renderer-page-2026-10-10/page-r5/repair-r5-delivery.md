# 交互终端 R5 狭窄视觉修复交付

仅静态实施完成；动态验收待父线程在 worker 退出后独立执行。

- Model: SHUAIAPI/gpt-6.1-sol
- Provider: opencodex
- baseline_sha256: 8fdda6a105ce0e28d7e46a6e9fc957c27a26ae4e3e61933db11eaeb91ef397c7
- contract_sha256: 26cc6eec457c1a51bd4a1a1e73f892070d1754ee75f26fa2650ec2b422cc20b9
- before_snapshot: /tmp/harness-terminal-renderer-page-repair-r5-before-20261010/before.json
- source_writes_stopped / write_set_released: true / true；已停止源码写入并释放写集。
- builds_run / tests_run / screenshots_run / native_execution_run / compiler_run / process_fixtures_started: false。

## 两文件 R5 before/after SHA

| 路径 | R5 before_sha256 | R5 after_sha256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt | f88cfe3bae428d26ab3b1a901c65485621243bfa7a58c9112304177dd01166a2 | cfa89844ca8485b15696c0fab4130c4ffb9aea0ae28453c84f53252e5e520358 |
| apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt | 1cb6746f92750208e7656e8f216200a30bd06fb34bdec2371e78a182f473b1db | a6c74d944687bbf78ccf33d24c544cd9f13e62345a11489f728a273799e4ddbd |

## 修改

- selector 仅在完整 TerminalKey 同时等于 selectedKey 与真实 snapshot.metadata.key 时采用 snapshot lifecycle，其余项保持 inventory lifecycle；不从 Unknown 回执推断状态。
- 既有 wideAndCompactLightDarkMappedGridRemainUsable 中新增 selector 完整 Text 精确断言：终端 1 · Running、终端 1 · Exited (7)。原四种尺寸/主题组合、数据来源、native snapshot 及既有断言保持。
- 既有 unknownUnavailableAndViewCloseHaveHonestActions 两处 HarnessApp setContent 显式传 HarnessThemeMode.Light，其余逻辑保持。

## 静态核查

- 精确重新枚举 2,253 输入，仅上述两文件 SHA 改变；写集外 2,251 输入 SHA 保持。
- 写集外 before/after digest 均为 4ff2e012681e1e0c63fdff8cf848253369015b80ae145d0727295805ce349b03。
- 合同与 baseline SHA 保持；另列三个 frontend YAML SHA 保持。
- 固定新增 8 类 36 方法集合逐项与合同及原交付记录一致。现有 R4 选定 30 类 184 方法与当前源码方法集合匹配；仅只读解析既有 XML，本轮未运行。
- 页面 6 个 @Test 名字与数量保持，仅授权两方法改动。historySelectionCopyAndExitedScreenRemainVisible 以及另外三个受保护方法逐字节一致，全部 helpers、imports 与其它页面字节一致。
- history 方法字节 SHA: 2c0cee2747e854a04c7cc03be15aea175b74f7c47faa1f9a2908a24602f75098。
- 通过恢复两条新增断言和两处 Light 参数，精确还原 R5 before 页面文件；生产文件仅 lifecycle 选择表达式最小替换。原断言及 fixture 来源保持。
- 限定两文件 git diff --check exit 0；untracked 文件另直接核对 UTF-8、LF 和尾空白。
- 原 22 条 before_sha256 与 baseline 一致；其它 20 条记录、固定 36 方法集合、旧 repair_r2/r3/r4 及其余原 JSON 字段保持。仅 status、两条 after_sha256 和新增 repair_r5 改动。
- 静态核查不代表编译、测试或截图通过。

## 动态交接

build、test、截图、native execution、compiler、PTY/native fixture 均未运行或启动。父线程独立动态验收待执行。源码写入已停止，写集已释放；交付后退出。
