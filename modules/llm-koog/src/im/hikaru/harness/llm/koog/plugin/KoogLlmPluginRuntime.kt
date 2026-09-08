package im.hikaru.harness.llm.koog

import ai.koog.prompt.executor.model.PromptExecutor
import im.hikaru.harness.credentials.CredentialRef
import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.llm.LlmKey
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.settings.SettingsApplies
import im.hikaru.harness.settings.SettingsKey
import im.hikaru.harness.settings.SettingsValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/** Owns activation, registration, and disposal for one configured Koog plugin. */
internal class KoogLlmPluginRuntime(
    private val executorFactory: KoogPromptExecutorFactory,
    routes: List<KoogProviderRoute>,
    private val optionMapper: KoogOptionMapper,
    private val usageMapper: KoogUsageMapper,
    private val finishReasonMapper: KoogFinishReasonMapper,
    private val replayRestorer: KoogReplayRestorer,
    private val failureClassifier: KoogProviderFailureClassifier,
    private val messageMapper: KoogMessageMapper?,
    private val toolMapper: KoogToolMapper?,
    private val reasoningMapper: KoogReasoningMapper?,
    private val toolStreamMapper: KoogToolStreamMapper?,
    private val toolCallTerminalPolicy: KoogToolCallTerminalPolicy?,
    private val textTerminalPolicy: KoogTextTerminalPolicy?,
    private val replayWriter: KoogReplayWriter,
    private val dynamicConfiguration: DynamicKoogConfiguration?,
) {
    private val routes = routes.map(KoogProviderRoute::detachedCopy)

    init {
        require(this.routes.isNotEmpty()) {
            "Koog plugin must declare at least one provider"
        }
        require(this.routes.map(KoogProviderRoute::id).distinct().size == this.routes.size) {
            "Koog plugin provider ids must not contain duplicates"
        }
    }

    val inject: Set<InjectSpec> =
        buildSet {
            add(InjectSpec.required(LlmKey))
            if (dynamicConfiguration != null) {
                add(InjectSpec.required(SettingsKey))
                add(InjectSpec.required(CredentialsKey))
            }
        }

    suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        dynamicConfiguration?.let { dynamic ->
            applyDynamic(context, scope, dynamic)
            return
        }
        applyStatic(context, scope)
    }

    private suspend fun applyDynamic(
        context: Context,
        scope: EffectScope,
        dynamic: DynamicKoogConfiguration,
    ) {
        val settingsService = context.require(SettingsKey)
        val credentials = context.require(CredentialsKey)
        val settingsScope =
            settingsService.register(
                namespace = dynamic.namespace,
                defaults = dynamic.defaults.toJson(),
                applies = SettingsApplies.LIVE,
                validator =
                    SettingsValidator { value ->
                        KoogLlmSettings.fromJson(value).resolveRoutes(routes)
                    },
            )
        val directoryRegistration =
            context.llm.registerConfigurableProviders(dynamic.configurableProviders)
        val coordinator =
            KoogLlmReloadCoordinator(
                installedRoutes = routes,
                settings = settingsScope,
                credentials = credentials,
                generationFactory =
                    KoogGenerationFactory { settings, resolvedRoutes ->
                        dynamic.factory.create(
                            routes = resolvedRoutes,
                            settings = resolvedRoutes.map { route ->
                                settings.providers.getValue(route.id)
                            },
                            credentials = { reference ->
                                credentials.resolve(CredentialRef(reference.name))?.value
                            },
                        )
                    },
                adapterFactory = ::createAdapter,
                llm = context.llm,
                directoryRegistration = directoryRegistration,
                baseDirectory = dynamic.configurableProviders,
                parentScope =
                    CoroutineScope(
                        currentCoroutineContext().minusKey(Job)
                    ),
            )
        try {
            coordinator.start()
            scope.add(coordinator)
        } catch (error: Throwable) {
            coordinator.dispose()
            throw error
        }
    }

    private suspend fun applyStatic(
        context: Context,
        scope: EffectScope,
    ) {
        val executor = executorFactory.create()
        scope.add(Disposable { executor.close() })
        scope.add(
            context.llm.registerAdapter(
                providers = routes.map(KoogProviderRoute::id),
                adapter = createAdapter(executor),
            )
        )
    }

    private fun createAdapter(
        executor: PromptExecutor,
        resolvedRoutes: List<KoogProviderRoute> = routes,
    ): KoogLlmAdapter =
        KoogLlmAdapter(
            executor = executor,
            routes = resolvedRoutes,
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
}
