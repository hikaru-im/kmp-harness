R4 已完成，仅修改指定两文件：

- 拖拽 anchor 改为真实 down.position，end 使用 slopTrigger.position。
- 三次复制均定位实际可见 row-00，保留单次 move，并校验完整 `row-00 中😀`。

静态核验通过：写集外 2251 输入 SHA 不变，固定 36／页面 6 方法保持，`git diff --check` 通过。

主交付 JSON/MD 已更新；[R4 交付记录](/tmp/harness-terminal-renderer-page-repair-r4-delivery-20261010.json) 已生成。

未运行编译、build、测试、截图或夹具。源码写入已停止，写集已释放；动态验收 pending，由父线程独立执行。退出，不推进下一轮。