# Design Documents

`docs` 保存 Harness 内部机制的详细设计；仓库级部署边界、本地与远程链路以根
[README](../README.md) 为准。

- [REFERENCES_GUIDE.md](REFERENCES_GUIDE.md)：本地参考仓库的初始化与更新。

- [runtime-lifecycle.md](runtime-lifecycle.md)：Context、Fiber、effect、dispose 与失败回滚。
- [advanced-context.md](advanced-context.md)：isolate、intercept 和事件派发语义。
- [management-layer.md](management-layer.md)：Registry、Entry、Loader 与配置协调。
- [modules/profile/README.md](../modules/profile/README.md)：DSH patch 算法与分层顺序。
- [modules/profile-file/README.md](../modules/profile-file/README.md)：Profile 目录、初始化和 live reload。
- [modules/bundle/desktop/README.md](../modules/bundle/desktop/README.md)：Desktop 编译期 Catalog 与 bundle patch。
- [infrastructure-plugins.md](infrastructure-plugins.md)：logger、timer 和 include。
- [modules/llm/README.md](../modules/llm/README.md)：provider-neutral LLM 词汇、Adapter 注册与流式调用语义。
- [modules/llm-koog/README.md](../modules/llm-koog/README.md)：Koog Adapter 边界、生命周期与分步实现路线。
- [session-agent-loop-plan.md](session-agent-loop-plan.md)：Session、Agent、AgentLoop 的模块边界、阶段任务与验收标准。
- [session-s1-plan.md](session-s1-plan.md)：Session S1 对齐 DSH 的数据格式、原子提交、Store 事务和实施拆分。
- [harness-host-api.md](harness-host-api.md)：Host、RuoYi Relay、配置所有权、DSH API 对照与实施顺序。
- [cordis-alignment.md](cordis-alignment.md)：与 Cordis 的能力、命名和明确差异。
- [ui-slot-integration-plan.md](ui-slot-integration-plan.md)：KMP Client Runtime、静态 Extension Point 与 Compose UI Slot 的结合方案。

这些文档记录 Harness 内部设计；部署边界、RuoYi Relay 与最终平台装配分别以 `backend`、
`apps` 和各模块 README 为准。设计与代码不一致时，应同时修正相关 README，不能只更新总览。
