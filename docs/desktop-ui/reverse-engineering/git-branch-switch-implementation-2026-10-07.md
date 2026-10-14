# 本地已有分支切换：本批实施边界

日期：2026-10-07。依据既有 Git/Worktrees ASAR 契约 G02/G08/G09，以及当前 DesktopGitBackend、GitController、GitModels、GitPages。实施由 SHUAIAPI/gpt-6.1-sol 完成，父线程审查并串行隔离验证。本地已有分支切换批次已通过定向功能验证，证据见文末；不代表全部 Git/Worktree 功能或视觉完成。

## 当前基础与本批范围

当前生产已可读取仓库、HEAD/状态、实际本地与远端分支、工作树和差异；独立 GitWorktreeCreator 已创建实际工作树。本批在同一 DesktopGitBackend 增加可选 GitBranchSwitcher，由现有 GitController 和 Git 页面接入。

只切换到实际读取的已有 refs/heads/ 分支。界面选择分支只更新选择；点击“切换”才提交命令。读取另一仓库/工作树仍只读取。新分支、提交、初始化、工作树删除、远端切换不混入本批。

## 请求与结果

请求包含 binding、repository root/commonDir/gitDir、来源 HEAD 分支与 OID、目标完整 ref 与 OID。目标来自真实分支读取，Backend 再复验；不将任意文本交给 shell。

实际命令为 argv 的 git switch --no-guess --no-overwrite-ignore --no-recurse-submodules -- 后接经校验的本地分支名；没有 force、stash、reset、自动 commit 或 ignore-other-worktrees。Backend 复验本机 Host、仓库目录、来源 HEAD、目标 OID 和干净状态。已暂存、未暂存、未跟踪、冲突均阻止切换；unborn 明确拒绝。Detached 来源仍必须有已确认的 OID。普通 clean status 不包含 ignored 文件，因此明确禁止覆盖 ignored 文件，并禁止从配置继承递归子模块更新。

命令执行后重新读取并确认 HEAD 分支、目标 OID 与状态，才发布 Switched 回执。前置验证与外部 Git 命令之间不保证原子锁；不得宣称阻止所有外部竞争。已经派发命令后的 timeout/cancel/失败或无法确认进入 UnknownOutcome，并要求重新读取，不自动重发或回滚。

Controller 校验回执归属和请求；与 createWorktree 共用 mutation admission。Refresh、切换页面或仓库不取消已接受的 mutation，旧读取和旧 scope 结果被 fence。未知结果按仓库保留，重新选择同一仓库仍不可再次切换/创建；status、branches、worktrees 三类真实读取成功后才解除 checkout 的 rescan 门槛，不能仅 worktree list 成功就解除。解除后使用独立 Rescanned 状态，重新读取当前状态不等于先前切换成功。同 commonDir 的当前选择在成功结果被导航 fence 后安排真实重读，跨身份/Host 不发布旧回执。

## 页面与验证

- 实际本地分支 picker 与明确切换按钮；只有后端可用、当前本机绑定、ready 干净状态、非当前实际本地分支才可操作。
- 进行中、前置拒绝、失败、未知与确认成功分别显示；不在选择 picker 时切换。
- 真临时仓库验证切换后的文件/HEAD；dirty index/working/untracked 原样保留，原工作区不执行 mutation。
- 目标/来源变更、其他工作树占用、选项注入、远端/foreign Host、确认失败/取消分别验证。
- Controller 验证与创建互斥、旧读取 fencing、跨导航保留未知、完整 rescan、回执不一致。
- 生产 Git 页面点击接真实 controller，测试读取可受控；不注入生产 fixture。

由子 Agent 落盘限定的四个生产文件与四个新增测试文件，父线程审查后在已有隔离目录串行验证。后续 managed 工作树保护/移除、提交与完整视觉继续按确认范围推进。

## 定向验证结果

本批最终共 **72/72**，没有失败、错误或跳过；其中新增分支切换测试 **20/20**，其余为共用 Controller、Backend、页面的既有读取与创建回归。

| 验证组 | 计数 | 保存的 JUnit 证据目录 |
| --- | --- | --- |
| Shared Git | 29/29（新增 checkout 7） | /tmp/harness-native-verification-evidence-20261007/checkout-shared/jvm |
| Desktop Git | 39/39（新增 backend 7、process 4） | /tmp/harness-native-verification-evidence-20261007/checkout-desktop-git/jvm |
| 分支切换页面交互 | 2/2 | /tmp/harness-native-verification-evidence-20261007/checkout-desktop-ui/jvm |
| Worktree 创建页面回归 | 2/2 | /tmp/harness-native-verification-evidence-20261007/checkout-worktree-ui-regression/jvm |

源码副本 `/tmp/harness-native-verification-20261007`；构建目录 `/tmp/harness-native-verification-build-20261007`。依次执行：

```sh
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m shared --include-classes='im.hikaru.harness.client.git.*'
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.git.*'
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.GitBranchCheckoutInteractionTest
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.WorktreeCreationInteractionTest
```

日志分别为 `/tmp/harness-checkout-shared-20261007.log`、`/tmp/harness-checkout-desktop-git-20261007.log`、`/tmp/harness-checkout-desktop-ui-20261007.log`、`/tmp/harness-checkout-worktree-ui-regression-20261007.log`。八文件 SHA256 清单保存在 `/tmp/harness-native-verification-evidence-20261007/checkout-source-manifest.json`；最终源码 hash 未变化且与隔离副本逐字节一致，`git diff --check` 通过。

真实临时仓库测试确认目标文件/HEAD 实际切换、源工作区证据不变、dirty 与 ignored 碰撞字节保留、其他 worktree 占用不强制切换，以及 detached/unborn 的不同处理。页面测试使用生产页面、Controller 与 DesktopGitBackend，证明 picker 只选择、点击实际执行、busy/前置拒绝/未知/Rescanned 的状态与真实 HEAD 一致。确认失败、取消和超时观察测试先执行真实 Git，再控制返回观察；不是 OS 进程超时的完整验证。未运行全平台或原版同尺寸像素对照，外部 Git 并发不具备原子锁保证。
