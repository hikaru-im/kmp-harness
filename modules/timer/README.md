# Timer Plugin

Timer 模块提供基于协程的 timeout、interval、debounce 和 throttle。每个计时器同时绑定
TimerService 与调用方 Context 生命周期，任一作用域 dispose 都会取消对应 Job。

Timer 不模拟 JavaScript 全局 `setTimeout`/`setInterval`，也不创建平台线程池。回调顺序、interval
失败策略和取消语义由 `TimerService` 明确定义。

详细设计见 [docs/infrastructure-plugins.md](../../docs/infrastructure-plugins.md)。
