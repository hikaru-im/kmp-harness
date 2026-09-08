package im.hikaru.harness.loader

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.plugin.Fiber
import im.hikaru.harness.runtime.plugin.FiberState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把 Entry 配置快照协调成 Runtime 中实际运行的 Fiber。
 *
 * Loader 只处理注册项与配置变化，不负责文件监听、动态代码加载或 HMR。
 */
class Loader(
    private val runtime: Runtime,
    private val registry: Registry,
    private val context: Context = runtime.context,
) {

    private val mutex =
        Mutex()

    private val managedEntries =
        linkedMapOf<String, ManagedEntry>()

    private var disposed =
        false

    init {
        check(context.root === runtime.context) {
            "Loader context does not belong to this Runtime"
        }

        check(!context.isDisposed) {
            "Loader context is disposed"
        }
    }

    val entries: List<Entry>
        get() = managedEntries.values.map { managed -> managed.entry }

    fun entry(id: String): Entry? =
        managedEntries[id]?.entry

    fun fiber(id: String): Fiber<*>? =
        managedEntries[id]?.fiber

    /**
     * 将当前状态协调到完整的 desired 快照。
     *
     * 相同 Entry 与相同注册定义会保留原 Fiber；配置、名称、禁用状态或
     * Registry 定义变化时会重建。失败时尽力恢复调用前的完整快照。
     */
    suspend fun reconcile(
        desired: List<Entry>,
    ) {
        mutex.withLock {
            check(!disposed) {
                "Loader is disposed"
            }

            val desiredById =
                validate(desired)

            val prepared =
                prepare(desiredById)

            reconcileLocked(
                desired = desiredById,
                prepared = prepared,
            )
        }
    }

    suspend fun dispose() {
        mutex.withLock {
            if (disposed) {
                return
            }

            disposed =
                true

            val fibers =
                managedEntries.values
                    .mapNotNull { managed -> managed.fiber }
                    .asReversed()

            managedEntries.clear()

            var failure: Throwable? =
                null

            for (fiber in fibers) {
                // A parent Context disposal unregisters every descendant Fiber
                // from Runtime before stopping their scopes. A child Loader may
                // therefore observe a Fiber it used to own after Runtime has
                // already claimed its cleanup. Runtime remains the authority in
                // that case and the Loader only drops its snapshot reference.
                if (fiber !in runtime.fibers) {
                    continue
                }

                try {
                    runtime.uninstall(fiber)
                } catch (error: Throwable) {
                    failure =
                        combineFailures(
                            failure,
                            error,
                        )
                }
            }

            failure?.let { error ->
                throw error
            }
        }
    }

    private fun validate(
        desired: List<Entry>,
    ): LinkedHashMap<String, Entry> {
        val result =
            linkedMapOf<String, Entry>()

        for (entry in desired) {
            check(entry.id.isNotBlank()) {
                "Loader Entry id must not be blank"
            }

            check(entry.name.isNotBlank()) {
                "Loader Entry '${entry.id}' name must not be blank"
            }

            check(entry.id !in result) {
                "Duplicate Loader Entry id '${entry.id}'"
            }

            result[entry.id] =
                entry
        }

        return result
    }

    private fun prepare(
        desired: LinkedHashMap<String, Entry>,
    ): Map<String, PreparedPlugin> {
        val result =
            mutableMapOf<String, PreparedPlugin>()

        for ((id, entry) in desired) {
            if (entry.disabled) {
                continue
            }

            result[id] =
                registry.prepare(
                    name = entry.name,
                    rawConfig = entry.config,
                )
        }

        return result
    }

    private suspend fun reconcileLocked(
        desired: LinkedHashMap<String, Entry>,
        prepared: Map<String, PreparedPlugin>,
    ) {
        val previous =
            LinkedHashMap(managedEntries)

        val changedIds =
            linkedSetOf<String>()

        for ((id, entry) in desired) {
            val old =
                previous[id]

            val nextPrepared =
                prepared[id]

            if (
                old == null ||
                !old.canReuse(entry, nextPrepared)
            ) {
                changedIds +=
                    id
            }
        }

        for (id in previous.keys) {
            if (id !in desired) {
                changedIds +=
                    id
            }
        }

        if (changedIds.isEmpty()) {
            managedEntries.clear()

            for (id in desired.keys) {
                managedEntries[id] =
                    previous.getValue(id)
            }

            return
        }

        val removedOldIds =
            linkedSetOf<String>()

        val createdFibers =
            mutableListOf<Fiber<*>>()

        val replacements =
            mutableMapOf<String, ManagedEntry>()

        try {
            val oldToRemove =
                previous.entries
                    .filter { (id) -> id in changedIds }
                    .asReversed()

            for ((id, managed) in oldToRemove) {
                val oldFiber =
                    managed.fiber
                        ?: continue

                try {
                    runtime.uninstall(oldFiber)
                } finally {
                    if (oldFiber !in runtime.fibers) {
                        removedOldIds +=
                            id
                    }
                }
            }

            for ((id, entry) in desired) {
                if (id !in changedIds) {
                    continue
                }

                val nextPrepared =
                    prepared[id]

                if (entry.disabled) {
                    replacements[id] =
                        ManagedEntry(
                            entry = entry,
                            prepared = null,
                            fiber = null,
                        )

                    continue
                }

                checkNotNull(nextPrepared)

                val result =
                    nextPrepared.install(
                        runtime = runtime,
                        parent = context,
                    )

                createdFibers +=
                    result.fiber

                result.failure?.let { error ->
                    throw error
                }

                replacements[id] =
                    ManagedEntry(
                        entry = entry,
                        prepared = nextPrepared,
                        fiber = result.fiber,
                    )
            }

            managedEntries.clear()

            for ((id, entry) in desired) {
                managedEntries[id] =
                    replacements[id]
                        ?: previous.getValue(id)
            }
        } catch (error: Throwable) {
            rollback(
                previous = previous,
                removedOldIds = removedOldIds,
                createdFibers = createdFibers,
                primaryFailure = error,
            )

            throw error
        }
    }

    private suspend fun rollback(
        previous: LinkedHashMap<String, ManagedEntry>,
        removedOldIds: Set<String>,
        createdFibers: List<Fiber<*>>,
        primaryFailure: Throwable,
    ) {
        for (fiber in createdFibers.asReversed()) {
            if (fiber !in runtime.fibers) {
                continue
            }

            try {
                runtime.uninstall(fiber)
            } catch (rollbackError: Throwable) {
                addSuppressed(
                    primary = primaryFailure,
                    secondary = rollbackError,
                )
            }
        }

        val restored =
            LinkedHashMap(previous)

        for ((id, old) in previous) {
            if (id !in removedOldIds) {
                continue
            }

            val oldPrepared =
                old.prepared

            if (oldPrepared == null) {
                restored[id] =
                    old.copy(fiber = null)

                continue
            }

            try {
                val result =
                    oldPrepared.install(
                        runtime = runtime,
                        parent = context,
                    )

                restored[id] =
                    old.copy(
                        fiber = result.fiber,
                    )

                result.failure?.let { rollbackError ->
                    addSuppressed(
                        primary = primaryFailure,
                        secondary = rollbackError,
                    )
                }
            } catch (rollbackError: Throwable) {
                restored[id] =
                    old.copy(fiber = null)

                addSuppressed(
                    primary = primaryFailure,
                    secondary = rollbackError,
                )
            }
        }

        managedEntries.clear()
        managedEntries.putAll(restored)
    }

    private data class ManagedEntry(
        val entry: Entry,
        val prepared: PreparedPlugin?,
        val fiber: Fiber<*>?,
    ) {

        fun canReuse(
            nextEntry: Entry,
            nextPrepared: PreparedPlugin?,
        ): Boolean {
            if (entry != nextEntry) {
                return false
            }

            if (entry.disabled) {
                return fiber == null
            }

            return prepared?.registration === nextPrepared?.registration &&
                fiber != null &&
                fiber.state != FiberState.Failed &&
                fiber.state != FiberState.Disposed
        }
    }

    private fun combineFailures(
        primary: Throwable?,
        secondary: Throwable,
    ): Throwable =
        if (primary == null) {
            secondary
        } else {
            addSuppressed(
                primary = primary,
                secondary = secondary,
            ).let { primary }
        }

    private fun addSuppressed(
        primary: Throwable,
        secondary: Throwable,
    ) {
        if (primary !== secondary) {
            primary.addSuppressed(secondary)
        }
    }
}
