package im.hikaru.harness.include

import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.Loader
import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class IncludeRefreshResult(
    val changed: Boolean,
    val snapshot: ConfigSnapshot,
)

/**
 * Serializes source loading, transforms, and reconciliation as one operation.
 * Loading/decoding failures happen before Loader is touched; reconciliation
 * itself supplies lifecycle rollback.
 */
public class IncludeService(
    private val loader: Loader,
    private val source: ConfigSource,
    transforms: List<EntryTransform> = emptyList(),
) : Disposable {
    private val mutex = Mutex()
    private val transform = CompositeEntryTransform(transforms)
    private var currentSnapshot: ConfigSnapshot? = null
    private var disposed = false

    public suspend fun snapshot(): ConfigSnapshot? =
        mutex.withLock {
            currentSnapshot
        }

    public suspend fun refresh(): IncludeRefreshResult =
        mutex.withLock {
            checkActive()

            val loaded = source.load()
            val next =
                loaded.copy(
                    entries = transform.apply(loaded.entries),
                )
            val changed = currentSnapshot != next

            loader.reconcile(next.entries)
            currentSnapshot = next

            IncludeRefreshResult(
                changed = changed,
                snapshot = next,
            )
        }

    /**
     * Reconciles first, then writes using the last loaded revision. If encoding
     * or writing fails, the Loader is restored to its exact previous snapshot.
     */
    public suspend fun save(entries: List<Entry>): ConfigSnapshot =
        mutex.withLock {
            checkActive()

            val mutableSource =
                source as? MutableConfigSource
                    ?: error("Config source is read-only")
            val previousEntries = loader.entries
            val desired = transform.apply(entries)

            loader.reconcile(desired)

            try {
                val saved =
                    mutableSource.save(
                        entries = desired,
                        expectedRevision = currentSnapshot?.revision,
                    )
                currentSnapshot = saved.copy(entries = desired)
                currentSnapshot!!
            } catch (error: Throwable) {
                try {
                    loader.reconcile(previousEntries)
                } catch (rollbackError: Throwable) {
                    if (rollbackError !== error) {
                        error.addSuppressed(rollbackError)
                    }
                }
                throw error
            }
        }

    override suspend fun dispose() {
        mutex.withLock {
            if (disposed) {
                return
            }

            disposed = true
            currentSnapshot = null
            loader.dispose()
        }
    }

    private fun checkActive() {
        check(!disposed) { "IncludeService is disposed" }
    }
}
