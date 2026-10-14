# 本地插件更新：后续窄切片分析

状态：分析完成，尚未派发。实施继续使用 SHUAIAPI/gpt-6.1-sol / opencodex；终端、子 Agent 历史先按既定顺序验收。本文件没有本批构建或测试通过结论。

## 当前原版证据

父线程重新核验当前 `/Applications/ChatGPT.app/Contents/Resources/app.asar` 的全文 SHA256：`797be85cd4c5b1be359c55c1d57970671cfba284a5eacf7612ee0ae9add1c360`。六个资源和78个短片段的字节范围、哈希、UTF-8 文本均与安装包一致，保存为 `plugins-local-update-refresh-2026-10-08.evidence.json`。旧 2026-10-07 包消费契约的 ASAR 哈希不作为当前版本证据。

当前叶子确认：本地安装/卸载通过 plugin/install、plugin/uninstall；本地启停通过 config/batchWrite，带 expectedVersion 和 reloadUserConfig，之后刷新；installed 列表由 plugin/installed 请求读取。Upload new version 出现在 hosted 管理分支，不能据此推导本地包替换事务或借用远程接口。当前未执行这些 RPC，原版的本地替换持久协议仍未知。

## Harness 已存在的真实能力

CodeGraph 读取 DesktopUserPlugins、DesktopInstalledPluginRepository、PluginManagement、InstalledPluginsController、InstalledPluginsPage 和 PluginModels。六文件的当前哈希保存在 `plugins-local-update-sources-2026-10-08.json`。

DesktopUserPlugins 把已校验的 manifest 摘要及原始技能文本保存在自有 schema=1 JSON；registerLocal 默认停用。服务通过进程 mutex、文件锁、revision CAS、临时文件 force 和原子 move 提交，后续 AgentInstructionsService 请求读取自有快照。登记的技能文本不依赖原目录继续存在，启停与移除登记已经有持久消费者。

ID 由规范化 source + manifest.name 计算，并在加载时重新验证。registerLocal 拒绝已有 ID 或已有 source，因此修改源目录后再次登记不会更新旧版本。当前没有面向指定登记的替换操作；包资源、MCP、Apps、hooks 和脚本的运行仍未支持。

## 建议的最小真实更新闭环

本地更新作为 Harness 自有适配，先支持原登记来源的重新读取；不声称复刻了 Codex 的全包安装协议。

1. 用户在真实已加载详情上选择更新，目标 ID、scope、owner、当前 revision 均固定。原来源缺失则报告更新失败，已保存旧技能仍可读。
2. backend 在写入前读取并验证原 source 的新 manifest/技能，复用现有有界 parser。要求 source 与 manifest.name 仍与目标 ID 一致；改名不隐式迁移身份。version 仅展示字符串，不推导 semver、latest 或升级顺序。
3. 新内容通过现有 CAS 事务替换目标记录，保留原 enabled 状态，校验整个 inventory 和 instruction budget。源解析、revision 冲突、owner 撤销等失败不能破坏旧文本；原目录始终只读。
4. 下一次真实 Agent 请求使用提交后的技能；已提交请求的内容不声称可以撤回。未启用目标更新后仍不注入指令。完全相同内容可成为真实幂等操作。
5. UI 继续复用既有 mutation busy/refresh/error 和详情重读流程，拒绝跨 host/account/generation 的迟到结果；只给真实本地管理能力显示更新入口。成功必须来自服务提交和重读，不能先把版本标签改成成功。

## 派发前还需固定

补全 controller 的 mutate/失败恢复和 UI 接线源码审阅，再冻结具体写集、API、回归类与截图要求。需覆盖 enabled 保留、文本确实更换、源失效保留旧快照、改名拒绝、CAS/owner 失效、重启恢复、幂等更新、预算失败以及真实请求消费者。为确认 UI 到 backend 完整链路，交互使用真实控制器、repository 与临时持久服务。

本窄切片不会完成受管包目录、附件读取、插件依赖、MCP/Apps 授权执行或远程 marketplace；这些继续保留在整体功能范围的缺口清单。
