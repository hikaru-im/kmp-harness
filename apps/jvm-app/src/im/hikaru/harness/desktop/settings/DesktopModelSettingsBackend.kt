package im.hikaru.harness.desktop.settings

import im.hikaru.harness.bundle.desktop.DesktopProviderConfiguration
import im.hikaru.harness.bundle.desktop.DesktopProviderRevisionConflict
import im.hikaru.harness.bundle.desktop.DesktopProviderSettings
import im.hikaru.harness.bundle.desktop.DesktopProviderModel
import im.hikaru.harness.llm.CapabilitySupport
import im.hikaru.harness.llm.ReasoningConstraint
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.runtime.Context

/** Maps the Desktop bundle service to the shared settings UI contract. */
public class DesktopModelSettingsBackend(context: Context) : ModelSettingsBackend {
    private val service = DesktopProviderSettings(context)

    override suspend fun read(): ModelSettingsSnapshot {
        val snapshot = service.read()
        return ModelSettingsSnapshot(
            providers = snapshot.providers.map { provider ->
                ConfigurableProvider(
                    id = provider.id,
                    displayName = provider.displayName,
                    configuration = provider.configuration?.toClient(),
                    active = provider.active,
                    credentialConfigured = provider.credentialConfigured,
                    credentialWritable = provider.credentialWritable,
                    credentialSource = provider.credentialSource,
                    canRemove = provider.canRemove,
                    models = provider.models.map { model ->
                        model.toClient()
                    },
                    catalogError = provider.catalogError,
                    applicationError = provider.applicationError,
                    applying = provider.applying,
                    catalogSource = provider.catalogSource,
                    catalogWarnings = provider.catalogWarnings,
                    disabledModelIds = provider.disabledModelIds,
                    selectedModelId = provider.selectedModelId,
                    catalogUpdatedAt = provider.catalogUpdatedAt,
                )
            },
            protocols = snapshot.protocols.map { ModelProtocol(it.id, it.label) },
            revision = snapshot.revision,
            available = true,
            writable = true,
            canAddProvider = true,
            unavailableReason = null,
            scopeLabel = "本机 Host",
            plugins = snapshot.plugins.map { plugin ->
                val descriptor = plugin.descriptor
                ProviderPlugin(descriptor.id, descriptor.displayName, descriptor.protocols,
                    endpoints = descriptor.endpointProfiles.map { ProviderEndpointProfile(it.id, it.label, it.baseUrl, it.api, it.region, it.serviceVariant, it.requiresWorkspace) },
                    connectionFields = descriptor.connectionFields.filterNot { it.secret }.map { ProviderConnectionField(it.id, it.label, it.required) },
                    supportsDiscovery = descriptor.supportsDiscovery,
                    inferenceSupported = plugin.inferenceSupported,
                    discoveryStatus = descriptor.discoveryStatus)
            },
        )
    }

    override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long) {
        configuration.validate()
        mapConflict { service.save(configuration.toBundle(), expectedRevision) }
    }

    override suspend fun remove(providerId: String, expectedRevision: Long) {
        mapConflict { service.remove(providerId, expectedRevision) }
    }

    override suspend fun setCredential(name: String, value: String) {
        service.setCredential(name, value)
    }

    override suspend fun setModelEnabled(providerId: String, modelId: String, enabled: Boolean, expectedRevision: Long) =
        mapConflict { service.setModelEnabled(providerId, modelId, enabled, expectedRevision) }

    override suspend fun selectModel(providerId: String, modelId: String, expectedRevision: Long) =
        mapConflict { service.selectModel(providerId, modelId, expectedRevision) }

    override suspend fun retryApplication() = service.retryApplication()
    override suspend fun discoverModels(providerId: String, expectedRevision: Long) {
        mapConflict { service.discoverModels(providerId, expectedRevision) }
    }

    override suspend fun discoverDraft(configuration: ProviderConfiguration, apiKey: String): ModelDiscoveryResult {
        val result = service.discoverDraft(configuration.toBundle(), apiKey)
        return ModelDiscoveryResult(result.models.map { it.toClient() }, result.source, result.complete, result.warnings)
    }

    override suspend fun saveWithCredential(configuration: ProviderConfiguration, apiKey: String, expectedRevision: Long) {
        mapConflict { service.saveWithCredential(configuration.toBundle(), apiKey, expectedRevision) }
    }

    private fun DesktopProviderModel.toClient(): ProviderModel = ProviderModel(
        id, name, description, contextWindow, maxTokens, reasoningEfforts,
        reasoningCapabilities?.let { capabilities -> ModelReasoningCapabilities(
            modes = if (capabilities.modeSupport == CapabilitySupport.SUPPORTED) capabilities.modes.map { it.value } else emptyList(),
            efforts = if (capabilities.effortSupport == CapabilitySupport.SUPPORTED) capabilities.efforts.map { it.id.value } else emptyList(),
            budgetSupported = capabilities.budgetSupport == CapabilitySupport.SUPPORTED,
            minBudget = capabilities.budgetRange?.minTokens, maxBudget = capabilities.budgetRange?.maxTokens,
            executionModes = if (capabilities.executionModeSupport == CapabilitySupport.SUPPORTED) capabilities.executionModes else emptyList(),
            ruleVersion = capabilities.ruleVersion,
            modeSupport = capabilities.modeSupport.toClient(),
            effortSupport = capabilities.effortSupport.toClient(),
            budgetSupport = capabilities.budgetSupport.toClient(),
            executionModeSupport = capabilities.executionModeSupport.toClient(),
            constraints = capabilities.constraints.map { constraint -> when (constraint) {
                ReasoningConstraint.EffortBudgetExclusive -> ModelReasoningConstraint.EffortBudgetExclusive
                ReasoningConstraint.BudgetLessThanMaxTokens -> ModelReasoningConstraint.BudgetLessThanMaxTokens
                is ReasoningConstraint.ForMode -> ModelReasoningConstraint.ForMode(
                    constraint.mode.value, constraint.effortSupport?.toClient(), constraint.allowedEfforts?.map { it.value },
                    constraint.budgetSupport?.toClient(), constraint.requiresBudget,
                )
            } },
            defaultMaxTokens = defaultMaxTokens,
        ) },
        selectable = selectable, absentFromCatalog = absentFromCatalog,
    )

    private fun CapabilitySupport.toClient(): ModelCapabilitySupport = when (this) {
        CapabilitySupport.SUPPORTED -> ModelCapabilitySupport.SUPPORTED
        CapabilitySupport.UNSUPPORTED -> ModelCapabilitySupport.UNSUPPORTED
        CapabilitySupport.UNKNOWN -> ModelCapabilitySupport.UNKNOWN
    }

    private suspend fun mapConflict(action: suspend () -> Unit) {
        try {
            action()
        } catch (_: DesktopProviderRevisionConflict) {
            throw ProviderRevisionConflict()
        }
    }

    private fun DesktopProviderConfiguration.toClient(): ProviderConfiguration = ProviderConfiguration(
        id, displayName, api, baseUrl, credentialName, requestTimeoutMillis, connectTimeoutMillis,
        socketTimeoutMillis, modelIds, defaultContextWindow, defaultMaxTokens,
        vendor, serviceVariant, region, workspace, apiVersion,
    )

    private fun ProviderConfiguration.toBundle(): DesktopProviderConfiguration = DesktopProviderConfiguration(
        id, displayName, api, baseUrl, credentialName, requestTimeoutMillis, connectTimeoutMillis,
        socketTimeoutMillis, modelIds, defaultContextWindow, defaultMaxTokens,
        vendor, serviceVariant, region, workspace, apiVersion,
    )
}
