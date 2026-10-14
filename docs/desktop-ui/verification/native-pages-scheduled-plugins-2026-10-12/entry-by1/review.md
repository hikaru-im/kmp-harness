# BY：定时任务页与插件页原生窗口截图（2026-10-12）

## §1 为什么做这一批

复刻计划的第 4 项（定时任务）与第 5 项（插件管理）长期都带着同一条边界「原版同状态像素、原生窗口待验收」；
此前只有 Settings（C1.5B，2026-10-07）与插件库存页（AL，2026-10-11）有真窗口截图。原因是本机 `screencapture`
自 2026-10-11 13:03 起持续返回 `could not create image from display`。

本批开始时复测：`screencapture -x -t png /tmp/probe-shot.png` 成功写入一张 2560x1440 实屏图（该探针是屏幕内容，
未入库）。据此判定阻塞已解除，并用仓库既有脚本补出定时任务页与插件页的原生窗口截图。

本批**不改产品代码**，只新增截图与证据。

## §2 写入集合

| 文件 | 内容 |
| --- | --- |
| `docs/desktop-ui/captures/native/plugins-native-{dark,light,system}-1440x900.png` | 重跑（AL 已存在）；重跑结果与 AL 记录的 sha256 **逐字节一致** |
| `docs/desktop-ui/captures/native/scheduled-native-{dark,light,system}-1440x900.png` | 新增，`screencapture -x -o -l <windowId>` |
| `docs/desktop-ui/verification/native-pages-scheduled-plugins-2026-10-12/entry-by1/measurement/measure-native-pages.py` | 新增，纯标准库 PNG 解码 + 几何校验（不依赖 Pillow） |
| 同上 `measurement/native-pages-measurements.txt` | 新增，脚本输出（`PROBLEMS 0`） |
| 同上 `measurement/captures.sha256` | 新增，六张 PNG 的 sha256 |
| 同上 `logs/capture.log` | 新增，六条命令与其输出 |
| `docs/desktop-ui/native-window-acceptance.md` | 新增 Batch BY 小节 |
| `docs/desktop-ui/replication-plan-2026-10-07.md` | 新增 BY 行 |

## §3 采集条件

主机外观 **Light**（`AppleInterfaceStyle` 键不存在），显示器 2560x1440，窗口参数 `--size 1440x900`，
每次以独立临时 Harness home 启动，`--destination plugins|scheduled`，`--theme dark|light|system`。
窗口由 CoreGraphics 定位、按 window id 截取，因此 `-l` 只会截到该窗口自己的内容。

## §4 测量结果（`measurement/native-pages-measurements.txt`）

| 元素 | plugins dark | plugins light | plugins system | scheduled dark | scheduled light | scheduled system | C1.5B settings 参考 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PNG 尺寸 | 1440x900 | 1440x900 | 1440x900 | 1440x900 | 1440x900 | 1440x900 | 1440x900 |
| 交通灯彩色像素 / bbox | 336 `(8,8,59,19)` | 360 `(8,8,59,19)` | 360 `(8,8,59,19)` | 336 `(8,8,59,19)` | 360 `(8,8,59,19)` | 360 `(8,8,59,19)` | `x 8..59, y 8..19` |
| 标题条分隔线（x=700） | y=44 | y=44 | y=44 | y=44 | y=44 | y=44 | y=44 |
| Rail/分隔/nav/边界（y=400） | `51,52,340,341` | `51,52,340,341` | `51,52,340,341` | `51,52,340,341` | `51,52,340,341` | `51,52,340,341` | `rail 0..50, 51, nav 52..340, 341` |
| Rail / nav / 正文采样色 | `(34,34,34)` / `(37,37,37)` / `(44,44,44)` | `(243,243,243)` / `(249,249,249)` / `(255,255,255)` | 同 light | `(34,34,34)` / `(37,37,37)` / `(29,29,29)` | 同 light | 同 light | 深/浅壳层 |
| Navigator 内容像素 / bbox | 3705 `(68,62,317,270)` | 3873 `(68,62,317,270)` | 3873 | 2075 `(68,62,317,186)` | 1969 `(68,62,317,186)` | 1969 | — |
| 正文内容像素 / bbox | 6628 `(571,65,1439,899)` | 7055 `(571,65,1439,899)` | 7055 | 18821 `(575,56,1095,891)` | 17849 `(575,56,1439,899)` | 17849 | — |

结论：

1. 六张都是**真窗口**截图：交通灯彩色像素 bbox `(8,8,59,19)`、标题条分隔线 y=44、Rail/nav/正文边界
   `51,52,340,341` 与 C1.5B 的 Settings 原生截图逐像素一致；离屏渲染不可能带出这些。
2. `system` 与 `light` **字节完全相同**，符合「主机外观为 Light、`--theme system` 跟随系统」的预期。
3. 两页都渲染了真实内容（Navigator 约 2.0k–3.9k 像素、正文约 6.6k–18.8k 像素的「非背景色」像素），
   不是空窗口。
4. 复现性：本次重跑插件页得到的三张 PNG 与 AL（2026-10-11）记录的 sha256 **完全相同**
   （`97082a6d…` / `c205d15d…` / `c205d15d…`），说明这条原生截图链路在同一外观与同一窗口尺寸下是可复现的，
   即使 Compose 节点 id 与窗口 id 每次都变。

## §5 观察到的问题

- 插件页在**隔离空 home** 下启动，展示的是库存不可用/空状态；此批截图**不能**代表「已登记库存」状态，
  也不能当作插件页的最终视觉验收。
- 该不可用状态里的文案仍是**英文**：正文 `Local plugin inventory or source is invalid, unsupported, or
  unavailable.`，按钮 `Retry`。同一页其余文案是中文，说明插件页的错误态与共享 `HarnessRow` 的默认
  `retryLabel = "Retry"` 尚未本地化。这是本批新发现的具体缺口，未在本批修改。
- 定时任务页在空 home 下展示空态与示例模板列表（正文含「还没有定时任务」「示例模板 · 点击编辑，不会保存或运行」），
  同样不代表已建任务的状态。

## §6 边界（明确不主张）

- **未做原版同状态像素比对**：仓库里的原版参考图是 2560x1440 的整屏截图（含菜单栏与 Dock），与本批 1440x900
  的真窗口截图坐标系不同；本批只做「与本仓自己的 Settings 原生截图同坐标系」的壳层几何核对。
- **未复刻原版窗口的标题栏文字、窗口位置、焦点/悬停态**；`system` 档在本机等于 Light，本批没有第二个系统外观可测。
- 未覆盖 `--destination settings`（C1.5B 已有）与其它页面的原生窗口。
- 截图里的示例/空状态数据不构成功能证据；远端链路与真实模型仍不在本批。

## §7 复现

```bash
defaults read -g AppleInterfaceStyle            # 期望：键不存在 → Light
./scripts/capture-desktop-window.sh --size 1440x900 --destination plugins \
  --theme dark --build-output /tmp/bx-native --name plugins-native-dark-1440x900 --timeout 600
python3 docs/desktop-ui/verification/native-pages-scheduled-plugins-2026-10-12/entry-by1/measurement/measure-native-pages.py
```
