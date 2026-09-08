# Include Plugin

Include 把外部配置快照转换为 Loader Entry，但不重复实现 Loader：

```text
ConfigResource -> EntryCodec -> ConfigSource -> IncludeService -> child Loader
```

本模块提供跨平台 JSON codec 和内存资源抽象，不隐含文件系统、Android Asset、Apple Bundle、
远程配置、文件监听或 HMR。平台 I/O 由最终应用适配。

每个 Include Fiber 拥有隔离的 child Context 和 Loader，refresh/save 失败会保持或恢复调用前快照。
详细设计见 [docs/infrastructure-plugins.md](../../docs/infrastructure-plugins.md)。
