# Apps

`apps` 是最终客户端和平台装配层。Connection、客户端状态、Repository、ViewModel 与 Compose UI
都属于这里，不会提升为 Harness `modules`。

```text
apps/
├── shared/       Android、iOS、Desktop 共用客户端代码
├── jvm-app/      Desktop Client 与本地 Harness Host 装配
├── android-app/  Mobile 远程客户端
└── ios-app/      Mobile 远程客户端
```

三端共享 wire contracts 和客户端业务逻辑，但不共享 Host 执行内核。只有 Desktop 装配 Runtime、
Loader、API Gateway 和 Plugin；Android/iOS 不运行本地 Agent。

连接实现由最终应用注入：Desktop 使用本地 `LocalConnection`，也可以像 Mobile 一样使用
`RemoteConnection`。客户端面对同一 Connection 语义，不直接判断 Agent 位于本地还是远程。
