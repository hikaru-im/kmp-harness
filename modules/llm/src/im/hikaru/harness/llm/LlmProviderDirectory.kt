package im.hikaru.harness.llm

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.event.EventKey

/** A provider route that an installed adapter plugin can activate through settings. */
data class LlmConfigurableProvider(
    val provider: String,
    val displayName: String,
    val settingsNamespace: String,
    val settingsPath: List<String>,
    val declared: Boolean? = null,
)

/** A live configurable-provider registration with atomic replacement. */
interface DirectoryRegistrationHandle : Disposable {
    suspend fun replace(entries: List<LlmConfigurableProvider>)
}

/** Emitted after an adapter-route or configurable-provider directory commit. */
object LlmAdaptersUpdatedEvent : EventKey<Unit>("llm/adapters-updated")

internal fun LlmConfigurableProvider.detachedCopy(): LlmConfigurableProvider =
    copy(settingsPath = settingsPath.toList())
