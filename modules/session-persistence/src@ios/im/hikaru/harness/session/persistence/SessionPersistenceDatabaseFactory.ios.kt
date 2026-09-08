package im.hikaru.harness.session.persistence

import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.Dispatchers

/** iOS launcher-owned factory. The caller resolves the application documents path. */
@OptIn(ExperimentalAtomicApi::class)
fun iosSessionPersistenceDatabaseFactory(databasePath: String): SessionPersistenceDatabaseFactory {
    val key = databasePath
    return object : SessionPersistenceDatabaseFactory {
        override fun create(): SessionDatabaseAccess =
            iosSessionDatabaseRegistry.acquire(key, this) {
                iosDatabaseAccess(
                    key = key,
                    database =
                        Room.databaseBuilder<SessionPersistenceDatabase>(databasePath)
                            .addMigrations(*SessionPersistenceMigrations.all)
                            .setDriver(BundledSQLiteDriver())
                            .setQueryCoroutineContext(Dispatchers.IO)
                            .build(),
                    registry = iosSessionDatabaseRegistry,
                )
            }
        }
}

private val iosSessionDatabaseRegistry = SessionDatabaseRegistry<String>()

@OptIn(ExperimentalAtomicApi::class)
private fun iosDatabaseAccess(
    key: String,
    database: SessionPersistenceDatabase,
    registry: SessionDatabaseRegistry<String>,
): SessionDatabaseAccess =
    object : SessionDatabaseAccess {
        private val disposed = AtomicBoolean(false)

        override val dao: SessionPersistenceDao
            get() {
                check(!disposed.load()) { "Session persistence database is disposed" }
                return database.sessions()
            }

        override suspend fun <T> transaction(block: suspend () -> T): T {
            check(!disposed.load()) { "Session persistence database is disposed" }
            return database.withWriteTransaction { block() }
        }

        override suspend fun dispose() {
            if (!disposed.compareAndSet(false, true)) return
            val registered = registry.beginClose(key, this)
            try {
                database.close()
            } finally {
                if (registered) registry.finishClose(key, this)
            }
        }
    }
