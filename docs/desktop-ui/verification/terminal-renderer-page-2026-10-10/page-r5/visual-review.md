# R5 离屏页面视觉检查

父线程在worker退出后逐张打开10张原尺寸PNG。检查的是状态、主题和可用布局。

- 1440×900与600×800的Light/Dark Running/Exited共8张：标题随真实snapshot正确显示Running或Exited (7)，运行/退出内容和控件可见。
- Unknown浅色宽屏：显示Running生命周期和Unknown输入回执，输入/resize暂停，显式关闭可用。
- Unavailable浅色窄屏：无伪造终端screen，新建/关闭/输入停用，刷新可用。
- 窄屏使用覆盖式面板，底部导航仍可见；按钮换行，文字可读。
- 主题抽样程序初次选到了滚动条，改在两个空白面板内点核验；保存修正脚本，不修改生产代码或测试。

本轮离屏页面状态检查通过。原生窗口/系统IME、Remote、其他平台launch与原版同状态像素对齐仍未验收，产品文案和样式的原版对齐属于后续视觉工作。逐张文件SHA见visual-review.json。
