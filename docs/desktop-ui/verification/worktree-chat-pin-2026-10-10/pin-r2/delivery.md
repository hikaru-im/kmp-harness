# Worktrees 关联聊天固定实施交付

model: SHUAIAPI/gpt-6.1-sol
provider: opencodex
baseline_sha256: 3f54ae3913707fae85bfe0ba219ae94ba545d2f002e86ca0ceee263513ee1c01
contract_sha256: a5f3faac4c4d724d3e7554dc2775a7cd9161ec440ffa2a7d11bc892f4c2d6e8e
delivered_at_utc: 2026-10-09T22:02:31Z

## 执行与释放状态

tests_run: false
builds_run: false
screenshots_run: false
native_execution_run: false
write_set_released: true
source_writes_stopped: true

## 原始实施静态核对（保留原记录）

- 比较 2254 个基线输入，合同写集之外无漂移；保留已有 dirty/untracked。
- 11 文件空白检查和 git diff --check 通过。
- 固定新增方法名称与顺序完全匹配：5 + 7 + 6 + 3 = 21。
- baseline 与 contract SHA256 均与授权值一致。

## 本次窄范围修复与静态核对

- 唯一源码修改：`apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinStoreTest.kt`，仅调整最后一个固定测试中的非法 owner 断言块。
- 空白 `HostId(" ")` 构造移入独立 `assertFailsWith<IllegalArgumentException>`；relative、`/../home`、`//server/share` 三个非法 home 路径仍在 `store.read` 处断言被拒绝。构造拒绝及每次非法 owner 读取拒绝后均增加磁盘 bytes 不变断言；原有 PinKey 拒绝和 bytes 断言保持。
- 对本轮 10731 个 Git 跟踪及非忽略未跟踪路径进行静态 SHA256/符号链接摘要核对；路径集合不变，除指定测试外 10730 个路径的联合摘要完全一致：`b424f74165c8927d51e5d26edec6b4e427fb95b779a668ca97a7e600fb0bbd73`。
- 11 文件全部 after_sha256 已重算，仅指定测试的 SHA 变化，其余 10 文件与原交付一致；model/provider/baseline_sha256/contract_sha256 及 11 文件原始 before_sha256 全部保留。
- 修复前测试 SHA：`b5c3e70a529397b738e06660f272f6bee2770010df24016eb75592c6640c6a03`；修复后 SHA：`28c02f8dc413af8bb2fcb80b664e42c1327d0134db8c11d38d8c3e27f4babd2a`。
- 精确补丁检查、目标空白检查和目标 `git diff --check` 通过；其他测试方法及现有断言保留。固定方法名称与顺序仍为 5 + 7 + 6 + 3 = 21，Store 全部 7 个测试名不变。
- 当前 worker PID 26406 启动参数已只读核对：`--model SHUAIAPI/gpt-6.1-sol`、`model_provider=opencodex`。
- 本轮仅静态检查，所有动态执行 flags 均 false；write_set_released/source_writes_stopped 均 true。

## 父线程独立冻结 pin-r1 结果（本 worker 未执行）

- ManagedWorktreePinControllerTest：5/5，通过；DesktopWorktreePinStoreTest：6/7，通过 6 项、error 1 项，无 skipped。
- 唯一未通过方法：`headerKeysRemainDistinctAndInvalidKeysRejected`；`IllegalArgumentException: Host id must not be blank`，R1 位置 `DesktopWorktreePinStoreTest.kt:154`。`listOf` 参数求值期间空白 HostId 已在类型边界抛出，尚未进入 `assertFailsWith`；这是断言位置修复。
- 已读取并核对两个类的 summary.json 与 TEST XML；summary/Jupiter XML SHA256 记录在同名 JSON 的 parent_frozen_r1_results.evidence_sha256。
- 父线程在本 worker 退出后另冻 pin-r2，独立验收 10 类 56 项及完整 JVM build；R1 结果不代表本次修复已动态通过。

## 实现摘要

