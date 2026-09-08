package im.hikaru.harness.settings

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

fun interface SettingsValidator {
    fun validate(value: JsonObject)
}

interface SettingsScope {
    val namespace: SettingsNamespace

    fun get(): JsonObject

    fun describe(
        redactSecrets: Boolean = true,
    ): SettingsDescriptor

    suspend fun update(
        patch: JsonObject,
        expectedRevision: Long? = null,
    )

    suspend fun replace(
        section: JsonObject,
        expectedRevision: Long? = null,
    )

    fun watch(
        listener: suspend (SettingsChange) -> Unit,
    ): Disposable
}

internal class Registration(
    override val namespace: SettingsNamespace,
    val defaults: JsonObject,
    val base: JsonObject,
    val applies: SettingsApplies,
    val secretPaths: Set<List<String>>,
    val validator: SettingsValidator,
    var user: JsonObject?,
    var resolved: JsonObject,
    var revision: Long,
) : SettingsScope {
    lateinit var service: SettingsService

    val watchers =
        mutableListOf<suspend (SettingsChange) -> Unit>()

    override fun get(): JsonObject =
        resolved

    override fun describe(redactSecrets: Boolean): SettingsDescriptor {
        val value =
            if (redactSecrets) {
                redactSettingsObject(resolved, secretPaths)
            } else {
                resolved
            }
        return SettingsDescriptor(
            namespace = namespace,
            value = value,
            user = user?.let { if (redactSecrets) redactSettingsObject(it, secretPaths) else it },
            base = if (redactSecrets) redactSettingsObject(base, secretPaths) else base,
            revision = revision,
            applies = applies,
            secrets = secretPaths,
        )
    }

    override suspend fun update(
        patch: JsonObject,
        expectedRevision: Long?,
    ) {
        service.update(
            registration = this,
            nextUser = mergeSettingsObjects(user ?: buildJsonObject {}, patch),
            expectedRevision = expectedRevision,
        )
    }

    override suspend fun replace(
        section: JsonObject,
        expectedRevision: Long?,
    ) {
        service.update(
            registration = this,
            nextUser = section,
            expectedRevision = expectedRevision,
        )
    }

    override fun watch(
        listener: suspend (SettingsChange) -> Unit,
    ): Disposable {
        watchers += listener
        return Disposable {
            watchers.remove(listener)
        }
    }
}

/**
 * Namespace settings service. The service is intentionally JSON-shaped at
 * the persistence boundary so file providers can preserve a document that
 * contains namespaces owned by other plugins.
 */
