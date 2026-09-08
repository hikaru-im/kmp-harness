package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmConfigurableProvider
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.settings.SettingsNamespace
import im.hikaru.harness.settings.settingsNamespace

/** 把一个 Koog PromptExecutor 及其 provider routes 安装到 LlmRuntime。 */
class KoogLlmPlugin(
    executorFactory: KoogPromptExecutorFactory,
    routes: List<KoogProviderRoute>,
    optionMapper: KoogOptionMapper = BasicKoogOptionMapper(),
    usageMapper: KoogUsageMapper = DefaultKoogUsageMapper(),
    finishReasonMapper: KoogFinishReasonMapper =
        StopOnlyKoogFinishReasonMapper(),
    replayRestorer: KoogReplayRestorer = RejectingKoogReplayRestorer,
    failureClassifier: KoogProviderFailureClassifier =
        UnclassifiedKoogProviderFailures,
    messageMapper: KoogMessageMapper? = null,
    toolMapper: KoogToolMapper? = null,
    reasoningMapper: KoogReasoningMapper? = null,
    toolStreamMapper: KoogToolStreamMapper? = null,
    toolCallTerminalPolicy: KoogToolCallTerminalPolicy? = null,
    textTerminalPolicy: KoogTextTerminalPolicy? = null,
    replayWriter: KoogReplayWriter = NoopKoogReplayWriter,
    dynamicConfiguration: DynamicKoogConfiguration? = null,
) : SimplePlugin {
    private val runtime =
        KoogLlmPluginRuntime(
            executorFactory = executorFactory,
            routes = routes,
            optionMapper = optionMapper,
            usageMapper = usageMapper,
            finishReasonMapper = finishReasonMapper,
            replayRestorer = replayRestorer,
            failureClassifier = failureClassifier,
            messageMapper = messageMapper,
            toolMapper = toolMapper,
            reasoningMapper = reasoningMapper,
            toolStreamMapper = toolStreamMapper,
            toolCallTerminalPolicy = toolCallTerminalPolicy,
            textTerminalPolicy = textTerminalPolicy,
            replayWriter = replayWriter,
            dynamicConfiguration = dynamicConfiguration,
        )

    /**
     * 使用与 routes 一一对应的 Provider semantics bundle。
     *
     * 多 Provider Host 应优先使用这个构造器，避免各语义组件分别维护不同的 dispatcher。
     */
    constructor(
        executorFactory: KoogPromptExecutorFactory,
        routes: List<KoogProviderRoute>,
        providerSemantics: List<KoogProviderSemantics>,
    ) : this(
        executorFactory = executorFactory,
        routes = routes,
        semanticsRegistry =
            KoogProviderSemanticsRegistry(
                routes = routes,
                semantics = providerSemantics,
            ),
    )

    /**
     * 从可持久化的 Provider settings 和 Host credential resolver 创建 Plugin。
     *
     * settings 会在构造时快照；真实凭据只在 Plugin 激活时交给 Provider factory 解析。
     */
    constructor(
        routes: List<KoogProviderRoute>,
        providerSettings: List<KoogProviderSettings>,
        credentials: KoogCredentialResolver,
        providerExecutorFactory: KoogProviderExecutorFactory,
        optionMapper: KoogOptionMapper = BasicKoogOptionMapper(),
        usageMapper: KoogUsageMapper = DefaultKoogUsageMapper(),
        finishReasonMapper: KoogFinishReasonMapper = StopOnlyKoogFinishReasonMapper(),
        replayRestorer: KoogReplayRestorer = RejectingKoogReplayRestorer,
        failureClassifier: KoogProviderFailureClassifier =
            UnclassifiedKoogProviderFailures,
        messageMapper: KoogMessageMapper? = null,
        toolMapper: KoogToolMapper? = null,
        reasoningMapper: KoogReasoningMapper? = null,
        toolStreamMapper: KoogToolStreamMapper? = null,
        toolCallTerminalPolicy: KoogToolCallTerminalPolicy? = null,
        textTerminalPolicy: KoogTextTerminalPolicy? = null,
        replayWriter: KoogReplayWriter = NoopKoogReplayWriter,
    ) : this(
        executorFactory =
            providerPromptExecutorFactory(
                routes = routes,
                settings = providerSettings,
                credentials = credentials,
                factory = providerExecutorFactory,
            ),
        routes = routes,
        optionMapper = optionMapper,
        usageMapper = usageMapper,
        finishReasonMapper = finishReasonMapper,
        messageMapper = messageMapper,
        toolMapper = toolMapper,
        reasoningMapper = reasoningMapper,
        toolStreamMapper = toolStreamMapper,
        toolCallTerminalPolicy = toolCallTerminalPolicy,
        textTerminalPolicy = textTerminalPolicy,
        replayWriter = replayWriter,
        replayRestorer = replayRestorer,
        failureClassifier = failureClassifier,
    )

    /**
     * 使用 `SettingsService` 和 `CredentialProvider` 驱动已有 route 的动态配置。
     * settings namespace 默认是 `llm-koog`，凭据只保存引用名。
     */
    constructor(
        routes: List<KoogProviderRoute>,
        providerExecutorFactory: KoogProviderExecutorFactory,
        settingsNamespace: SettingsNamespace = settingsNamespace("llm-koog"),
        defaults: KoogLlmSettings = KoogLlmSettings(emptyMap()),
        configurableProviders: List<LlmConfigurableProvider> =
            routes.defaultDirectory(settingsNamespace),
        optionMapper: KoogOptionMapper = BasicKoogOptionMapper(),
        usageMapper: KoogUsageMapper = DefaultKoogUsageMapper(),
        finishReasonMapper: KoogFinishReasonMapper = StopOnlyKoogFinishReasonMapper(),
        replayRestorer: KoogReplayRestorer = RejectingKoogReplayRestorer,
        failureClassifier: KoogProviderFailureClassifier =
            UnclassifiedKoogProviderFailures,
        messageMapper: KoogMessageMapper? = null,
        toolMapper: KoogToolMapper? = null,
        reasoningMapper: KoogReasoningMapper? = null,
        toolStreamMapper: KoogToolStreamMapper? = null,
        toolCallTerminalPolicy: KoogToolCallTerminalPolicy? = null,
        textTerminalPolicy: KoogTextTerminalPolicy? = null,
        replayWriter: KoogReplayWriter = NoopKoogReplayWriter,
    ) : this(
        executorFactory =
            KoogPromptExecutorFactory {
                error("Dynamic Koog plugin does not use the static executor factory")
            },
        routes = routes,
        optionMapper = optionMapper,
        usageMapper = usageMapper,
        finishReasonMapper = finishReasonMapper,
        replayRestorer = replayRestorer,
        failureClassifier = failureClassifier,
        messageMapper = messageMapper,
        toolMapper = toolMapper,
        reasoningMapper = reasoningMapper,
        toolStreamMapper = toolStreamMapper,
        toolCallTerminalPolicy = toolCallTerminalPolicy,
        textTerminalPolicy = textTerminalPolicy,
        replayWriter = replayWriter,
        dynamicConfiguration =
            DynamicKoogConfiguration(
                namespace = settingsNamespace,
                defaults = defaults,
                factory = providerExecutorFactory,
                configurableProviders = configurableProviders,
            ),
    )

    /** Dynamic settings entry point with one shared semantics bundle. */
    constructor(
        routes: List<KoogProviderRoute>,
        providerExecutorFactory: KoogProviderExecutorFactory,
        providerSemantics: List<KoogProviderSemantics>,
        settingsNamespace: SettingsNamespace = settingsNamespace("llm-koog"),
        defaults: KoogLlmSettings = KoogLlmSettings(emptyMap()),
        configurableProviders: List<LlmConfigurableProvider> =
            routes.defaultDirectory(settingsNamespace),
    ) : this(
        routes = routes,
        dynamicConfiguration =
            DynamicKoogConfiguration(
                namespace = settingsNamespace,
                defaults = defaults,
                factory = providerExecutorFactory,
                configurableProviders = configurableProviders,
            ),
        semanticsRegistry =
            KoogProviderSemanticsRegistry(
                routes = routes,
                semantics = providerSemantics,
            ),
    )

    /** settings/credential 生命周期与严格 Provider semantics bundle 的组合入口。 */
    constructor(
        routes: List<KoogProviderRoute>,
        providerSettings: List<KoogProviderSettings>,
        credentials: KoogCredentialResolver,
        providerExecutorFactory: KoogProviderExecutorFactory,
        providerSemantics: List<KoogProviderSemantics>,
    ) : this(
        executorFactory =
            providerPromptExecutorFactory(
                routes = routes,
                settings = providerSettings,
                credentials = credentials,
                factory = providerExecutorFactory,
            ),
        routes = routes,
        semanticsRegistry =
            KoogProviderSemanticsRegistry(
                routes = routes,
                semantics = providerSemantics,
            ),
    )

    private constructor(
        executorFactory: KoogPromptExecutorFactory,
        routes: List<KoogProviderRoute>,
        semanticsRegistry: KoogProviderSemanticsRegistry,
    ) : this(
        executorFactory = executorFactory,
        routes = routes,
        optionMapper = semanticsRegistry,
        usageMapper = semanticsRegistry,
        finishReasonMapper = semanticsRegistry,
        messageMapper = semanticsRegistry.messageMapper,
        toolMapper = semanticsRegistry.toolMapper,
        reasoningMapper = semanticsRegistry.reasoningMapper,
        toolStreamMapper = semanticsRegistry.toolStreamMapper,
        toolCallTerminalPolicy = semanticsRegistry.toolCallTerminalPolicy,
        textTerminalPolicy = semanticsRegistry.textTerminalPolicy,
        replayWriter = semanticsRegistry,
        replayRestorer = semanticsRegistry,
        failureClassifier = semanticsRegistry,
    )

    private constructor(
        routes: List<KoogProviderRoute>,
        dynamicConfiguration: DynamicKoogConfiguration,
        semanticsRegistry: KoogProviderSemanticsRegistry,
    ) : this(
        executorFactory =
            KoogPromptExecutorFactory {
                error("Dynamic Koog plugin does not use the static executor factory")
            },
        routes = routes,
        optionMapper = semanticsRegistry,
        usageMapper = semanticsRegistry,
        finishReasonMapper = semanticsRegistry,
        messageMapper = semanticsRegistry.messageMapper,
        toolMapper = semanticsRegistry.toolMapper,
        reasoningMapper = semanticsRegistry.reasoningMapper,
        toolStreamMapper = semanticsRegistry.toolStreamMapper,
        toolCallTerminalPolicy = semanticsRegistry.toolCallTerminalPolicy,
        textTerminalPolicy = semanticsRegistry.textTerminalPolicy,
        replayWriter = semanticsRegistry,
        replayRestorer = semanticsRegistry,
        failureClassifier = semanticsRegistry,
        dynamicConfiguration = dynamicConfiguration,
    )

    override val inject: Set<InjectSpec>
        get() = runtime.inject

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        runtime.apply(context, scope)
    }
}

private fun List<KoogProviderRoute>.defaultDirectory(
    namespace: SettingsNamespace,
): List<LlmConfigurableProvider> =
    map { route ->
        LlmConfigurableProvider(
            provider = route.id,
            displayName = route.name,
            settingsNamespace = namespace.value,
            settingsPath = listOf("providers", route.id),
        )
    }
