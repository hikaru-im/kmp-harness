# A1 gate-r1 编译失败记录

SessionAdmission.kt:165 的 this 绑定到了 withContext 的 CoroutineScope，比较类型不兼容。实际测试执行0项；其余三类及完整JVM构建未运行。本目录保留失败源码、原始日志及完整输入清单，不作为通过证据。