- 完整 SessionHeader 经 encodeDefaults=true 和格式版本归一编码后生成 SHA256 PinKey；旧 header 指纹保留，不向同 id 的新身份重绑定。
- 独立 managed-chat-pins.json 按 owner 分区读取 Available/Unknown/Unavailable，严格 schema、UTF8、重复键和路径校验，短 OS lease 与完整原始 bytes CAS，revision 恰好递增、临时文件 force、atomic replace、目录 fsync 已提交警告。
- 请求捕获 repository/owner/root/key/revision/desiredPinned；提交前复核真实本机匿名 owner、Git 指纹和 HEAD，并在 Git 复核后再次完整观察 header，最终 source/owner fence 后有限 NonCancellable 文件提交。
- Controller 分离 read epoch、binding epoch 和 operation ticket，保留 revision 刷新中的 busy/receipt，接受操作独立于页面关闭；精确身份和 revision 校验，成功后重读磁盘，无本地状态翻转或自动重投。
- Worktrees 会话行显示真实固定状态及共享 HarnessButton；未知、缺服务、过期、busy、pageReady=false 或 scope/key 不匹配禁用，切 scope 首帧隐藏旧状态；main 注入真实 home 下的独立文件。
- 新增固定 21 方法覆盖 Controller、Store、真实临时 Git/Runtime/Room 集成及真实鼠标 UI；四种宽窄浅深截图和 geometry 输出由 HARNESS_WORKTREE_PIN_CAPTURE_DIR 控制，状态/几何断言始终保留。

## 未验证边界

- 本 worker 未运行任何动态测试、构建、截图、原生应用或 PTY；本次断言修复仅静态核对，pin-r2 的 Kotlin 编译、运行行为和视觉布局待父线程独立验收。
- 父线程在本次释放写集后另冻 pin-r2，独立验收新增 21 项加旧 35 项，总计 10 类 56 项，以及完整 JVM build 和四张 PNG/geometry。
- 完整原始 bytes CAS 和短 lease 不保证锁住不合作的外部 writer；最后 byte check 与 atomic replace 间仍有明确的外部竞争边界。
- Live 与 Durable header 为独立读取，不提供原子会话快照或 absence proof；完整保护、删除、归档与清理仍不可用。
- 原生窗口、Remote、其他平台、原版像素一致性和真实目录 fsync 故障行为尚未动态验收。

## 唯一源码写集及哈希（11）

| path | before_sha256 | after_sha256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt | 3654455a0d0c381465f745508415f8e30e3dc6bf66c2e0f81960fb5eb38be1d4 | e8e8ede21a6e726188b7851d41023471e28735330c5cb7d910b054549b58e338 |
| apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeController.kt | c41b750437b35ca603cda24b5cc5e60bf3755fd31b6bf9aae8f11ba1bca07431 | d6e04660c05222683b4bb4374bc0870a57df3a1ea784b2c708ea645df8cfe249 |
| apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt | 651673d4940403650b1004055ca40b7fad5439dae8f81c2c0de05f7312feab43 | 4a45842162ad6b0f82fd33541099b51e0ffd62d9f5db776af2c65436cc938be0 |
| apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt | 11a66b28a37a3bf44005ff288715835d94a9778f35f3a9af2bd8f195ab3c04f9 | 12c1bc6231e42163d4585809340f79984eb983420fd1ea8c1606fe3a97508c75 |
| apps/shared/src/im/hikaru/harness/client/git/GitPages.kt | ea4f017e59fa9c6d42f1703131d13a9a1031c7d2b9823a99b82e0b24fb147746 | 16f7b65398b110dd262890890c28cfb4caae652d437dc97c6a7f2829fe3757ed |
| apps/jvm-app/src/main.kt | d39aa62167cf3330e1bb0ce84db8aa281e0805087215575add0a3f2189e7e66d | b050ba2cd6b3cf895b87e5c03df00fcdf71c5c1c1d8e7d2c7a4dc27a4e158352 |
| apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreePinStore.kt | null | 3a88292fd670c5c6973286a377676db58658f89b2990e9b03cb067781f24f874 |
| apps/shared/test/im/hikaru/harness/client/git/ManagedWorktreePinControllerTest.kt | null | 485f588332c149bb61834250d3275491d1e67830b6fdabd37a2d1e52dd21bb03 |
| apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinStoreTest.kt | null | 28c02f8dc413af8bb2fcb80b664e42c1327d0134db8c11d38d8c3e27f4babd2a |
| apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinIntegrationTest.kt | null | 70258ed8bfe0c69c937df7aeee9de958386ac3ead7f46af4bdc6d849ad279b85 |
| apps/jvm-app/test/im/hikaru/harness/desktop/WorktreePinInteractionTest.kt | null | d0e9009bf213d1fa6c7edf8de6eca29d592517495ef95ff577f5a9cd2a4aa967 |

写集已释放；源码写入已停止，父线程可冻结并串行验收。
