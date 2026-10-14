# 2026-10-08 独立验收记录

父线程冻结源码后串行运行；实现模型为 `SHUAIAPI/gpt-6.1-sol`，provider 为 `opencodex`。各子目录保留实际命令、退出状态、耗时、独立JUnit XML及汇总，避免后续构建覆盖报告。

| 目录 | 验证结果 |
| --- | --- |
| projects-main-path | ProjectsRecentInteractionTest 14 tests，0 failures/errors/skipped |
| plugins-interaction | PluginsPageInteractionTest 9 tests，0 failures/errors/skipped |
| plugins-screenshot | PluginsPageScreenshotTest 4 tests，0 failures/errors/skipped |

`harness-plugins-reader-units-20261008-diagnostics.json` 为24组reader前后轴值/bounds及父列表轴值；`harness-plugins-reader-units-assertions-20261008.json` 为父线程配对核验，24组均到达末尾，父位置和目标bounds不变。该证据来自macOS运行，其他平台没有运行验证。

截图及geometry：`../../captures/projects-main-path-2026-10-08`（16组）与 `../../captures/plugins-page-2026-10-08`（64组）。PNG完整性、尺寸、状态和geometry已核验，并人工查看contact sheet及选定完整原图；未作原版同尺寸像素验收。

Projects使用完整HarnessApp、临时真实文件存储/LocalConnection/Gateway及受控模型/session端点，不证明真实模型/Agent运行、原生窗口或远端链路。Plugins截图是独立页面/弹窗及测试fixture，不证明完整壳层/原生窗口，也不改变生产数据。
