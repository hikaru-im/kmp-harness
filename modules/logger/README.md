# Logger Plugin

Logger 模块是 `kotlin-logging` 与 Harness Context 之间的薄桥接，只提供 `LoggerService`、
`LoggerKey` 和 `LoggerPlugin`。

它不定义日志实现、格式、Console exporter、OSLog 或 JVM/Android backend。最终平台选择输出：
Desktop 与 RuoYi Server 使用各自的 Logback，Android 使用 Logcat，Darwin 使用 OSLog。

Logger Service 的绑定跟随 Fiber 生命周期，平台 logger backend 不归 Runtime 所有。详细设计见
[docs/infrastructure-plugins.md](../../docs/infrastructure-plugins.md)。
