# 本地插件更新实施合同

状态：九文件实现及唯一 UI 测试滚轮修正已由同一 `SHUAIAPI/gpt-6.1-sol` / `opencodex` 工作者交付并释放。父线程冻结 2,205 输入，同一 manifest 串行通过 **65/65**（服务21、仓库11、新UI10、管理UI3、控制器9和11），无失败/错误/跳过。8 张浅深色、320×600/640×800 更新前后 PNG 及完整末尾文本、无省略、滚动终点、marker 可见性证明已核对；contact sheet 已目视复核。最终归档见 `verification/plugin-local-update-2026-10-08/wheel-r1-final/`；原基础设施和9/10失败历史保留。使用临时缓存和 headless JVM 验证离屏生产 UI，不证明原版同状态像素一致、原生窗口或远端生命周期。基线为已验收子 Agent 历史 R1（2,204 输入）。2026-10-09（Asia/Shanghai）沿用本合同批次名。

## 原版证据和当前生产链

当前ASAR全文SHA `797be85cd4c5b1be359c55c1d57970671cfba284a5eacf7612ee0ae9add1c360`；六资源78个短片段已复核，见 `plugins-local-update-refresh-2026-10-08.evidence.json`。本地安装/卸载RPC、启停CAS和刷新可确认；hosted Upload new version不能用作本地替换事务证据。本合同的更新语义是Harness自有本地适配。

父线程用CodeGraph补全读取DesktopUserPlugins.mutate/commit/importLocal/validateRecords/render、完整PluginManagement/PluginModels/DesktopInstalledPluginRepository及InstalledPluginsController的dispatch/list/select/mutate。当前JSON schema=1已保存manifest摘要与原始技能文本，进程mutex+文件锁+revision CAS+force+原子move提交；next request通过AgentInstructionsService真实消费。controller固定scope/revision/epoch，成功后重读，失败锁住后续写入直到显式刷新；adapter绑定已验证本机连接并在提交前检查lease。新增流程必须复用这些边界。

## 有限九文件写集

1. `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt`：增加updateLocal与必要局部helper，复用parser/事务，不改持久schema或既有限制。
2. `apps/shared/src/im/hikaru/harness/client/plugins/PluginManagement.kt`：增加可选更新capability和方法。
3. `apps/shared/src/im/hikaru/harness/client/plugins/PluginModels.kt`：更新action及可用性state。
4. `apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsController.kt`：固定目标并接入现有mutate。
5. `apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt`：详情更新入口与必要清楚文案，复用当前controls/feedback。
6. `apps/jvm-app/src/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepository.kt`：真实本机更新adapter。
7. `modules/bundle/desktop/test/im/hikaru/harness/bundle/desktop/DesktopUserPluginsTest.kt`：源更新和持久回归。
8. `apps/jvm-app/test/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepositoryTest.kt`：真实owner/CAS、重新读取和下一请求消费者。
9. 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/PluginLocalUpdateInteractionTest.kt`。

不改connection/transport/AgentLoop/生产config/Comet/git，不管理Codex私有状态。保留dirty/untracked；工作者不构建、不测试、不创建其他agent；交付释放后父线程验收。

## 固定API与持久行为

- service：`updateLocal(id: String, expectedRevision: String, isCurrent: () -> Boolean = { true }): DesktopUserPluginSnapshot`。不接受任意新路径；在CAS保护的现有records中找到指定id，从它的source重新importLocal。必须仍是同一规范化source、同一manifest.name和同一生成id；改名、缺目标、缺源、错误格式或超限失败。version只展示字符串，不比较semver/latest。
- 保留旧enabled并仅替换目标record，其它记录顺序与内容不变。重新校验整份inventory、总文本和组合指令预算，成功才原子发布。失败保留旧记录/字节/指令；源目录始终只读。解析与guard复用现有有界路径检查，不读取MCP凭据或执行附件。
- 相同源字节重复更新保持revision/文件字节及enabled不变。修改技能正文或manifest版本会改变真实snapshot并能从新service读回；不把UI版本标签变更当作成功。
- `LocalPluginManagementRepository`增加 `canUpdateLocal: Boolean` 默认false，以及有默认UnsupportedHost结果的 `updateLocal(scope,id,expectedRevision)`，现有只读/fixture实现保持兼容；Desktop adapter通过实际本机管理lease启用。state跟随真实capability，按钮仅在该能力存在时显示，仅在匹配selectedId的成功detail及可管理revision下可用。
- action `UpdateLocal(id)` 在dispatch时冻结真实detail的id、scope、revision；伪造id、旧scope、detail Loading/Error/Unavailable、busy或需要刷新时拒绝。调用现有mutate，成功重读list和当前所选detail；失败显示具体保存错误且只由显式刷新解锁，不自动重试。scope/owner切换忽略迟到结果；选中其它详情不得把新内容写到错误记录或显示错误身份正文。
- 可见入口采用“从源目录更新”，说明保存Skills快照、保留启用状态、下一请求生效；busy/失败/已保存但刷新失败复用当前反馈。新mutation开始清除上一mutation的receipt/notice，避免旧成功提示冒充本轮更新成功。不能因源不存在而丢失已保存旧文本。
- 下一真实Agent请求读取新技能；disabled仍不注入，启用后使用新内容。已提交给模型的请求不声称可以撤回。MCP/Apps/hooks/受管附件/marketplace生命周期仍有独立缺口。

## 定向验收

- service真实临时目录：enabled与disabled更新；manifest/技能内容更换；新service恢复一致；重复更新幂等；缺源、manifest改名、无目标、parser错误、路径/symlink和预算失败保持旧字节及可用指令。复用旧限制和原错误语义，不为通过新增测试降低限制。
- repository真实LocalConnection+ApiGateway+服务：所有旧access deny/foreign/Relay/同host不同连接/替换generation测试补上新方法；stale revision与真实文件锁等待中的owner撤销不得提交。对照old/new snapshot、receipt和fresh adapter读回。新增真实AgentLoop请求，捕获GenerateOptions.system与REQUEST_HEADER：旧技能→更新后新技能→停用更新仍无技能→启用新技能；无重复累积，保留base system。
- UI用生产InstalledPluginsController+DesktopInstalledPluginRepository+真实临时持久服务，使用真实鼠标/键盘/滚轮；展示重新读取后的version/skill metadata、enabled保留、失败后旧版本、busy禁用、显式刷新解锁、scope撤销/迟到响应、back/选择切换防护；不能用成功fixture返回或performClick替代正常操作。可控锁/dispatcher延迟仅用于明确竞态。
- 320×600、640×800×浅/深×更新前/更新后，共8PNG及几何，环境变量 `HARNESS_PLUGIN_LOCAL_UPDATE_CAPTURE_DIR`。截图必须来自真实临时服务；记录实际尺寸、SHA、controls/最后技能行的viewport几何，紧凑布局可滚动到完整内容。长文使用真实滚轮和10s deadline，禁止skip/吞异常。
- 交付 `/tmp/harness-plugin-local-update-delivery-20261008.json` 和 `.md`：九文件before/after SHA、指定模型/provider、各类实际测试数和names、tests_run=false、builds_run=false、write_set_released=true。基线在派发时固定为上一通过候选manifest。

父线程冻结同一manifest串行验收DesktopUserPluginsTest、DesktopInstalledPluginRepositoryTest、新UI，以及原PluginManagementInteractionTest和受影响controller管理suite。依据具体剩余风险扩大验证，不重复无关整套测试。fixture/离屏结构证据不等于原版像素一致、真实远端或进程crash验证。
