package im.hikaru.harness.settings

import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Raw settings document backend.
 *
 * Backends own persistence and external-change observation; SettingsService
 * owns namespace validation, merge semantics, revisions, and watchers.
 */
interface SettingsDocumentStore : Disposable {
    suspend fun read(): JsonObject

    suspend fun write(document: JsonObject)

    fun watch(
        listener: suspend (JsonObject) -> Unit,
    ): Disposable =
        Disposable {}
}

class InMemorySettingsDocumentStore(
    initial: JsonObject = buildJsonObject {},
) : SettingsDocumentStore {
    private var document: JsonObject = initial

    override suspend fun read(): JsonObject =
        document

    override suspend fun write(document: JsonObject) {
        this.document = document
    }

    suspend fun replaceExternally(document: JsonObject) {
        this.document = document
    }

    override suspend fun dispose() = Unit
}
