# Worktrees 关联聊天固定：当前来源与实施分析

日期：2026-10-10。指定实施模型：SHUAIAPI/gpt-6.1-sol，provider opencodex。

## 原版行为

当前本机 app.asar 来源和精确片段见 `worktree-chat-pin-evidence-2026-10-10/manifest.json`。Worktrees 页面通过 pinnedThreads.list 读取聊天固定集合，并将 `isPinned` 投影到关联会话；服务缺失会报 unavailable。删除前在目标锁内重读固定集合，有关联固定聊天就拒绝。归档另调用 thread/archive，恢复另调用 thread/unarchive；固定不等于归档，Worktree 自身没有可替代聊天固定的 pin 位。

## Harness 当前可复用链

- 正式 Desktop bootstrap 捕获真实 Runtime SessionStore 和 Room SessionPersistence。Observer 独立读取 live headers 与 durable headers，不加载历史、不 flush、不修复；来源引用、匿名 Local Host/home 与 cwd canonical 组件匹配已核验。
- Managed 仓库核验 Git root/commonDir/gitDir 和当前 HEAD，页面绑定真实仓库并过滤旧 scope。登记生命周期已存在，无 Pin 写入接口。
- SessionHeader 是完整 serializable 不可变数据：id/createdAt/cwd/parentSession/seedLength/origin/delegationDepth/agentPreset，version 只描述格式。将 version 固定为 SESSION_FORMAT_VERSION 后编码并 SHA256，作为同 id 重用隔离键。
- 现有 main 接线可注入独立 managed-chat-pins.json，保持 managed-worktrees 登记文档和 Room schema 不变。

## 本批范围

新增独立 owner 分区的聊天固定存储，Worktrees 关联聊天行可固定/取消固定、刷新和重启重读。变更请求捕获 owner/repository/root/headerKey/expectedRevision；提交前重读来源并核对精确键。已有旧指纹保留，不自动绑定到同 id 的新聊天。

Controller 的读 epoch 与绑定/操作 epoch 分开；仓库 revision 导致刷新不能吃掉已接受回执，导航或关闭不能把旧回执显示到新页面。提交阶段有限 NonCancellable IO 返回实际提交结果；目录 fsync 失败是已提交警告，不称未写入。

完整保护仍未完成：两次独立目录读取不提供 absence proof，没有共享删除 gate。新增 Pin 读取状态只证明当前自有存储，不把空列表展示为无固定保护或可删除。归档/恢复与删除/清理不在本批开放。

## 验收方式

工作者只实施和静态检查，退出释放写集后父线程核对全部输入、冻结隔离副本并串行验证。真实临时 Git/Runtime/Room/JSON 和 Compose 鼠标操作；宽窄浅深四张 PNG 及对应 geometry。功能、离屏布局与原版像素验收分开，原生窗口、Remote 和其他平台仍未验收。
