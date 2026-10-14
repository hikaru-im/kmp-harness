package im.hikaru.harness.desktop.settings

import im.hikaru.harness.bundle.desktop.DesktopProviderConfiguration
import im.hikaru.harness.bundle.desktop.DesktopProviderRevisionConflict
import im.hikaru.harness.bundle.desktop.DesktopProviderSettings
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
                        ProviderModel(model.id, model.name, model.description, model.contextWindow,
                            model.maxTokens, model.reasoningEfforts)
                    },
                    catalogError = provider.catalogError,
                )
            },
            protocols = snapshot.protocols.map { ModelProtocol(it.id, it.label) },
            revision = snapshot.revision,
            available = true,
            writable = true,
            canAddProvider = true,
            unavailableReason = null,
            scopeLabel = "本机 Host",
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
    )

    private fun ProviderConfiguration.toBundle(): DesktopProviderConfiguration = DesktopProviderConfiguration(
        id, displayName, api, baseUrl, credentialName, requestTimeoutMillis, connectTimeoutMillis,
        socketTimeoutMillis, modelIds, defaultContextWindow, defaultMaxTokens,
    )
}
