# 子 Agent 历史父线程验收准备

状态：2026-10-09（Asia/Shanghai）R1 最终验收通过，沿用 2026-10-08 批次名。模型固定 `SHUAIAPI/gpt-6.1-sol` / `opencodex`。工作者已释放六文件写集；父线程冻结 2,204 个输入，同一 manifest 串行通过 59/59，无失败、错误或跳过。

最终类级数量：ConnectionThreadPanelsRepositoryTest 9、SubagentHistoryInteractionTest 10、ThreadPanelsControllerTest 12、ThreadPanelsInteractionTest 9、LiveConversationInteractionTest 11、HarnessAppThreadPanelsShellTest 8。原始 XML、日志、逐类 summary、manifest 和交付保存在 `verification/subagent-history-2026-10-08/r1-final/`。8 张历史及 80 张壳层截图和 geometry 在 `captures/subagent-history-2026-10-08/r1/`；已核对尺寸、SHA、全文末尾并查看历史 contact sheet。

初轮 telemetry 缓存拒写及模块参数错误均为 0 tests；初轮仓库 9/9、controller 12/12 后，UI 编译发现测试 fixture 的 ChildAgentSummary 缺 canInteract，原工作者补 canInteract=false。R1 只依据修正后同一 manifest 的实际 XML 统计；历史记录另存，不累加。交付 JSON 的 baseline 日期笔误由父线程纠正，原件保留，正确 baseline_manifest 与 before SHA 未改变。下方保留原准备规则。

## 冻结入口

父线程脚本：`/tmp/harness-subagent-history-parent-freeze-20261008.py` 和 `/tmp/harness-subagent-history-parent-verify-20261008.py`；副本归档于 `verification/subagent-history-2026-10-08/prepared/`。仅作 Python 语法检查，尚未执行冻结、构建或测试。

冻结须显式提供已验收终端候选的 `--base`、`--base-build` 和唯一 `--attempt`。要求六文件有限写集全部交付、无重复路径、`write_set_released=true`、`tests_run=false`、`builds_run=false`；每份 before SHA 对应基线，新增文件 before 为 null。检查当前输入漂移及新增 Kotlin 文件，拷贝后再次检查交付 SHA 和未修改输入。任何异常均停止，不覆盖已有候选。

验证入口参数是 module、完整 class 名称和预期用例数。每类串行执行，检查同一 manifest，要求最新 XML 用例数量精确匹配且失败/错误/跳过均为零；测试结束再次核对输入 SHA。记录原始日志、XML、命令及 summary。截图目录通过 `HARNESS_SUBAGENT_HISTORY_CAPTURE_DIR` 注入。

## 有限验证顺序

| 顺序 | 测试类 | 预期数量 | 验证目的 |
| --- | --- | --- | --- |
| 1 | ConnectionThreadPanelsRepositoryTest | 现有 6 项及新增，交付后核定 | typed 结构、来源、递归内容和可变集合快照 |
| 2 | SubagentHistoryInteractionTest | 新类，交付后核定 | 真实选择/展开/滚轮、状态分支、身份隔离和全文末尾 |
| 3 | ThreadPanelsControllerTest | 12 | 保留 owner、迟到请求、移除和历史权限撤销防护 |
| 4 | ThreadPanelsInteractionTest | 9 | 既有只读详情、返回/关闭及终端动作 |
| 5 | LiveConversationInteractionTest | 11 | 共享 MessageCard 可见性调整后的主对话回归 |
| 6 | HarnessAppThreadPanelsShellTest | 8 | 完整壳层的窄/宽、浅/深及真实服务调用链路 |

上述现有数量来自当前源码；Shell 同文件另有 6 项 DraftIsolation 测试，不能把整文件 14 项误作 Shell 类数量。新增数量必须根据实际实现核定，不用报告数字代替 XML。新增 UI 类使用 jvm-app；共享类的模块名按实际 Kotlin 项目解析结果确定。

## 截图与通过边界

320×600、640×800 × 浅深色 × 收起/展开：共 8 组基本截图，逐张核对实际尺寸、SHA、viewport/text 几何及末尾证据，并查看代表图片。额外状态仅按契约或具体失败增加。

通过结论只覆盖受控 typed fixture 的离屏生产 UI，不证明原版同状态像素一致、原生窗口、远端或完整持久后代历史。Loading/Error/Unavailable 不算空成功。终端 PTY/ANSI/resize、Cron 生产启用、插件生命周期与 Worktree 完整会话仍按既有计划推进。
