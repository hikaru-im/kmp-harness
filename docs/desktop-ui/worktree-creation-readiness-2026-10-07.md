# 本地 Worktree 创建闭环

日期：2026-10-07。实施委托使用 `SHUAIAPI/gpt-6.1-sol`，主线程负责集成与串行隔离验收。依据既有 ASAR 契约 `reverse-engineering/git-worktrees-implementation-contract-2026-10-07.md` 与当前 CodeGraph 源码。

## 本批范围

选择已验证本机仓库、指定未存在的绝对目标路径、新分支名与实际提交起点，显式创建普通本地 Git worktree；真实重新扫描确认路径/分支/提交后读取新目录。只读 GitBackend 保持兼容，创建由独立静态 capability 提供。

普通 Git worktree 不是 managed inventory。本批不代表创建后自动绑定会话、复制源工作区未提交内容、执行 setup、归档/恢复、删除或本地环境管理已实现。源目录的暂存区、未暂存与未跟踪文件必须保留。云端环境、PR/发布和自动代码审查继续排除。

## 实施契约

1. 只有当前 verified Local binding 和真实 Desktop capability 允许创建；Relay、断线、其他 Host 不执行父机 Git。
2. 请求明确目标路径、新分支和起点；校验路径/分支，起点先解析为提交 OID，再使用 argv 执行 Git。已有目标目录拒绝，不使用 force、stash、reset、commit 或自动清理。
3. 创建 mutation 与读取 job 分离，refresh 不取消创建；同 controller 创建单飞，身份/Host/generation/选择切换对迟到发布设 fence。
4. 成功必须由真实 worktree list 与路径/分支/OID 证实；创建后读取失败仍保留创建 receipt。超时、取消或重扫失败明确保留结果未知，不能自动重发或假装回滚。
5. 页面有真实路径、新分支、起点输入和进行中/成功/失败状态；缺能力不可使用示例内容代填。

## 验收矩阵

| 层 | 必测证据 |
| --- | --- |
| Shared controller | 创建单飞、刷新不取消、scope/选择 fence、成功后读取、新目录读取失败仍保留 receipt、只读/远端禁用 |
| Desktop adapter | 路径/branch/ref 校验、argv、真实起点 OID、成功重扫、失败/超时/取消不假成功 |
| 临时真实 Git | 创建目录/分支/HEAD、源 dirty index/files 不变、existing target 不覆盖、未知结果重扫 |
| Desktop 交互 | 从设置 Worktrees 路由创建实际目录并读取；Relay 切换清除本机创建入口 |

本批测试只操作自行创建的临时仓库，不修改用户项目、配置或发起模型执行。定向验证不等于全平台测试、逐页视觉对照或像素一致。生产 Cron 开关保持关闭，Heartbeat 仍不可用。

## 验证记录

本批实现与定向功能验证完成，共 **52/52**，无失败、错误或跳过。实施使用指定的 `SHUAIAPI/gpt-6.1-sol`；主线程审查、补充真实 Git 及桌面交互测试，并在隔离副本串行验证。

| 最终测试 | 计数 | 独立证据目录 |
| --- | --- | --- |
| Shared Git controller | 22/22（创建14、只读8） | /tmp/harness-native-verification-evidence-20261007/worktree-shared-final/jvm |
| Desktop Git | 28/28（creator12、真实创建5、backend6、parser4、既有真实读取1） | /tmp/harness-native-verification-evidence-20261007/worktree-desktop-git-final/jvm |
| Worktree 桌面交互 | 2/2 | /tmp/harness-native-verification-evidence-20261007/worktree-desktop-ui-final/jvm |

源码副本：`/tmp/harness-native-verification-20261007`；构建目录：`/tmp/harness-native-verification-build-20261007`。在源码副本内依次执行以下命令，各次 JUnit XML 单独保存后才开始下一次：

```sh
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m shared --include-classes='im.hikaru.harness.client.git.*'
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.git.*'
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.WorktreeCreationInteractionTest
```

真实进程测试证明中文/空格路径、指定提交起点和实际工作树列表；源仓库 HEAD、index、未暂存与未跟踪文件保持不变。确认扫描失败或创建后取消时，实际已创建工作树保留，状态不伪造成功，也不自动清理或重发。桌面交互证明设置入口实际创建并读取新目录，Relay 替换为 unavailable 页面且无创建副作用。

最终源码与隔离副本逐字节核对一致，`git diff --check` 通过。首轮桌面筛选表达式未匹配测试、UI 文本/Relay 断言修正前失败均不计入最终通过数；最终按以上独立筛选重新运行。未运行全量 shared/desktop、Android/iOS/live 或像素对照，整体复刻仍未完成。Worktree 归档/恢复/删除、managed inventory、会话绑定与本地环境配置继续待做。
