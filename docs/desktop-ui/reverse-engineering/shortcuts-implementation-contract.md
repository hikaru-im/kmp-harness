# Keyboard Shortcuts 实施契约（2026-10-07）

状态：反向分析完成，开始实施。用户指定委托模型 SHUAIAPI/gpt-6.1-sol。

## 证据

从 /Applications/ChatGPT.app/Contents/Resources/app.asar 当前 header/offset 读取，未修改应用。分析副本在 /tmp/harness-asar-shortcuts-20261007。下列偏移为解码后 UTF-8 字符位置，不能用于 ASAR 字节定位。

| Bundle | 字节数 | SHA256 |
| --- | ---: | --- |
| keyboard-shortcut-command-order-61fd44460f71.js | 2275 | 4680059aa434796a9c494364893b4af6e8604c54176e8b0515ec5627362b8c08 |
| keyboard-shortcuts-search-input-506461fdb127.js | 2229 | 8462858598e2ae107bae96c29d40214579103a7659c63d8266e8df5929327a41 |
| keyboard-shortcuts-settings-43cf2d8e4f9b.js | 35742 | fb8e11fbe54d3afdff39c72a4af1f2b0716ad9aee68858a90218975740e8dfa4 |
| app-initial-69cd8dbddec5.js | 11173718 | 8c11b76e960300895e8a3dde8b2613f87c92ed226b58173aa73ceaff9cd2b604 |

settings 主模块 pn 区分 codex/chatgpt presentation。Composer/Chat/App 分组（wn，34511附近）、48px圆形按钮行和页脚说明属于 ChatGPT 分支；Codex 使用 Settings 框架 Je、stickyControls 搜索、action 重置按钮和 Bt 设置行。不要把 ChatGPT 分支当作 Codex 主界面证据。

Codex Bt：标签/描述在左，右侧宽度上限 w-96，max-sm 改为上下布局；支持多个绑定、append/replace/remove/reset 与无绑定状态。来源5700–10390。错误文字为行内 alert。

pn 数据源 codex-command-keymap-state；动作 set-codex-command-keybinding / reset-codex-command-keybindings（20000–22200）。这是原版 IPC，Harness 需要独立本地实现，不能冒称兼容此 IPC。加载27760、无匹配28627、文本/按键搜索24671/25100、重置确认32105–34100。搜索 Escape 退出录制；重复键忽略。

Ht 10390：保留 Find/Find Next/Previous；冲突检查包括多段序列的前缀。默认修改必须先完成宿主保存再替换可执行映射。原版支持多段序列，本批先支持单组按键，明确描述此限制；拒绝无法执行的序列，不能存储后假称可用。

## 命令与消费者

原版 app-initial 中 jV 为静态命令注册表；Rqr/zqr 解析用户覆盖与默认键，null key代表禁用；Lqr按平台映射 CmdOrCtrl。

| 命令 | 原版默认 | 本批消费者 |
| --- | --- | --- |
| settings | CmdOrCtrl+,（3692073） | HarnessApp typed Settings General route |
| showKeyboardShortcuts | CmdOrCtrl+/（3693418） | Settings Shortcuts route |
| toggleSidebar | CmdOrCtrl+Shift+S、CmdOrCtrl+B（3695062） | 现有 sidebar/drawer；本批仅默认第一组，B保留格式用途，原版B含Composer上下文例外，不全局截获 |
| navigateBack | CmdOrCtrl+[、MouseBack（3701785） | NavigationState.back；本批键盘组，鼠标键待平台适配 |
| closeWindow | CmdOrCtrl+W；非mac另有Ctrl+F4（3664032） | DesktopWindowCloseController；native close保持现有策略，本批默认W |
| manageTasks | 无默认（3694110） | 现有 ScheduledTasks route，用户可分配 |

Composer发送/附件/模型、终端与子Agent/下方面板属于保留范围但目前没有完整消费者；如展示必须明确不可用且禁止编辑/执行。被排除的语音、电脑/浏览器操控、云端等命令不展示。无需填充一长串无实现动作。

## 数据与执行契约

静态typed命令/平台/chord模型；设置页与事件消费者使用同一repository有效快照；不增加JS、动态Slot或运行时声明。Desktop在应用本客户端home保存独立client-shortcuts.json，atomic replace；测试仅临时目录。默认、用户覆盖、显式禁用区分；命令仍有默认时reset删除覆盖。允许同命令多个不同绑定，禁止不同命令冲突；保留Find、系统退出与剪贴板/常规格式快捷键。无modifier的字符不允许全局绑定，不能抢占输入。

load未完成或失败禁止编辑，不覆盖损坏文件；错误净化、取消传播、重复提交阻止。保存成功后才发布有效键；失败保留旧映射与录制草稿，可重试。未知schema/非法chord或冲突配置拒绝读取，禁止悄悄重置。无平台adapter时页面可搜索、显示原因，编辑与close命令不可用。

事件：KeyDown触发一次；KeyUp释放；忽略重复按下。同一组合不可被窗口和HarnessApp双执行。录制/按键搜索优先于动作分发，capture状态必须同时阻止窗口close和App动作；Escape取消并恢复焦点。普通Dialog/菜单/输入保留自身键盘行为；窗口确认已打开时不可执行其他导航。配置变更立即作用于真实消费者，旧键应失效，清除close后快捷键不可关闭窗口，native close继续可用。

## UI和验收

SettingsFormFrame，728dp上限，紧凑窗口16dp padding；共享Search/Button/SettingsRow/Dialog/Feedback，浅深色。标题→重置action→搜索/按键模式→简短单组限制说明→可用命令行，必要不可用行→加载/错误/无匹配。录制字段、冲突错误、移除/恢复/添加、重置确认均可键盘与鼠标操作；窄屏控件换行不裁切。

验收：实际Settings路由切换/返回/sidebar/Scheduled入口、DesktopWindowCloseController的自定义/移除/default键与策略；capture和搜索不触发旧close、modifier-only/保留/冲突拒绝、重复按键/重复提交、写失败恢复、重新实例重读、损坏文件不覆盖、420/600/1440浅深截图。测试数据只用于测试。无原版本页截图，目前仅能验收结构与行为，不能宣称像素一致。

写集：shared/settings新增Shortcuts模型/repository与JVM store、page/SettingsShortcutsPage；HarnessApp接入；desktop/main与window接入同一repository；对应shared/desktop测试。保持当前dirty tree所有其他工作，Amper由主线程串行运行。
