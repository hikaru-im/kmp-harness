# 本地插件更新父线程验收准备

状态：准备完成，子 Agent typed 历史 R1 59/59 验收归档后已向同一工作者派发。实施模型固定 `SHUAIAPI/gpt-6.1-sol` / `opencodex`，工作者不构建或测试。固定基线为 `/tmp/harness-subagent-history-parent-candidate-20261008-r1` 及对应 build；本批尚未运行构建或测试。

## 冻结与测试

脚本为 `/tmp/harness-plugin-local-update-parent-freeze-20261008.py` 和 `/tmp/harness-plugin-local-update-parent-verify-20261008.py`，副本在 `verification/plugin-local-update-2026-10-08/prepared/`。基线必须使用最终通过的子 Agent 候选及其 build 目录；不得使用正在修改的工作区作为基线。

交付九文件 before/after SHA 和 release flags 后先审查，再以唯一 attempt 冻结。脚本检查有限写集、全量 Kotlin/YAML 输入及基线 SHA，测试前后检查同一 manifest。每类串行执行，禁止共享 build/report 目录并行测试。

| 顺序 | 测试类 | 数量 | 目的 |
| --- | --- | --- | --- |
| 1 | DesktopUserPluginsTest | 交付后核定 | 真实持久服务、幂等、失败保留旧内容、限制和重启读回 |
| 2 | DesktopInstalledPluginRepositoryTest | 交付后核定 | lease/CAS、owner 撤销及下一次真实模型请求使用新内容 |
| 3 | PluginLocalUpdateInteractionTest | 新类，交付后核定 | 真实服务 UI、更新/失败/刷新/选择切换与截图 |
| 4 | PluginManagementInteractionTest | 3 | 注册、启停、移除以及隐藏不可用管理动作 |
| 5 | PluginManagementControllerTest | 9 | 单次写入、迟到回执、显式刷新及写后读取失败 |
| 6 | InstalledPluginsControllerTest | 11 | 读取、scope/owner 身份、列表与详情的迟到结果 |

后三类数量由本轮 CodeGraph 定位后读取当前源码确认；实际 XML 必须逐 testcase.classname 验证。新增两类数量在释放后由源码核定，不用交付声明代替测试结果。若原有 controller 断言暴露更新 capability 兼容问题，先修复实现，再重新冻结候选。

## 截图与结论边界

生产 UI 接真实临时服务；320×600、640×800 × 浅深色 × 更新前后，共 8 PNG。记录 SHA、尺寸、控件和最后技能行的几何，紧凑窗口用真实滚轮确认全文可达。截屏及结构通过不等于原版像素一致或真实远端验证。

原版本地替换事务尚未确认，更新语义为 Harness 本地适配。MCP/Apps/hooks/受管附件和 marketplace 完整生命周期仍须单独分析。生产 Cron 保持禁用。
