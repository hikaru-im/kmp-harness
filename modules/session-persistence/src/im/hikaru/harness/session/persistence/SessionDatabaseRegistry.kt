package im.hikaru.harness.session.persistence

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Process-local guard for platform Room builders.
 *
 * A SQLite location is owned by one factory lifecycle and must never be backed
 * by two independent RoomDatabase objects. The opening and closing states also
 * prevent a second builder from racing an in-flight close.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SessionDatabaseRegistry<K> {
    private sealed interface Entry {
        class Opening(val owner: Any) : Entry

        class Ready(val owner: Any, val access: SessionDatabaseAccess) : Entry

        class Closing(val owner: Any, val access: SessionDatabaseAccess) : Entry
    }

    private val entries = AtomicReference<Map<K, Entry>>(emptyMap())

    fun acquire(
        key: K,
        owner: Any,
        builder: () -> SessionDatabaseAccess,
    ): SessionDatabaseAccess {
        while (true) {
            val current = entries.load()
            when (val entry = current[key]) {
                is Entry.Ready -> {
                    error("A session Room database is already owned for '$key'")
                }
                is Entry.Opening,
                is Entry.Closing,
                -> error("A session Room database is already opening or closing for '$key'")
                null -> {
                    if (!entries.compareAndSet(current, current + (key to Entry.Opening(owner)))) continue
                    val access =
                        try {
                            builder()
                        } catch (error: Throwable) {
                            clearOpening(key, owner)
                            throw error
                        }
                    while (true) {
                        val opening = entries.load()
                        check((opening[key] as? Entry.Opening)?.owner === owner) {
                            "Session Room database registry changed while opening '$key'"
                        }
                        if (entries.compareAndSet(opening, opening + (key to Entry.Ready(owner, access)))) {
                            return access
                        }
                    }
                }
            }
        }
    }

    fun beginClose(key: K, access: SessionDatabaseAccess): Boolean {
        while (true) {
            val current = entries.load()
            val entry = current[key] as? Entry.Ready ?: return false
            if (entry.access !== access) return false
            if (entries.compareAndSet(current, current + (key to Entry.Closing(entry.owner, access)))) return true
        }
    }

    fun finishClose(key: K, access: SessionDatabaseAccess) {
        while (true) {
            val current = entries.load()
            val entry = current[key] as? Entry.Closing ?: return
            if (entry.access !== access) return
            if (entries.compareAndSet(current, current - key)) return
        }
    }

    private fun clearOpening(key: K, owner: Any) {
        while (true) {
            val current = entries.load()
            if ((current[key] as? Entry.Opening)?.owner !== owner) return
            if (entries.compareAndSet(current, current - key)) return
        }
    }
}
