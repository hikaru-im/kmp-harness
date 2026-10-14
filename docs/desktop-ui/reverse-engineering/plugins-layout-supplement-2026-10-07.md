# 插件管理布局补充分析

日期：2026-10-07。此补充基于现有 ASAR 契约中相同 SHA256 的两个提取文件，具体字节偏移和上下文见同名 `.evidence.json`；不重新定义插件服务范围。

## 管理列表与市场的区别

`plugins-settings-9d5c3f603c0d.js` 的管理内容调用 `Zr` 时明确传入 `installedStateAction: toggle`、`isSingleColumn: true`、`useCompactEmptyState: true`。因此已安装管理页按单列分析，不能直接照市场截图使用双列网格。

管理顶部为 `flex flex-wrap items-center justify-between gap-3`；搜索容器为 `w-56 min-w-0`，输入 ID 为 `plugins-page-manage-search`。包装容器使用 `embedded: true` 与 `fullWidth: d`，非全宽分支为 `h-full overflow-y-auto p-panel [scrollbar-gutter:stable]`。这些是 CSS 类与分支证据，不等于已经测得最终窗口中的物理像素。

`plugin-detail-view-85d1ba24f587.js` 中 Apps/Skills 相关行使用 `presentation: row` 与 `descriptionClassName: line-clamp-1`；按行展示是后续详情对齐的依据，字段和动作仍需按 Harness 实际能力取舍。

`installed-plugins-96e0e868bafa.js` 本轮读取的主体是 Customize 侧栏已安装插件导航；`plugins-settings-row-d104736ec9a1.js` 是 General 的全局插件开关。两者不能单独证明管理页主体卡片几何。旧实施契约的 bundle 前缀只是定位索引，按此补充区分表面。

## Harness 当前几何与确认的缺陷

当前插件页面内容最大宽度 760dp、内边距 16dp；1440×900 离屏截图内容约为 x=356..1084。600×800 列表、长标题、详情 Skills/Apps 滚动、错误和不可用状态未见明显横向裁切。详情返回/刷新与管理动作仍纵向排布，完整视觉对齐尚未完成。

登记弹窗发现确定的公共输入缺陷：`HarnessTextField` 使用 Material3 floating label，却把单行整体强制限制为 36dp，非空值、标签和校验说明被裁切。此组件也用于项目、Git/Worktree、Home、定时任务等表单，优先修复公共实现并验证实际绘制，再继续页面几何调整。

现有真实参考 `reference/captures/2026-10-06-user-pages/plugins-marketplace.png` 是市场页，旧 Harness 插件截图是历史示例数据。当前没有同尺寸已安装管理页真实截图，所以本批截图用于组件可读性、状态与滚动检查，不宣称像素一致。测试 fixture 仅用于截图，生产列表使用真实服务。


## 公共输入缺陷已修复并验证

上述发现保留为实施前依据。现已使用独立 label/supporting text 和最小36dp的 BasicTextField 编辑区域；字号增大时允许区域扩展。最终 foundation 3/3，通过32种绘制组合、独立文字像素参照与真实交互；desktop截图/插件/项目/Worktree回归24/24，无失败或跳过。登记与invalid path弹窗的路径文字、标签及说明已人工复核；完整64张最终PNG与manifest位于 `../captures/plugins-visual-2026-10-07/`。新弹窗命名 `dialog-page-viewport` 表示完整viewport，含背景和scrim。

组件修复不改变本补充的原版证据边界：没有同尺寸真实管理页对照，仍不能称像素一致。详情几何与其余保留页面尚待继续对齐。完整命令与JUnit证据路径见 `../plugins-local-registration-readiness-2026-10-07.md` 最新同步节。