class SettingsService(
    private val store: SettingsDocumentStore,
) : Disposable {
    private val mutex =
        Mutex()

    private val notificationMutex =
        Mutex()

    private val registrations =
        linkedMapOf<SettingsNamespace, Registration>()

    private var document: JsonObject =
        buildJsonObject {}

    private var externalWatch: Disposable? =
        null

    private var started =
        false

    suspend fun start() {
        check(!started) {
            "SettingsService is already started"
        }
        document = store.read()
        started = true
        externalWatch =
            store.watch { next ->
                applyExternalDocument(next)
            }
    }

    suspend fun register(
        namespace: SettingsNamespace,
        defaults: JsonObject = buildJsonObject {},
        base: JsonObject = buildJsonObject {},
        applies: SettingsApplies = SettingsApplies.LIVE,
        secretPaths: Set<List<String>> = emptySet(),
        validator: SettingsValidator = SettingsValidator {},
    ): SettingsScope =
        mutex.withLock {
            check(started) {
                "SettingsService must be started before registering a namespace"
            }
            check(namespace !in registrations) {
                "Settings namespace '$namespace' is already registered"
            }
            val user =
                document[namespace.value]?.jsonObject
            val resolved =
                resolve(
                    defaults = defaults,
                    base = base,
                    user = user,
                    validator = validator,
                )
            val registration =
                Registration(
                    namespace = namespace,
                    defaults = defaults,
                    base = base,
                    applies = applies,
                    secretPaths = secretPaths,
                    validator = validator,
                    user = user,
                    resolved = resolved,
                    revision = 0,
                )
            registration.service = this@SettingsService
            registrations[namespace] = registration
            registration
        }

    /** Return a registered namespace for host/admin surfaces that need to update it. */
    fun scope(namespace: SettingsNamespace): SettingsScope =
        registrations[namespace]
            ?: error("Settings namespace '$namespace' is not registered")

    private fun resolve(
        defaults: JsonObject,
        base: JsonObject,
        user: JsonObject?,
        validator: SettingsValidator,
    ): JsonObject {
        val resolved =
            mergeSettingsObjects(
                mergeSettingsObjects(defaults, base),
                user ?: buildJsonObject {},
            )
        validator.validate(resolved)
        return resolved
    }

    internal suspend fun update(
        registration: Registration,
        nextUser: JsonObject,
        expectedRevision: Long?,
    ) {
        val change =
            mutex.withLock {
                checkRevision(registration, expectedRevision)
                val previousUser = registration.user
                if (previousUser == nextUser) {
                    return@withLock null
                }
                val nextResolved =
                    resolve(
                        defaults = registration.defaults,
                        base = registration.base,
                        user = nextUser,
                        validator = registration.validator,
                    )
                val nextDocument =
                    buildJsonObject {
                        document.forEach { (key, value) ->
                            if (key != registration.namespace.value) put(key, value)
                        }
                        put(registration.namespace.value, nextUser)
                    }
                store.write(nextDocument)
                document = nextDocument
                val previous = registration.resolved
                registration.user = nextUser
                registration.resolved = nextResolved
                registration.revision += 1
                SettingsChange(
                    namespace = registration.namespace,
                    next = nextResolved,
                    previous = previous,
                    revision = registration.revision,
                )
            }
        change?.let { notify(it, registration) }
    }

    private fun checkRevision(
        registration: Registration,
        expectedRevision: Long?,
    ) {
        if (expectedRevision != null && expectedRevision != registration.revision) {
            throw SettingsConflictException(
                namespace = registration.namespace,
                expectedRevision = expectedRevision,
                actualRevision = registration.revision,
            )
        }
    }

    private suspend fun applyExternalDocument(
        nextDocument: JsonObject,
    ) {
        val changes =
            mutex.withLock {
                registrations.values.map { registration ->
                    val user =
                        nextDocument[registration.namespace.value]?.jsonObject
                    val nextResolved =
                        resolve(
                            defaults = registration.defaults,
                            base = registration.base,
                            user = user,
                            validator = registration.validator,
                        )
                    val previous = registration.resolved
                    registration.user = user
                    registration.resolved = nextResolved
                    registration.revision += 1
                    SettingsChange(
                        namespace = registration.namespace,
                        next = nextResolved,
                        previous = previous,
                        revision = registration.revision,
                    )
                }.also {
                    document = nextDocument
                }
            }
        for (change in changes) {
            registrations[change.namespace]?.let { registration ->
                notify(change, registration)
            }
        }
    }

    private suspend fun notify(
        change: SettingsChange,
        registration: Registration,
    ) {
        notificationMutex.withLock {
            for (watcher in registration.watchers.toList()) {
                try {
                    watcher(change)
                } catch (_: Throwable) {
                    // A broken observer must not make a committed settings
                    // write look failed or stop later observers.
                }
            }
        }
    }

    override suspend fun dispose() {
        externalWatch?.dispose()
        externalWatch = null
        store.dispose()
        registrations.clear()
        started = false
    }
}

class SettingsPlugin(
    private val store: SettingsDocumentStore = InMemorySettingsDocumentStore(),
) : SimplePlugin {
    override suspend fun apply(
        context: im.hikaru.harness.runtime.Context,
        scope: EffectScope,
    ) {
        val service =
            SettingsService(store).also { it.start() }
        scope.add(context.provide(SettingsKey, service))
        scope.add(service)
    }
}
