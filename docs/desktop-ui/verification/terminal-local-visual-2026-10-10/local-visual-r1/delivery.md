# 本地终端紧凑视觉实施交付

模型：SHUAIAPI/gpt-6.1-sol；provider：opencodex。

仅修改合同允许的三份源码。写集已释放，源码写入已停止；交付后退出，由父线程冻结源码并独立验收。

## 修改

- PtyTerminalPanel: 将六按钮与独立 selector 行收敛为单行紧凑操作区，使用 typed HarnessIconButton 和 HarnessMenu；保留真实完整 key、编号、lifecycle 与原动作条件及调用。
- 菜单作用域覆盖 bindingEpoch、selectionEpoch、selectedKey，作用域变化关闭 popup 并废弃待调度动作；保留非成功回执和真实异常、紧凑 Exited 状态及 screen/history。
- PtyTerminalGrid: 保留 10dp 宽度和原滚动、selection、绘制、测量逻辑；track/普通 thumb/hover 与 active thumb 分别采用 Surface/Border/BorderStrong。
- 页面测试保留旧六项名称及全部原断言，仅适配真实指针菜单路径；新增 terminalActionMenuUsesRealPointerKeyboardAndScope，覆盖键盘、disabled、真实粘贴与 scope 失效。
- 保留原十组完整页面 capture 路径，新增 1440 浅色和 600 深色菜单完整页面 capture 与 geometry；使用完整 scene capture API。

## 静态证据

- 基线 2253 个输入已核对，仅授权三文件变化，无缺失；before、after、基线和合同 SHA 已核实。
- 旧六项名称及 82 处原断言保留；输入字节与 kernel resize 测试体 token 未变，原十组 capture 调用序列保留。
- 七个页面测试声明；源码控制字符检查通过，git diff --check 返回 0。
- 六个归档 ASAR asset 的尺寸与 SHA 匹配 manifest。纯 Python classfile 检查确认完整 scene capture API，无 Java/Kotlin 执行。

## SHA256

基线：4f07269960f844caac4c17d1ae3210e51037521f080f683393ed3fe8df64cab5

合同：0a239221335824658fe16e1ce37b344a99ce8c758f35bdfff96715f8b3d2368e

| 路径 | Before | After |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt | cfa89844ca8485b15696c0fab4130c4ffb9aea0ae28453c84f53252e5e520358 | 60549f044d75d44f3d547462b67972be2fec75a3258a6ed70b53d26f4c505d32 |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt | efc37ddac576eaf4e4acc32083c9ee2151463cc82c1be67308b548990ef3d54b | beb49b1499ab6e91663186347a55e1bcc2893357129f12a1569369ea0d1f39b9 |
| apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt | a6c74d944687bbf78ccf33d24c544cd9f13e62345a11489f728a273799e4ddbd | f53a3c73e5effea44d896c102245494bb7707b29a646e2ec3a6eddd2baa49e61 |

## 证据边界与待验收

- 本 worker 仅实施和静态检查，没有运行 build、test、Kotlin、Gradle、Amper、screenshot、PTY fixture 或 native app。
- 35 项定向测试与完整 JVM build 由父线程冻结源码后独立执行，目前待验收；本交付不声称动态通过。
- 12 张页面 PNG 和对应 geometry/matrix 仅实现捕获路径，本 worker 未生成或检查动态产物。
- ASAR 支持本地分支、正文 start16/top8/bottom12、10px scrollbar 和 track/thumb/hover-active 角色；具体 Harness token 映射与紧凑操作区属于 Harness 适配。
- 没有原版同状态截图对照，不声称原版像素一致、原生窗口、IME 或 Remote 验收完成。

父线程计划：Page7、controller8、grid4、bridge4、Shell8、ConnectionIntegration4，共 35 项；另执行完整 JVM build。均待父线程执行。

预期 capture 目录：/tmp/harness-terminal-renderer-page-captures-20261010；保留十张完整页面图，新增 1440x900-light-Running-menu.png 与 600x800-dark-Running-menu.png 及 geometry/matrix。本 worker 未生成这些文件。

动态执行字段全部为 false；write_set_released 和 source_writes_stopped 均为 true。

机器可读交付：/tmp/harness-terminal-local-visual-delivery-20261010.json
