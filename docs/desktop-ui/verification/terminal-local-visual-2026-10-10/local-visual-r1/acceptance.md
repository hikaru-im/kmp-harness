# 本地终端紧凑视觉 R1：已验收

实施：SHUAIAPI/gpt-6.1-sol / opencodex。工作者退出释放写集后，父线程冻结并独立串行验收。

| 项目 | 结果 |
| --- | --- |
| 编译输入 | 2,253 项，仅合同内 3 文件变化，2,250 项不变 |
| 定向回归 | 6 类 35/35，失败、错误、跳过均 0 |
| 完整 JVM 应用构建 | exit 0 |
| 终端页面 | 12 张完整页面 PNG 及 geometry/matrix，全部原尺寸查看 |
| 壳层回归 | 80 张 PNG / geometry，8 张 contact sheets 全部查看 |
| 正文可见行数 | 宽窗口 38→42；窄窗口 26→31；列数保持 |

操作区统一为一行选择器、新建图标和 typed 操作菜单；复用共享 IconButton/Menu/Tooltip，成功回执不占常驻正文，失败与 Unknown/Unavailable 继续展示。滚动条采用主题 Surface/Border/BorderStrong。原六个页面方法及业务、真实字节、生命周期、身份、history 和 kernel resize 断言保留，新增真实指针/键盘及菜单作用域验证。

真实 PTY 功能 R5 的 184/184 保留为独立历史证据，各批测试有重叠，不累加。字体仍为本批合同约定的 13sp/18sp；Appearance 字体消费者另行接入。

原版同状态像素、原生窗口/系统 IME、Remote 与其他平台 launch 尚未验收；整体复刻目标继续 active。
