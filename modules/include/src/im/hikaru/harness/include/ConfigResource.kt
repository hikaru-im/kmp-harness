package im.hikaru.harness.include

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Raw configuration content together with an optional optimistic-lock token. */
public data class ResourceSnapshot(
    val content: String,
    val revision: String? = null,
)

/** Platform-neutral boundary for reading configuration bytes represented as text. */
public fun interface ConfigResource {
    public suspend fun read(): ResourceSnapshot
}

/** A resource that supports revision-aware writes. */
public interface MutableConfigResource : ConfigResource {
    public suspend fun write(
        content: String,
        expectedRevision: String? = null,
    ): ResourceSnapshot
}

/**
 * Common-test and embedded resource implementation.
 *
 * Files, classpath resources, Android assets, and remote stores should be
 * adapters around [ConfigResource] rather than responsibilities of include.
 */
public class InMemoryConfigResource(
    content: String,
) : MutableConfigResource {
    private val mutex = Mutex()
    private var content: String = content
    private var revision: Long = 1L

    override suspend fun read(): ResourceSnapshot =
        mutex.withLock {
            snapshot()
        }

    override suspend fun write(
        content: String,
        expectedRevision: String?,
    ): ResourceSnapshot =
        mutex.withLock {
            if (expectedRevision != null) {
                check(expectedRevision == revision.toString()) {
                    "Config resource revision mismatch: expected $expectedRevision, actual $revision"
                }
            }

            this.content = content
            revision += 1L
            snapshot()
        }

    private fun snapshot(): ResourceSnapshot =
        ResourceSnapshot(
            content = content,
            revision = revision.toString(),
        )
}
